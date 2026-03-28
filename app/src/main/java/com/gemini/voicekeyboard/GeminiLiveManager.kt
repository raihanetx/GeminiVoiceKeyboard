package com.gemini.voicekeyboard

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.FunctionCallPart
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.liveGenerationConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject

@OptIn(PublicPreviewAPI::class)
class GeminiLiveManager(private val context: android.content.Context) {

    companion object {
        private const val TAG = "GeminiLiveManager"
        private const val MODEL_NAME = "gemini-2.5-flash-native-audio-preview-09-2025"
    }

    private var liveSession: LiveSession? = null

    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)
    val state: StateFlow<SessionState> = _state

    sealed class SessionState {
        data object Idle : SessionState()
        data object Connecting : SessionState()
        data object Listening : SessionState()
        data object Processing : SessionState()
        data class Error(val message: String) : SessionState()
        data class Result(val text: String) : SessionState()
    }

    private fun handler(functionCall: FunctionCallPart): FunctionResponsePart {
        return FunctionResponsePart(functionCall.name, JsonObject(emptyMap()), functionCall.id)
    }

    suspend fun startSession(onTranscription: (String) -> Unit) {
        try {
            _state.value = SessionState.Connecting

            val liveGenerationConfig = liveGenerationConfig {
                speechConfig = SpeechConfig(voice = Voice("CHARON"))
                responseModality = ResponseModality.AUDIO
            }

            val liveModel = Firebase.ai(backend = GenerativeBackend.googleAI())
                .liveModel(
                    modelName = MODEL_NAME,
                    generationConfig = liveGenerationConfig
                )

            liveSession = liveModel.connect()
            _state.value = SessionState.Listening

            liveSession?.startAudioConversation(::handler)

        } catch (e: Exception) {
            Log.e(TAG, "Error starting session", e)
            _state.value = SessionState.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun stopSession() {
        try {
            liveSession?.stopAudioConversation()
            liveSession = null
            _state.value = SessionState.Idle
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping session", e)
        }
    }

    fun isSessionActive(): Boolean = liveSession != null && _state.value != SessionState.Idle
}
