package org.avmedia.gshockGoogleSync.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.avmedia.translateapi.DynamicTranslator
import java.util.Locale
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TranslateApiModule {

    @Provides
    @Singleton
    fun provideTranslateAPI(): DynamicTranslator {
        return DynamicTranslator().init().setOverwrites(arrayOf()).setLanguage(Locale.getDefault())
    }
}
