package org.avmedia.gshockGoogleSync.homeassistant

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The app's side of the Home Assistant wiring, read from the sources. */
class HaAppWiringTest {
    /** The button press goes straight to the plugins that asked for it (Home Assistant runs its chosen action). */
    @Test fun theButtonActionGoesStraightToThePlugins() {
        val src = File("src/main/java/org/avmedia/gshockGoogleSync/ActionContainer.kt").readText()
        val action = src.substringAfter("inner class PluginButtonAction").substringBefore("inner class Separator")
        assertTrue(action, "pluginHub.buttonPressed()" in action)
    }

    /** No screen, string, icon, log source or class of the removed feature is left (HA's own mobile_app webhook stays). */
    @Test fun nothingOfTheRemovedFeatureIsLeft() {
        val gone = listOf("WebhookSender", "WebhookDispatcher", "WebhookWorker", "WebhooksScreen", "WebhookStorage",
            "TemplateRenderer", "R.string.webhook", "ic_webhook", "LogSource.Webhook", "Service.WEBHOOKS", "onOpenWebhooks")
        val roots = listOf(File("src/main/java"), File("../api/src/main/java"), File("../plugins"))
        val hits = roots.flatMap { r -> r.walk().filter { it.extension == "kt" }.toList() }
            .flatMap { f -> f.readText().let { text -> gone.filter { it in text }.map { "${f.name}: $it" } } }
        assertEquals(emptyList<String>(), hits)
        val strings = File("src/main/res/values/strings.xml").readText()
        assertTrue("strings.xml still has a webhooks entry", !Regex("""name="[a-z_]*webhook""").containsMatchIn(strings))
        assertTrue(!File("src/main/res/drawable/ic_webhook.xml").exists())
    }

    /** The old Home Assistant (mobile app) plugin is gone: no module, no build line, no class, no switch default. */
    @Test fun nothingOfTheMobileAppPluginIsLeft() {
        assertTrue(!File("../plugins/homeassistant").exists())
        assertTrue(":plugins:homeassistant" !in File("../settings.gradle").readText())
        assertTrue(":plugins:homeassistant" !in File("build.gradle").readText())
        val gone = listOf("org.avmedia.smartsync.homeassistant", "WatchSettingCommands", "legacyOn", "HaPushChannel", "HaDashboardService")
        val hits = File("src/main/java").walk().filter { it.extension == "kt" }
            .flatMap { f -> f.readText().let { text -> gone.filter { it in text }.map { "${f.name}: $it" } } }.toList()
        assertEquals(emptyList<String>(), hits)
    }
}
