package com.daymark.app.data

import com.daymark.app.domain.HabitKind
import com.daymark.app.domain.ReminderConfig
import com.daymark.app.domain.ReminderMode
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
data class BackupUrge(
    val timestamp: Long,
    val intensity: Int = 0,
    val triggers: List<String> = emptyList(),
    val notes: String = "",
)

@Serializable
data class BackupJournal(
    val timestamp: Long,
    val mood: Int = 0,
    val text: String = "",
    /** v4: attached photos as base64 JPEG (empty when the user chose not to back up photos). */
    val photos: List<String> = emptyList(),
)

@Serializable
data class BackupHabit(
    val name: String,
    val emoji: String = "🌱",
    val startAt: Long,
    val relapses: List<BackupRelapse> = emptyList(),
    // v3
    val spendAmount: Double = 0.0,
    val spendPeriodDays: Int = 7,
    val dailyLimit: Int = 0,
    val reasons: List<String> = emptyList(),
    val urges: List<BackupUrge> = emptyList(),
    val journal: List<BackupJournal> = emptyList(),
    // v5: "build a habit"
    /** 0 = break a habit, 1 = build a habit ([HabitKind.id]). */
    val kind: Int = 0,
    val goalTarget: Double = 0.0,
    val goalUnit: String = "",
    val goalStep: Double = 1.0,
    /** Weekly schedule bitmask, bit 0 = Monday (see `Schedule`). */
    val scheduleDays: Int = 127,
    /** [ReminderMode] id: 0 none, 1 every N minutes, 2 fixed times. */
    val reminderMode: Int = 0,
    val reminderInterval: Int = 60,
    /** Minutes after midnight. */
    val reminderStart: Int = 480,
    val reminderEnd: Int = 1260,
    /** Minutes after midnight, for [ReminderMode.TIMES]. */
    val reminderTimes: List<Int> = emptyList(),
    val completions: List<BackupCompletion> = emptyList(),
)

/** v5: one day's progress on a build habit. [amount] is in the habit's goal unit (1 for a simple check-off). */
@Serializable
data class BackupCompletion(val epochDay: Long, val amount: Double)

