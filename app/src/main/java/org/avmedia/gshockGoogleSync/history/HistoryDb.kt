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
import kotlinx.coroutines.flow.Flow

/** What one decoded transfer adds to the history. */
class HistoryBatch(val days: List<DayRow>, val hours: List<HourRow>, val nights: List<NightRow>)

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
    }

    @Query("SELECT * FROM day ORDER BY date") abstract fun allDays(): List<DayRow>
    @Query("SELECT * FROM hour ORDER BY date, hour") abstract fun allHours(): List<HourRow>
    @Query("SELECT * FROM night ORDER BY date") abstract fun allNights(): List<NightRow>

    @Query("SELECT * FROM day ORDER BY date") abstract fun watchDays(): Flow<List<DayRow>>
    @Query("SELECT * FROM night ORDER BY date") abstract fun watchNights(): Flow<List<NightRow>>
    @Query("SELECT * FROM hour WHERE date = :date ORDER BY hour") abstract fun hoursOf(date: String): List<HourRow>
}

@Database(entities = [DayRow::class, HourRow::class, NightRow::class], version = 1, exportSchema = false)
abstract class HistoryDb : RoomDatabase() {
    abstract fun dao(): HistoryDao

    companion object {
        const val NAME = "health_history.db"

        @Volatile private var instance: HistoryDb? = null

        fun get(context: Context): HistoryDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, HistoryDb::class.java, NAME).build().also { instance = it }
        }
    }
}
