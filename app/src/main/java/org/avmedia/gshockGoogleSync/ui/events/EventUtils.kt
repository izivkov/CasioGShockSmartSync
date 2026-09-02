package org.avmedia.gshockGoogleSync.ui.events

import java.text.Normalizer
import java.util.regex.Pattern

object EventUtils {

    fun sanitizeEventTitle(input: String): String {
        fun filterAllowedCharacters(s: String): String {
            val allowedSymbols = " !\"#\$%&'()*+,-./:;<=>?@[\\]^_`{|}"
            val regex = "[^A-Za-z0-9${Pattern.quote(allowedSymbols)}]".toRegex()
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
