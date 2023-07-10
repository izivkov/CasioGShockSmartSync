package org.avmedia.gShockPhoneSync

import android.app.Application
import android.content.Intent
import org.avmedia.gShockPhoneSync.utils.ForegroundService

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        startService(Intent(this, ForegroundService::class.java))
    }
}