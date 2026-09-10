package org.avmedia.gshockGoogleSync.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class VoiceCommandManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var speechRecognizer: SpeechRecognizer? = null

    // A couple of error codes reported by Android's speech service are
    // transient rather than real failures - most commonly ERROR_SERVER_DISCONNECTED,
    // which the on-device recognizer throws sporadically right after starting a
    // session. Recreating the recognizer and retrying once clears these up
    // without bothering the user.
    private val transientErrors = setOf(
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY
    )
    private val maxRetries = 1

    fun isRecognitionAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun startListening(onResult: (String) -> Unit, onError: (String) -> Unit) {
        startListeningInternal(onResult, onError, retryCount = 0)
    }

    private fun startListeningInternal(
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        retryCount: Int
    ) {
        if (!isRecognitionAvailable()) {
            onError("Speech recognition not available")
            return
        }

        stopListening()

        var resultsDelivered = false

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    if (resultsDelivered) return

                    if (error in transientErrors && retryCount < maxRetries) {
                        Timber.w("Transient speech recognition error (code: $error), retrying...")
                        startListeningInternal(onResult, onError, retryCount + 1)
                        return
                    }

                    val message = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No match found"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
                        SpeechRecognizer.ERROR_SERVER -> "Server error"
                        SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "Speech service disconnected"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                        else -> "Unknown error (code: $error)"
                    }
                    Timber.w("Speech recognition error: $message (code: $error)")
                    onError(message)
                }

                override fun onResults(results: Bundle?) {
                    resultsDelivered = true
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        onResult(matches[0])
                    } else {
                        onError("No results found")
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)

                // IntentParser only understands English text. Without this, the
                // recognizer follows the phone's system locale - so a phone set
                // to e.g. Bulgarian would transcribe against a Bulgarian
                // acoustic/language model even for a user speaking English
                // commands, degrading recognition accuracy for no benefit
                // (nothing downstream can act on non-English text anyway).
                // Pinning this makes behavior predictable regardless of locale.
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")

                // Increase silence timeouts for slower/hesitant speakers
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2000L)
            }
            startListening(intent)
        }
    }

    fun stopListening() {
        speechRecognizer?.apply {
            stopListening()
            cancel()
            destroy()
        }
        speechRecognizer = null
    }
}
