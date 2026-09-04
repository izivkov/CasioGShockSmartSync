package org.avmedia.gshockapi.casio

import android.os.Build
import androidx.annotation.RequiresApi
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.zone.ZoneOffsetTransition

/*
From Gadgetbridge project:

There are six clocks on the Casio GW-B5600
0 is the main clock
1-5 are the world clocks

0x1d 00 01 DST0 DST1 TZ0A TZ0B TZ1A TZ1B ff ff ff ff ff
0x1d 02 03 DST2 DST3 TZ2A TZ2B TZ3A TZ3B ff ff ff ff ff
0x1d 04 05 DST4 DST5 TZ4A TZ4B TZ5A TZ5B ff ff ff ff ff
DST: bitwise flags; bit0: DST on, bit1: DST auto

0x1e 0-5 TZ_A TZ_B TZ_OFF TZ_DSTOFF TZ_DSTRULES
A/B seem to be ignored by the watch
OFF & DSTOFF in 15 minute intervals

0x1f 0-5 (18 bytes ASCII TZ name)

Timezones selectable on the watch:
                   A  B   OFF DSTOFF DSTRULES
BAKER ISLAND       39 01  D0  04     00
PAGO PAGO          D7 00  D4  04     00
HONOLULU           7B 00  D8  04     00
MARQUESAS ISLANDS  3A 01  DA  04     00
ANCHORAGE          0C 00  DC  04     01
LOS ANGELES        A1 00  E0  04     01
DENVER             54 00  E4  04     01
CHICAGO            42 00  E8  04     01
NEW YORK           CA 00  EC  04     01
HALIFAX            71 00  F0  04     01
ST.JOHN'S          0C 01  F2  04     01
RIO DE JANEIRO     F1 00  F4  04     00
F.DE NORONHA       62 00  F8  04     00
PRAIA              E9 00  FC  04     00
UTC                00 00  00  00     00
LONDON             A0 00  00  04     02
PARIS              DC 00  04  04     02
ATHENS             13 00  08  04     02
JEDDAH             85 00  0C  04     00
TEHRAN             16 01  0E  04     2B
DUBAI              5B 00  10  04     00
KABUL              88 00  12  04     00
KARACHI            8B 00  14  04     00
DELHI              52 00  16  04     00
KATHMANDU          8C 00  17  04     00
DHAKA              56 00  18  04     00
YANGON             2F 01  1A  04     00
BANGKOK            1C 00  1C  04     00
HONG KONG          7A 00  20  04     00
PYONGYANG          EA 00  24  04     00
EUCLA              36 01  23  04     00
TOKYO              19 01  24  04     00
ADELAIDE           05 00  26  04     04
SYDNEY             0F 01  28  04     04
LORD HOWE ISLAND   37 01  2A  02     12
NOUMEA             CD 00  2C  04     00
WELLINGTON         2B 01  30  04     05
CHATHAM ISLANDS    3F 00  33  04     17
NUKUALOFA          D0 00  34  04     00
KIRITIMATI         93 00  38  04     00

JERUSALEM          86 00  08  04     2A
CASABLANCA         3A 00  00  04     0F
BEIRUT             22 00  08  04     0C
NORFOLK ISLAND     38 01  2C  04     04
EASTER ISLAND      5E 00  E8  04     1C
HAVANA             75 00  EC  04     15
SANTIAGO           02 01  F0  04     1B
ASUNCION           12 00  F0  04     09
PONTA DELGADA      E4 00  FC  04     02
*/

@RequiresApi(Build.VERSION_CODES.O)
object CasioTimeZoneHelper {
    class CasioTimeZone(val name: String, val zoneName: String, private val _dstRules: Int = 0) {
        val zoneId: ZoneId = ZoneId.of(zoneName)
        val dstOffset = getDTSDuration().seconds / 60 / 15
        val offset = zoneId.rules.getStandardOffset(Instant.now()).totalSeconds / 60 / 15

        // If we have no DST for this timezone, override the dstRules with a 0,
        // since the Casio table might be outdated, i.e for TEHRAN.

        val dstRules = adjustRules(dstOffset, _dstRules)

        fun isInDST(): Boolean {
            val now = ZonedDateTime.now(zoneId)
            return zoneId.rules.isDaylightSavings(now.toInstant())
        }

        fun hasDST() = dstOffset > 0
        fun hasRules() = dstRules != 0

        private fun adjustRules(dstOffset: Long, dstRules: Int) =
            if (dstOffset == 0L) 0 else dstRules

        /**
         * Calculates the daylight saving time (DST) offset duration for the current time zone.
         *
         * - Gets the DST rules for the current `zoneId`.
         * - Checks the current instant (`now`).
         * - Finds the next DST transition after `now`.
         * - If currently in DST, uses `now`; otherwise, uses the instant just after the next transition.
         * - Returns the DST offset as a `Duration`.
         *
         * @return Duration representing the DST offset for this zone, or zero if not applicable.
         */
        private fun getDTSDuration(): Duration {
            val rules = zoneId.rules ?: return Duration.ZERO
            val now = Instant.now()
            val next: ZoneOffsetTransition = rules.nextTransition(now) ?: return Duration.ZERO
            return Duration.ofSeconds(
                rules.getDaylightSavings(
                    if (rules.isDaylightSavings(now)) now else next.instant?.plusSeconds(1)
                ).seconds
            )
        }

        override fun toString(): String {
            return "CasioTimeZone(name='$name', zoneName='$zoneName', zoneId=$zoneId, dstOffset=$dstOffset, offset=$offset, dstRules: $dstRules)"
        }
    }

