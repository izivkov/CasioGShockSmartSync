package org.avmedia.gshockGoogleSync.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.avmedia.gshockGoogleSync.utils.UppercaseTranslationEngine
import org.avmedia.translateapi.DynamicTranslator
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TranslateApiModule {

    @Provides
    @Singleton
    fun provideTranslateAPI(): DynamicTranslator {
        return DynamicTranslator().init()
    }
}
