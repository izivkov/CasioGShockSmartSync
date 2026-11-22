package org.avmedia.gshockGoogleSync.webhook

import org.avmedia.gshockGoogleSync.notifications.AppEntry
import org.avmedia.gshockGoogleSync.notifications.InstalledApps
import org.junit.Assert.assertEquals
import org.junit.Test

class InstalledAppsFilterTest {
    private val apps = listOf(AppEntry("com.whatsapp", "WhatsApp"), AppEntry("com.google.android.gm", "Gmail"), AppEntry("com.spotify.music", "Spotify"))

    @Test fun blankQueryKeepsAll() = assertEquals(apps, InstalledApps.filter(apps, "  "))

    @Test fun searchMatchesNameOrPackageIgnoringCase() {
        assertEquals(listOf("Gmail"), InstalledApps.filter(apps, "GMA").map { it.label })
        assertEquals(listOf("Spotify"), InstalledApps.filter(apps, "spotify.music").map { it.label })
        assertEquals(emptyList<AppEntry>(), InstalledApps.filter(apps, "zzz"))
    }
}