    private val timeZoneTable = arrayOf(
        CasioTimeZone("BAKER ISLAND", "UTC-12"),
        CasioTimeZone("MARQUESAS ISLANDS", "Pacific/Marquesas", 0xDA),
        CasioTimeZone("POGO POGO", "Pacific/Pago_Pago"),
        CasioTimeZone("HONOLULU", "Pacific/Honolulu"),
        CasioTimeZone("ANCHORAGE", "America/Anchorage", 0x1),
        CasioTimeZone("LOS ANGELES", "America/Los_Angeles", 0x1),
        CasioTimeZone("DENVER", "America/Denver", 0x1),
        CasioTimeZone("CHICAGO", "America/Chicago", 0x1),
        CasioTimeZone("NEW YORK", "America/New_York", 0x1),
        CasioTimeZone("HALIFAX", "America/Halifax", 0x1),
        CasioTimeZone("ST.JOHN'S", "America/St_Johns", 0x1),
        CasioTimeZone("RIO DE JANEIRO", "America/Sao_Paulo"),
        CasioTimeZone("F.DE NORONHA", "America/Noronha"),
        CasioTimeZone("PRAIA", "Atlantic/Cape_Verde"),
        CasioTimeZone("UTC", "UTC"),
        CasioTimeZone("LONDON", "Europe/London", 0x02),
        CasioTimeZone("PARIS", "Europe/Paris", 0x02),
        CasioTimeZone("ATHENS", "Europe/Athens", 0x02),
        CasioTimeZone("JEDDAH", "Asia/Riyadh", 0x0),
        CasioTimeZone("JERUSALEM", "Asia/Jerusalem", 0x2A),
        CasioTimeZone("TEHRAN", "Asia/Tehran", 0x2B),
        CasioTimeZone("DUBAI", "Asia/Dubai"),
        CasioTimeZone("KABUL", "Asia/Kabul"),
        CasioTimeZone("KARACHI", "Asia/Karachi"),
        CasioTimeZone("DELHI", "Asia/Kolkata"),
        CasioTimeZone("KATHMANDU", "Asia/Kathmandu"),
        CasioTimeZone("DHAKA", "Asia/Dhaka"),
        CasioTimeZone("YANGON", "Asia/Yangon"),
        CasioTimeZone("BANGKOK", "Asia/Bangkok"),
        CasioTimeZone("HONG KONG", "Asia/Hong_Kong"),
        CasioTimeZone("PYONGYANG", "Asia/Pyongyang"),
        CasioTimeZone("EUCLA", "Australia/Eucla"),
        CasioTimeZone("TOKYO", "Asia/Tokyo"),
        CasioTimeZone("ADELAIDE", "Australia/Adelaide", 0x4),
        CasioTimeZone("SYDNEY", "Australia/Sydney", 0x4),
        CasioTimeZone("LORD HOWE ISLAND", "Australia/Lord_Howe", 0x12),
        CasioTimeZone("NOUMEA", "Pacific/Noumea"),
        CasioTimeZone("WELLINGTON", "Pacific/Auckland", 0x5),
        CasioTimeZone("CHATHAM ISLANDS", "Pacific/Chatham", 0x17),
        CasioTimeZone("NUKUALOFA", "Pacific/Tongatapu"),
        CasioTimeZone("KIRITIMATI", "Pacific/Kiritimati"),
        CasioTimeZone("JERUSALEM", "Asia/Jerusalem", 0x2A),
        CasioTimeZone("CASABLANCA", "Africa/Casablanca", 0x0f),
        CasioTimeZone("BEIRUT", "Asia/Beirut", 0x0C),
        CasioTimeZone("NORFOLK ISLAND", "Pacific/Norfolk", 0x04),
        CasioTimeZone("EASTER ISLAND", "Pacific/Easter", 0x1C),
        CasioTimeZone("HAVANA", "America/Havana", 0x15),
        CasioTimeZone("SANTIAGO", "America/Santiago", 0x1B),
        CasioTimeZone("ASUNCION", "America/Asuncion", 0x09),
        CasioTimeZone("PONTA DELGADA", "Atlantic/Azores", 0x02),
    )

    private val timeZoneMap by lazy {
        timeZoneTable.associateBy { it.zoneName }.toMap()
    }

    private fun isEquivalent(tz1: ZoneId, tz2: ZoneId): Boolean {
        val rules1 = tz1.normalized().rules
        val rules2 = tz2.normalized().rules

        return rules1.getStandardOffset(Instant.now()) == rules2.getStandardOffset(Instant.now())
                && rules1.getDaylightSavings(Instant.now()) == rules2.getDaylightSavings(Instant.now())
                && rules1.transitionRules == rules2.transitionRules
    }

    fun findTimeZone(timeZoneName: String): CasioTimeZone {

        val foundEntry = timeZoneMap[timeZoneName]
        if (foundEntry != null) {
            return foundEntry
        }

        for (entry in timeZoneMap.values) {
            if (isEquivalent(entry.zoneId, ZoneId.of(timeZoneName))) {
                return entry
            }
        }

        // Sometimes, text comes as "LON:LONDON". Get the last part.
        // val name = timeZoneName.split("/").lastOrNull()?.split(":")?.lastOrNull()?.uppercase() ?: "UNKNOWN"

        val name = timeZoneName.split("/").lastOrNull()?.uppercase() ?: "UNKNOWN"
        return CasioTimeZone(name, timeZoneName, 0x00)
    }

    /*
    Coordinates for the GW-BX5600's "world-city data" protocol (SP_DATA
    register 0x1E) -- see GwBx5600TimeIO.kt for the wire format this
    feeds into. Not used by any other Casio protocol in this codebase;
    lives here (rather than in GwBx5600TimeIO) because it's naturally
    keyed off the same zoneName strings as timeZoneTable above, and any
    other watch that ends up needing coordinates can reuse it too.

    Reverse-engineered from three btsnoop captures with different home
    cities configured (Ho Chi Minh, Shanghai, Madrid) -- see
    GwBx5600TimeIO.kt's file-level doc comment for the full writeup.
    Casio stores one representative coordinate per *time zone*, not per
    named city (confirmed: the "Madrid" capture decoded to Barcelona's
    coordinates, "Shanghai" to Hong Kong's) -- so entries below use the
    coordinates of whichever city name Casio displays for a zone, not
    necessarily the zone's IANA reference city (e.g. Asia/Riyadh is
    keyed with Jeddah's coordinates, matching the JEDDAH label above).
    That's a judgment call, confirmed correct only for the 3 zones
    actually seen in a capture (Ho Chi Minh, Madrid, Shanghai below) --
    the rest are the best available guess pending more captures.
    */
    private data class LatLon(val lat: Double, val lon: Double)

