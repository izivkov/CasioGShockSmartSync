package org.avmedia.gshockapi.casio

import java.util.UUID
import org.avmedia.gshockapi.ble.GetSetMode

/** The characteristics of the watch features service and how each one is written (Casio: request without response, settings with response). */
enum class WatchChannel(val handle: GetSetMode, val uuid: UUID, val withResponse: Boolean) {
    READ_REQUEST(GetSetMode.GET, CasioConstants.CASIO_READ_REQUEST_FOR_ALL_FEATURES_CHARACTERISTIC_UUID, false),
    ALL_FEATURES(GetSetMode.SET, CasioConstants.CASIO_ALL_FEATURES_CHARACTERISTIC_UUID, true),
    NOTIFICATION(GetSetMode.NOTIFY, CasioConstants.CASIO_NOTIFICATION_CHARACTERISTIC_UUID, false),
    DATA_REQUEST(GetSetMode.DATA_REQUEST, CasioConstants.CASIO_DATA_REQUEST_CHARACTERISTIC_UUID, true),
    CONVOY(GetSetMode.CONVOY, CasioConstants.CASIO_CONVOY_CHARACTERISTIC_UUID, false),
    SP_REQUEST(GetSetMode.SP_REQUEST, CasioConstants.CASIO_SET_CONFIGURATION_CHARACTERISTIC_UUID, false),
    SP_DATA(GetSetMode.SP_DATA, CasioConstants.CASIO_GET_CONFIGURATION_CHARACTERISTIC_UUID, true),
    ;

    companion object {
        fun of(handle: GetSetMode): WatchChannel = values().first { it.handle == handle }
    }
}

/** The long processes that run one at a time, with their limits (null = none of its own). */
enum class LongProcess(
    val answerMs: Long?,
    val writeResultMs: Long?,
    val totalMs: Long?,
    val chunkGapMs: Long?,
) {
    UPDATE_CHECK(null, null, null, null),
    TIME_EXCHANGE(null, null, 30_000L, null),
    SETUP_STEP(null, null, 20_000L, null),
    LIFELOG(5_000L, 10_000L, null, null),
    WORKOUT(5_000L, 10_000L, 30_000L, null),
    TIDE(5_000L, 10_000L, 70_000L, 400L),
    NOTIFICATION_TEXTS(null, null, null, 2_000L),
}

/** Whether a checked write is read again after its write result (never as a reason to fail a write that was confirmed). */
enum class ReadBack { NONE, AFTER_WRITE_RESULT }

/**
 * How a read request of this code looks and what answers it. [options] = how many option bytes follow the code (0 = the bare code),
 * [fixedOption] = a required first option byte, [minValue] = the shortest answer in bytes after the code and its options,
 * [matchOptions] = how many option bytes of the answer must equal those of the request.
 */
data class ReadSpec(
    val options: IntRange = 0..0,
    val fixedOption: Int? = null,
    val minValue: Int = 0,
    val matchOptions: Int = 0,
)

/** A write frame: code + value, on [channel], cut to [CommandTable.FRAME_CUT] bytes. */
data class WriteSpec(val channel: WatchChannel = WatchChannel.ALL_FEATURES)

data class CommandSpec(
    val code: Int,
    val name: String,
    val read: ReadSpec? = null,
    val write: WriteSpec? = null,
    /** The watch also sends this code on its own: it must be accepted at any time. */
    val unsolicited: Boolean = false,
    val process: LongProcess? = null,
    val readBack: ReadBack = ReadBack.NONE,
    val timeoutMs: Long = CommandTable.REQUEST_TIMEOUT_MS,
)

sealed class Verdict {
    /** [frame] is what goes on the air (cut), [spec] is null for a code the table does not know. */
    class Send(val frame: ByteArray, val spec: CommandSpec?) : Verdict()

    class Reject(val reason: String) : Verdict()
}

/**
 * The exchange rules of the watch link in one place (docs/exchange-review.md): per code the request, the answer, the write, the
 * read-back policy and the long process it belongs to. Every request and write of the app goes through [check].
 */
