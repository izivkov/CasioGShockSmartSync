package org.avmedia.gshockGoogleSync

import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.data.repository.TranslateRepository

interface IScreenManager {
    fun showContentSelector(repository: GShockRepository, translateApi: TranslateRepository)
    fun showRunActionsScreen(translateApi: TranslateRepository)
    fun showPreConnectionScreen()
    fun showInitialScreen()
    fun showError(message: String)
}