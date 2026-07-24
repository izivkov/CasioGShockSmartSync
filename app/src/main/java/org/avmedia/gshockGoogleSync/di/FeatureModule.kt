package org.avmedia.gshockGoogleSync.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.avmedia.gshockGoogleSync.ui.common.IWatchFeatureManager
import org.avmedia.gshockGoogleSync.ui.common.WatchFeatureManager
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FeatureModule {

    @Binds
    @Singleton
    abstract fun bindWatchFeatureManager(
        watchFeatureManager: WatchFeatureManager
    ): IWatchFeatureManager
}
