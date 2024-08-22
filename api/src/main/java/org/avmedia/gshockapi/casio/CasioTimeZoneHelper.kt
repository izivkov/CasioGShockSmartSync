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

    val timeZoneMap by lazy {
        timeZoneTable.associateBy { it.zoneName }.toMap()
    }

    private fun isEquivalent(tz1: ZoneId, tz2: ZoneId): Boolean {
        val rules1 = tz1.normalized().rules
        val rules2 = tz2.normalized().rules

        return rules1.getStandardOffset(Instant.now())
            .equals(rules2.getStandardOffset(Instant.now())) && rules1.getDaylightSavings(Instant.now())
            .equals(rules1.getDaylightSavings(Instant.now())) && rules1.transitionRules.equals(
            rules2.transitionRules
        )
    }

    fun findTimeZone(timeZoneName: String): CasioTimeZone {

        var foundEntry = timeZoneMap[timeZoneName]
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
}