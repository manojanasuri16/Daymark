package com.daymark.app.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.daymark.app.domain.Habit
import com.daymark.app.domain.RelapseRecord
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String,
    /** When tracking began for this habit. */
    val startAt: Long,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toHabit() = Habit(id, name, emoji, startAt, sortOrder)

    companion object {
        fun from(h: Habit) = HabitEntity(id = h.id, name = h.name, emoji = h.emoji, startAt = h.startAt, sortOrder = h.sortOrder)
    }
}

@Entity(tableName = "relapses", indices = [Index("habitId")])
data class RelapseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "1") val habitId: Long = 1,
    val timestamp: Long,
    /** Pipe-separated trigger labels. */
    val triggers: String = "",
    /** 1..5, 0 = not recorded. */
    val mood: Int = 0,
    val situation: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toRecord() = RelapseRecord(
        id = id,
        habitId = habitId,
        timestamp = timestamp,
        triggers = triggers.split(SEP).filter { it.isNotBlank() },
        mood = mood,
        situation = situation,
        notes = notes,
    )

    companion object {
        const val SEP = "|"
        fun from(r: RelapseRecord) = RelapseEntity(
            id = r.id,
            habitId = r.habitId,
            timestamp = r.timestamp,
            triggers = r.triggers.joinToString(SEP) { it.replace(SEP, "/") },
            mood = r.mood,
            situation = r.situation,
            notes = r.notes,
        )
    }
}

@Dao
interface RelapseDao {
    @Query("SELECT * FROM relapses ORDER BY timestamp ASC")
    fun observeAll(): Flow<List<RelapseEntity>>

    @Query("SELECT * FROM relapses ORDER BY timestamp ASC")
    suspend fun getAll(): List<RelapseEntity>

    @Query("SELECT * FROM relapses WHERE id = :id")
    suspend fun get(id: Long): RelapseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RelapseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<RelapseEntity>)

    @Update
    suspend fun update(entity: RelapseEntity)

    @Delete
    suspend fun delete(entity: RelapseEntity)

    @Query("DELETE FROM relapses")
    suspend fun clear()

    @Query("DELETE FROM relapses WHERE habitId = :habitId")
    suspend fun deleteForHabit(habitId: Long)
}

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits ORDER BY sortOrder ASC, id ASC")
    suspend fun getAll(): List<HabitEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: HabitEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<HabitEntity>)

    @Update
    suspend fun update(entity: HabitEntity)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM habits")
    suspend fun clear()
}

/** v1 → v2: multiple trackers. Existing entries belong to tracker #1 (created from v1 settings at startup). */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `habits` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`emoji` TEXT NOT NULL, `startAt` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)",
        )
        db.execSQL("ALTER TABLE `relapses` ADD COLUMN `habitId` INTEGER NOT NULL DEFAULT 1")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_relapses_habitId` ON `relapses` (`habitId`)")
    }
}

@Database(entities = [RelapseEntity::class, HabitEntity::class], version = 2, exportSchema = true)
abstract class DaymarkDatabase : RoomDatabase() {
    abstract fun relapseDao(): RelapseDao
    abstract fun habitDao(): HabitDao

    companion object {
        fun build(context: Context): DaymarkDatabase =
            Room.databaseBuilder(context, DaymarkDatabase::class.java, "daymark.db").addMigrations(MIGRATION_1_2).build()
    }
}
