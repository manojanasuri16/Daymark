package com.daymark.app.data

import androidx.room.withTransaction
import com.daymark.app.domain.Habit
import com.daymark.app.domain.JournalEntry
import com.daymark.app.domain.RelapseRecord
import com.daymark.app.domain.UrgeRecord
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

    /** Deletes the habit and everything logged against it, together. */
    suspend fun delete(id: Long) = db.withTransaction {
        db.relapseDao().deleteForHabit(id)
        db.urgeDao().deleteForHabit(id)
        db.journalDao().deleteForHabit(id)
        dao.delete(id)
    }

    /** v1 → v2: turn the single legacy journey (kept in settings) into tracker #1, once. */
    suspend fun ensureLegacyHabit(settings: Settings) {
        if (!settings.onboarded || dao.getAll().isNotEmpty()) return
        dao.insert(HabitEntity(id = 1, name = settings.habitName, emoji = "🌱", startAt = settings.journeyStart))
    }

    suspend fun replaceAll(
        habits: List<HabitEntity>,
        relapses: List<RelapseEntity>,
        urges: List<UrgeEntity> = emptyList(),
        journal: List<JournalEntity> = emptyList(),
    ) = db.withTransaction {
        clearAll()
        dao.insertAll(habits)
        db.relapseDao().insertAll(relapses)
        db.urgeDao().insertAll(urges)
        db.journalDao().insertAll(journal)
    }

    suspend fun clear() = db.withTransaction { clearAll() }

    private suspend fun clearAll() {
        db.relapseDao().clear()
        db.urgeDao().clear()
        db.journalDao().clear()
        dao.clear()
    }
}

class UrgeRepository(db: DaymarkDatabase) {
    private val dao = db.urgeDao()

    val urges: Flow<List<UrgeRecord>> = dao.observeAll().map { list -> list.map { it.toRecord() } }

    suspend fun allEntities(): List<UrgeEntity> = dao.getAll()

    suspend fun save(record: UrgeRecord): Long =
        if (record.id == 0L) dao.insert(UrgeEntity.from(record))
        else { dao.update(UrgeEntity.from(record)); record.id }

    suspend fun delete(record: UrgeRecord) = dao.delete(UrgeEntity.from(record))
}

class JournalRepository(db: DaymarkDatabase) {
    private val dao = db.journalDao()

    val entries: Flow<List<JournalEntry>> = dao.observeAll().map { list -> list.map { it.toEntry() } }

    suspend fun allEntities(): List<JournalEntity> = dao.getAll()

    suspend fun save(entry: JournalEntry): Long =
        if (entry.id == 0L) dao.insert(JournalEntity.from(entry))
        else { dao.update(JournalEntity.from(entry)); entry.id }

    suspend fun delete(entry: JournalEntry) = dao.delete(JournalEntity.from(entry))
}
