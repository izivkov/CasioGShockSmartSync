package org.avmedia.gshockGoogleSync.h5600

import android.content.Context
import android.content.SharedPreferences

/** The one SharedPreferences file all DW-H5600 additions keep their settings in. */
object H5600Prefs {
    const val NAME = "h5600_prefs"

    /** Weather trick: what the watch's top line was last sent ("W:TT") and when (epoch ms). */
    const val KEY_LAST_SENT_TOP = "last_sent_top"
    const val KEY_LAST_SENT_AT = "last_sent_at"

    fun get(context: Context): SharedPreferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
}
