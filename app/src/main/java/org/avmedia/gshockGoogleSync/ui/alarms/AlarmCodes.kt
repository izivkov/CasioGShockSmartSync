package org.avmedia.gshockGoogleSync.ui.alarms

/**
 * A type-safe enum to manage the mapping between prayer names and their specific integer codes.
 * This ensures that only valid prayer names can be used and prevents errors from typos.
 *
 * It includes a companion object with helper methods to easily look up codes by name or
 * names by code.
 */
enum class AlarmCodes(val code: Int, val prayerName: String) {
    // Defines the default/unspecified case with code 0.
    DEFAULT(0, "Daily"),

    // Defines the standard Islamic prayer times with their respective codes.
    FAJR(1, "Fajr"),
    DHUHR(2, "Dhuhr"),
    ASR(3, "Asr"),
    MAGHRIB(4, "Maghrib"),
    ISHA(5, "Isha");

    companion object {
        // Creates a map for efficient lookup of an enum entry by its integer code.
        // This map is initialized lazily the first time it's accessed.
        private val codeToEnumMap by lazy { entries.associateBy { it.code } }

        // Creates a map for efficient lookup of an enum entry by its prayer name (case-insensitive).
        private val nameToEnumMap by lazy { entries.associateBy { it.prayerName.uppercase() } }

        /**
         * Looks up the integer code for a given prayer name.
         *
         * @param name The string name of the prayer (e.g., "Fajr", "dhuhr"). Case-insensitive.
         * @return The corresponding integer code. Returns the code for DEFAULT (0) if the name is not found.
         */
        fun getCode(name: String): Int {
            return nameToEnumMap[name.uppercase()]?.code ?: DEFAULT.code
        }

        /**
         * Looks up the prayer name for a given integer code.
         *
         * @param code The integer code to look up.
         * @return The corresponding string name. Returns the name for DEFAULT ("Default") if the code is not found.
         */
        fun getName(code: Int): String {
            return codeToEnumMap[code]?.prayerName ?: DEFAULT.prayerName
        }
    }
}
