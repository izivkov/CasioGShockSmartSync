package org.avmedia.gshockapi.protocols

import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.ble.GetSetMode
import org.avmedia.gshockapi.casio.CasioTimeZoneHelper
import org.avmedia.gshockapi.io.ButtonPressedIO
import org.avmedia.gshockapi.io.TimeAdjustmentIO
import org.avmedia.gshockapi.io.TimeIO
import org.avmedia.gshockapi.model.Alarm
import org.avmedia.gshockapi.model.ExerciseProfile
import org.avmedia.gshockapi.model.Settings
import org.avmedia.gshockapi.utils.Utils
import timber.log.Timber
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Protocol for Oceanus analog watches such as the OCW-S400 (module 5728).
 *
 * These watches are supported for pairing and time sync, plus battery/temperature and the
 * auto-sync on/off setting (BLE settings, 0x11, encoded by [StandardProtocol]). Time is set
 * with the commands the official app sends for it: the home-time DST records are read,
 * written back with only the time-zone fields (UTC offset, DST shift and rule, DST in
 * effect) updated for the phone's time zone, and then the current time is written. The
 * home city is kept. Alarms, timer, world cities and the like do no I/O.
 */
@RequiresApi(Build.VERSION_CODES.O)
object OceanusProtocol : StandardProtocol() {
    override val dataReceivedHandlers: Map<Int, (String) -> Unit>
        get() =
            super.dataReceivedHandlers +
                mapOf(
                    // DST watch state, DST setting
                    0x1D to OceanusIO::onReceived,
                    0x1E to OceanusIO::onReceived,
                    // BLE settings: also read by the pairing sequence
                    0x11 to { data: String ->
                        OceanusIO.onReceived(data)
                        TimeAdjustmentIO.onReceived(data)
                    },
                )

    override suspend fun setTime(
        timeMs: Long?,
        offset: Long?,
    ) {
        val kind = ButtonPressedIO.getConnectionKind()
        if (kind !in OceanusTimeSync.TIME_SYNC_KINDS) {
            Timber.w("OceanusProtocol: not setting time on connection kind $kind")
            return
        }

        val casioTimeZone = TimeIO.getCasioTimezone()
        val zoneId = casioTimeZone.zoneId
        val homeZone = OceanusTimeSync.homeZone(zoneId, casioTimeZone.dstRules, Instant.now())
        OceanusTimeSync.run(OceanusIO, homeZone) {
            val ms = timeMs ?: (Clock.systemDefaultZone().millis() + (offset ?: 0L))
            LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), zoneId)
        }
    }

    /**
     * On a pairing connection (kind 0: the watch was unpaired and (A) held 2.5 s), writes
     * app information and BLE settings as the official app does, then sets the time.
     */
    override suspend fun onInitialized() {
        if (ButtonPressedIO.getConnectionKind() != OceanusTimeSync.PAIRING_KIND) return
        val zoneId = ZoneId.systemDefault()
        val rule = CasioTimeZoneHelper.findTimeZone(zoneId.id).dstRules
        runCatching {
            OceanusTimeSync.run(OceanusIO, OceanusTimeSync.homeZone(zoneId, rule, Instant.now()), pairing = true) {
                LocalDateTime.now(zoneId)
            }
        }.onSuccess { Timber.i("OceanusProtocol: paired") }
            .onFailure { Timber.e(it, "OceanusProtocol: pairing stopped") }
    }

    /**
     * BLE settings (auto-sync on/off) are only written on the connection the official app
     * writes them on: (A) held 2.5 s, app open.
     */
    override fun setSettings(settings: Settings) {
        val kind = ButtonPressedIO.getConnectionKind()
        if (kind != OceanusTimeSync.SETTINGS_KIND) {
            Timber.w("OceanusProtocol: not writing settings on connection kind $kind")
            return
        }
        super.setSettings(settings)
    }

    override suspend fun getTimer(): Int = 0

    override fun setTimer(timerValue: Int) = Unit

    override suspend fun getHomeTime(): String = ""

    override suspend fun getAlarms(): ArrayList<Alarm> = ArrayList()

    override fun setAlarms(alarms: ArrayList<Alarm>) = Unit

    override suspend fun getExerciseProfile(): ExerciseProfile = ExerciseProfile()

    override fun setExerciseProfile(profile: ExerciseProfile) = Unit
}

