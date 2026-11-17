package org.avmedia.gshockGoogleSync.ui.actions

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri

/** Find Phone ring sound and volume, chosen in the Custom tab. */
object FindPhoneSettings {
    private const val PREFS = "find_phone"
    private const val KEY_URI = "sound_uri"
    private const val KEY_VOLUME = "volume_pct"
    const val DEFAULT_VOLUME = 100

    private fun p(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Chosen sound, or null for the phone's default alarm sound. */
    fun soundUri(context: Context): Uri? = p(context).getString(KEY_URI, null)?.let(Uri::parse)

    fun setSoundUri(context: Context, uri: Uri?) =
        p(context).edit().apply { if (uri == null) remove(KEY_URI) else putString(KEY_URI, uri.toString()) }.apply()

    /** Alarm-stream volume while ringing, 10..100 % of the maximum. */
    fun volumePct(context: Context): Int = p(context).getInt(KEY_VOLUME, DEFAULT_VOLUME).coerceIn(10, 100)

    fun setVolumePct(context: Context, pct: Int) = p(context).edit().putInt(KEY_VOLUME, pct.coerceIn(10, 100)).apply()

    fun soundTitle(context: Context): String {
        val uri = soundUri(context) ?: return "Default alarm sound"
        return runCatching { RingtoneManager.getRingtone(context, uri)?.getTitle(context) }.getOrNull()
            ?: uri.lastPathSegment ?: "Custom sound"
    }
}
