package com.daymark.app.data

import androidx.room.withTransaction
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
