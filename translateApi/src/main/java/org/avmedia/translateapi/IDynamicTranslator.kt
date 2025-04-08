package org.avmedia.translateapi

import android.content.Context
import org.avmedia.translateapi.engine.ITranslationEngine
import java.util.Locale

interface IDynamicTranslator {
    var appLocale: Locale
    val translationOverwrites: TranslationOverwrites
    val networkConnectionChecker: NetworkConnectionChecker
    fun init(): DynamicTranslator
    fun setAppLocale(locale: Locale): DynamicTranslator
    fun setOverwrites(entries: Array<Pair<ResourceLocaleKey, () -> String>>): DynamicTranslator
    fun setEngine(engine: ITranslationEngine): DynamicTranslator
    fun addEngine(engine: ITranslationEngine): DynamicTranslator
    fun addEngines(engines: Collection<ITranslationEngine>): DynamicTranslator
    fun addOverwrites(entries: Array<Pair<ResourceLocaleKey, () -> String>>)
    fun addOverwrite(overWrite: Pair<ResourceLocaleKey, () -> String>)
    fun setSafeMode(safeMode: Boolean): DynamicTranslator

    fun getString(
        context: Context,
        id: Int,
        vararg formatArgs: Any,
        callback: (String) -> Unit = {},
    ): String

    fun stringResource(
        context: Context,
        id: Int,
        vararg formatArgs: Any,
        callback: (String) -> Unit = {},
    ): String

    fun getStringBlocking(
        context: Context,
        id: Int,
        vararg formatArgs: Any,
    ): String

    fun stringResourceBlocking(
        context: Context,
        id: Int,
        vararg formatArgs: Any,
    ): String
}