package org.avmedia.gshockGoogleSync.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.avmedia.gshockGoogleSync.data.repository.TranslateRepository

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TranslateEntryPoint {
    fun getTranslateRepository(): TranslateRepository
}