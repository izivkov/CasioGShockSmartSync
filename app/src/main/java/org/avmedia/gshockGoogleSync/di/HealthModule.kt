package org.avmedia.gshockGoogleSync.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.avmedia.gshockGoogleSync.health.HealthConnectManager
import org.avmedia.gshockGoogleSync.health.IHealthConnectManager
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HealthModule {
    @Binds
    @Singleton
    abstract fun bindHealthConnectManager(
        healthConnectManager: HealthConnectManager
    ): IHealthConnectManager

    companion object {
        @Provides
        @Singleton
        fun provideHealthConnectManager(@ApplicationContext context: Context): HealthConnectManager {
            return HealthConnectManager(context)
        }
    }
}