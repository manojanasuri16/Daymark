package com.daymark.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BackupRelapse(
    val timestamp: Long,
    val triggers: List<String> = emptyList(),
    val mood: Int = 0,
    val situation: String = "",
    val notes: String = "",
)

@Serializable
data class BackupHabit(
    val name: String,
    val emoji: String = "🌱",
    val startAt: Long,
    val relapses: List<BackupRelapse> = emptyList(),
)

/**
 * v2: multiple trackers (`habits`). v1 files (single journey: `habitName`, `journeyStart`, top-level `relapses`)
 * are still accepted and become one tracker.
 */
@Serializable
data class BackupFile(
    @SerialName("app") val app: String = APP_ID,
    val version: Int = VERSION,
    val exportedAt: Long,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: String? = null,
    val habits: List<BackupHabit> = emptyList(),
    // v1 fields
    val habitName: String? = null,
    val journeyStart: Long? = null,
    val relapses: List<BackupRelapse>? = null,
) {
    companion object {
        const val APP_ID = "daymark"
        const val VERSION = 2
    }
}

class InvalidBackupException(message: String) : Exception(message)

object BackupCodec {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun encode(
        settings: Settings,
        habits: List<HabitEntity>,
        relapses: List<RelapseEntity>,
        now: Long = System.currentTimeMillis(),
    ): String = json.encodeToString(
        BackupFile.serializer(),
        BackupFile(
            exportedAt = now,
            themeMode = settings.themeMode,
            accent = settings.accent,
            habits = habits.map { h ->
                BackupHabit(
                    name = h.name, emoji = h.emoji, startAt = h.startAt,
                    relapses = relapses.filter { it.habitId == h.id }.map {
                        val r = it.toRecord()
                        BackupRelapse(r.timestamp, r.triggers, r.mood, r.situation, r.notes)
                    },
                )
            },
        ),
    )

    fun decode(text: String): BackupFile {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: Exception) {
            throw InvalidBackupException("This file isn't a Daymark backup.")
        }
        if (file.app != BackupFile.APP_ID) throw InvalidBackupException("This file isn't a Daymark backup.")
        if (file.version > BackupFile.VERSION) throw InvalidBackupException("This backup was made by a newer version of Daymark.")
        val normalized = if (file.habits.isEmpty() && file.journeyStart != null) {
            file.copy(habits = listOf(BackupHabit(file.habitName.orEmpty(), "🌱", file.journeyStart, file.relapses.orEmpty())))
        } else file
        if (normalized.habits.isEmpty() || normalized.habits.any { it.startAt <= 0L }) {
            throw InvalidBackupException("The backup is missing a start date.")
        }
        return normalized
    }

    /** Assigns fresh ids 1..n so relapses can reference their habit. */
    fun toEntities(file: BackupFile): Pair<List<HabitEntity>, List<RelapseEntity>> {
        val habits = file.habits.mapIndexed { i, h -> HabitEntity(id = i + 1L, name = h.name, emoji = h.emoji, startAt = h.startAt, sortOrder = i) }
        val relapses = file.habits.flatMapIndexed { i, h ->
            h.relapses.map {
                RelapseEntity(
                    habitId = i + 1L,
                    timestamp = it.timestamp,
                    triggers = it.triggers.joinToString(RelapseEntity.SEP),
                    mood = it.mood.coerceIn(0, 5),
                    situation = it.situation,
                    notes = it.notes,
                )
            }
        }
        return habits to relapses
    }
}
