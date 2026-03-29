package com.gemini.voicekeyboard

import android.inputmethodservice.InputMethodService
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.material3.MaterialTheme
import kotlinx.coroutines.launch
import android.util.Log

class GeminiKeyboardService : InputMethodService(), LifecycleOwner, ViewModelStoreOwner {

    companion object {
        private const val TAG = "GeminiKeyboardService"
    }

    private lateinit var lifecycleRegistry: LifecycleRegistry
    private lateinit var viewModelStoreInstance: ViewModelStore
    private val geminiManager = GeminiLiveManager()
    private var isVoiceActive = false

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = viewModelStoreInstance

    override fun onCreate() {
        super.onCreate()
        Log.d("GeminiKeyboardService", "onCreate() called")
        lifecycleRegistry = LifecycleRegistry(this)
        viewModelStoreInstance = ViewModelStore()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onCreateInputView(): View {
        Log.d("GeminiKeyboardService", "onCreateInputView() called")
        lifecycleRegistry.currentState = Lifecycle.State.STARTED

        val view = layoutInflater.inflate(R.layout.keyboard_layout, null)

        val micButton = view.findViewById<ImageButton>(R.id.btn_mic)
        val voiceOverlay = view.findViewById<FrameLayout>(R.id.voice_overlay)
        val voiceStatus = view.findViewById<TextView>(R.id.voice_status)
        val btnVoiceStop = view.findViewById<ImageButton>(R.id.btn_voice_stop)
        val suggestionText = view.findViewById<TextView>(R.id.suggestion_text)
        val keyboardContainer = view.findViewById<FrameLayout>(R.id.keyboard_container)

        // Set up QWERTY keyboard using Compose
        val composeView = ComposeView(this).apply {
            setContent {
                MaterialTheme {
                    QwertyKeyboard(
                        onKeyClick = { char -> handleKeyInput(char) },
                        onDelete = { handleDelete() },
                        onSpace = { handleSpace() }
                    )
                }
            }
        }
        keyboardContainer.addView(composeView)

        micButton.setOnClickListener {
            toggleVoiceInput(voiceOverlay, voiceStatus, btnVoiceStop)
        }

        btnVoiceStop.setOnClickListener {
            toggleVoiceInput(voiceOverlay, voiceStatus, btnVoiceStop)
        }

        // Observe Gemini state
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    geminiManager.state.collect { state ->
                        when (state) {
                            is GeminiLiveManager.SessionState.Connecting -> {
                                voiceStatus.text = getString(R.string.connecting)
                            }
                            is GeminiLiveManager.SessionState.Connected -> {
                                voiceStatus.text = getString(R.string.listening)
                                btnVoiceStop.setBackgroundResource(R.drawable.mic_button_active_bg)
                            }
                            is GeminiLiveManager.SessionState.Listening -> {
                                voiceStatus.text = getString(R.string.listening)
                                btnVoiceStop.setBackgroundResource(R.drawable.mic_button_active_bg)
                            }
                            is GeminiLiveManager.SessionState.Processing -> {
                                voiceStatus.text = "Processing…"
                            }
                            is GeminiLiveManager.SessionState.Error -> {
                                voiceStatus.text = "${getString(R.string.error_connection)}: ${state.message}"
                            }
                            is GeminiLiveManager.SessionState.Idle -> {
                                voiceStatus.text = getString(R.string.tap_to_speak)
                                btnVoiceStop.setBackgroundResource(R.drawable.mic_button_bg)
                            }
                        }
                    }
                }
                launch {
                    geminiManager.transcribedText.collect { text ->
                        if (text.isNotEmpty()) {
                            suggestionText.text = text
                            commitText(text)
                        }
                    }
                }
            }
        }

        return view
    }

    private fun toggleVoiceInput(
        voiceOverlay: FrameLayout,
        voiceStatus: TextView,
        btnVoiceStop: ImageButton
    ) {
        if (isVoiceActive) {
            isVoiceActive = false
            voiceOverlay.visibility = View.GONE
            geminiManager.stop()
        } else {
            isVoiceActive = true
            voiceOverlay.visibility = View.VISIBLE
            voiceStatus.text = getString(R.string.connecting)

            val apiKey = ApiKeyStore.get(this)
            if (apiKey.isBlank()) {
                voiceStatus.text = getString(R.string.enter_api_key)
                return
            }

            geminiManager.connect(apiKey) { transcription ->
                lifecycleScope.launch {
                    commitText(transcription)
                }
            }
        }
    }

    private fun handleKeyInput(char: Char) {
        currentInputConnection?.commitText(char.toString(), 1)
    }

    private fun commitText(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    private fun handleDelete() {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (selected.isNullOrEmpty()) {
            ic.deleteSurroundingText(1, 0)
        } else {
            ic.commitText("", 1)
        }
    }

    private fun handleSpace() {
        currentInputConnection?.commitText(" ", 1)
    }

    override fun onDestroy() {
        geminiManager.stop()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }
}
