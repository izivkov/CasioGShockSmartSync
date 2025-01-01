package org.avmedia.translateapi.engine

import kotlinx.coroutines.delay
import me.bush.translator.Language
import me.bush.translator.TranslationException
import me.bush.translator.Translator
import java.lang.Thread.sleep
import java.util.Locale

class BushTranslationEngine (
) : ITranslationEngine {

    private val translator: Translator = Translator()

    override fun translate(
        text: String,
        target: Locale,
    ): String {
        return translator.translateBlocking(
            text,
            Language(remapObsoleteCodes(target.language)),
            Language.AUTO,
        ).translatedText
    }

    override suspend fun translateAsync(
        text: String,
        target: Locale,
    ): String {
        return translator.translate(
            text,
            Language(remapObsoleteCodes(target.language)),
            Language.AUTO,
        ).translatedText
    }

    private fun remapObsoleteCodes(languageCode: String): String {
        // Map of obsolete codes to updated codes
        val languageMap = mapOf(
            "in" to "id", // Indonesian
            "iw" to "he", // Hebrew
            "ji" to "yi", // Yiddish
            "sh" to "sr",  // Serbo-Croatian
            "nb" to "no",  // Norwegian Bokmål
        )

        // Return the updated code if found in the map; otherwise, return the input code
        return languageMap[languageCode] ?: languageCode
    }
}