    private val worldCityCoordinates = mapOf(
        // Confirmed from real btsnoop captures -- these are known-correct,
        // not just city-name lookups.
        "Asia/Ho_Chi_Minh" to LatLon(10.7958, 106.7062),
        "Europe/Madrid" to LatLon(41.4548, 2.2502),
        "Asia/Shanghai" to LatLon(22.7230, 114.2611),

        // From timeZoneTable above, keyed by the same zoneName strings.
        // Coordinates are for the city name Casio displays for the zone
        // (see comment above for why).
        "UTC-12" to LatLon(0.1936, -176.4769),               // BAKER ISLAND
        "Pacific/Marquesas" to LatLon(-8.9167, -140.1000),   // MARQUESAS ISLANDS
        "Pacific/Pago_Pago" to LatLon(-14.2781, -170.7025),  // PAGO PAGO
        "Pacific/Honolulu" to LatLon(21.3069, -157.8583),    // HONOLULU
        "America/Anchorage" to LatLon(61.2181, -149.9003),   // ANCHORAGE
        "America/Los_Angeles" to LatLon(34.0522, -118.2437), // LOS ANGELES
        "America/Denver" to LatLon(39.7392, -104.9903),      // DENVER
        "America/Chicago" to LatLon(41.8781, -87.6298),      // CHICAGO
        "America/New_York" to LatLon(40.7128, -74.0060),     // NEW YORK
        "America/Halifax" to LatLon(44.6488, -63.5752),      // HALIFAX
        "America/St_Johns" to LatLon(47.5615, -52.7126),     // ST.JOHN'S
        "America/Sao_Paulo" to LatLon(-22.9068, -43.1729),   // RIO DE JANEIRO
        "America/Noronha" to LatLon(-3.8536, -32.4297),      // F.DE NORONHA
        "Atlantic/Cape_Verde" to LatLon(14.9330, -23.5133),  // PRAIA
        "UTC" to LatLon(0.0, 0.0),                           // UTC
        "Europe/London" to LatLon(51.5074, -0.1278),         // LONDON
        "Europe/Paris" to LatLon(48.8566, 2.3522),           // PARIS
        "Europe/Athens" to LatLon(37.9838, 23.7275),         // ATHENS
        "Asia/Riyadh" to LatLon(21.4858, 39.1925),           // JEDDAH
        "Asia/Jerusalem" to LatLon(31.7683, 35.2137),        // JERUSALEM
        "Asia/Tehran" to LatLon(35.6892, 51.3890),           // TEHRAN
        "Asia/Dubai" to LatLon(25.2048, 55.2708),            // DUBAI
        "Asia/Kabul" to LatLon(34.5553, 69.2075),            // KABUL
        "Asia/Karachi" to LatLon(24.8607, 67.0011),          // KARACHI
        "Asia/Kolkata" to LatLon(28.6139, 77.2090),          // DELHI
        "Asia/Kathmandu" to LatLon(27.7172, 85.3240),        // KATHMANDU
        "Asia/Dhaka" to LatLon(23.8103, 90.4125),            // DHAKA
        "Asia/Yangon" to LatLon(16.8661, 96.1951),           // YANGON
        "Asia/Bangkok" to LatLon(13.7563, 100.5018),         // BANGKOK
        "Asia/Hong_Kong" to LatLon(22.3193, 114.1694),       // HONG KONG
        "Asia/Pyongyang" to LatLon(39.0392, 125.7625),       // PYONGYANG
        "Australia/Eucla" to LatLon(-31.6784, 128.8869),     // EUCLA
        "Asia/Tokyo" to LatLon(35.6762, 139.6503),           // TOKYO
        "Australia/Adelaide" to LatLon(-34.9285, 138.6007),  // ADELAIDE
        "Australia/Sydney" to LatLon(-33.8688, 151.2093),    // SYDNEY
        "Australia/Lord_Howe" to LatLon(-31.5553, 159.0821), // LORD HOWE ISLAND
        "Pacific/Noumea" to LatLon(-22.2758, 166.4581),      // NOUMEA
        "Pacific/Auckland" to LatLon(-41.2865, 174.7762),    // WELLINGTON
        "Pacific/Chatham" to LatLon(-43.9500, -176.5500),    // CHATHAM ISLANDS
        "Pacific/Tongatapu" to LatLon(-21.1789, -175.1982),  // NUKUALOFA
        "Pacific/Kiritimati" to LatLon(1.8721, -157.4278),   // KIRITIMATI
        "Africa/Casablanca" to LatLon(33.5731, -7.5898),     // CASABLANCA
        "Asia/Beirut" to LatLon(33.8938, 35.5018),           // BEIRUT
        "Pacific/Norfolk" to LatLon(-29.0408, 167.9547),     // NORFOLK ISLAND
        "Pacific/Easter" to LatLon(-27.1127, -109.3497),     // EASTER ISLAND
        "America/Havana" to LatLon(23.1136, -82.3666),       // HAVANA
        "America/Santiago" to LatLon(-33.4489, -70.6693),    // SANTIAGO
        "America/Asuncion" to LatLon(-25.2637, -57.5759),    // ASUNCION
        "Atlantic/Azores" to LatLon(37.7412, -25.6756),      // PONTA DELGADA

        // Extended mapping for all remaining time zones from the attached list
        "Africa/Abidjan" to LatLon(5.3600, -4.0083),
        "Africa/Accra" to LatLon(5.6037, -0.1870),
        "Africa/Addis_Ababa" to LatLon(9.0300, 38.7400),
        "Africa/Algiers" to LatLon(36.7538, 3.0588),
        "Africa/Asmara" to LatLon(15.3229, 38.9251),
        "Africa/Asmera" to LatLon(15.3229, 38.9251),
        "Africa/Bamako" to LatLon(12.6392, -8.0029),
        "Africa/Bangui" to LatLon(4.3946, 18.5582),
        "Africa/Banjul" to LatLon(13.4549, -16.5790),
        "Africa/Bissau" to LatLon(11.8631, -15.5977),
        "Africa/Blantyre" to LatLon(-15.7861, 35.0058),
        "Africa/Brazzaville" to LatLon(-4.2634, 15.2429),
        "Africa/Bujumbura" to LatLon(-3.3614, 29.3599),
        "Africa/Cairo" to LatLon(30.0444, 31.2357),
        "Africa/Ceuta" to LatLon(35.8894, -5.3213),
        "Africa/Conakry" to LatLon(9.5092, -13.7122),
        "Africa/Dakar" to LatLon(14.7167, -17.4677),
        "Africa/Dar_es_Salaam" to LatLon(-6.7924, 39.2083),
        "Africa/Djibouti" to LatLon(11.5886, 43.1456),
        "Africa/Douala" to LatLon(4.0511, 9.7679),
        "Africa/El_Aaiun" to LatLon(27.1536, -13.2033),
        "Africa/Freetown" to LatLon(8.4840, -13.2299),
        "Africa/Gaborone" to LatLon(-24.6282, 25.9231),
        "Africa/Harare" to LatLon(-17.8252, 31.0335),
        "Africa/Johannesburg" to LatLon(-26.2041, 28.0473),
        "Africa/Juba" to LatLon(4.8594, 31.5713),
        "Africa/Kampala" to LatLon(0.3476, 32.5825),
        "Africa/Khartoum" to LatLon(15.5007, 32.5599),
        "Africa/Kigali" to LatLon(-1.9441, 30.0619),
        "Africa/Kinshasa" to LatLon(-4.4419, 15.2663),
        "Africa/Lagos" to LatLon(6.5244, 3.3792),
        "Africa/Libreville" to LatLon(0.3901, 9.4544),
        "Africa/Lome" to LatLon(6.1375, 1.2125),
        "Africa/Luanda" to LatLon(-8.83998, 13.2894),
        "Africa/Lubumbashi" to LatLon(-11.6609, 27.4794),
        "Africa/Lusaka" to LatLon(-15.3875, 28.3228),
        "Africa/Malabo" to LatLon(3.7504, 8.7745),
        "Africa/Maputo" to LatLon(-25.9692, 32.5732),
        "Africa/Maseru" to LatLon(-29.3170, 27.4853),
        "Africa/Mbabane" to LatLon(-26.3187, 31.1410),
        "Africa/Mogadishu" to LatLon(2.0469, 45.3182),
        "Africa/Monrovia" to LatLon(6.3156, -10.8074),
        "Africa/Nairobi" to LatLon(-1.2921, 36.8219),
        "Africa/Ndjamena" to LatLon(12.1348, 15.0557),
        "Africa/Niamey" to LatLon(13.5116, 2.1254),
        "Africa/Nouakchott" to LatLon(18.0735, -15.9582),
        "Africa/Ouagadougou" to LatLon(12.3714, -1.5197),
        "Africa/Porto-Novo" to LatLon(6.4969, 2.6288),
        "Africa/Sao_Tome" to LatLon(0.3302, 6.7333),
        "Africa/Timbuktu" to LatLon(16.7735, -3.0074),
        "Africa/Tripoli" to LatLon(32.8872, 13.1913),
        "Africa/Tunis" to LatLon(36.8065, 10.1815),
        "Africa/Windhoek" to LatLon(-22.5609, 17.0658),
        "America/Adak" to LatLon(51.8800, -176.6581),
        "America/Anguilla" to LatLon(18.2206, -63.0686),
        "America/Antigua" to LatLon(17.1274, -61.8468),
        "America/Araguaina" to LatLon(-7.1910, -48.2072),
        "America/Argentina/Buenos_Aires" to LatLon(-34.6037, -58.3816),
        "America/Argentina/Catamarca" to LatLon(-28.4696, -65.7795),
        "America/Argentina/ComodRivadavia" to LatLon(-45.8658, -67.4817),
        "America/Argentina/Cordoba" to LatLon(-31.4201, -64.1888),
        "America/Argentina/Jujuy" to LatLon(-24.1858, -65.2995),
        "America/Argentina/La_Rioja" to LatLon(-29.4131, -66.8558),
        "America/Argentina/Mendoza" to LatLon(-32.8895, -68.8458),
        "America/Argentina/Rio_Gallegos" to LatLon(-51.6226, -69.2181),
        "America/Argentina/Salta" to LatLon(-24.7821, -65.4232),
        "America/Argentina/San_Juan" to LatLon(-31.5375, -68.5364),
        "America/Argentina/San_Luis" to LatLon(-33.3017, -66.3378),
        "America/Argentina/Tucuman" to LatLon(-26.8083, -65.2176),
        "America/Argentina/Ushuaia" to LatLon(-54.8019, -68.3030),
        "America/Aruba" to LatLon(12.5211, -70.0297),
        "America/Atikokan" to LatLon(48.7562, -91.6163),
        "America/Atka" to LatLon(51.8800, -176.6581),
        "America/Bahia" to LatLon(-12.9714, -38.5014),
        "America/Bahia_Banderas" to LatLon(20.8080, -105.2420),
        "America/Barbados" to LatLon(13.1939, -59.5432),
        "America/Belem" to LatLon(-1.4558, -48.4902),
        "America/Belize" to LatLon(17.4995, -88.1976),
        "America/Blanc-Sablon" to LatLon(51.4172, -57.1261),
        "America/Boa_Vista" to LatLon(2.8235, -60.6758),
        "America/Bogota" to LatLon(4.7110, -74.0721),
        "America/Boise" to LatLon(43.6150, -116.2023),
        "America/Buenos_Aires" to LatLon(-34.6037, -58.3816),
        "America/Cambridge_Bay" to LatLon(69.1127, -105.0530),
        "America/Campo_Grande" to LatLon(-20.4428, -54.6464),
        "America/Cancun" to LatLon(21.1619, -86.8515),
        "America/Caracas" to LatLon(10.4806, -66.9036),
        "America/Catamarca" to LatLon(-28.4696, -65.7795),
        "America/Cayenne" to LatLon(4.9333, -52.3333),
        "America/Cayman" to LatLon(19.3133, -81.2546),
        "America/Chihuahua" to LatLon(28.6330, -106.0691),
        "America/Ciudad_Juarez" to LatLon(31.6904, -106.4245),
        "America/Coral_Harbour" to LatLon(64.1356, -83.1678),
        "America/Cordoba" to LatLon(-31.4201, -64.1888),
        "America/Costa_Rica" to LatLon(9.9281, -84.0907),
        "America/Coyhaique" to LatLon(-45.5752, -72.0662),
        "America/Creston" to LatLon(49.0961, -116.5144),
        "America/Cuiaba" to LatLon(-15.6014, -56.0974),
        "America/Curacao" to LatLon(12.1696, -68.9900),
        "America/Danmarkshavn" to LatLon(76.7667, -18.6667),
        "America/Dawson" to LatLon(64.0603, -139.4329),
        "America/Dawson_Creek" to LatLon(55.7596, -120.2375),
        "America/Detroit" to LatLon(42.3314, -83.0458),
        "America/Dominica" to LatLon(15.3092, -61.3794),
        "America/Edmonton" to LatLon(53.5461, -113.4938),
        "America/Eirunepe" to LatLon(-6.6603, -69.8736),
        "America/El_Salvador" to LatLon(13.6929, -89.2182),
        "America/Ensenada" to LatLon(31.8667, -116.6000),
        "America/Fort_Nelson" to LatLon(58.8050, -122.6972),
        "America/Fort_Wayne" to LatLon(41.0793, -85.1394),
        "America/Fortaleza" to LatLon(-3.7319, -38.5267),
        "America/Glace_Bay" to LatLon(46.1942, -59.9566),
        "America/Godthab" to LatLon(64.1836, -51.7214),
        "America/Goose_Bay" to LatLon(53.3168, -60.3790),
        "America/Grand_Turk" to LatLon(21.4674, -71.1312),
        "America/Grenada" to LatLon(12.1165, -61.6790),
        "America/Guadeloupe" to LatLon(16.2650, -61.5510),
        "America/Guatemala" to LatLon(14.6349, -90.5069),
        "America/Guayaquil" to LatLon(-2.1894, -79.8891),
        "America/Guyana" to LatLon(6.8013, -58.1553),
        "America/Hermosillo" to LatLon(29.0729, -110.9559),
        "America/Indiana/Indianapolis" to LatLon(39.7684, -86.1581),
        "America/Indiana/Knox" to LatLon(41.2934, -86.6231),
        "America/Indiana/Marengo" to LatLon(38.3639, -86.3478),
        "America/Indiana/Petersburg" to LatLon(38.4895, -87.2792),
        "America/Indiana/Tell_City" to LatLon(37.9495, -86.7578),
        "America/Indiana/Vevay" to LatLon(38.7478, -85.0706),
        "America/Indiana/Vincennes" to LatLon(38.6773, -87.5286),
        "America/Indiana/Winamac" to LatLon(41.0503, -86.6006),
        "America/Indianapolis" to LatLon(39.7684, -86.1581),
        "America/Inuvik" to LatLon(68.3537, -133.7251),
        "America/Iqaluit" to LatLon(63.7467, -68.5170),
        "America/Jamaica" to LatLon(18.0179, -76.8099),
        "America/Jujuy" to LatLon(-24.1858, -65.2995),
        "America/Juneau" to LatLon(58.3019, -134.4197),
        "America/Kentucky/Louisville" to LatLon(38.2527, -85.7585),
        "America/Kentucky/Monticello" to LatLon(36.8298, -84.8520),
        "America/Knox_IN" to LatLon(41.2934, -86.6231),
        "America/Kralendijk" to LatLon(12.1442, -68.2685),
        "America/La_Paz" to LatLon(-16.5000, -68.1500),
        "America/Lima" to LatLon(-12.0464, -77.0428),
        "America/Louisville" to LatLon(38.2527, -85.7585),
        "America/Lower_Princes" to LatLon(18.0333, -63.0500),
        "America/Maceio" to LatLon(-9.6658, -35.7353),
        "America/Managua" to LatLon(12.1149, -86.2362),
        "America/Manaus" to LatLon(-3.1190, -60.0217),
        "America/Marigot" to LatLon(18.0731, -63.0822),
        "America/Martinique" to LatLon(14.6415, -61.0242),
        "America/Matamoros" to LatLon(25.8690, -97.5027),
        "America/Mazatlan" to LatLon(23.2494, -106.4111),
        "America/Mendoza" to LatLon(-32.8895, -68.8458),
        "America/Menominee" to LatLon(45.1075, -87.6143),
        "America/Merida" to LatLon(20.9674, -89.5926),
        "America/Metlakatla" to LatLon(55.1328, -131.5775),
        "America/Mexico_City" to LatLon(19.4326, -99.1332),
        "America/Miquelon" to LatLon(47.1000, -56.3333),
        "America/Moncton" to LatLon(46.0878, -64.7782),
        "America/Monterrey" to LatLon(25.6866, -100.3161),
        "America/Montevideo" to LatLon(-34.9011, -56.1645),
        "America/Montreal" to LatLon(45.5017, -73.5673),
        "America/Montserrat" to LatLon(16.7425, -62.1874),
        "America/Nassau" to LatLon(25.0343, -77.3963),
        "America/Nipigon" to LatLon(49.0167, -88.2667),
        "America/Nome" to LatLon(64.5011, -165.4064),
        "America/North_Dakota/Beulah" to LatLon(47.2635, -101.7770),
        "America/North_Dakota/Center" to LatLon(47.1160, -101.2996),
        "America/North_Dakota/New_Salem" to LatLon(46.8455, -101.4101),
        "America/Nuuk" to LatLon(64.1836, -51.7214),
        "America/Ojinaga" to LatLon(29.5645, -104.4119),
        "America/Panama" to LatLon(8.9824, -79.5199),
        "America/Pangnirtung" to LatLon(66.1455, -65.7125),
        "America/Paramaribo" to LatLon(5.8520, -55.2038),
        "America/Phoenix" to LatLon(33.4484, -112.0740),
        "America/Port-au-Prince" to LatLon(18.5944, -72.3074),
        "America/Port_of_Spain" to LatLon(10.6549, -61.5019),
        "America/Porto_Acre" to LatLon(-9.8333, -67.6167),
        "America/Porto_Velho" to LatLon(-8.7619, -63.9039),
        "America/Puerto_Rico" to LatLon(18.2208, -66.5901),
        "America/Punta_Arenas" to LatLon(-53.1638, -70.9171),
        "America/Rainy_River" to LatLon(48.7183, -94.5714),
        "America/Rankin_Inlet" to LatLon(62.8100, -92.0833),
        "America/Recife" to LatLon(-8.0476, -34.8770),
        "America/Regina" to LatLon(50.4452, -104.6189),
        "America/Resolute" to LatLon(74.6972, -94.8311),
        "America/Rio_Branco" to LatLon(-9.9749, -67.8243),
        "America/Rosario" to LatLon(-32.9468, -60.6393),
        "America/Santa_Isabel" to LatLon(32.6522, -116.9631),
        "America/Santarem" to LatLon(-2.4431, -54.7083),
        "America/Santo_Domingo" to LatLon(18.4861, -69.9312),
        "America/Scoresbysund" to LatLon(70.4853, -21.9633),
        "America/Shiprock" to LatLon(36.7911, -108.8878),
        "America/Sitka" to LatLon(57.0531, -135.33),
        "America/St_Barthelemy" to LatLon(17.8961, -62.8520),
        "America/St_Kitts" to LatLon(17.3578, -62.7829),
        "America/St_Lucia" to LatLon(13.9094, -60.9789),
        "America/St_Thomas" to LatLon(18.3419, -64.9307),
        "America/St_Vincent" to LatLon(13.1584, -61.2248),
        "America/Swift_Current" to LatLon(50.2858, -107.7997),
        "America/Tegucigalpa" to LatLon(14.0723, -87.1921),
        "America/Thule" to LatLon(76.5312, -68.7032),
        "America/Thunder_Bay" to LatLon(48.3809, -89.2477),
        "America/Tijuana" to LatLon(32.5149, -117.0382),
        "America/Toronto" to LatLon(43.6532, -79.3832),
        "America/Tortola" to LatLon(18.4207, -64.6399),
        "America/Vancouver" to LatLon(49.2827, -123.1207),
        "America/Virgin" to LatLon(18.3419, -64.9307),
        "America/Whitehorse" to LatLon(60.7212, -135.0568),
        "America/Winnipeg" to LatLon(49.8951, -97.1384),
        "America/Yakutat" to LatLon(59.5535, -139.7289),
        "America/Yellowknife" to LatLon(62.4540, -114.3718),
        "Antarctica/Casey" to LatLon(-66.2800, 110.5300),
        "Antarctica/Davis" to LatLon(-68.5866, 77.9650),
        "Antarctica/DumontDUrville" to LatLon(-66.6600, 140.0000),
        "Antarctica/Macquarie" to LatLon(-54.5000, 158.9500),
        "Antarctica/Mawson" to LatLon(-67.6000, 62.8833),
        "Antarctica/McMurdo" to LatLon(-77.8500, 166.6667),
        "Antarctica/Palmer" to LatLon(-64.7742, -64.0531),
        "Antarctica/Rothera" to LatLon(-67.5681, -68.1228),
        "Antarctica/South_Pole" to LatLon(-90.0000, 0.0000),
        "Antarctica/Syowa" to LatLon(-69.0022, 39.5856),
        "Antarctica/Troll" to LatLon(-72.0111, 2.5333),
        "Antarctica/Vostok" to LatLon(-78.4645, 106.8370),
        "Arctic/Longyearbyen" to LatLon(78.2232, 15.6469),
        "Asia/Aden" to LatLon(12.7972, 45.0186),
        "Asia/Almaty" to LatLon(43.2220, 76.8512),
        "Asia/Amman" to LatLon(31.9454, 35.9284),
        "Asia/Anadyr" to LatLon(64.7333, 177.5000),
        "Asia/Aqtau" to LatLon(43.6500, 51.1500),
        "Asia/Aqtobe" to LatLon(50.2833, 57.1667),
        "Asia/Ashgabat" to LatLon(37.9500, 58.3833),
        "Asia/Ashkhabad" to LatLon(37.9500, 58.3833),
        "Asia/Atyrau" to LatLon(47.1167, 51.9333),
        "Asia/Baghdad" to LatLon(33.3152, 44.3661),
        "Asia/Bahrain" to LatLon(26.0667, 50.5577),
        "Asia/Baku" to LatLon(40.4093, 49.8671),
        "Asia/Barnaul" to LatLon(53.3500, 83.7500),
        "Asia/Bishkek" to LatLon(42.8746, 74.5698),
        "Asia/Brunei" to LatLon(4.9031, 114.9398),
        "Asia/Calcutta" to LatLon(22.5726, 88.3639),
        "Asia/Chita" to LatLon(52.0333, 113.5000),
        "Asia/Choibalsan" to LatLon(48.0667, 114.5333),
        "Asia/Chongqing" to LatLon(29.5628, 106.5528),
        "Asia/Chungking" to LatLon(29.5628, 106.5528),
        "Asia/Colombo" to LatLon(6.9271, 79.8612),
        "Asia/Dacca" to LatLon(23.8103, 90.4125),
        "Asia/Damascus" to LatLon(33.5138, 36.2765),
        "Asia/Dili" to LatLon(-8.5569, 125.5603),
        "Asia/Dushanbe" to LatLon(38.5598, 68.7870),
        "Asia/Famagusta" to LatLon(35.1231, 33.9450),
        "Asia/Gaza" to LatLon(31.5000, 34.4667),
        "Asia/Harbin" to LatLon(45.8038, 126.5350),
        "Asia/Hebron" to LatLon(31.5326, 35.0998),
        "Asia/Hovd" to LatLon(48.0056, 91.6419),
        "Asia/Irkutsk" to LatLon(52.2871, 104.3050),
        "Asia/Istanbul" to LatLon(41.0082, 28.9784),
        "Asia/Jakarta" to LatLon(-6.2088, 106.8456),
        "Asia/Jayapura" to LatLon(-2.5333, 140.7000),
        "Asia/Kamchatka" to LatLon(53.0167, 158.6500),
        "Asia/Kashgar" to LatLon(39.4670, 75.9897),
        "Asia/Katmandu" to LatLon(27.7172, 85.3240),
        "Asia/Khandyga" to LatLon(62.6667, 135.5500),
        "Asia/Krasnoyarsk" to LatLon(56.0184, 92.8672),
        "Asia/Kuala_Lumpur" to LatLon(3.1390, 101.6869),
        "Asia/Kuching" to LatLon(1.5533, 110.3592),
        "Asia/Kuwait" to LatLon(29.3759, 47.9774),
        "Asia/Macao" to LatLon(22.1987, 113.5439),
        "Asia/Macau" to LatLon(22.1987, 113.5439),
        "Asia/Magadan" to LatLon(59.5653, 150.8042),
        "Asia/Makassar" to LatLon(-5.1477, 119.4327),
        "Asia/Manila" to LatLon(14.5995, 120.9842),
        "Asia/Muscat" to LatLon(23.5859, 58.4059),
        "Asia/Nicosia" to LatLon(35.1856, 33.3823),
        "Asia/Novokuznetsk" to LatLon(53.7500, 87.1167),
        "Asia/Novosibirsk" to LatLon(55.0084, 82.9357),
        "Asia/Omsk" to LatLon(54.9885, 73.3242),
        "Asia/Oral" to LatLon(51.2333, 51.3667),
        "Asia/Phnom_Penh" to LatLon(11.5564, 104.9282),
        "Asia/Pontianak" to LatLon(-0.0263, 109.3425),
        "Asia/Qatar" to LatLon(25.2854, 51.5310),
        "Asia/Qostanay" to LatLon(53.2144, 63.6246),
        "Asia/Qyzylorda" to LatLon(44.8528, 65.5092),
        "Asia/Rangoon" to LatLon(16.8661, 96.1951),
        "Asia/Saigon" to LatLon(10.7958, 106.7062),
        "Asia/Sakhalin" to LatLon(46.9541, 142.7360),
        "Asia/Samarkand" to LatLon(39.6542, 66.9597),
        "Asia/Seoul" to LatLon(37.5665, 126.9780),
        "Asia/Singapore" to LatLon(1.3521, 103.8198),
        "Asia/Srednekolymsk" to LatLon(67.4500, 153.7167),
        "Asia/Taipei" to LatLon(25.0330, 121.5654),
        "Asia/Tashkent" to LatLon(41.2995, 69.2401),
        "Asia/Tbilisi" to LatLon(41.7151, 44.8271),
        "Asia/Tel_Aviv" to LatLon(32.0853, 34.7818),
        "Asia/Thimbu" to LatLon(27.4728, 89.6393),
        "Asia/Thimphu" to LatLon(27.4728, 89.6393),
        "Asia/Tomsk" to LatLon(56.4977, 84.9744),
        "Asia/Ujung_Pandang" to LatLon(-5.1477, 119.4327),
        "Asia/Ulaanbaatar" to LatLon(47.8864, 106.9057),
        "Asia/Ulan_Bator" to LatLon(47.8864, 106.9057),
        "Asia/Urumqi" to LatLon(43.8256, 87.6169),
        "Asia/Ust-Nera" to LatLon(64.5667, 143.2333),
        "Asia/Vientiane" to LatLon(17.9757, 102.6331),
        "Asia/Vladivostok" to LatLon(43.1155, 131.8855),
        "Asia/Yakutsk" to LatLon(62.0355, 129.6755),
        "Asia/Yekaterinburg" to LatLon(56.8389, 60.6057),
        "Asia/Yerevan" to LatLon(40.1792, 44.4991),
        "Atlantic/Bermuda" to LatLon(32.3078, -64.7505),
        "Atlantic/Canary" to LatLon(28.1248, -15.4300),
        "Atlantic/Faeroe" to LatLon(62.0100, -6.7700),
        "Atlantic/Faroe" to LatLon(62.0100, -6.7700),
        "Atlantic/Jan_Mayen" to LatLon(70.9333, -8.6667),
        "Atlantic/Madeira" to LatLon(32.6500, -16.9000),
        "Atlantic/Reykjavik" to LatLon(64.1466, -21.9426),
        "Atlantic/South_Georgia" to LatLon(-54.4296, -36.5879),
        "Atlantic/St_Helena" to LatLon(-15.9650, -5.7089),
        "Atlantic/Stanley" to LatLon(-51.6925, -57.8544),
        "Australia/ACT" to LatLon(-35.2809, 149.1300),
        "Australia/Brisbane" to LatLon(-27.4698, 153.0251),
        "Australia/Broken_Hill" to LatLon(-31.9535, 141.4545),
        "Australia/Canberra" to LatLon(-35.2809, 149.1300),
        "Australia/Currie" to LatLon(-39.9333, 143.8333),
        "Australia/Darwin" to LatLon(-12.4634, 130.8456),
        "Australia/Hobart" to LatLon(-42.8821, 147.3272),
        "Australia/LHI" to LatLon(-31.5553, 159.0821),
        "Australia/Lindeman" to LatLon(-20.4500, 149.0333),
        "Australia/Melbourne" to LatLon(-37.8136, 144.9631),
        "Australia/NSW" to LatLon(-33.8688, 151.2093),
        "Australia/North" to LatLon(-12.4634, 130.8456),
        "Australia/Perth" to LatLon(-31.9505, 115.8605),
        "Australia/Queensland" to LatLon(-27.4698, 153.0251),
        "Australia/South" to LatLon(-34.9285, 138.6007),
        "Australia/Tasmania" to LatLon(-42.8821, 147.3272),
        "Australia/Victoria" to LatLon(-37.8136, 144.9631),
        "Australia/West" to LatLon(-31.9505, 115.8605),
        "Australia/Yancowinna" to LatLon(-31.9535, 141.4545),
        "Brazil/Acre" to LatLon(-9.9749, -67.8243),
        "Brazil/DeNoronha" to LatLon(-3.8536, -32.4297),
        "Brazil/East" to LatLon(-22.9068, -43.1729),
        "Brazil/West" to LatLon(-3.1190, -60.0217),
        "CET" to LatLon(50.8503, 4.3517),
        "CST6CDT" to LatLon(41.8781, -87.6298),
        "Canada/Atlantic" to LatLon(44.6488, -63.5752),
        "Canada/Central" to LatLon(49.8951, -97.1384),
        "Canada/Eastern" to LatLon(43.6532, -79.3832),
        "Canada/Mountain" to LatLon(51.0447, -114.0719),
        "Canada/Newfoundland" to LatLon(47.5615, -52.7126),
        "Canada/Pacific" to LatLon(49.2827, -123.1207),
        "Canada/Saskatchewan" to LatLon(50.4452, -104.6189),
        "Canada/Yukon" to LatLon(60.7212, -135.0568),
        "Chile/Continental" to LatLon(-33.4489, -70.6693),
        "Chile/EasterIsland" to LatLon(-27.1127, -109.3497),
        "Cuba" to LatLon(23.1136, -82.3666),
        "EET" to LatLon(37.9838, 23.7275),
        "EST" to LatLon(40.7128, -74.0060),
        "EST5EDT" to LatLon(40.7128, -74.0060),
        "Egypt" to LatLon(30.0444, 31.2357),
        "Eire" to LatLon(53.3498, -6.2603),
        "Etc/GMT" to LatLon(0.0, 0.0),
        "Etc/GMT+0" to LatLon(0.0, 0.0),
        "Etc/GMT+1" to LatLon(0.0, -15.0),
        "Etc/GMT+10" to LatLon(0.0, -150.0),
        "Etc/GMT+11" to LatLon(0.0, -165.0),
        "Etc/GMT+12" to LatLon(0.0, -180.0),
        "Etc/GMT+2" to LatLon(0.0, -30.0),
        "Etc/GMT+3" to LatLon(0.0, -45.0),
        "Etc/GMT+4" to LatLon(0.0, -60.0),
        "Etc/GMT+5" to LatLon(0.0, -75.0),
        "Etc/GMT+6" to LatLon(0.0, -90.0),
        "Etc/GMT+7" to LatLon(0.0, -105.0),
        "Etc/GMT+8" to LatLon(0.0, -120.0),
        "Etc/GMT+9" to LatLon(0.0, -135.0),
        "Etc/GMT-0" to LatLon(0.0, 0.0),
        "Etc/GMT-1" to LatLon(0.0, 15.0),
        "Etc/GMT-10" to LatLon(0.0, 150.0),
        "Etc/GMT-11" to LatLon(0.0, 165.0),
        "Etc/GMT-12" to LatLon(0.0, 180.0),
        "Etc/GMT-13" to LatLon(0.0, 195.0),
        "Etc/GMT-14" to LatLon(0.0, 210.0),
        "Etc/GMT-2" to LatLon(0.0, 30.0),
        "Etc/GMT-3" to LatLon(0.0, 45.0),
        "Etc/GMT-4" to LatLon(0.0, 60.0),
        "Etc/GMT-5" to LatLon(0.0, 75.0),
        "Etc/GMT-6" to LatLon(0.0, 90.0),
        "Etc/GMT-7" to LatLon(0.0, 105.0),
        "Etc/GMT-8" to LatLon(0.0, 120.0),
        "Etc/GMT-9" to LatLon(0.0, 135.0),
        "Etc/GMT0" to LatLon(0.0, 0.0),
        "Etc/Greenwich" to LatLon(51.4769, -0.0005),
        "Etc/UCT" to LatLon(0.0, 0.0),
        "Etc/UTC" to LatLon(0.0, 0.0),
        "Etc/Universal" to LatLon(0.0, 0.0),
        "Etc/Zulu" to LatLon(0.0, 0.0),
        "Europe/Amsterdam" to LatLon(52.3676, 4.9041),
        "Europe/Andorra" to LatLon(42.5063, 1.5218),
        "Europe/Astrakhan" to LatLon(46.3497, 48.0408),
        "Europe/Athens" to LatLon(37.9838, 23.7275),
        "Europe/Belfast" to LatLon(54.5973, -5.9301),
        "Europe/Belgrade" to LatLon(44.7866, 20.4489),
        "Europe/Berlin" to LatLon(52.5200, 13.4050),
        "Europe/Bratislava" to LatLon(48.1486, 17.1077),
        "Europe/Brussels" to LatLon(50.8503, 4.3517),
        "Europe/Bucharest" to LatLon(44.4268, 26.1025),
        "Europe/Budapest" to LatLon(47.4979, 19.0402),
        "Europe/Busingen" to LatLon(47.6983, 8.6853),
        "Europe/Chisinau" to LatLon(47.0105, 28.8638),
        "Europe/Copenhagen" to LatLon(55.6761, 12.5683),
        "Europe/Dublin" to LatLon(53.3498, -6.2603),
        "Europe/Gibraltar" to LatLon(36.1408, -5.3536),
        "Europe/Guernsey" to LatLon(49.4482, -2.5895),
        "Europe/Helsinki" to LatLon(60.1699, 24.9384),
        "Europe/Isle_of_Man" to LatLon(54.2361, -4.5481),
        "Europe/Istanbul" to LatLon(41.0082, 28.9784),
        "Europe/Jersey" to LatLon(49.1904, -2.1091),
        "Europe/Kaliningrad" to LatLon(54.7065, 20.5110),
        "Europe/Kiev" to LatLon(50.4501, 30.5234),
        "Europe/Kirov" to LatLon(58.5966, 49.6601),
        "Europe/Kyiv" to LatLon(50.4501, 30.5234),
        "Europe/Lisbon" to LatLon(38.7223, -9.1393),
        "Europe/Ljubljana" to LatLon(46.0569, 14.5058),
        "Europe/London" to LatLon(51.5074, -0.1278),
        "Europe/Luxembourg" to LatLon(49.6116, 6.1319),
        "Europe/Madrid" to LatLon(41.4548, 2.2502),
        "Europe/Malta" to LatLon(35.8985, 14.5146),
        "Europe/Mariehamn" to LatLon(60.0973, 19.9348),
        "Europe/Minsk" to LatLon(53.9006, 27.5590),
        "Europe/Monaco" to LatLon(43.7384, 7.4246),
        "Europe/Moscow" to LatLon(55.7558, 37.6173),
        "Europe/Nicosia" to LatLon(35.1856, 33.3823),
        "Europe/Oslo" to LatLon(59.9139, 10.7522),
        "Europe/Paris" to LatLon(48.8566, 2.3522),
        "Europe/Podgorica" to LatLon(42.4411, 19.2636),
        "Europe/Prague" to LatLon(50.0755, 14.4378),
        "Europe/Riga" to LatLon(56.9496, 24.1052),
        "Europe/Rome" to LatLon(41.9028, 12.4964),
        "Europe/Samara" to LatLon(53.1959, 50.1832),
        "Europe/San_Marino" to LatLon(43.9424, 12.4578),
        "Europe/Sarajevo" to LatLon(43.8563, 18.4131),
        "Europe/Saratov" to LatLon(51.5332, 46.0343),
        "Europe/Simferopol" to LatLon(44.9521, 34.1024),
        "Europe/Skopje" to LatLon(41.9981, 21.4254),
        "Europe/Sofia" to LatLon(42.6977, 23.3219),
        "Europe/Stockholm" to LatLon(59.3293, 18.0686),
        "Europe/Tallinn" to LatLon(59.4370, 24.7536),
        "Europe/Tirane" to LatLon(41.3275, 19.8187),
        "Europe/Tiraspol" to LatLon(46.8403, 29.6356),
        "Europe/Ulyanovsk" to LatLon(54.3282, 48.3866),
        "Europe/Uzhgorod" to LatLon(48.6208, 22.2879),
        "Europe/Vaduz" to LatLon(47.1410, 9.5209),
        "Europe/Vatican" to LatLon(41.9029, 12.4534),
        "Europe/Vienna" to LatLon(48.2082, 16.3738),
        "Europe/Vilnius" to LatLon(54.6872, 25.2797),
        "Europe/Volgograd" to LatLon(48.7080, 44.5133),
        "Europe/Warsaw" to LatLon(52.2297, 21.0122),
        "Europe/Zagreb" to LatLon(45.8150, 15.9819),
        "Europe/Zaporozhye" to LatLon(47.8388, 35.1396),
        "Europe/Zurich" to LatLon(47.3769, 8.5417),
        "GB" to LatLon(51.5074, -0.1278),
        "GB-Eire" to LatLon(51.5074, -0.1278),
        "GMT" to LatLon(51.4769, -0.0005),
        "GMT+0" to LatLon(0.0, 0.0),
        "GMT-0" to LatLon(0.0, 0.0),
        "GMT0" to LatLon(0.0, 0.0),
        "Greenwich" to LatLon(51.4769, -0.0005),
        "HST" to LatLon(21.3069, -157.8583),
        "Hongkong" to LatLon(22.3193, 114.1694),
        "Iceland" to LatLon(64.1466, -21.9426),
        "Indian/Antananarivo" to LatLon(-18.8792, 47.5079),
        "Indian/Chagos" to LatLon(-7.3195, 72.4229),
        "Indian/Christmas" to LatLon(-10.4475, 105.6904),
        "Indian/Cocos" to LatLon(-12.1642, 96.8710),
        "Indian/Comoro" to LatLon(-11.7022, 43.2551),
        "Indian/Kerguelen" to LatLon(-49.3500, 70.2167),
        "Indian/Mahe" to LatLon(-4.6796, 55.4920),
        "Indian/Maldives" to LatLon(3.2028, 73.2207),
        "Indian/Mauritius" to LatLon(-20.3484, 57.5522),
        "Indian/Mayotte" to LatLon(-12.8275, 45.1662),
        "Indian/Reunion" to LatLon(-21.1151, 55.5364),
        "Iran" to LatLon(35.6892, 51.3890),
        "Israel" to LatLon(31.7683, 35.2137),
        "Jamaica" to LatLon(18.0179, -76.8099),
        "Japan" to LatLon(35.6762, 139.6503),
        "Kwajalein" to LatLon(8.7183, 167.7342),
        "Libya" to LatLon(32.8872, 13.1913),
        "MET" to LatLon(52.5200, 13.4050),
        "MST" to LatLon(39.7392, -104.9903),
        "MST7MDT" to LatLon(39.7392, -104.9903),
        "Mexico/BajaNorte" to LatLon(32.5149, -117.0382),
        "Mexico/BajaSur" to LatLon(24.1426, -110.3128),
        "Mexico/General" to LatLon(19.4326, -99.1332),
        "NZ" to LatLon(-41.2865, 174.7762),
        "NZ-CHAT" to LatLon(-43.9500, -176.5500),
        "Navajo" to LatLon(36.7911, -108.8878),
        "PRC" to LatLon(39.9042, 114.4070),
        "PST8PDT" to LatLon(34.0522, -118.2437),
        "Pacific/Apia" to LatLon(-13.8333, -171.7500),
        "Pacific/Auckland" to LatLon(-41.2865, 174.7762),
        "Pacific/Bougainville" to LatLon(-6.2167, 155.5667),
        "Pacific/Chatham" to LatLon(-43.9500, -176.5500),
        "Pacific/Chuuk" to LatLon(7.4167, 151.8333),
        "Pacific/Easter" to LatLon(-27.1127, -109.3497),
        "Pacific/Efate" to LatLon(-17.7333, 168.3167),
        "Pacific/Enderbury" to LatLon(-3.1333, -171.0833),
        "Pacific/Fakaofo" to LatLon(-9.3833, -171.2167),
        "Pacific/Fiji" to LatLon(-18.1416, 178.4419),
        "Pacific/Funafuti" to LatLon(-8.5211, 179.1962),
        "Pacific/Galapagos" to LatLon(-0.7400, -90.3018),
        "Pacific/Gambier" to LatLon(-23.1250, -134.9694),
        "Pacific/Guadalcanal" to LatLon(-9.4288, 159.9545),
        "Pacific/Guam" to LatLon(13.4443, 144.7937),
        "Pacific/Honolulu" to LatLon(21.3069, -157.8583),
        "Pacific/Johnston" to LatLon(16.7333, -169.5333),
        "Pacific/Kanton" to LatLon(-2.8167, -171.6833),
        "Pacific/Kiritimati" to LatLon(1.8721, -157.4278),
        "Pacific/Kosrae" to LatLon(5.3167, 162.9833),
        "Pacific/Kwajalein" to LatLon(8.7183, 167.7342),
        "Pacific/Majuro" to LatLon(7.1315, 171.3803),
        "Pacific/Marquesas" to LatLon(-8.9167, -140.1000),
        "Pacific/Midway" to LatLon(28.2120, -177.3755),
        "Pacific/Nauru" to LatLon(-0.5228, 166.9158),
        "Pacific/Niue" to LatLon(-19.0544, -169.9187),
        "Pacific/Norfolk" to LatLon(-29.0408, 167.9547),
        "Pacific/Noumea" to LatLon(-22.2758, 166.4581),
        "Pacific/Pago_Pago" to LatLon(-14.2781, -170.7025),
        "Pacific/Palau" to LatLon(7.5150, 134.5825),
        "Pacific/Pitcairn" to LatLon(-25.0667, -130.1000),
        "Pacific/Pohnpei" to LatLon(6.9667, 158.2167),
        "Pacific/Ponape" to LatLon(6.9667, 158.2167),
        "Pacific/Port_Moresby" to LatLon(-9.4438, 147.1803),
        "Pacific/Rarotonga" to LatLon(-21.2333, -159.7667),
        "Pacific/Saipan" to LatLon(15.1906, 145.7467),
        "Pacific/Samoa" to LatLon(-14.2781, -170.7025),
        "Pacific/Tahiti" to LatLon(-17.5350, -149.5694),
        "Pacific/Tarawa" to LatLon(1.3292, 172.9791),
        "Pacific/Tongatapu" to LatLon(-21.1789, -175.1982),
        "Pacific/Truk" to LatLon(7.4167, 151.8333),
        "Pacific/Wake" to LatLon(19.2833, 166.6167),
        "Pacific/Wallis" to LatLon(-13.2987, -176.2126),
        "Pacific/Yap" to LatLon(9.5167, 138.1333),
        "Poland" to LatLon(52.2297, 21.0122),
        "Portugal" to LatLon(38.7223, -9.1393),
        "ROC" to LatLon(25.0330, 121.5654),
        "ROK" to LatLon(37.5665, 126.9780),
        "Singapore" to LatLon(1.3521, 103.8198),
        "Turkey" to LatLon(41.0082, 28.9784),
        "UCT" to LatLon(0.0, 0.0),
        "US/Alaska" to LatLon(58.3019, -134.4197),
        "US/Aleutian" to LatLon(51.8800, -176.6581),
        "US/Arizona" to LatLon(33.4484, -112.0740),
        "US/Central" to LatLon(41.8781, -87.6298),
        "US/East-Indiana" to LatLon(39.7684, -86.1581),
        "US/Eastern" to LatLon(40.7128, -74.0060),
        "US/Hawaii" to LatLon(21.3069, -157.8583),
        "US/Indiana-Starke" to LatLon(41.2934, -86.6231),
        "US/Michigan" to LatLon(42.3314, -83.0458),
        "US/Mountain" to LatLon(39.7392, -104.9903),
        "US/Pacific" to LatLon(34.0522, -118.2437),
        "US/Samoa" to LatLon(-14.2781, -170.7025),
        "UTC" to LatLon(0.0, 0.0),
        "Universal" to LatLon(0.0, 0.0),
        "W-SU" to LatLon(55.7558, 37.6173),
        "WET" to LatLon(51.5074, -0.1278),
        "Zulu" to LatLon(0.0, 0.0)
    )

    /**
     * Returns a (lat, lon) pair for the given zone, plus whether it came
     * from the known-good table above or a coarse offset-based fallback
     * (longitude estimated from the zone's UTC offset, latitude pinned
     * to the equator) for a zone outside that table. Callers should log
     * the fallback flag -- see GwBx5600TimeIO's Step2 write log line --
     * so a plain logcat capture can tell us whether a failure correlates
     * with the fallback approximation.
     */
    fun getWorldCityCoordinates(zoneId: ZoneId): Triple<Double, Double, Boolean> {
        worldCityCoordinates[zoneId.id]?.let { return Triple(it.lat, it.lon, true) }

        val offsetHours = zoneId.rules.getStandardOffset(Instant.now()).totalSeconds / 3600.0
        val approxLon = (offsetHours * 15.0).coerceIn(-180.0, 180.0)
        return Triple(0.0, approxLon, false)
    }
}
