package org.avmedia.gshockGoogleSync.history

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** What one decoded transfer adds to the history. */
class HistoryBatch(val days: List<DayRow>, val hours: List<HourRow>, val nights: List<NightRow>, val hrSlots: List<HrSlotRow> = emptyList())

@Dao
abstract class HistoryDao {
    @Query("SELECT * FROM day WHERE date = :date") abstract fun day(date: String): DayRow?
    @Query("SELECT * FROM night WHERE date = :date") abstract fun night(date: String): NightRow?
    @Query("SELECT * FROM hour WHERE date = :date AND hour = :hour") abstract fun hourRow(date: String, hour: Int): HourRow?

    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract fun putDay(row: DayRow)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract fun putHour(row: HourRow)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract fun putNight(row: NightRow)

    /** One row per day, hour and night however often the same data is read: the new row is laid over the old one. */
    @Transaction
    open fun upsert(batch: HistoryBatch) {
        batch.days.forEach { putDay(it.over(day(it.date))) }
        batch.hours.forEach { putHour(it.over(hourRow(it.date, it.hour))) }
        batch.nights.forEach { putNight(it.over(night(it.date))) }
        // A heart rate slot is a reading of its own: the newest read of the same slot replaces it, other slots stay.
        if (batch.hrSlots.isNotEmpty()) putHrSlots(batch.hrSlots)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract fun putHrSlots(rows: List<HrSlotRow>)
    @Query("SELECT * FROM hr_slot WHERE date = :date ORDER BY minute") abstract fun hrSlotsOf(date: String): List<HrSlotRow>

    @Query("SELECT * FROM day ORDER BY date") abstract fun allDays(): List<DayRow>
    @Query("SELECT * FROM hour ORDER BY date, hour") abstract fun allHours(): List<HourRow>
    @Query("SELECT * FROM night ORDER BY date") abstract fun allNights(): List<NightRow>

    @Query("SELECT * FROM day ORDER BY date") abstract fun watchDays(): Flow<List<DayRow>>
    @Query("SELECT * FROM night ORDER BY date") abstract fun watchNights(): Flow<List<NightRow>>
    @Query("SELECT * FROM hour WHERE date = :date ORDER BY hour") abstract fun hoursOf(date: String): List<HourRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract fun putWorkout(row: WorkoutRow)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract fun putLaps(rows: List<WorkoutLapRow>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract fun putSamples(rows: List<WorkoutSampleRow>)
    @Query("DELETE FROM workout_lap WHERE startMs = :startMs AND sport = :sport") abstract fun clearLaps(startMs: Long, sport: Int)
    @Query("DELETE FROM workout_sample WHERE startMs = :startMs AND sport = :sport") abstract fun clearSamples(startMs: Long, sport: Int)

    @Query("SELECT COUNT(*) FROM workout WHERE startMs = :startMs AND sport = :sport") abstract fun workoutCount(startMs: Long, sport: Int): Int

    /**
     * One row per workout (start and sport) however often it is read: laps and samples are replaced as a set, never added to.
     * True when the workout was not stored before.
     */
    @Transaction
    open fun upsert(batch: WorkoutBatch): Boolean {
        val w = batch.workout
        val isNew = workoutCount(w.startMs, w.sport) == 0
        clearLaps(w.startMs, w.sport)
        clearSamples(w.startMs, w.sport)
        putWorkout(w)
        putLaps(batch.laps)
        putSamples(batch.samples)
        return isNew
    }

    @Query("SELECT * FROM workout ORDER BY startMs DESC") abstract fun allWorkouts(): List<WorkoutRow>
    @Query("SELECT * FROM workout ORDER BY startMs DESC LIMIT 1") abstract fun newestWorkout(): WorkoutRow?
    @Query("SELECT * FROM workout ORDER BY startMs DESC") abstract fun watchWorkouts(): Flow<List<WorkoutRow>>
    @Query("SELECT * FROM workout_lap ORDER BY startMs, sport, idx") abstract fun allLaps(): List<WorkoutLapRow>
    @Query("SELECT * FROM workout_sample ORDER BY startMs, sport, idx") abstract fun allSamples(): List<WorkoutSampleRow>
    @Query("SELECT * FROM workout_lap WHERE startMs = :startMs AND sport = :sport ORDER BY idx") abstract fun lapsOf(startMs: Long, sport: Int): List<WorkoutLapRow>
    @Query("SELECT * FROM workout_sample WHERE startMs = :startMs AND sport = :sport ORDER BY idx") abstract fun samplesOf(startMs: Long, sport: Int): List<WorkoutSampleRow>
}

@Database(
    entities = [DayRow::class, HourRow::class, NightRow::class, WorkoutRow::class, WorkoutLapRow::class, WorkoutSampleRow::class, HrSlotRow::class],
    version = 3, exportSchema = false,
)
abstract class HistoryDb : RoomDatabase() {
    abstract fun dao(): HistoryDao

    companion object {
        const val NAME = "health_history.db"

        /** Version 2 adds the workout tables; days, hours and nights are left as they are. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `workout` (`startMs` INTEGER NOT NULL, `sport` INTEGER NOT NULL, `sportName` TEXT NOT NULL, `endMs` INTEGER, `utcOffsetRaw` INTEGER NOT NULL, `hasGps` INTEGER NOT NULL, `totalSec` INTEGER, `distance` REAL, `avgPace` INTEGER, `avgSpeed` REAL, `kcal` INTEGER, `hrAvg` INTEGER, `hrMax` INTEGER, `hrMin` INTEGER, `ascent` INTEGER, `descent` INTEGER, `cadenceAvg` INTEGER, `lapCount` INTEGER, `warnings` TEXT, `savedAtMs` INTEGER NOT NULL, PRIMARY KEY(`startMs`, `sport`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `workout_lap` (`startMs` INTEGER NOT NULL, `sport` INTEGER NOT NULL, `idx` INTEGER NOT NULL, `lapSec` INTEGER, `manual` INTEGER NOT NULL, `distance` REAL, `pace` INTEGER, `speed` REAL, `hrAvg` INTEGER, `hrMax` INTEGER, `hrMin` INTEGER, `ascent` INTEGER, `descent` INTEGER, `cadence` INTEGER, PRIMARY KEY(`startMs`, `sport`, `idx`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `workout_sample` (`startMs` INTEGER NOT NULL, `sport` INTEGER NOT NULL, `idx` INTEGER NOT NULL, `elapsedSec` INTEGER, `paused` INTEGER NOT NULL, `pace` INTEGER, `hr` INTEGER, `altitude` INTEGER, `cadence` INTEGER, `latitude` REAL, `longitude` REAL, `speed` REAL, `totalDistance` REAL, PRIMARY KEY(`startMs`, `sport`, `idx`))")
            }
        }

        /** Version 3 adds the heart rate per 2-minute slot; everything else is left as it is. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `hr_slot` (`date` TEXT NOT NULL, `minute` INTEGER NOT NULL, `bpm` INTEGER NOT NULL, PRIMARY KEY(`date`, `minute`))")
            }
        }

        @Volatile private var instance: HistoryDb? = null

        fun get(context: Context): HistoryDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, HistoryDb::class.java, NAME).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }
    }
}
