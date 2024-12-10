package org.avmedia.translateapi.engine

import me.bush.translator.Language
import java.util.Locale

interface ITranslationEngine {

    // True if all conversion is dome inLike, without accessing any language specific string.xml files
    fun isInline (): Boolean

    fun translate(
        text: String,
        target: Locale,
    ): String

    suspend fun translateAsync(
        text: String,
        target: Locale,
    ): String
}