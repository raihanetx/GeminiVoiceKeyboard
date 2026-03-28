package com.gemini.voicekeyboard

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64
import java.util.concurrent.TimeUnit

class GeminiLiveManager {

    companion object {
        private const val TAG = "GeminiLiveManager"
        private const val MODEL = "gemini-3.1-flash-live-preview"
        private const val SEND_SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val CHUNK_SIZE = 4096
        private const val AUTH_TOKENS_URL = "https://generativelanguage.googleapis.com/v1alpha/authTokens:create"
    }

    private var webSocket: WebSocket? = null
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var scopeJob: Job? = null
    private var scope: CoroutineScope? = null
    private var httpClient: OkHttpClient? = null

    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)
    val state: StateFlow<SessionState> = _state

    private val _transcribedText = MutableStateFlow("")
    val transcribedText: StateFlow<String> = _transcribedText

    sealed class SessionState {
        data object Idle : SessionState()
        data object Connecting : SessionState()
        data object Listening : SessionState()
        data object Processing : SessionState()
        data class Error(val message: String) : SessionState()
        data object Connected : SessionState()
    }

    fun connect(apiKey: String, onTranscription: (String) -> Unit) {
        if (_state.value != SessionState.Idle) {
            Log.w(TAG, "Already connected or connecting")
            return
        }

        _state.value = SessionState.Connecting
        val clientJob = Job()
        val clientScope = CoroutineScope(Dispatchers.IO + clientJob)
        scope = clientScope
        scopeJob = clientJob

        httpClient = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .build()

        clientScope.launch {
            try {
                val token = getEphemeralToken(apiKey)
                if (token == null) {
                    _state.value = SessionState.Error("Failed to get auth token")
                    return@launch
                }
                connectWebSocket(token, onTranscription)
            } catch (e: Exception) {
                Log.e(TAG, "Connection error", e)
                _state.value = SessionState.Error(e.message ?: "Connection failed")
            }
        }
    }

    private fun getEphemeralToken(apiKey: String): String? {
        val requestBody = JSONObject().apply {
            put("uses", 1)
        }.toString()

        val request = Request.Builder()
            .url("$AUTH_TOKENS_URL?key=$apiKey")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val client = httpClient ?: OkHttpClient()
        val response = client.newCall(request).execute()

        return if (response.isSuccessful) {
            val body = response.body?.string()
            val json = JSONObject(body)
            val name = json.optString("name")
            Log.d(TAG, "Got ephemeral token")
            name
        } else {
            Log.e(TAG, "Token request failed: ${response.code} ${response.message}")
            val errorBody = response.body?.string()
            Log.e(TAG, "Error body: $errorBody")
            null
        }
    }

    private fun connectWebSocket(token: String, onTranscription: (String) -> Unit) {
        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContentConstrained?access_token=$token"

        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = httpClient?.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket opened")
                sendSetup(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleResponse(text, onTranscription)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                _state.value = SessionState.Error(t.message ?: "Connection failed")
                stop()
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code $reason")
                webSocket.close(1000, null)
            }
        })
    }

    private fun sendSetup(ws: WebSocket) {
        val setup = JSONObject().apply {
            put("setup", JSONObject().apply {
                put("model", "models/$MODEL")
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().put("AUDIO"))
                    put("speechConfig", JSONObject().apply {
                        put("voiceConfig", JSONObject().apply {
                            put("prebuiltVoiceConfig", JSONObject().apply {
                                put("voiceName", "Puck")
                            })
                        })
                    })
                })
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().apply {
                        put("text", "You are a voice-to-text transcription assistant. When the user speaks, listen carefully and repeat back exactly what they said, word for word. Do not add any commentary, greeting, or explanation. Just output the exact transcription of their speech.")
                    }))
                })
                put("inputAudioTranscription", JSONObject())
                put("outputAudioTranscription", JSONObject())
                put("realtimeInputConfig", JSONObject().apply {
                    put("automaticActivityDetection", JSONObject().apply {
                        put("disabled", false)
                        put("silenceDurationMs", 2000)
                        put("prefixPaddingMs", 500)
                    })
                    put("turnCoverage", "TURN_INCLUDES_ONLY_ACTIVITY")
                })
            })
        }
        ws.send(setup.toString())
        Log.d(TAG, "Sent setup message")
    }

    private fun handleResponse(text: String, onTranscription: (String) -> Unit) {
        try {
            val json = JSONObject(text)

            if (json.has("setupComplete")) {
                Log.d(TAG, "Setup complete")
                _state.value = SessionState.Connected
                startRecording()
                return
            }

            val serverContent = json.optJSONObject("serverContent") ?: return

            // Input transcription - what the user said (this is what we want)
            val inputTranscription = serverContent.optJSONObject("inputTranscription")
            if (inputTranscription != null) {
                val userText = inputTranscription.optString("text", "")
                val finished = inputTranscription.optBoolean("finished", false)
                if (userText.isNotEmpty()) {
                    Log.d(TAG, "Input transcription (finished=$finished): $userText")
                    if (finished) {
                        _transcribedText.value = userText
                        onTranscription(userText)
                    }
                }
            }

            // Output transcription - what Gemini responded (for logging)
            val outputTranscription = serverContent.optJSONObject("outputTranscription")
            if (outputTranscription != null) {
                val geminiText = outputTranscription.optString("text", "")
                if (geminiText.isNotEmpty()) {
                    Log.d(TAG, "Output transcription: $geminiText")
                }
            }

            // Handle audio data from model (we ignore this for keyboard)
            val modelTurn = serverContent.optJSONObject("modelTurn")
            if (modelTurn != null) {
                val parts = modelTurn.optJSONArray("parts")
                if (parts != null) {
                    for (i in 0 until parts.length()) {
                        val part = parts.getJSONObject(i)
                        if (part.has("inlineData")) {
                            Log.d(TAG, "Received audio chunk (ignored)")
                        }
                    }
                }
            }

            // Turn complete
            if (serverContent.optBoolean("turnComplete", false)) {
                Log.d(TAG, "Turn complete")
                _state.value = SessionState.Connected
            }

            // Interrupted
            if (serverContent.optBoolean("interrupted", false)) {
                Log.d(TAG, "Interrupted")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing response: ${e.message}", e)
        }
    }

    fun startRecording() {
        val clientScope = scope ?: return
        _state.value = SessionState.Listening

        val bufferSize = AudioRecord.getMinBufferSize(SEND_SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Invalid buffer size")
            _state.value = SessionState.Error("Audio config error")
            return
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SEND_SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize * 2
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord not initialized")
                _state.value = SessionState.Error("Microphone not available")
                return
            }

            audioRecord?.startRecording()
            Log.d(TAG, "Recording started, buffer size: $bufferSize")

            recordingJob = clientScope.launch {
                val buffer = ByteArray(CHUNK_SIZE)
                while (isActive && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (bytesRead > 0) {
                        sendAudio(buffer.copyOf(bytesRead))
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "No RECORD_AUDIO permission", e)
            _state.value = SessionState.Error("Microphone permission required")
        }
    }

    private fun sendAudio(audioData: ByteArray) {
        val base64Audio = Base64.getEncoder().encodeToString(audioData)
        val message = JSONObject().apply {
            put("realtimeInput", JSONObject().apply {
                put("audio", JSONObject().apply {
                    put("mimeType", "audio/pcm;rate=$SEND_SAMPLE_RATE")
                    put("data", base64Audio)
                })
            })
        }
        webSocket?.send(message.toString())
    }

    fun stop() {
        try {
            recordingJob?.cancel()
            recordingJob = null

            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null

            webSocket?.close(1000, "User stopped")
            webSocket = null

            httpClient?.dispatcher?.executorService?.shutdown()
            httpClient = null

            scopeJob?.cancel()
            scopeJob = null
            scope = null

            _state.value = SessionState.Idle
            _transcribedText.value = ""
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping", e)
        }
    }

    fun isActive(): Boolean = _state.value != SessionState.Idle
}
