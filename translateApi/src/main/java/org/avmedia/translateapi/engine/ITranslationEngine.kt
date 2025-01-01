package org.avmedia.translateapi.engine

import me.bush.translator.Language
import java.util.Locale

interface ITranslationEngine {

    fun translate(
        text: String,
        target: Locale,
    ): String

    suspend fun translateAsync(
        text: String,
        target: Locale,
    ): String
}