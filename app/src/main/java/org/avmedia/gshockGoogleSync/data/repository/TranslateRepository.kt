package org.avmedia.gshockGoogleSync.data.repository

import org.avmedia.translateapi.DynamicTranslator
import org.avmedia.translateapi.IDynamicTranslator
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranslateRepository @Inject constructor(
    api: DynamicTranslator
) : IDynamicTranslator by api