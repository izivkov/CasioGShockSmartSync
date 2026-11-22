package org.avmedia.gshockGoogleSync.ui.watchhealth

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import org.avmedia.gshockGoogleSync.session.WatchModelOverride
import org.avmedia.gshockGoogleSync.ui.health.H5600Session
import org.avmedia.gshockGoogleSync.webhook.RawSettings
import org.avmedia.gshockGoogleSync.webhook.RawWatchRequest
import org.avmedia.gshockGoogleSync.webhook.WatchDecoders
import org.avmedia.gshockapi.WatchInfo
import org.avmedia.gshockapi.ble.GetSetMode
import org.avmedia.gshockapi.io.H5600HealthIO
import org.avmedia.gshockapi.io.IO
import org.avmedia.gshockapi.io.SafeSettingWrite
import org.avmedia.gshockapi.io.SettingWriteResult

/** True when the connected watch is of the DW-H5600 family (the one whose settings these screens know). */
fun isH5600Watch(): Boolean = WatchModelOverride.isH5600Family(WatchModelOverride.displayName(WatchInfo.name))

/**
 * Reads and writes watch settings for the Watch face and Watch health settings screens.
 * Every write goes through [SafeSettingWrite] (read, change only the decoded fields, write, read back).
 * Nothing is read or written while a LifeLog transfer runs: both use the same link.
 */
@Singleton
class WatchSettingsAccess @Inject constructor(private val raw: RawWatchRequest) {
    private val writer =
        SafeSettingWrite(
            read = { read(it) },
            write = { IO.writeCmd(GetSetMode.SET, it) },
            settle = { delay(SETTLE_MS) },
        )

    val isBusy: Boolean
        get() = H5600HealthIO.isActive || H5600Session.inFlight.get()

    /** The answer to request [code] as frame bytes (code first), or null when the watch did not answer. */
    suspend fun read(code: Int): ByteArray? {
        if (isBusy) return null
        val bytes = raw.request(code, REPLY_TIMEOUT_MS).firstNotNullOfOrNull { answer ->
            WatchDecoders.bytes(answer)?.takeIf { it.firstOrNull() == code && it.size > 1 }
        } ?: return null
        return ByteArray(bytes.size) { bytes[it].toByte() }
    }

    suspend fun update(
        code: Int,
        touched: Set<Int>,
        expectedBack: (before: ByteArray, written: ByteArray) -> ByteArray = { _, written -> written },
        change: (ByteArray) -> ByteArray?,
    ): SettingWriteResult {
        if (isBusy) return SettingWriteResult.Failed("The watch is sending health data. Try again in a minute.")
        val result = writer.update(code, touched, expectedBack, change)
        if (result == SettingWriteResult.Done) RawSettings.shared.forget(code)
        return result
    }

    private companion object {
        const val REPLY_TIMEOUT_MS = 2_000L
        const val SETTLE_MS = 700L
    }
}

/** Plain text for the person using the app. */
fun SettingWriteResult.text(what: String): String =
    when (this) {
        SettingWriteResult.Done -> "$what saved and read back from the watch."
        SettingWriteResult.Unchanged -> "$what is already like that."
        is SettingWriteResult.Failed -> message
    }