object CommandTable {
    const val REQUEST_TIMEOUT_MS = 10_000L
    const val FRAME_CUT = 20

    /** True in unit tests: a code that is not in the table is refused instead of passed on with a warning. */
    @Volatile var strict = false

    private fun rw(
        code: Int,
        name: String,
        min: Int = 0,
        options: IntRange = 0..0,
        match: Int = 0,
        fixed: Int? = null,
        back: ReadBack = ReadBack.NONE,
        process: LongProcess? = null,
        unsolicited: Boolean = false,
    ) = CommandSpec(code, name, ReadSpec(options, fixed, min, match), WriteSpec(), unsolicited, process, back)

    private fun readOnly(code: Int, name: String, min: Int = 0, process: LongProcess? = null, unsolicited: Boolean = false) =
        CommandSpec(code, name, ReadSpec(minValue = min), null, unsolicited, process)

    private fun writeOnly(code: Int, name: String, process: LongProcess? = null, unsolicited: Boolean = false) =
        CommandSpec(code, name, null, WriteSpec(), unsolicited, process)

    private val CHECKED = ReadBack.AFTER_WRITE_RESULT
    private val ONE = 1..1

    val specs: List<CommandSpec> =
        listOf(
            writeOnly(0x00, "link loss alert level"),
            writeOnly(0x01, "find me, app to watch"),
            writeOnly(0x09, "current time", LongProcess.TIME_EXCHANGE),
            CommandSpec(0x0A, "find phone (watch to app)", unsolicited = true),
            readOnly(0x10, "kind of connection", min = 8),
            rw(0x11, "ble setting", min = 11),
            rw(0x13, "basic settings", min = 16, back = CHECKED),
            rw(0x15, "alarm 1 (first part)", min = 4),
            rw(0x16, "alarms (second part)", min = 16),
            rw(0x18, "timer", min = 6),
            rw(0x1D, "dst watch state", options = 0..1, match = 1, process = LongProcess.TIME_EXCHANGE),
            rw(0x1E, "dst for world cities", options = 0..1, match = 1, process = LongProcess.TIME_EXCHANGE),
            rw(0x1F, "world city name", options = ONE, match = 1, process = LongProcess.TIME_EXCHANGE),
            readOnly(0x20, "version information", process = LongProcess.UPDATE_CHECK),
            rw(0x22, "app information", min = 11),
            rw(0x23, "watch name"),
            rw(0x24, "home time city", options = ONE, match = 1),
            writeOnly(0x21, "dial reset sequence (older models)"),
            writeOnly(0x25, "leap seconds", LongProcess.TIME_EXCHANGE),
            readOnly(0x26, "module id"),
            readOnly(0x28, "watch condition", min = 9, unsolicited = true),
            rw(0x2D, "exercise profile (older models)"),
            rw(0x2E, "tide", min = 6, options = ONE, match = 1, back = CHECKED),
            rw(0x2F, "time exchange value"),
            rw(0x30, "reminder title (older models)", options = ONE, match = 1),
            rw(0x31, "reminder time (older models)", options = ONE, match = 1),
            rw(0x39, "current time manager", min = 1, process = LongProcess.TIME_EXCHANGE, unsolicited = true),
            rw(0x3A, "connection supervision", min = 9),
            rw(0x3B, "connection debug"),
            rw(0x3D, "notification manager", min = 16, options = ONE, match = 1, fixed = 0x30, back = CHECKED, unsolicited = true),
            rw(0x40, "activity setting 40", min = 5, back = CHECKED),
            rw(0x41, "setting 41", min = 4, back = CHECKED),
            rw(0x42, "notification method", min = 5, back = CHECKED),
            rw(0x43, "step goal", min = 16, back = CHECKED),
            rw(0x44, "timer name", options = ONE, match = 1),
            rw(0x45, "profile", min = 9, back = CHECKED),
            CommandSpec(0x47, "setup done (watch to app)", unsolicited = true),
            CommandSpec(0x49, "connection check", write = WriteSpec(), unsolicited = true),
            rw(0x4E, "activity value", options = ONE, match = 1, back = CHECKED),
            rw(0x4F, "physical settings", min = 9, back = CHECKED),
            rw(0x50, "setting 50", min = 8, back = CHECKED),
            rw(0x51, "watch face", min = 5, back = CHECKED),
            rw(0x52, "widgets", back = CHECKED),
            rw(0x53, "activity list", min = 14, back = CHECKED),
            rw(0x54, "activity item wrapper", options = 1..8, back = CHECKED),
            rw(0x55, "setting 55", min = 14, back = CHECKED),
            rw(0x56, "activity value 56", options = ONE, match = 0, back = CHECKED),
            rw(0x57, "stride", min = 2, back = CHECKED),
            rw(0x58, "activity value 58", options = ONE, match = 1, back = CHECKED),
        )

