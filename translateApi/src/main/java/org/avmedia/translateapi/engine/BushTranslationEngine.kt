package org.avmedia.translateapi.engine

import me.bush.translator.Language
import me.bush.translator.Translator
import org.avmedia.translateapi.NetworkConnectionChecker
import java.util.Locale

class BushTranslationEngine (
) : ITranslationEngine {

    private val translator: Translator = Translator()

    override fun isInline(): Boolean {
        return false
    }

    override fun translate(
        text: String,
        target: Locale,
    ): String {
        return translator.translateBlocking(
            text,
            Language(target.language),
            Language.AUTO,
        ).translatedText
    }

    override suspend fun translateAsync(
        text: String,
        target: Locale,
    ): String {
        return translator.translate(
            text,
            Language(target.language),
            Language.AUTO,
        ).translatedText
    }
}
