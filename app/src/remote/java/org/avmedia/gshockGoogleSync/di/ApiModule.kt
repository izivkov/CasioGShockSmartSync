package org.avmedia.gshockGoogleSync.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.avmedia.gshockGoogleSync.remote.RemoteGShockApi
import org.avmedia.gshockapi.IGShockAPI
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ApiModule {

    @Provides
    @Singleton
    fun provideIGShockAPI(remoteApi: RemoteGShockApi): IGShockAPI {
        return remoteApi
    }
}
