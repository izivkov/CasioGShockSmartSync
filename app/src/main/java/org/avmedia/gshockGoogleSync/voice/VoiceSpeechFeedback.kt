package org.avmedia.gshockGoogleSync.voice

import android.content.Context
import android.speech.tts.TextToSpeech
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

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Timber.w("TTS Language not supported or missing data")
            }
            isInitialized = true
            pendingText?.let {
                speak(it)
                pendingText = null
            }
        } else {
            Timber.e("TTS Initialization failed with status: $status")
        }
    }

    fun speak(text: String) {
        if (isInitialized) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "VoiceFeedback")
        } else {
            pendingText = text
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
