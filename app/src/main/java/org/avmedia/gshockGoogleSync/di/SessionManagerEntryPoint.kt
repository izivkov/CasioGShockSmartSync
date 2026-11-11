package org.avmedia.gshockGoogleSync.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.avmedia.gshockGoogleSync.session.SessionManager

/** Lets composables that have no ViewModel read the connection [SessionManager]. */
@EntryPoint
@InstallIn(SingletonComponent::class)
fun interface SessionManagerEntryPoint {
    fun sessionManager(): SessionManager
}
