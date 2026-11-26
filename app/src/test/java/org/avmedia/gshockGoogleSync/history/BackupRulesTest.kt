package org.avmedia.gshockGoogleSync.history

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The history database is part of the Android app backup: backup stays on and no rule leaves the database out. */
class BackupRulesTest {
    private fun main(path: String) = File("src/main/$path").readText()

    @Test fun backupIsOn() {
        assertTrue(main("AndroidManifest.xml").contains("""android:allowBackup="true""""))
    }

    @Test fun noRuleExcludesTheDatabaseOrLimitsTheBackup() {
        for (f in listOf("res/xml/backup_rules.xml", "res/xml/data_extraction_rules.xml")) {
            val text = main(f).replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
            assertFalse("$f excludes the database", Regex("<exclude[^>]*domain=\"(database|root)\"").containsMatchIn(text))
            // An include on its own would back up only that path: the database must then be named too.
            if (text.contains("<include")) assertTrue("$f has includes but not the database", text.contains(""""database"""") && text.contains(HistoryDbName))
        }
    }

    @Test fun theDatabaseNameIsTheOneTheRulesWouldNeed() {
        assertEquals("health_history.db", HistoryDb.NAME)
    }

    private val HistoryDbName = HistoryDb.NAME
}
