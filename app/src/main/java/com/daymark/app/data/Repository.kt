package com.daymark.app.data

import androidx.room.withTransaction
import com.daymark.app.domain.Completion
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
        db.completionDao().deleteForHabit(id)
        db.journalPhotoDao().deleteForHabit(id) // image files are swept by PhotoStore
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
        photos: List<JournalPhotoEntity> = emptyList(),
        completions: List<CompletionEntity> = emptyList(),
    ) = db.withTransaction {
        clearAll()
        dao.insertAll(habits)
        db.relapseDao().insertAll(relapses)
        db.urgeDao().insertAll(urges)
        db.journalDao().insertAll(journal)
        db.journalPhotoDao().insertAll(photos)
        db.completionDao().insertAll(completions)
    }

    suspend fun clear() = db.withTransaction { clearAll() }

    private suspend fun clearAll() {
        db.relapseDao().clear()
        db.urgeDao().clear()
        db.completionDao().clear()
        db.journalPhotoDao().clear()
        db.journalDao().clear()
        dao.clear()
    }
}

/** Daily progress for build habits. */
class CompletionRepository(private val db: DaymarkDatabase) {
    private val dao = db.completionDao()

    val completions: Flow<List<Completion>> = dao.observeAll().map { list -> list.map { it.toCompletion() } }

    suspend fun all(): List<Completion> = dao.getAll().map { it.toCompletion() }
    suspend fun allEntities(): List<CompletionEntity> = dao.getAll()

    /** Sets the day's total. An amount <= 0 removes the row. */
    suspend fun set(habitId: Long, epochDay: Long, amount: Double) {
        if (amount <= 0.0) dao.delete(habitId, epochDay)
        else dao.upsert(CompletionEntity(habitId, epochDay, amount, System.currentTimeMillis()))
    }

    /** Adds [delta] (may be negative) to the day's total, never below zero. Returns the new total. */
    suspend fun add(habitId: Long, epochDay: Long, delta: Double): Double = db.withTransaction {
        val next = ((dao.get(habitId, epochDay)?.amount ?: 0.0) + delta).coerceAtLeast(0.0)
        set(habitId, epochDay, next)
        next
    }

    suspend fun amount(habitId: Long, epochDay: Long): Double = dao.get(habitId, epochDay)?.amount ?: 0.0
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

class JournalRepository(private val db: DaymarkDatabase) {
    private val dao = db.journalDao()
    private val photoDao = db.journalPhotoDao()

    val entries: Flow<List<JournalEntry>> = kotlinx.coroutines.flow.combine(dao.observeAll(), photoDao.observeAll()) { list, photos ->
        val byEntry = photos.groupBy({ it.journalId }, { it.fileName })
        list.map { it.toEntry().copy(photos = byEntry[it.id].orEmpty()) }
    }

    suspend fun allEntities(): List<JournalEntity> = dao.getAll()
    suspend fun allPhotos(): List<JournalPhotoEntity> = photoDao.getAll()

    /** Saves the entry and replaces its photo list. */
    suspend fun save(entry: JournalEntry): Long = db.withTransaction {
        val id = if (entry.id == 0L) dao.insert(JournalEntity.from(entry)) else { dao.update(JournalEntity.from(entry)); entry.id }
        photoDao.deleteForJournal(id)
        photoDao.insertAll(entry.photos.mapIndexed { i, name -> JournalPhotoEntity(journalId = id, fileName = name, sortOrder = i) })
        id
    }

    suspend fun delete(entry: JournalEntry) = db.withTransaction {
        photoDao.deleteForJournal(entry.id)
        dao.delete(JournalEntity.from(entry))
    }
}
