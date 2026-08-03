package org.avmedia.gshockGoogleSync.utils

import java.util.Locale

// ============================================================================
// Cyrillic -> Latin transliteration
// ============================================================================
// Watch displays are Latin-only with no diacritics, so this produces plain
// ASCII output rather than the accented Latin some transliteration standards
// (and general-purpose libraries) default to.

object CyrillicToLatin {

    enum class Lang { RUSSIAN, BULGARIAN, MACEDONIAN, SERBIAN, MONGOLIAN, KAZAKH }

    private val CYRILLIC_LOCALES = mapOf(
        "ru" to Lang.RUSSIAN,
        "bg" to Lang.BULGARIAN,
        "mk" to Lang.MACEDONIAN,
        "sr" to Lang.SERBIAN,
        "mn" to Lang.MONGOLIAN,
        "kk" to Lang.KAZAKH
    )

    private val russian = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d",
        'е' to "e", 'ё' to "e", 'ж' to "zh", 'з' to "z", 'и' to "i",
        'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n",
        'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t",
        'у' to "u", 'ф' to "f", 'х' to "kh", 'ц' to "ts", 'ч' to "ch",
        'ш' to "sh", 'щ' to "shch",
        'ъ' to "", 'ы' to "y", 'ь' to "",
        'э' to "e", 'ю' to "yu", 'я' to "ya"
    )

    // Official Bulgarian "Streamlined System" (2009 transliteration law)
    private val bulgarian = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d",
        'е' to "e", 'ж' to "zh", 'з' to "z", 'и' to "i",
        'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n",
        'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t",
        'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "ts", 'ч' to "ch",
        'ш' to "sh", 'щ' to "sht",
        'ъ' to "a",   // real vowel in Bulgarian, NOT silent
        'ь' to "y",
        'ю' to "yu", 'я' to "ya"
        // no ё, ы, э, щ(as shch), ъ(as silent) — those are Russian-only forms
    )

    // National romanization of Macedonian
    private val macedonian = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d",
        'ѓ' to "gj", 'е' to "e", 'ж' to "zh", 'з' to "z", 'ѕ' to "dz",
        'и' to "i", 'ј' to "j", 'к' to "k", 'л' to "l", 'љ' to "lj",
        'м' to "m", 'н' to "n", 'њ' to "nj", 'о' to "o", 'п' to "p",
        'р' to "r", 'с' to "s", 'т' to "t", 'ќ' to "kj", 'у' to "u",
        'ф' to "f", 'х' to "h", 'ц' to "ts", 'ч' to "ch", 'џ' to "dz",
        'ш' to "sh"
    )

    // Standard Serbian Latin digraph equivalents (Cyrillic -> Gaj's Latin)
    private val serbian = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d",
        'ђ' to "dj", 'е' to "e", 'ж' to "zh", 'з' to "z", 'и' to "i",
        'ј' to "j", 'к' to "k", 'л' to "l", 'љ' to "lj", 'м' to "m",
        'н' to "n", 'њ' to "nj", 'о' to "o", 'п' to "p", 'р' to "r",
        'с' to "s", 'т' to "t", 'ћ' to "c", 'у' to "u", 'ф' to "f",
        'х' to "h", 'ц' to "ts", 'ч' to "ch", 'џ' to "dz", 'ш' to "sh"
    )

    // Mongolian Cyrillic (adds ө, ү); conventions closer to Russian on kh/shch
    private val mongolian = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d",
        'е' to "e", 'ё' to "yo", 'ж' to "j", 'з' to "z", 'и' to "i",
        'й' to "i", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n",
        'о' to "o", 'ө' to "u", 'п' to "p", 'р' to "r", 'с' to "s",
        'т' to "t", 'у' to "u", 'ү' to "u", 'ф' to "f", 'х' to "kh",
        'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "shch",
        'ъ' to "", 'ы' to "y", 'ь' to "",
        'э' to "e", 'ю' to "yu", 'я' to "ya"
    )

    // Kazakh Cyrillic = Russian alphabet + 9 additional letters; shared
    // letters keep Russian conventions (х->kh, щ->shch, ъ/ь silent, etc.)
    private val kazakh = russian + mapOf(
        'ә' to "a",
        'ғ' to "gh",
        'қ' to "q",
        'ң' to "ng",
        'ө' to "o",
        'ұ' to "u",
        'ү' to "u",
        'һ' to "h",
        'і' to "i"
    )

    private fun tableFor(lang: Lang): Map<Char, String> = when (lang) {
        Lang.RUSSIAN -> russian
        Lang.BULGARIAN -> bulgarian
        Lang.MACEDONIAN -> macedonian
        Lang.SERBIAN -> serbian
        Lang.MONGOLIAN -> mongolian
        Lang.KAZAKH -> kazakh
    }

    private const val CYRILLIC_RANGE_START = 0x0400
    private const val CYRILLIC_RANGE_END = 0x04FF
    private fun isCyrillic(c: Char) = c.code in CYRILLIC_RANGE_START..CYRILLIC_RANGE_END

    private fun detectLang(): Lang =
        CYRILLIC_LOCALES[Locale.getDefault().language] ?: Lang.RUSSIAN

    fun transliterate(input: String, lang: Lang = detectLang()): String {
        val table = tableFor(lang)
        return input.map { ch ->
            val lower = ch.lowercaseChar()
            if (isCyrillic(lower)) {
                val mapped = table[lower] ?: ch.toString()
                if (ch.isUpperCase()) mapped.replaceFirstChar { it.uppercase() } else mapped
            } else {
                ch.toString()
            }
        }.joinToString("")
    }
}
