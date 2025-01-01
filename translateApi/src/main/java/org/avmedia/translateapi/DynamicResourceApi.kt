package org.avmedia.translateapi

import org.avmedia.translateapi.engine.BushTranslationEngine
import org.avmedia.translateapi.engine.ITranslationEngine
import java.util.Locale

object DynamicResourceApi {
    private lateinit var translator: DynamicTranslator

    fun init(): DynamicResourceApi {

        translator = DynamicTranslator()
        translator.init()
        translator.setEngine(BushTranslationEngine())

        return this
    }

    fun setAppLocale(locale: Locale): DynamicResourceApi {
        getApi().setAppLocale(locale)
        return this
    }

    fun setOverwrites(entries: Array<Pair<ResourceLocaleKey, () -> String>>): DynamicResourceApi {
        getApi().setOverwrites(entries)
        return this
    }

    fun setEngine(engine: ITranslationEngine): DynamicResourceApi {
        getApi().setEngine(engine)
        return this
    }

    fun addEngines(engines: Collection<ITranslationEngine>): DynamicResourceApi {
        getApi().addEngines(engines)
        return this
    }

    fun addEngine(engine: ITranslationEngine): DynamicResourceApi {
        getApi().addEngine(engine)
        return this
    }

    fun getApi(): DynamicTranslator {
        require(::translator.isInitialized) {"DynamicResourceApi not initialized. Call init() first."}
        return translator
    }
}