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
data class BackupFile(
    @SerialName("app") val app: String = APP_ID,
    val version: Int = VERSION,
    val exportedAt: Long,
    val habitName: String,
    val journeyStart: Long,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val relapses: List<BackupRelapse>,
) {
    companion object {
        const val APP_ID = "daymark"
        const val VERSION = 1
    }
}

class InvalidBackupException(message: String) : Exception(message)

object BackupCodec {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(settings: Settings, relapses: List<RelapseEntity>, now: Long = System.currentTimeMillis()): String =
        json.encodeToString(
            BackupFile.serializer(),
            BackupFile(
                exportedAt = now,
                habitName = settings.habitName,
                journeyStart = settings.journeyStart,
                themeMode = settings.themeMode,
                relapses = relapses.map {
                    val r = it.toRecord()
                    BackupRelapse(r.timestamp, r.triggers, r.mood, r.situation, r.notes)
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
        if (file.journeyStart <= 0L) throw InvalidBackupException("The backup is missing a start date.")
        return file
    }

    fun toEntities(file: BackupFile): List<RelapseEntity> = file.relapses.map {
        RelapseEntity(
            timestamp = it.timestamp,
            triggers = it.triggers.joinToString(RelapseEntity.SEP),
            mood = it.mood.coerceIn(0, 5),
            situation = it.situation,
            notes = it.notes,
        )
    }
}
