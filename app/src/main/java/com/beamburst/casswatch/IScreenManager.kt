package com.beamburst.casswatch

import com.beamburst.casswatch.data.repository.GShockRepository

interface IScreenManager {
    fun showContentSelector(repository: GShockRepository)
    fun showRunActionsScreen()
    fun showPreConnectionScreen()
    fun showInitialScreen()
    fun showError(message: String)
}