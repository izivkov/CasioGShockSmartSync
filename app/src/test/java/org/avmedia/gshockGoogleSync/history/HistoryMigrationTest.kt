package org.avmedia.gshockGoogleSync.history

import android.content.Context
import androidx.room.Room
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** A version 1 file (days, hours, nights only) opens as version 3 with its rows kept and the workout and heart rate tables added. */
@RunWith(RobolectricTestRunner::class)
class HistoryMigrationTest {
    @Test fun versionOneFileKeepsItsDaysAndTakesWorkouts() {
        val context: Context = RuntimeEnvironment.getApplication()
        val file = context.getDatabasePath("migration-test.db").also { it.parentFile?.mkdirs(); it.delete() }
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            old.execSQL("CREATE TABLE IF NOT EXISTS `day` (`date` TEXT NOT NULL, `steps` INTEGER, `distanceM` INTEGER, `kcal` INTEGER, `activeMin` INTEGER, `walkSec` INTEGER, `runSec` INTEGER, `stillSec` INTEGER, `hrRest` INTEGER, `hrMin` INTEGER, `hrMax` INTEGER, `spo2` INTEGER, `stairsUp` INTEGER, `stairsDown` INTEGER, PRIMARY KEY(`date`))")
            old.execSQL("CREATE TABLE IF NOT EXISTS `hour` (`date` TEXT NOT NULL, `hour` INTEGER NOT NULL, `steps` INTEGER, `hr` INTEGER, PRIMARY KEY(`date`, `hour`))")
            old.execSQL("CREATE TABLE IF NOT EXISTS `night` (`date` TEXT NOT NULL, `score` INTEGER, `rechargeStatus` INTEGER, `rechargeLevel` INTEGER, `fellAsleep` TEXT, `wokeUp` TEXT, `totalMin` INTEGER, `deepMin` INTEGER, `lightMin` INTEGER, `remMin` INTEGER, `awakeMin` INTEGER, `hr` INTEGER, `breathing` REAL, `stages` TEXT, PRIMARY KEY(`date`))")
            old.execSQL("INSERT INTO day (date, steps) VALUES ('2026-11-20', 5000)")
            old.version = 1
        }
        val db = Room.databaseBuilder(context, HistoryDb::class.java, "migration-test.db")
            .addMigrations(HistoryDb.MIGRATION_1_2, HistoryDb.MIGRATION_2_3).allowMainThreadQueries().build()
        try {
            assertEquals(5000, db.dao().allDays().single().steps)
            db.dao().upsert(WorkoutMapper.map(WorkoutTestData.session(), 1L))
            assertEquals(1, db.dao().allWorkouts().size)
            assertEquals(2, db.dao().allLaps().size)
            db.dao().upsert(HistoryBatch(emptyList(), emptyList(), emptyList(), listOf(HrSlotRow("2026-11-20", 600, 70))))
            assertEquals(70, db.dao().hrSlotsOf("2026-11-20").single().bpm)
        } finally {
            db.close()
        }
    }
}
