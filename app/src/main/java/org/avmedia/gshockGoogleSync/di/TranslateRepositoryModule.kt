package org.avmedia.gshockGoogleSync.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.avmedia.gshockGoogleSync.data.repository.TranslateRepository
import org.avmedia.translateapi.DynamicTranslator
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TranslateRepositoryModule {

    @Provides
    @Singleton
    fun provideTranslateRepository(translateApi: DynamicTranslator): TranslateRepository {
        return TranslateRepository(translateApi)
    }
}