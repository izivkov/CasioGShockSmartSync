package org.avmedia.gshockGoogleSync.ui.events

import java.text.Normalizer
import java.util.regex.Pattern

object EventUtils {

    fun sanitizeEventTitle(input: String): String {
        fun filterAllowedCharacters(s: String): String {
            val allowedSymbols = " !\"#\$%&'()*+,-./:;<=>?@[\\]^_`{|}"
            // Allow Latin (A-Z, a-z), Digits (0-9), standard symbols, and Cyrillic (\u0400-\u04FF)
            // Add Cyrillic as well, which will be converted to Latin on the watch.
            val regex = "[^A-Za-z0-9\u0400-\u04FF${Pattern.quote(allowedSymbols)}]".toRegex()
            return s.replace(regex, "")
        }

        fun removeEmojis(s: String): String {
            return s.replace(Regex("[\\p{So}\\p{Cn}]"), "")
        }

        fun removeAccents(s: String): String {
            val normalized = Normalizer.normalize(s, Normalizer.Form.NFD)
            return Pattern.compile("\\p{InCombiningDiacriticalMarks}+").matcher(normalized)
                .replaceAll("")
        }

        val noEmojis = removeEmojis(input)
        val noAccents = removeAccents(noEmojis)
        val filtered = filterAllowedCharacters(noAccents)
        return filtered.take(18)
    }
}
