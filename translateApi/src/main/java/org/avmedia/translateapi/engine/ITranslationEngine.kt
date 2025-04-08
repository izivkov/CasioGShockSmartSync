package org.avmedia.translateapi.engine

import java.util.Locale

interface ITranslationEngine {

    fun translate(
        text: String,
        target: Locale,
    ): String
}