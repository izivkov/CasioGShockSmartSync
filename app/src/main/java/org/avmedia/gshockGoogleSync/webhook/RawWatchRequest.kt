package org.avmedia.gshockGoogleSync.webhook

import javax.inject.Inject
import javax.inject.Singleton
import org.avmedia.gshockapi.io.RawSettingIO

fun interface RawRequester {
    suspend fun request(code: Int, timeoutMs: Long): List<String>
}

/**
 * The webhook variables' view of the one settings reader ([RawSettingIO]): the answer to read-request [code] as the
 * text the raw cache keeps ("0x41 01 02"), or nothing. It has no request path of its own: the library's
 * notification handler is never replaced, and nothing is read while a LifeLog transfer runs.
 */
@Singleton
class RawWatchRequest @Inject constructor() : RawRequester {
    override suspend fun request(code: Int, timeoutMs: Long): List<String> =
        RawSettingIO.read(RawSettingIO.requestFor(code), timeoutMs).frame
            ?.let { listOf("0x" + it.joinToString(" ") { b -> "%02X".format(b.toInt() and 0xFF) }) }
            .orEmpty()
}
