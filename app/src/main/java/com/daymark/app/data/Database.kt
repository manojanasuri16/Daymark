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
import com.daymark.app.domain.JournalEntry
import com.daymark.app.domain.RelapseRecord
import com.daymark.app.domain.UrgeRecord
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
    @ColumnInfo(defaultValue = "0") val spendAmount: Double = 0.0,
    @ColumnInfo(defaultValue = "7") val spendPeriodDays: Int = 7,
    @ColumnInfo(defaultValue = "0") val dailyLimit: Int = 0,
    /** Newline-separated. */
    @ColumnInfo(defaultValue = "''") val reasons: String = "",
) {
    fun toHabit() = Habit(
        id, name, emoji, startAt, sortOrder,
        spendAmount = spendAmount, spendPeriodDays = spendPeriodDays, dailyLimit = dailyLimit,
        reasons = reasons.split("\n").map { it.trim() }.filter { it.isNotEmpty() },
    )

    companion object {
        fun from(h: Habit) = HabitEntity(
            id = h.id, name = h.name, emoji = h.emoji, startAt = h.startAt, sortOrder = h.sortOrder,
            spendAmount = h.spendAmount, spendPeriodDays = h.spendPeriodDays.coerceAtLeast(1), dailyLimit = h.dailyLimit.coerceAtLeast(0),
            reasons = h.reasons.map { it.replace("\n", " ").trim() }.filter { it.isNotEmpty() }.joinToString("\n"),
        )
    }
}

@Entity(tableName = "urges", indices = [Index("habitId")])
data class UrgeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val timestamp: Long,
    val intensity: Int = 0,
    /** Pipe-separated trigger labels. */
    val triggers: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toRecord() = UrgeRecord(id, habitId, timestamp, intensity, triggers.split(RelapseEntity.SEP).filter { it.isNotBlank() }, notes)

    companion object {
        fun from(u: UrgeRecord) = UrgeEntity(
            id = u.id, habitId = u.habitId, timestamp = u.timestamp, intensity = u.intensity.coerceIn(0, 5),
            triggers = u.triggers.joinToString(RelapseEntity.SEP) { it.replace(RelapseEntity.SEP, "/") }, notes = u.notes,
        )
    }
}

@Entity(tableName = "journal", indices = [Index("habitId")])
data class JournalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val timestamp: Long,
    val mood: Int = 0,
    val text: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toEntry() = JournalEntry(id, habitId, timestamp, mood, text)

    companion object {
        fun from(j: JournalEntry) = JournalEntity(id = j.id, habitId = j.habitId, timestamp = j.timestamp, mood = j.mood.coerceIn(0, 5), text = j.text)
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

/** A photo attached to a journal entry. The image itself is a JPEG in private storage (see [PhotoStore]). */
@Entity(tableName = "journal_photos", indices = [Index("journalId")])
data class JournalPhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val journalId: Long,
    val fileName: String,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface JournalPhotoDao {
    @Query("SELECT * FROM journal_photos ORDER BY journalId ASC, sortOrder ASC")
    fun observeAll(): Flow<List<JournalPhotoEntity>>

    @Query("SELECT * FROM journal_photos ORDER BY journalId ASC, sortOrder ASC")
    suspend fun getAll(): List<JournalPhotoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<JournalPhotoEntity>)

    @Query("DELETE FROM journal_photos WHERE journalId = :journalId")
    suspend fun deleteForJournal(journalId: Long)

    @Query("DELETE FROM journal_photos WHERE journalId IN (SELECT id FROM journal WHERE habitId = :habitId)")
    suspend fun deleteForHabit(habitId: Long)

    @Query("DELETE FROM journal_photos")
    suspend fun clear()
}

@Dao
interface UrgeDao {
    @Query("SELECT * FROM urges ORDER BY timestamp ASC")
    fun observeAll(): Flow<List<UrgeEntity>>

    @Query("SELECT * FROM urges ORDER BY timestamp ASC")
    suspend fun getAll(): List<UrgeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: UrgeEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<UrgeEntity>)

    @Update
    suspend fun update(entity: UrgeEntity)

    @Delete
    suspend fun delete(entity: UrgeEntity)

    @Query("DELETE FROM urges")
    suspend fun clear()

    @Query("DELETE FROM urges WHERE habitId = :habitId")
    suspend fun deleteForHabit(habitId: Long)
}

@Dao
interface JournalDao {
    @Query("SELECT * FROM journal ORDER BY timestamp ASC")
    fun observeAll(): Flow<List<JournalEntity>>

    @Query("SELECT * FROM journal ORDER BY timestamp ASC")
    suspend fun getAll(): List<JournalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: JournalEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<JournalEntity>)

    @Update
    suspend fun update(entity: JournalEntity)

    @Delete
    suspend fun delete(entity: JournalEntity)

    @Query("DELETE FROM journal")
    suspend fun clear()

    @Query("DELETE FROM journal WHERE habitId = :habitId")
    suspend fun deleteForHabit(habitId: Long)
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

/** v2 → v3: money kept, "cut down" mode and reasons on trackers; urge log and journal tables. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `habits` ADD COLUMN `spendAmount` REAL NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `habits` ADD COLUMN `spendPeriodDays` INTEGER NOT NULL DEFAULT 7")
        db.execSQL("ALTER TABLE `habits` ADD COLUMN `dailyLimit` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `habits` ADD COLUMN `reasons` TEXT NOT NULL DEFAULT ''")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `urges` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `habitId` INTEGER NOT NULL, " +
                "`timestamp` INTEGER NOT NULL, `intensity` INTEGER NOT NULL, `triggers` TEXT NOT NULL, `notes` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_urges_habitId` ON `urges` (`habitId`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `journal` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `habitId` INTEGER NOT NULL, " +
                "`timestamp` INTEGER NOT NULL, `mood` INTEGER NOT NULL, `text` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_journal_habitId` ON `journal` (`habitId`)")
    }
}

/** v3 → v4: photos on journal entries. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `journal_photos` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `journalId` INTEGER NOT NULL, " +
                "`fileName` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_journal_photos_journalId` ON `journal_photos` (`journalId`)")
    }
}

@Database(
    entities = [RelapseEntity::class, HabitEntity::class, UrgeEntity::class, JournalEntity::class, JournalPhotoEntity::class],
    version = 4,
    exportSchema = true,
)
abstract class DaymarkDatabase : RoomDatabase() {
    abstract fun relapseDao(): RelapseDao
    abstract fun habitDao(): HabitDao
    abstract fun urgeDao(): UrgeDao
    abstract fun journalDao(): JournalDao
    abstract fun journalPhotoDao(): JournalPhotoDao

    companion object {
        fun build(context: Context): DaymarkDatabase =
            Room.databaseBuilder(context, DaymarkDatabase::class.java, "daymark.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
    }
}
