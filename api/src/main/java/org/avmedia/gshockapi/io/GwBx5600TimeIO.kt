package org.avmedia.gshockapi.io

import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import org.avmedia.gshockapi.WatchInfo
import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.ble.GetSetMode
import org.avmedia.gshockapi.casio.CasioConstants
import org.avmedia.gshockapi.casio.CasioTimeZoneHelper
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.time.Duration.Companion.milliseconds

/*
Step 2 (register 0x1E, "world-city data") is NOT a pure read-and-echo like
steps 1 and 3. Reverse-engineered from three btsnoop captures with
different home cities configured (Ho Chi Minh, Shanghai, Madrid):

The watch's GET response for this register is always exactly 28 bytes,
covering two short 9-byte sub-records (still undecoded, echoed as-is)
plus the first 7 bytes of a third record. The SET the watch actually
requires is 94 bytes: those same 28 bytes, followed by three 22-byte
per-city-slot records that the app must construct itself -- they never
come from the watch:

  [u16 LE length=0x0014] [u8 tag=0x24] [u8 slot idx 0/1/2] [u8 flag]
  [f64 BE latitude] [f64 BE longitude] [u8 trailing]

Confirmed against real coordinates (decoded lat/lon vs. actual city):
  Ho Chi Minh capture -> (10.7958, 106.7062) vs actual (10.8231, 106.6297)
  "Madrid" capture     -> (41.4548,   2.2502) vs Barcelona (41.3874, 2.1686)
  "Shanghai" capture   -> (22.7230, 114.2611) vs Hong Kong  (22.3193,114.1694)
The Madrid/Shanghai mismatches aren't decode errors -- Casio stores one
representative coordinate per *time zone*, not per named city, so this
is expected.

An unpopulated slot (seen in the Madrid capture, whose watch only had
one world city configured) reliably looks like:
  flag=0x01, latitude=0.0, longitude=0.0, trailing=0x00
That exact pattern is used below as the default for slots 1 and 2,
since it's an observed wire value rather than a guess.

STILL UNCONFIRMED (flagged inline, needs a targeted capture to verify):
  - the "flag" byte's real meaning (always seen as 0x01, populated or not)
  - a real per-city coordinate source for slot 0. There is no lat/lon
    anywhere else in this codebase (CasioTimeZoneHelper only has
    offset/DST-rule codes). Below, a tiny seed table holds the 3
    coordinates confirmed from captures; anything else falls back to a
    coarse longitude-from-UTC-offset / latitude-0 approximation. This
    approximation is a placeholder, not a real fix -- see
    WorldCityCoordinates below.
  - whether slots 1/2 should ever carry a *different* zone (a real
    world-city list). GwBx5600TimeIO.set() currently only receives one
    timezone total (via TimeIO.getCasioTimezone()), so for now the same
    zone's data goes in slot 0 and slots 1/2 use the empty-slot pattern.

The trailing byte is the city's current DST setting, encoded as a plain
0/1 boolean (isInDST()) -- NOT the fuller 0-3 TimeIOFunctional.
calculateDSTValue() (which also encodes "has DST rules at all" via a
separate bit). Verified against the official app's own Ho Chi
Minh/Yekaterinburg/Miami capture: Miami (US Eastern, DST rules,
captured during EDT) got trailing=0x01, which only matches the plain
isInDST() boolean -- calculateDSTValue() would have produced 0x03.
Ho Chi Minh and Yekaterinburg have no DST rules, so both formulas
agreed there (0x00), which is why this needed a DST-observing zone to
catch. Logged at Step 2 write time (GwBx5600TimeIO Step2 write log
line) alongside the coordinate source, so a plain logcat capture will
show what value was actually sent.
*/

/**
 * Best-effort latitude/longitude lookup for building Step 2's per-city
 * records. This is a placeholder, not a real geo database -- see the
 * file-level doc comment above for what's confirmed vs. guessed.
 */
object WorldCityCoordinates {

    private data class LatLon(val lat: Double, val lon: Double)

    // Reverse-engineered from real captures -- these are known-correct.
    private val knownZones = mapOf(
        "Asia/Ho_Chi_Minh" to LatLon(10.7958, 106.7062),
        "Europe/Madrid" to LatLon(41.4548, 2.2502),
        "Asia/Shanghai" to LatLon(22.7230, 114.2611),
    )

    /**
     * Returns a (lat, lon) pair for the given zone, plus whether it came
     * from the known-good table or the offset-based fallback -- callers
     * use the flag to log which path was taken, so a plain logcat
     * capture later can tell us whether a failure correlates with the
     * fallback approximation.
     */
    fun forZone(zoneId: ZoneId): Triple<Double, Double, Boolean> {
        knownZones[zoneId.id]?.let { return Triple(it.lat, it.lon, true) }

        val offsetHours = zoneId.rules.getStandardOffset(Instant.now()).totalSeconds / 3600.0
        val approxLon = (offsetHours * 15.0).coerceIn(-180.0, 180.0)
        return Triple(0.0, approxLon, false)
    }
}

