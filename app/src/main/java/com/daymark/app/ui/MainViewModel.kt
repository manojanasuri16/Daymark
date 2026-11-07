package com.daymark.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.daymark.app.container
import com.daymark.app.data.BackupCodec
import com.daymark.app.data.BackupCrypto
import com.daymark.app.data.BackupWriter
import com.daymark.app.data.InvalidBackupException
import com.daymark.app.data.Settings
import com.daymark.app.data.ThemeMode
import com.daymark.app.domain.Habit
import com.daymark.app.domain.InsightsData
import com.daymark.app.domain.InsightsEngine
import com.daymark.app.domain.JournalEntry
import com.daymark.app.domain.RelapseRecord
import com.daymark.app.domain.UrgeRecord
import com.daymark.app.domain.StreakEngine
import com.daymark.app.domain.StreakStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A tracker plus its live stats, for the overview. */
data class HabitSummary(val habit: Habit, val stats: StreakStats)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.container

    /** null until DataStore has been read once — lets the splash screen wait for it. */
    val settings: StateFlow<Settings?> = c.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** null until Room has emitted once. */
    val habits: StateFlow<List<Habit>?> = c.habits.habits
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val allRelapses: StateFlow<List<RelapseRecord>> = c.relapses.relapses
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The tracker currently shown on Today / Calendar / Insights. */
    val selectedHabit: StateFlow<Habit?> = combine(settings, habits) { s, h ->
        h?.firstOrNull { it.id == s?.selectedHabitId } ?: h?.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Entries for the selected tracker only. */
    val relapses: StateFlow<List<RelapseRecord>> = combine(selectedHabit, allRelapses) { h, r ->
        if (h == null) emptyList() else r.filter { it.habitId == h.id }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allUrges: StateFlow<List<UrgeRecord>> = c.urges.urges
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allJournal: StateFlow<List<JournalEntry>> = c.journal.entries
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Urges ridden out, for the selected tracker. */
    val urges: StateFlow<List<UrgeRecord>> = combine(selectedHabit, allUrges) { h, u ->
        if (h == null) emptyList() else u.filter { it.habitId == h.id }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Journal entries for the selected tracker. */
    val journal: StateFlow<List<JournalEntry>> = combine(selectedHabit, allJournal) { h, j ->
        if (h == null) emptyList() else j.filter { it.habitId == h.id }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Wall clock that ticks on the second boundary so live counters stay in step. */
    val now: StateFlow<Long> = flow {
        while (true) {
            val t = System.currentTimeMillis()
            emit(t)
            delay(1000 - t % 1000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), System.currentTimeMillis())

    val stats: StateFlow<StreakStats> = combine(selectedHabit, relapses, now) { h, r, t ->
        if (h == null) StreakStats.EMPTY else StreakEngine.compute(h.startAt, h.slipTimes(r.map { it.timestamp }), t)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StreakStats.EMPTY)

    val overview: StateFlow<List<HabitSummary>> = combine(habits, allRelapses, now) { hs, r, t ->
        hs.orEmpty().map { h -> HabitSummary(h, StreakEngine.compute(h.startAt, h.slipTimes(r.filter { it.habitId == h.id }.map { it.timestamp }), t)) }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Recomputed on data change (and hourly) rather than every second. */
    private val hourly = flow { while (true) { emit(System.currentTimeMillis()); delay(3_600_000) } }

    val insights: StateFlow<InsightsData?> = combine(selectedHabit, relapses, urges, hourly) { h, r, u, t ->
        if (h == null) null
        else InsightsEngine.compute(r, StreakEngine.spans(h.startAt, h.slipTimes(r.map { it.timestamp }), t), t, urges = u)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** One-off user-facing messages (snackbars). */
    val events: SharedFlow<String> = _events

    private val _pendingEncryptedImport = MutableStateFlow<Uri?>(null)
    /** A chosen backup file that needs a password before it can be restored. */
    val pendingEncryptedImport: StateFlow<Uri?> = _pendingEncryptedImport

    // region Actions
    fun completeOnboarding(name: String, emoji: String, start: Long) = viewModelScope.launch {
        val id = c.habits.save(Habit(0, name.trim(), emoji, start))
        c.settings.completeOnboarding(id)
    }

    fun selectHabit(id: Long) = viewModelScope.launch { c.settings.setSelectedHabit(id) }

    /** Creates or updates a tracker; new trackers become the selected one. */
    fun saveHabit(habit: Habit) = viewModelScope.launch {
        val id = c.habits.save(habit.copy(name = habit.name.trim()))
        if (habit.id == 0L) c.settings.setSelectedHabit(id)
    }

    fun deleteHabit(habit: Habit) = viewModelScope.launch {
        c.habits.delete(habit.id)
        if (settings.value?.selectedHabitId == habit.id) c.settings.setSelectedHabit(0)
        _events.emit("Deleted ${habit.displayName}")
    }

    fun saveRelapse(record: RelapseRecord) = viewModelScope.launch { c.relapses.save(record) }

    fun deleteRelapse(record: RelapseRecord) = viewModelScope.launch { c.relapses.delete(record) }

    fun restoreRelapse(record: RelapseRecord) = viewModelScope.launch { c.relapses.save(record) }

    fun saveUrge(record: UrgeRecord) = viewModelScope.launch { c.urges.save(record) }
    fun deleteUrge(record: UrgeRecord) = viewModelScope.launch { c.urges.delete(record) }

    fun saveJournal(entry: JournalEntry) = viewModelScope.launch { c.journal.save(entry) }
    fun deleteJournal(entry: JournalEntry) = viewModelScope.launch { c.journal.delete(entry) }

    fun setDiscreet(v: Boolean) = viewModelScope.launch { c.settings.setDiscreet(v) }
    fun setCheckInReminder(enabled: Boolean, minutes: Int) = viewModelScope.launch {
        c.settings.setCheckInReminder(enabled, minutes)
        com.daymark.app.notify.Reminders.reschedule(getApplication())
    }
    fun setMilestoneAlerts(v: Boolean) = viewModelScope.launch {
        c.settings.setMilestoneAlerts(v)
        com.daymark.app.notify.Reminders.reschedule(getApplication())
    }

    /** [uri] "" turns automatic backups off. */
    fun setAutoBackup(uri: Uri?, password: String) = viewModelScope.launch {
        val resolver = getApplication<Application>().contentResolver
        val old = settings.value?.autoBackupUri.orEmpty()
        if (old.isNotEmpty() && old != uri?.toString()) {
            runCatching { resolver.releasePersistableUriPermission(Uri.parse(old), android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        }
        if (uri != null) {
            runCatching { resolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        }
        c.settings.setAutoBackup(uri?.toString().orEmpty(), if (uri == null) "" else password)
        com.daymark.app.data.AutoBackup.schedule(getApplication(), enabled = uri != null)
        if (uri != null) {
            val ok = withContext(Dispatchers.IO) { com.daymark.app.data.AutoBackup.runOnce(getApplication()) }
            _events.emit(if (ok) "Automatic backups on. First backup saved." else "Couldn't write to that folder")
        } else _events.emit("Automatic backups off")
    }

    fun setThemeMode(v: ThemeMode) = viewModelScope.launch { c.settings.setThemeMode(v) }
    fun setAccent(id: String) = viewModelScope.launch { c.settings.setAccent(id) }
    fun setDynamicColor(v: Boolean) = viewModelScope.launch { c.settings.setDynamicColor(v) }
    fun setAppLock(v: Boolean) = viewModelScope.launch { c.settings.setAppLock(v) }
    fun setHideInRecents(v: Boolean) = viewModelScope.launch { c.settings.setHideInRecents(v) }
    fun setHaptics(v: Boolean) = viewModelScope.launch { c.settings.setHaptics(v) }

    /** [password] blank = plain JSON. */
    fun exportBackup(uri: Uri, password: String = "") = viewModelScope.launch {
        val result = runCatching {
            withContext(Dispatchers.IO) {
                val text = BackupWriter.build(c, password)
                getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use {
                    it.write(text.toByteArray(Charsets.UTF_8))
                } ?: error("Couldn't open the file")
            }
        }
        _events.emit(if (result.isSuccess) "Backup saved" else "Backup failed: ${result.exceptionOrNull()?.message}")
    }

    fun cancelEncryptedImport() { _pendingEncryptedImport.value = null }

    /** [password] null = first attempt; an encrypted file then asks for one via [pendingEncryptedImport]. */
    fun importBackup(uri: Uri, password: String? = null) = viewModelScope.launch {
        _pendingEncryptedImport.value = null
        val result = runCatching {
            withContext(Dispatchers.IO) {
                val raw = getApplication<Application>().contentResolver.openInputStream(uri)?.use {
                    it.readBytes().toString(Charsets.UTF_8)
                } ?: error("Couldn't open the file")
                val text = if (BackupCrypto.isEncrypted(raw)) {
                    if (password == null) return@withContext null
                    BackupCrypto.decrypt(raw, password)
                } else raw
                val file = BackupCodec.decode(text)
                val e = BackupCodec.toEntities(file)
                val habits = e.habits
                val relapses = e.relapses
                c.habits.replaceAll(habits, relapses, e.urges, e.journal)
                val current = c.settings.current()
                c.settings.replaceAll(
                    current.copy(
                        onboarded = true,
                        selectedHabitId = habits.first().id,
                        themeMode = file.themeMode,
                        accent = file.accent ?: current.accent,
                    ),
                )
                habits.size to relapses.size
            }
        }
        if (result.getOrNull() == null && result.isSuccess) {
            _pendingEncryptedImport.value = uri
            return@launch
        }
        _events.emit(
            result.fold(
                onSuccess = { counts ->
                    val (h, r) = counts!!
                    "Restored $h ${if (h == 1) "tracker" else "trackers"} and $r ${if (r == 1) "entry" else "entries"}"
                },
                onFailure = {
                    when (it) {
                        is InvalidBackupException -> it.message ?: "Restore failed"
                        is com.daymark.app.data.WrongPasswordException -> { _pendingEncryptedImport.value = uri; it.message ?: "Wrong password" }
                        else -> "Restore failed: ${it.message}"
                    }
                },
            ),
        )
    }

    fun resetEverything() = viewModelScope.launch {
        settings.value?.autoBackupUri?.takeIf { it.isNotEmpty() }?.let { old ->
            runCatching {
                getApplication<Application>().contentResolver.releasePersistableUriPermission(
                    Uri.parse(old), android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        c.habits.clear()
        c.settings.clear()
        com.daymark.app.data.AutoBackup.schedule(getApplication(), enabled = false)
        com.daymark.app.notify.Reminders.reschedule(getApplication())
    }
    // endregion
}
