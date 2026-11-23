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
import org.avmedia.gshockapi.io.ActivityItemCodec
import org.avmedia.gshockapi.io.H5600HealthIO
import org.avmedia.gshockapi.io.IO
import org.avmedia.gshockapi.io.SafeSettingWrite
import org.avmedia.gshockapi.io.SettingWriteResult
import org.avmedia.gshockapi.io.TideSettingCodec

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

    /** Why the last read of each code gave nothing: a refusal with its status, a timeout, or the raw bytes of an answer that is too short. */
    private val problems = java.util.concurrent.ConcurrentHashMap<Int, String>()

    /** The plain-words reasons for the failed reads of [codes], or null when none was recorded. */
    fun explain(vararg codes: Int): String? =
        codes.toList().mapNotNull { c -> problems[c]?.let { "0x%02X: %s".format(c, it) } }.joinToString(" ").ifEmpty { null }

    /** The request bytes for [code]: 0x3D is read as `3D 30` and never bare; every other code is the code alone. */
    fun requestFor(code: Int): ByteArray = SettingRequests.forCode(code)

    /** The answer to request [code] as frame bytes (code first), or null when the watch did not answer; [explain] says why. */
    suspend fun read(code: Int): ByteArray? {
        problems.remove(code)
        if (isBusy) return null
        val request = requestFor(code)
        val reply = raw.read(request, REPLY_TIMEOUT_MS) { it.size > 1 && (request.size < 2 || it[1] == request[1]) }
        val frame = reply.frame
        if (frame == null) {
            problems[code] = reply.failure ?: "no answer"
            return null
        }
        val tooShort = SettingRequests.tooShort(code, frame)
        if (tooShort != null) {
            problems[code] = tooShort
            return null
        }
        return frame
    }

    /** The answer to a request of several bytes (first byte = code), kept only when [accept] says it is the one asked for. */
    suspend fun readRequest(request: ByteArray, accept: (ByteArray) -> Boolean): ByteArray? {
        if (isBusy) return null
        val code = request[0].toInt() and 0xFF
        problems.remove(code)
        val reply = raw.read(request, REPLY_TIMEOUT_MS, accept)
        if (reply.frame == null) problems[code] = reply.failure ?: "no answer"
        return reply.frame
    }

    /** A per-activity read such as 4E + activity or 56 + activity; the answer must name the same activity. */
    suspend fun readForActivity(code: Int, activity: Int): ByteArray? =
        readRequest(byteArrayOf(code.toByte(), activity.toByte())) { it.size > 1 && (it[1].toInt() and 0xFF) == activity }

    /** One wrapped item of an activity (0x54), as the watch answered it. */
    suspend fun readItem(activity: Int, cls: Int): ByteArray? =
        readRequest(ActivityItemCodec.request(activity, cls)) { ActivityItemCodec.parse(it, activity, cls) != null }

    /** Changes the wrapped value of [cls] for [activity]: read, change only [innerTouched] bytes of it, write, read back. */
    suspend fun updateItem(
        activity: Int,
        cls: Int,
        innerTouched: Set<Int>,
        change: (ByteArray) -> ByteArray?,
    ): SettingWriteResult {
        if (isBusy) return SettingWriteResult.Failed("The watch is sending health data. Try again in a minute.")
        val item = readItem(activity, cls)?.let { ActivityItemCodec.parse(it, activity, cls) }
            ?: return SettingWriteResult.Failed("The watch did not answer. Nothing was changed.")
        return writer.updateWith(ActivityItemCodec.CODE, { readItem(activity, cls) }, ActivityItemCodec.touched(item, innerTouched)) { frame ->
            val now = ActivityItemCodec.parse(frame, activity, cls)
            if (now == null || now.offset != item.offset) {
                null
            } else {
                ActivityItemCodec.withInner(frame, now, change(ActivityItemCodec.inner(frame, now)))
            }
        }
    }

    /** A 0x2E value (tide setting or own point): the answer must name the same unit. */
    suspend fun readTide(unit: Int): ByteArray? =
        readRequest(byteArrayOf(TideSettingCodec.CODE.toByte(), unit.toByte())) { it.size > 2 && (it[1].toInt() and 0xFF) == unit }

    /** Read, change only [touched] bytes of the 0x2E value of [unit], write, read back. */
    suspend fun updateTide(unit: Int, touched: Set<Int>, change: (ByteArray) -> ByteArray?): SettingWriteResult {
        if (isBusy) return SettingWriteResult.Failed("The watch is sending health data. Try again in a minute.")
        return writer.updateWith(TideSettingCodec.CODE, { readTide(unit) }, touched, change = change)
    }

    suspend fun update(
        code: Int,
        touched: Set<Int>,
        change: (ByteArray) -> ByteArray?,
        expectedBack: (before: ByteArray, written: ByteArray) -> ByteArray = { _, written -> written },
        matches: (back: ByteArray, expected: ByteArray) -> Boolean = { back, expected -> back.contentEquals(expected) },
    ): SettingWriteResult {
        if (isBusy) return SettingWriteResult.Failed("The watch is sending health data. Try again in a minute.")
        val result = writer.update(code, touched, expectedBack = expectedBack, matches = matches, change = change)
        if (result == SettingWriteResult.Done) RawSettings.shared.forget(code)
        return result
    }

    private companion object {
        val REPLY_TIMEOUT_MS = RawWatchRequest.READ_TIMEOUT_MS
        const val SETTLE_MS = 1_000L
    }
}

/** Plain text for the person using the app. */
fun SettingWriteResult.text(what: String): String =
    when (this) {
        SettingWriteResult.Done -> "$what saved and read back from the watch."
        SettingWriteResult.Unchanged -> "$what is already like that."
        is SettingWriteResult.Failed -> message
    }

/** What a settings read sends, and the shortest answer each known layout needs (notes D2). */
object SettingRequests {
    private const val NOTIFICATION_MANAGER = 0x3D
    private const val PROFILE = 0x45
    private const val NOTIFICATION_SOUND = 0x42

    /** Shortest answer in value bytes (after the code, and after the `30` of 0x3D). */
    private val MIN_VALUE = mapOf(PROFILE to 9, NOTIFICATION_SOUND to 6, NOTIFICATION_MANAGER to 17)

    fun forCode(code: Int): ByteArray =
        if (code == NOTIFICATION_MANAGER) byteArrayOf(NOTIFICATION_MANAGER.toByte(), 0x30) else byteArrayOf(code.toByte())

    /** Plain words with the raw hex when [frame] is shorter than the layout needs; null when it is long enough. */
    fun tooShort(code: Int, frame: ByteArray): String? {
        val need = MIN_VALUE[code] ?: return null
        val have = frame.size - (if (code == NOTIFICATION_MANAGER) 2 else 1)
        if (have >= need) return null
        return "the answer has $have value bytes, at least $need are needed. Raw: " + frame.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
    }
}
