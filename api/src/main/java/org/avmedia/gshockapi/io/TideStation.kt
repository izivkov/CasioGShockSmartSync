package org.avmedia.gshockapi.io

/** One harmonic constant: amplitude in cm and the Greenwich phase lag in degrees (TICON and NOAA convention). */
data class TideConstant(val amplitudeCm: Double, val phaseDeg: Double)

/**
 * The open constants of one tide gauge. [constants] is keyed by the canonical names of [TideConstituents].
 * [zoneQuarterHours]: UTC offset of the place in quarter hours (east positive), standard time.
 * [z0Cm] is the mean level above chart datum; null when not known (the builders then use the sum of the four main amplitudes
 * and the screen shows "approximate").
 */
data class TideStation(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val zoneQuarterHours: Int,
    val constants: Map<String, TideConstant>,
    val z0Cm: Double? = null,
)

object TideConstituents {
    /** The 60 of the 60 constituent form, in the order of the record (notes I3). */
    val ORDER: List<String> = listOf(
        "Sa", "Ssa", "Mm", "MSf", "Mf", "2Q1", "sigma1", "Q1", "rho1", "O1", "MP1", "M1", "chi1", "pi1", "P1", "S1", "K1", "psi1",
        "phi1", "theta1", "J1", "SO1", "OO1", "OQ2", "MNS2", "2N2", "mu2", "N2", "nu2", "OP2", "M2", "MKS2", "lambda2", "L2", "T2",
        "S2", "R2", "K2", "MSN2", "KJ2", "2SM2", "MO3", "M3", "SO3", "MK3", "SK3", "MN4", "M4", "SN4", "MS4", "MK4", "S4", "SK4",
        "2MN6", "M6", "MSN6", "2MS6", "2MK6", "2SM6", "MSK6",
    )

    private val ALIASES = mapOf("sgm" to "sigma1")
    private val BY_LOWER = ORDER.associateBy { it.lowercase() }

    /** The canonical name for a name as TICON, NOAA or a person writes it (any case), or null when the watch has no such constituent. */
    fun canonical(name: String): String? {
        val k = name.trim().lowercase()
        return BY_LOWER[ALIASES[k] ?: k]
    }
}