/** The reads and writes the time-sync script needs. */
interface OceanusLink {
    /** Sends a read request and returns the watch's reply, whose bytes start with [request]. */
    suspend fun read(request: ByteArray): ByteArray

    /** Writes a command. Calls must reach the watch in order; throws if the write fails. */
    suspend fun write(command: ByteArray)
}

class OceanusSyncException(message: String) : Exception(message)

/**
 * Pure functional core for Oceanus time sync: encoders and the time-sync script.
 * The script only depends on an [OceanusLink], so it can be tested without a watch.
 */
@RequiresApi(Build.VERSION_CODES.O)
object OceanusTimeSync {
    /** Connection kinds on which the official app sets the time: 1 normal, 3 auto, 4 time button. */
    val TIME_SYNC_KINDS = setOf(1, 3, 4)

    /** Connection kind on which BLE settings may be written: (A) held 2.5 s, app open. */
    const val SETTINGS_KIND = 1

    /** Connection kind of a pairing connection: the watch is unpaired and (A) was held 2.5 s. */
    const val PAIRING_KIND = 0

    private const val CURRENT_TIME = 0x09
    private const val DST_WATCH_STATE = 0x1D
    private const val DST_SETTING = 0x1E
    private const val BLE_SETTINGS = 0x11

    // DST watch state (0x1D) byte 3, home time: bit 0 = DST in effect, bits 1-2 = automatic.
    private const val DST_IN_EFFECT = 0x01
    private const val DST_AUTO = 0x06

    /**
     * App information (0x22) written when pairing: an app id (10 bytes) and the app type
     * `02`. The official app uses 10 random bytes per install; this library uses its
     * scratchpad marker 0x95 followed by zeros.
     */
    val PAIRING_APP_INFO: ByteArray = bytes(0x22, 0x95, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x02)

    /**
     * BLE settings (0x11) written when pairing, as the official app does: automatic time
     * sync on, first at 00:30, connection time 3 minutes.
     */
    val PAIRING_BLE_SETTINGS: ByteArray =
        bytes(0x11, 0x0F, 0x0F, 0x0F, 0x06, 0x00, 0x50, 0x00, 0x04, 0x00, 0x01, 0x00, 0x00, 0x1E, 0x03)

    /**
     * The phone's time zone as the watch's home-time DST records express it.
     *
     * @param offset standard UTC offset in 15-minute units (−5 h = −20).
     * @param dstShift DST shift in 15-minute units, 0 if the zone has no DST.
     * @param rule DST rule number (as in [CasioTimeZoneHelper]), 0 if none.
     * @param inDst whether DST is in effect now.
     */
    data class HomeZone(
        val offset: Int,
        val dstShift: Int,
        val rule: Int,
        val inDst: Boolean,
    )

    /** Pure: [HomeZone] for [zoneId] at [at], with the DST [rule] from the time-zone table. */
    fun homeZone(
        zoneId: ZoneId,
        rule: Int,
        at: Instant,
    ): HomeZone {
        val rules = zoneId.rules
        val inDst = rules.isDaylightSavings(at)
        val shift =
            if (inDst) {
                rules.getDaylightSavings(at)
            } else {
                rules.nextTransition(at)?.let { rules.getDaylightSavings(it.instant) }
            }
        val dstShift = ((shift?.seconds ?: 0L) / 900).toInt()
        return HomeZone(
            offset = rules.getStandardOffset(at).totalSeconds / 900,
            dstShift = dstShift,
            rule = if (dstShift == 0) 0 else rule,
            inDst = inDst,
        )
    }

    /**
     * DST watch state (0x1D) as read, with only the home-time "DST in effect" bit set
     * for [zone]. Only when the watch's Summer Time is automatic; a manual ON/OFF is kept.
     */
    fun encodeDstWatchState(
        read: ByteArray,
        zone: HomeZone,
    ): ByteArray =
        read.copyOf().also {
            val state = it[3].toInt() and 0xFF
            if (state and DST_AUTO != 0) {
                it[3] = ((state and DST_IN_EFFECT.inv()) or (if (zone.inDst) DST_IN_EFFECT else 0)).toByte()
            }
        }

