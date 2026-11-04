package com.daymark.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import com.daymark.app.domain.RelapseRecord
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "relapses")
data class RelapseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
}

@Database(entities = [RelapseEntity::class], version = 1, exportSchema = true)
abstract class DaymarkDatabase : RoomDatabase() {
    abstract fun relapseDao(): RelapseDao

    companion object {
        fun build(context: Context): DaymarkDatabase =
            Room.databaseBuilder(context, DaymarkDatabase::class.java, "daymark.db").build()
    }
}