/**
 * v5: "build a habit": kind, daily goal, weekly schedule, reminders and daily completions per tracker.
 * v4: journal photos (base64 JPEG inside each journal entry).
 * v3: money, "cut down" limit, reasons, urges and journal per tracker. v2: multiple trackers (`habits`).
 * v1 files (single journey: `habitName`, `journeyStart`, top-level `relapses`) are still accepted and become one tracker.
 * Older files simply lack the newer fields, which fall back to their defaults (v1–v4 decode as break-a-habit trackers).
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
        const val VERSION = 5
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
        urges: List<UrgeEntity> = emptyList(),
        journal: List<JournalEntity> = emptyList(),
        now: Long = System.currentTimeMillis(),
        /** journal id → base64 photos, in order. */
        journalPhotos: Map<Long, List<String>> = emptyMap(),
        completions: List<CompletionEntity> = emptyList(),
    ): String = json.encodeToString(
        BackupFile.serializer(),
        BackupFile(
            exportedAt = now,
            themeMode = settings.themeMode,
            accent = settings.accent,
            habits = habits.map { h ->
                val habit = h.toHabit()
                BackupHabit(
                    name = h.name, emoji = h.emoji, startAt = h.startAt,
                    relapses = relapses.filter { it.habitId == h.id }.map {
                        val r = it.toRecord()
                        BackupRelapse(r.timestamp, r.triggers, r.mood, r.situation, r.notes)
                    },
                    spendAmount = habit.spendAmount,
                    spendPeriodDays = habit.spendPeriodDays,
                    dailyLimit = habit.dailyLimit,
                    reasons = habit.reasons,
                    urges = urges.filter { it.habitId == h.id }.map {
                        val u = it.toRecord()
                        BackupUrge(u.timestamp, u.intensity, u.triggers, u.notes)
                    },
                    journal = journal.filter { it.habitId == h.id }.map { BackupJournal(it.timestamp, it.mood, it.text, journalPhotos[it.id].orEmpty()) },
                    kind = habit.kind.id,
                    goalTarget = habit.goalTarget,
                    goalUnit = habit.goalUnit,
                    goalStep = habit.goalStep,
                    scheduleDays = habit.scheduleDays,
                    reminderMode = habit.reminder.mode.id,
                    reminderInterval = habit.reminder.intervalMinutes,
                    reminderStart = habit.reminder.activeStart,
                    reminderEnd = habit.reminder.activeEnd,
                    reminderTimes = habit.reminder.times,
                    completions = completions.filter { it.habitId == h.id }.map { BackupCompletion(it.epochDay, it.amount) },
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

    data class Entities(
        val habits: List<HabitEntity>,
        val relapses: List<RelapseEntity>,
        val urges: List<UrgeEntity>,
        val journal: List<JournalEntity>,
        /** journal id (as assigned in [journal]) → base64 photos. */
        val journalPhotos: Map<Long, List<String>> = emptyMap(),
        val completions: List<CompletionEntity> = emptyList(),
    )

    /** Assigns fresh ids 1..n so entries can reference their habit. */
    fun toEntities(file: BackupFile): Entities {
        fun finite(value: Double, fallback: Double) = if (value.isFinite()) value else fallback
        val habits = file.habits.mapIndexed { i, h ->
            HabitEntity.from(
                com.daymark.app.domain.Habit(
                    id = i + 1L, name = h.name, emoji = h.emoji, startAt = h.startAt, sortOrder = i,
                    spendAmount = finite(h.spendAmount, 0.0).coerceAtLeast(0.0), spendPeriodDays = h.spendPeriodDays.coerceAtLeast(1),
                    dailyLimit = h.dailyLimit.coerceAtLeast(0), reasons = h.reasons,
                    kind = HabitKind.of(h.kind),
                    goalTarget = finite(h.goalTarget, 0.0).coerceAtLeast(0.0),
                    goalUnit = h.goalUnit,
                    goalStep = finite(h.goalStep, 1.0).takeIf { it > 0 } ?: 1.0,
                    scheduleDays = h.scheduleDays,
                    reminder = ReminderConfig(
                        mode = ReminderMode.of(h.reminderMode),
                        intervalMinutes = h.reminderInterval,
                        activeStart = h.reminderStart,
                        activeEnd = h.reminderEnd,
                        times = h.reminderTimes,
                    ),
                ),
            )
        }
        // Completions reference habits by the fresh ids above. Duplicate days are merged (the table key is habitId+epochDay).
        val completions = file.habits.flatMapIndexed { i, h ->
            h.completions
                .filter { it.amount.isFinite() && it.amount > 0.0 }
                .groupBy { it.epochDay }
                .map { (day, rows) -> CompletionEntity(habitId = i + 1L, epochDay = day, amount = rows.sumOf { it.amount }) }
        }
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
        val urges = file.habits.flatMapIndexed { i, h ->
            h.urges.map {
                UrgeEntity(
                    habitId = i + 1L, timestamp = it.timestamp, intensity = it.intensity.coerceIn(0, 5),
                    triggers = it.triggers.joinToString(RelapseEntity.SEP), notes = it.notes,
                )
            }
        }
        // Fresh journal ids 1..n so photos can be attached to the right entry.
        val backed = file.habits.flatMapIndexed { i, h -> h.journal.map { i + 1L to it } }
        val journal = backed.mapIndexed { j, (habitId, it) ->
            JournalEntity(id = j + 1L, habitId = habitId, timestamp = it.timestamp, mood = it.mood.coerceIn(0, 5), text = it.text)
        }
        val photos = backed.mapIndexedNotNull { j, (_, it) -> it.photos.takeIf { p -> p.isNotEmpty() }?.let { p -> (j + 1L) to p.take(PhotoStore.MAX_PER_ENTRY) } }.toMap()
        return Entities(habits, relapses, urges, journal, photos, completions)
    }
}

class WrongPasswordException : Exception("That password doesn't match this backup.")

/**
 * Optional password protection for backup files: AES-256-GCM with a PBKDF2-SHA256 key.
 * The wrapper is itself JSON so the file is still recognisable as a Daymark backup.
 */
object BackupCrypto {
    @Serializable
    data class Envelope(
        val app: String = BackupFile.APP_ID,
        val encrypted: Boolean = true,
        val kdf: String = "PBKDF2WithHmacSHA256",
        val iterations: Int = ITERATIONS,
        val salt: String,
        val iv: String,
        val data: String,
    )

    private const val ITERATIONS = 120_000
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val random = java.security.SecureRandom()

    fun isEncrypted(text: String): Boolean =
        runCatching { json.decodeFromString(Envelope.serializer(), text).encrypted }.getOrDefault(false)

    fun encrypt(plain: String, password: String): String {
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key(password, salt, ITERATIONS), javax.crypto.spec.GCMParameterSpec(128, iv))
        val data = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val b64 = java.util.Base64.getEncoder()
        return json.encodeToString(Envelope.serializer(), Envelope(salt = b64.encodeToString(salt), iv = b64.encodeToString(iv), data = b64.encodeToString(data)))
    }

    fun decrypt(text: String, password: String): String {
        val env = try {
            json.decodeFromString(Envelope.serializer(), text)
        } catch (e: Exception) {
            throw InvalidBackupException("This file isn't a Daymark backup.")
        }
        val b64 = java.util.Base64.getDecoder()
        return try {
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                javax.crypto.Cipher.DECRYPT_MODE,
                key(password, b64.decode(env.salt), env.iterations),
                javax.crypto.spec.GCMParameterSpec(128, b64.decode(env.iv)),
            )
            String(cipher.doFinal(b64.decode(env.data)), Charsets.UTF_8)
        } catch (e: javax.crypto.AEADBadTagException) {
            throw WrongPasswordException()
        }
    }

    private fun key(password: String, salt: ByteArray, iterations: Int): javax.crypto.SecretKey {
        val spec = javax.crypto.spec.PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        val bytes = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return javax.crypto.spec.SecretKeySpec(bytes, "AES")
    }
}
