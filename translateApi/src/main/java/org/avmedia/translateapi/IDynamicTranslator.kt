package org.avmedia.translateapi

import android.content.Context
import org.avmedia.translateapi.engine.ITranslationEngine
import java.util.Locale

interface IDynamicTranslator {
    var locale: Locale?
    val translationOverwrites: TranslationOverwrites
    val networkConnectionChecker: NetworkConnectionChecker
    fun init(): DynamicTranslator
    fun setLanguage(locale: Locale): DynamicTranslator
    fun setOverwrites(entries: Array<Pair<ResourceLocaleKey, String>>): DynamicTranslator
    fun getString(
        context: Context,
        id: Int,
        vararg formatArgs: Any,
        locale: Locale? = null,
    ): String

    fun stringResource(
        context: Context,
        id: Int,
        vararg formatArgs: Any,
        locale: Locale? = null
    ): String

    suspend fun stringResourceAsync(
        context: Context,
        id: Int,
        vararg formatArgs: Any,
        locale: Locale? = null
    ): String
}