    /**
     * Home-time DST setting (0x1E slot 0) as read, keeping the home city, with the UTC
     * offset, DST shift and DST rule of [zone]. For a zone without DST the shift is kept.
     */
    fun encodeHomeDstSetting(
        read: ByteArray,
        zone: HomeZone,
    ): ByteArray =
        read.copyOf().also {
            it[4] = zone.offset.toByte()
            if (zone.dstShift != 0) it[5] = zone.dstShift.toByte()
            it[6] = zone.rule.toByte()
        }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    /**
     * 0x09 current time: year (LE), month, day, hour, minute, second,
     * day of week (1 = Monday … 7 = Sunday), 1/256 s fraction, adjust reason 1.
     */
    fun encodeCurrentTime(time: LocalDateTime): ByteArray =
        bytes(
            CURRENT_TIME,
            time.year and 0xFF,
            time.year shr 8 and 0xFF,
            time.monthValue,
            time.dayOfMonth,
            time.hour,
            time.minute,
            time.second,
            time.dayOfWeek.value,
            (time.nano.toLong() * 256 / 1_000_000_000).toInt(),
            1,
        )

    private suspend fun OceanusLink.readChecked(
        request: ByteArray,
        size: Int,
        prefix: ByteArray = request,
    ): ByteArray {
        val reply = read(request)
        if (reply.size != size || prefix.indices.any { reply[it] != prefix[it] }) {
            throw OceanusSyncException("Unexpected reply to ${Utils.fromByteArrayToHexStrWithSpaces(request)}")
        }
        return reply
    }

    /**
     * Sets the time, or with [pairing] first writes the pairing app information and BLE
     * settings. All DST records are read before anything is written; any unexpected reply,
     * timeout or failed write stops the sequence before the next write. The DST records are
     * written back as read, with the home time zone's fields updated for [zone].
     *
     * @param now called right before the time is encoded.
     */
    suspend fun run(
        link: OceanusLink,
        zone: HomeZone,
        pairing: Boolean = false,
        now: () -> LocalDateTime,
    ) {
        with(link) {
            if (pairing) {
                write(PAIRING_APP_INFO)
                readChecked(bytes(BLE_SETTINGS), 15)
                write(PAIRING_BLE_SETTINGS)
            }

            val dstWatchState = readChecked(bytes(DST_WATCH_STATE), 15)
            // The watch answers each bare 0x1E read with the next slot: home, then world.
            val homeDst = readChecked(bytes(DST_SETTING), 7, bytes(DST_SETTING, 0x00))
            val worldDst = readChecked(bytes(DST_SETTING), 7, bytes(DST_SETTING, 0x01))

            write(encodeDstWatchState(dstWatchState, zone))
            write(encodeHomeDstSetting(homeDst, zone))
            write(worldDst)
            write(encodeCurrentTime(now()))
        }
    }
}

/**
 * Imperative shell: reads one reply at a time from the watch, with a timeout,
 * and writes in call order.
 */
@RequiresApi(Build.VERSION_CODES.O)
object OceanusIO : OceanusLink {
    private const val TIMEOUT_MS = 5_000L

    private data class Pending(
        val prefix: ByteArray,
        val reply: CompletableDeferred<ByteArray>,
    )

    @Volatile
    private var pending: Pending? = null

    override suspend fun read(request: ByteArray): ByteArray {
        val deferred = CompletableDeferred<ByteArray>()
        synchronized(this) { pending = Pending(request, deferred) }
        try {
            if (withTimeoutOrNull(TIMEOUT_MS) { Connection.writeInOrder(GetSetMode.GET, request) } != true) {
                throw OceanusSyncException("Read request not sent: ${Utils.fromByteArrayToHexStrWithSpaces(request)}")
            }
            return withTimeoutOrNull(TIMEOUT_MS) { deferred.await() }
                ?: throw OceanusSyncException("No reply to ${Utils.fromByteArrayToHexStrWithSpaces(request)}")
        } finally {
            synchronized(this) { if (pending?.reply === deferred) pending = null }
        }
    }

    override suspend fun write(command: ByteArray) {
        if (withTimeoutOrNull(TIMEOUT_MS) { Connection.writeInOrder(GetSetMode.SET, command) } != true) {
            throw OceanusSyncException("Write failed: ${Utils.fromByteArrayToHexStrWithSpaces(command)}")
        }
    }

    fun onReceived(data: String) {
        val reply = runCatching { Utils.toIntArray(data).map { it.toByte() }.toByteArray() }.getOrNull() ?: return
        synchronized(this) {
            val current = pending ?: return
            if (reply.size >= current.prefix.size && current.prefix.indices.all { reply[it] == current.prefix[it] }) {
                current.reply.complete(reply)
                pending = null
            }
        }
    }
}
