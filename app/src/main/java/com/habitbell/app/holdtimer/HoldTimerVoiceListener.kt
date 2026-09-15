package com.habitbell.app.holdtimer

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * # VoiceCommandSource
 *
 * Abstraction governing hands-free voice command capture and recognition.
 */
interface VoiceCommandSource {
    /** Begins listening for spoken user commands. */
    fun startListening()

    /** Halts voice capture. */
    fun stopListening()

    /** Whether the listener is actively capturing microphone audio. */
    val isListening: Boolean

    /** Callback invoked whenever a valid [VoiceHoldCommand] is extracted from speech. */
    var onCommandRecognized: ((command: VoiceHoldCommand) -> Unit)?

    /** Releases system speech recognizer resources. */
    fun release()
}

/**
 * # AndroidSpeechCommandSource
 *
 * Production hands-free voice command listener utilizing Android's on-device [SpeechRecognizer].
 *
 * ## Architectural Role & Component Relationships
 * - Operates entirely offline with [RecognizerIntent.EXTRA_PREFER_OFFLINE] set to true.
 * - Parses incoming speech transcripts via [VoiceHoldTimerParser].
 * - Automatically re-arms listening upon end-of-speech to provide a continuous hands-free experience.
 *
 * ## Lifecycle & Concurrency
 * Bound to the application UI / Main looper required by Android's `SpeechRecognizer` API.
 *
 * @param context Android application context.
 */
class AndroidSpeechCommandSource(
    private val context: Context
) : VoiceCommandSource, RecognitionListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListeningInternal = false
    private var isExplicitlyStopped = true

    override val isListening: Boolean
        get() = isListeningInternal

    override var onCommandRecognized: ((command: VoiceHoldCommand) -> Unit)? = null

    init {
        initRecognizer()
    }

    private fun initRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w("AndroidSpeechCommandSource", "Android SpeechRecognizer is not available on this device")
            return
        }
        speechRecognizer?.destroy()
        speechRecognizer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
        speechRecognizer?.setRecognitionListener(this)
    }

    override fun startListening() {
        isExplicitlyStopped = false
        if (speechRecognizer == null) initRecognizer()
        if (speechRecognizer == null) return

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        try {
            speechRecognizer?.startListening(intent)
            isListeningInternal = true
        } catch (e: Exception) {
            Log.e("AndroidSpeechCommandSource", "Error starting speech recognition: ${e.message}")
            isListeningInternal = false
        }
    }

    override fun stopListening() {
        isExplicitlyStopped = true
        isListeningInternal = false
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.e("AndroidSpeechCommandSource", "Error stopping speech recognition: ${e.message}")
        }
    }

    override fun release() {
        stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    // --- RecognitionListener Callbacks ---

    override fun onReadyForSpeech(params: Bundle?) {
        isListeningInternal = true
    }

    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        isListeningInternal = false
    }

    override fun onError(error: Int) {
        isListeningInternal = false
        // Re-arm listener for hands-free continuous monitoring unless stopped
        if (!isExplicitlyStopped && error != SpeechRecognizer.ERROR_CLIENT) {
            startListening()
        }
    }

    override fun onResults(results: Bundle?) {
        isListeningInternal = false
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            for (candidate in matches) {
                val command = VoiceHoldTimerParser.parse(candidate)
                if (command !is VoiceHoldCommand.Unknown) {
                    onCommandRecognized?.invoke(command)
                    break
                }
            }
        }
        // Continuous hands-free loop
        if (!isExplicitlyStopped) {
            startListening()
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
}

/**
 * # SimulatedVoiceCommandSource
 *
 * Deterministic synthetic voice command provider used for automated testing
 * and environments without physical speech recognition.
 */
class SimulatedVoiceCommandSource : VoiceCommandSource {
    private var isListeningInternal = false
    override val isListening: Boolean get() = isListeningInternal
    override var onCommandRecognized: ((command: VoiceHoldCommand) -> Unit)? = null

    override fun startListening() {
        isListeningInternal = true
    }

    override fun stopListening() {
        isListeningInternal = false
    }

    override fun release() {
        isListeningInternal = false
    }

    /**
     * Injects a synthetic utterance directly into the parser and command dispatcher.
     *
     * @param utterance Spoken command string.
     */
    fun dispatchSpokenUtterance(utterance: String) {
        val command = VoiceHoldTimerParser.parse(utterance)
        onCommandRecognized?.invoke(command)
    }
}
