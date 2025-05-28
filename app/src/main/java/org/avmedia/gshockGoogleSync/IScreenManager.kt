package org.avmedia.gshockGoogleSync

import org.avmedia.gshockGoogleSync.data.repository.GShockRepository

interface IScreenManager {
    fun showContentSelector(repository: GShockRepository)
    fun showRunActionsScreen()
    fun showPreConnectionScreen()
    fun showInitialScreen()
    fun showError(message: String)
}