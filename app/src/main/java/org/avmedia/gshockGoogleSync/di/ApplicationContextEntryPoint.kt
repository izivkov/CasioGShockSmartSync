package org.avmedia.gshockGoogleSync.di

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ApplicationContextEntryPoint {
    @ApplicationContext
    fun getApplicationContext(): Context
}