@RequiresApi(Build.VERSION_CODES.O)
object GwBx5600TimeIO {

    // --- Unconfirmed record fields -- see file-level doc comment. ---
    // Change these here (not inline below) if a future capture shows
    // either assumption is wrong.
    private const val CITY_RECORD_FLAG: Byte = 0x01 // always 0x01 in every capture seen, populated or not
    private const val EMPTY_SLOT_TRAILING: Byte = 0x00 // observed wire value for an unpopulated slot
    private const val EMPTY_SLOT_LAT = 0.0
    private const val EMPTY_SLOT_LON = 0.0

    private var step: Int = 0
    private var expectedBytes: Int = 0
    private var accumulator = ByteArray(0)
    private var result: CompletableDeferred<ByteArray>? = null

    suspend fun set(timeMs: Long? = null) {
        val nowMs = timeMs ?: Clock.systemDefaultZone().millis()
        val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMs), ZoneId.systemDefault())

        Timber.i("GwBx5600TimeIO.set: $now")

        // Step 1 -- confirmed pure echo (101 bytes in, 101 bytes out).
        Timber.i("Step 1/4: time-slot data")
        var req1 = byteArrayOf(0x05)
        req1 += byteArrayOf(0x1D, 0x00, 0x1D, 0x00) // DST Watch State blocks
        req1 += byteArrayOf(0x24, 0x00, 0x24, 0x01, 0x24, 0x02) // Time Slot blocks

        val notif1 = request(1, req1, expected = 101)
        if (notif1 != null) {
            val wb1 = notif1.copyOf()
            wb1[0] = 0x02
            Timber.d("GwBx5600TimeIO Step1 write: ${wb1.size}B ${wb1.toHexString()}")
            Connection.write(GetSetMode.SP_DATA, wb1)
        } else {
            Timber.w("GwBx5600TimeIO Step1: no response from watch (timed out)")
        }

        // Step 2 -- NOT a pure echo. See file-level doc comment.
        Timber.i("Step 2/4: world-city data")
        var req2 = byteArrayOf(0x03)
        val blocks = ceil(WatchInfo.worldCitiesCount / 2.0).toInt()
        for (i in 0 until blocks) {
            req2 += byteArrayOf(CasioConstants.CHARACTERISTICS.CASIO_DST_SETTING.code.toByte(), 0x00)
        }

        val notif2 = request(2, req2, expected = 28)
        if (notif2 != null) {
            val wb2 = notif2.copyOf()
            wb2[0] = 0x06 // command byte: read (0x03) -> write (0x06)
            val withCityData = wb2 + buildWorldCityRecords()
            Timber.d("GwBx5600TimeIO Step2 write: ${withCityData.size}B (expect 94) ${withCityData.toHexString()}")
            Connection.write(GetSetMode.SP_DATA, withCityData)
        } else {
            Timber.w("GwBx5600TimeIO Step2: no response from watch (timed out)")
        }

        // Step 3 -- confirmed pure echo (133 bytes in, 133 bytes out).
        Timber.i("Step 3/4: city names")
        var req3 = byteArrayOf(0x06)
        for (i in 0 until WatchInfo.worldCitiesCount) {
            val idx = (i / 2) + if (i % 2 != 0) 6 else 0
            req3 += byteArrayOf(CasioConstants.CHARACTERISTICS.CASIO_WORLD_CITIES.code.toByte(), idx.toByte())
        }

        val notif3 = request(3, req3, expected = 1 + (WatchInfo.worldCitiesCount * 22))
        if (notif3 != null) {
            Timber.d("GwBx5600TimeIO Step3 write: ${notif3.size}B ${notif3.toHexString()}")
            Connection.write(GetSetMode.SP_DATA, notif3)
        } else {
            Timber.w("GwBx5600TimeIO Step3: no response from watch (timed out)")
        }

        // Step 4
        writeTimeCommand(now)
        Timber.i("GwBx5600TimeIO.set: complete")
    }

    /**
     * Builds the 66 bytes (three 22-byte records) that Step 2 needs
     * appended after the watch's own 28-byte response. Slot 0 (home)
     * carries the currently-configured timezone's coordinates; slots 1
     * and 2 use the observed "unpopulated slot" wire pattern, since this
     * app doesn't currently track a separate per-slot world-city list.
     * See the file-level doc comment for what's confirmed vs. assumed
     * here.
     */
    private fun buildWorldCityRecords(): ByteArray {
        val casioTimezone = TimeIO.getCasioTimezone()
        val (lat, lon, isKnownGood) = WorldCityCoordinates.forZone(casioTimezone.zoneId)
        // Trailing byte = current DST setting, as a plain 0/1 "is DST in
        // effect right now" boolean -- NOT TimeIOFunctional.calculateDSTValue()
        // (which also ORs in a bit for "zone has DST rules at all" and
        // would give 2 or 3 for a DST-observing zone). Confirmed against
        // the official app's own capture: Miami (US Eastern, DST rules,
        // captured during EDT) got trailing=0x01, not 0x03.
        val dstValue = if (casioTimezone.isInDST()) 1 else 0

        Timber.i(
            "GwBx5600TimeIO Step2: home zone=${casioTimezone.zoneId} " +
                    "coords=($lat, $lon) [${if (isKnownGood) "known-good" else "FALLBACK approximation"}] " +
                    "dstValue=$dstValue"
        )

        val homeRecord = cityRecord(slotIndex = 0, lat = lat, lon = lon, trailing = dstValue.toByte())
        val emptySlot1 = cityRecord(slotIndex = 1, lat = EMPTY_SLOT_LAT, lon = EMPTY_SLOT_LON, trailing = EMPTY_SLOT_TRAILING)
        val emptySlot2 = cityRecord(slotIndex = 2, lat = EMPTY_SLOT_LAT, lon = EMPTY_SLOT_LON, trailing = EMPTY_SLOT_TRAILING)

        return homeRecord + emptySlot1 + emptySlot2
    }

    private fun cityRecord(slotIndex: Int, lat: Double, lon: Double, trailing: Byte): ByteArray {
        val buf = ByteBuffer.allocate(22).order(ByteOrder.BIG_ENDIAN)
        buf.put(0x14) // length field low byte
        buf.put(0x00) // length field high byte (LE u16 = 0x0014 = 20 bytes follow)
        buf.put(0x24) // tag
        buf.put(slotIndex.toByte())
        buf.put(CITY_RECORD_FLAG)
        buf.putDouble(lat)
        buf.putDouble(lon)
        buf.put(trailing)
        return buf.array()
    }

    private suspend fun request(currentStep: Int, reqPayload: ByteArray, expected: Int): ByteArray? {
        val deferred = CompletableDeferred<ByteArray>()
        synchronized(this) {
            step = currentStep
            expectedBytes = expected
            accumulator = ByteArray(0)
            result = deferred
        }
        try {
            Connection.write(GetSetMode.SP_REQUEST, reqPayload)
            return withTimeoutOrNull(5000L.milliseconds) {
                deferred.await()
            }
        } finally {
            synchronized(this) {
                result = null
                accumulator = ByteArray(0)
                step = 0
                expectedBytes = 0
            }
        }
    }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02X".format(it) }

    private fun writeTimeCommand(now: LocalDateTime) {
        val casioDow = (now.dayOfWeek.value % 7)
        val subSecondByte = ((now.nano.toLong() * 256) / 1_000_000_000).toByte()

        val timeCmd = byteArrayOf(
            0x09,
            (now.year and 0xFF).toByte(),
            ((now.year shr 8) and 0xFF).toByte(),
            now.monthValue.toByte(),
            now.dayOfMonth.toByte(),
            now.hour.toByte(),
            now.minute.toByte(),
            now.second.toByte(),
            casioDow.toByte(),
            subSecondByte,
            0x01
        )
        val hexStr = timeCmd.joinToString("") { "%02X".format(it) }
        Timber.i("Step 4/4: time command: $hexStr")
        Connection.write(GetSetMode.SET, timeCmd)
    }

    fun onReceived(data: String) {
        val deferred = synchronized(this) { result }
        if (deferred == null) return

        val ints = org.avmedia.gshockapi.utils.Utils.toIntArray(data)
        val bytes = ByteArray(ints.size) { i -> ints[i].toByte() }

        accumulator += bytes

        val accumulated = accumulator.size
        Timber.d("GwBx5600TimeIO.onReceived: step=$step accumulated=${accumulated}B")

        // expectedBytes is the CONFIRMED exact byte count for the current
        // step (101 / 28 / cities*22+1), set by request() and verified
        // against raw ACL-reassembled captures -- not a guessed floor. A
        // single Android onCharacteristicChanged() callback already
        // delivers a fully reassembled ATT value, so a straight >= check
        // here is correct; no debounce/quiet-period logic is needed (an
        // earlier version of this file added one based on a mistaken
        // read of fragmented raw HCI logs -- that was unnecessary
        // complexity, removed).
        if (accumulated >= expectedBytes) {
            synchronized(this) {
                deferred.complete(accumulator)
            }
        }
    }
}