    private val byCode: Map<Int, CommandSpec> = specs.associateBy { it.code }

    fun spec(code: Int): CommandSpec? = byCode[code]

    /** Shortest answer for a read of [code], in bytes after the code and its options. 0 when the table has no rule. */
    fun minValue(code: Int): Int = byCode[code]?.read?.minValue ?: 0

    fun cut(frame: ByteArray): ByteArray = if (frame.size > FRAME_CUT) frame.copyOf(FRAME_CUT) else frame

    /**
     * The rule for one request or write: refuses what the table forbids (a read of a write-only code, a bare code that needs its
     * option bytes, a write of a read-only code), cuts an all-features write to 20 bytes. Other characteristics are passed as they are.
     */
    fun check(handle: GetSetMode, data: ByteArray): Verdict {
        if (data.isEmpty()) return Verdict.Reject("empty frame")
        val channel = WatchChannel.of(handle)
        if (channel != WatchChannel.READ_REQUEST && channel != WatchChannel.ALL_FEATURES) return Verdict.Send(data, null)
        val code = data[0].toInt() and 0xFF
        val spec = byCode[code]
        if (spec == null) {
            return if (strict) Verdict.Reject("code 0x%02X is not in the command table".format(code)) else Verdict.Send(cutFor(channel, data), null)
        }
        if (channel == WatchChannel.READ_REQUEST) {
            val read = spec.read ?: return Verdict.Reject("code 0x%02X is never read".format(code))
            val options = data.size - 1
            if (options !in read.options) return Verdict.Reject("code 0x%02X needs %s option bytes, got %d".format(code, read.options, options))
            if (read.fixedOption != null && (data[1].toInt() and 0xFF) != read.fixedOption) {
                return Verdict.Reject("code 0x%02X is read with the option byte 0x%02X".format(code, read.fixedOption))
            }
            return Verdict.Send(data, spec)
        }
        if (spec.write == null) return Verdict.Reject("code 0x%02X is never written".format(code))
        return Verdict.Send(cutFor(channel, data), spec)
    }

    private fun cutFor(channel: WatchChannel, data: ByteArray): ByteArray = if (channel == WatchChannel.ALL_FEATURES) cut(data) else data

    /** True when [answer] answers [request]: same code and the option bytes the spec says must match. */
    fun matches(request: ByteArray, answer: ByteArray): Boolean {
        if (request.isEmpty() || answer.isEmpty() || request[0] != answer[0]) return false
        val spec = byCode[request[0].toInt() and 0xFF]
        val compare = minOf(spec?.read?.matchOptions ?: 0, request.size - 1)
        for (i in 1..compare) if (answer.size <= i || answer[i] != request[i]) return false
        return true
    }

    /** True when the answer has at least the shortest length for the request's code. */
    fun longEnough(request: ByteArray, answer: ByteArray): Boolean {
        val read = byCode[request[0].toInt() and 0xFF]?.read ?: return true
        return answer.size - 1 - (request.size - 1).coerceAtMost(read.matchOptions) >= read.minValue
    }
}
