package org.avmedia.gshockGoogleSync.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceSpeechFeedback @Inject constructor(
    @ApplicationContext private val context: Context
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var pendingText: String? = null
    private var onFinishedCallback: (() -> Unit)? = null

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val locale = Locale.getDefault()
            val result = tts?.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Timber.w("TTS Language $locale not supported, falling back to US")
                tts?.setLanguage(Locale.US)
            }

            // Set modern audio attributes for better routing
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            tts?.setAudioAttributes(audioAttributes)

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Timber.d("TTS started: $utteranceId")
                }

                override fun onDone(utteranceId: String?) {
                    Timber.d("TTS done: $utteranceId")
                    if (utteranceId == "InitialPrompt") {
                        onFinishedCallback?.invoke()
                        onFinishedCallback = null
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    Timber.e("TTS error: $utteranceId")
                    if (utteranceId == "InitialPrompt") {
                        onFinishedCallback?.invoke()
                        onFinishedCallback = null
                    }
                }
            })

            isInitialized = true
            
            // Log current volume for diagnostics
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            Timber.d("TTS Initialized. Media Volume: $volume/$maxVolume")

            pendingText?.let {
                speak(it, onFinishedCallback)
                pendingText = null
                onFinishedCallback = null
            }
        } else {
            Timber.e("TTS Initialization failed with status: $status")
        }
    }

    fun speak(text: String, onFinished: (() -> Unit)? = null) {
        Timber.d("Request to speak: '$text' (Initialized: $isInitialized)")
        if (isInitialized) {
            val utteranceId = if (onFinished != null) "InitialPrompt" else "VoiceFeedback"
            onFinishedCallback = onFinished
            
            val params = Bundle()
            // Ensure we use the music stream which is most likely to be audible
            params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
            
            val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            if (result == TextToSpeech.ERROR) {
                Timber.e("TTS speak() returned ERROR")
                onFinished?.invoke()
            }
        } else {
            pendingText = text
            onFinishedCallback = onFinished
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
