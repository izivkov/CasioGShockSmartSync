package org.avmedia.gshockapi.io

import org.avmedia.gshockapi.model.WorkoutSession
import org.avmedia.gshockapi.model.WorkoutSports
import org.avmedia.gshockapi.model.WorkoutSummary
import org.avmedia.gshockapi.model.WorkoutUnits

/** Puts the decoded parts of one workout together. Pure. */
object WorkoutSessionBuilder {
    fun build(slot: Int, header: WorkoutHeader, dataBlocks: List<ByteArray>, lapBlocks: List<ByteArray>): WorkoutSession {
        val warnings = ArrayList<String>()
        val sport = header.sport
        if (WorkoutSportFamily.of(sport) == null) warnings += "unknown sport code $sport"
        if (WorkoutSportFamily.isInferred(sport)) warnings += "sport $sport is decoded like its running or walking kind (unconfirmed)"
        val summary = WorkoutSummaryDecoder.decode(header.raw, sport, header.formatVersion)
        if (summary == null) warnings += "summary not decoded"
        val dataPayload = cut(WorkoutRecords.join(dataBlocks), header.dataBytes, "data", warnings)
        val lapPayload = cut(WorkoutRecords.join(lapBlocks), header.lapBytes, "laps", warnings)
        val samples = WorkoutRecords.samples(sport, dataPayload)
        val laps = WorkoutRecords.laps(sport, lapPayload)
        WorkoutRecords.sampleRecordSize(sport)?.let { if (dataPayload.size % it != 0) warnings += "data ends in a cut record" }
        WorkoutRecords.lapRecordSize(sport)?.let { if (lapPayload.size % it != 0) warnings += "laps end in a cut record" }
        return WorkoutSession(
            slot = slot,
            sport = sport,
            sportName = WorkoutSports.name(sport),
            startUtcMillis = header.startUtcMillis,
            endUtcMillis = header.endUtcMillis,
            utcOffsetRaw = header.utcOffsetRaw,
            hasGps = header.hasGps,
            formatVersion = header.formatVersion,
            summary = summary ?: WorkoutSummary(),
            laps = laps,
            samples = samples,
            unconfirmed = WorkoutUnits.UNCONFIRMED,
            warnings = warnings,
        )
    }

    /** Cuts the joined payload to the byte count the header announces; a shorter payload is kept and noted. */
    private fun cut(joined: ByteArray, announced: Long, what: String, warnings: MutableList<String>): ByteArray {
        if (announced in 0..joined.size.toLong()) return joined.copyOf(announced.toInt())
        warnings += "$what: header announces $announced bytes, chain holds ${joined.size}"
        return joined
    }
}
