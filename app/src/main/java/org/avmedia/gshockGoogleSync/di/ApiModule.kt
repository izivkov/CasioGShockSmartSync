package org.avmedia.gshockGoogleSync.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.avmedia.gshockapi.GShockAPI
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ApiModule {

    @Provides
    @Singleton
    fun provideGShockAPI(@ApplicationContext context: Context): GShockAPI {
        return GShockAPI(context)
    }
}
