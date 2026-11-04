package com.daymark.app.data

import androidx.room.withTransaction
import com.daymark.app.domain.Habit
import com.daymark.app.domain.RelapseRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RelapseRepository(private val db: DaymarkDatabase) {
    private val dao = db.relapseDao()

    val relapses: Flow<List<RelapseRecord>> = dao.observeAll().map { list -> list.map { it.toRecord() } }

    suspend fun all(): List<RelapseRecord> = dao.getAll().map { it.toRecord() }
    suspend fun allEntities(): List<RelapseEntity> = dao.getAll()
    suspend fun get(id: Long): RelapseRecord? = dao.get(id)?.toRecord()

    suspend fun save(record: RelapseRecord): Long =
        if (record.id == 0L) dao.insert(RelapseEntity.from(record))
        else { dao.update(RelapseEntity.from(record)); record.id }

    suspend fun delete(record: RelapseRecord) = dao.delete(RelapseEntity.from(record))

    suspend fun replaceAll(entities: List<RelapseEntity>) = db.withTransaction {
        dao.clear()
        dao.insertAll(entities)
    }

    suspend fun clear() = dao.clear()
}

class HabitRepository(private val db: DaymarkDatabase) {
    private val dao = db.habitDao()

    val habits: Flow<List<Habit>> = dao.observeAll().map { list -> list.map { it.toHabit() } }

    suspend fun all(): List<Habit> = dao.getAll().map { it.toHabit() }

    suspend fun save(habit: Habit): Long =
        if (habit.id == 0L) dao.insert(HabitEntity.from(habit.copy(sortOrder = (dao.getAll().maxOfOrNull { it.sortOrder } ?: -1) + 1)))
        else { dao.update(HabitEntity.from(habit)); habit.id }

    /** Deletes the habit and its entries together. */
    suspend fun delete(id: Long) = db.withTransaction {
        db.relapseDao().deleteForHabit(id)
        dao.delete(id)
    }

    /** v1 → v2: turn the single legacy journey (kept in settings) into tracker #1, once. */
    suspend fun ensureLegacyHabit(settings: Settings) {
        if (!settings.onboarded || dao.getAll().isNotEmpty()) return
        dao.insert(HabitEntity(id = 1, name = settings.habitName, emoji = "🌱", startAt = settings.journeyStart))
    }

    suspend fun replaceAll(habits: List<HabitEntity>, relapses: List<RelapseEntity>) = db.withTransaction {
        db.relapseDao().clear()
        dao.clear()
        dao.insertAll(habits)
        db.relapseDao().insertAll(relapses)
    }

    suspend fun clear() = db.withTransaction {
        db.relapseDao().clear()
        dao.clear()
    }
}
