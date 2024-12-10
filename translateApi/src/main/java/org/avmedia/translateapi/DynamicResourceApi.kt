package org.avmedia.translateapi

import org.avmedia.translateapi.engine.BushTranslationEngine
import org.avmedia.translateapi.engine.ITranslationEngine
import java.util.Locale

object DynamicResourceApi {
    private lateinit var translator: DynamicTranslator

    fun init(engine: ITranslationEngine? = null, language: Locale = Locale.getDefault(), overWrites: Array<Pair<ResourceLocaleKey, String>> = arrayOf()): DynamicResourceApi {
        translator = DynamicTranslator(engine ?: BushTranslationEngine())
        translator.init().setOverwrites(overWrites).setLanguage(language)
        return this
    }

    fun getApi(): DynamicTranslator {
        require(::translator.isInitialized) {"DynamicResourceApi not initialized. Call init() first."}
        return translator
    }
}