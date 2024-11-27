package org.avmedia.gshockGoogleSync.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockapi.GShockAPI
import org.avmedia.gshockapi.GShockAPIMock
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    @Named("api")
    fun provideGShockRepository(api: GShockAPI): GShockRepository {
        return GShockRepository(api)
    }
}