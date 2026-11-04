package com.daymark.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.daymark.app.container
import com.daymark.app.data.BackupCodec
import com.daymark.app.data.InvalidBackupException
import com.daymark.app.data.Settings
import com.daymark.app.data.ThemeMode
import com.daymark.app.domain.InsightsData
import com.daymark.app.domain.InsightsEngine
import com.daymark.app.domain.RelapseRecord
import com.daymark.app.domain.StreakEngine
import com.daymark.app.domain.StreakStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.container

    /** null until DataStore has been read once — lets the splash screen wait for it. */
    val settings: StateFlow<Settings?> = c.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val relapses: StateFlow<List<RelapseRecord>> = c.relapses.relapses
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Wall clock that ticks on the second boundary so live counters stay in step. */
    val now: StateFlow<Long> = flow {
        while (true) {
            val t = System.currentTimeMillis()
            emit(t)
            delay(1000 - t % 1000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), System.currentTimeMillis())

    val stats: StateFlow<StreakStats> = combine(settings, relapses, now) { s, r, t ->
        if (s == null || !s.onboarded) StreakStats.EMPTY
        else StreakEngine.compute(s.journeyStart, r.map { it.timestamp }, t)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StreakStats.EMPTY)

    /** Recomputed on data change (and hourly) rather than every second. */
    private val hourly = flow { while (true) { emit(System.currentTimeMillis()); delay(3_600_000) } }

    val insights: StateFlow<InsightsData?> = combine(settings, relapses, hourly) { s, r, t ->
        if (s == null || !s.onboarded) null
        else {
            val spans = StreakEngine.spans(s.journeyStart, r.map { it.timestamp }, t)
            InsightsEngine.compute(r, spans, t)
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** One-off user-facing messages (snackbars). */
    val events: SharedFlow<String> = _events

    // region Actions
    fun completeOnboarding(name: String, start: Long) = viewModelScope.launch { c.settings.completeOnboarding(name, start) }

    fun saveRelapse(record: RelapseRecord) = viewModelScope.launch { c.relapses.save(record) }

    fun deleteRelapse(record: RelapseRecord) = viewModelScope.launch { c.relapses.delete(record) }

    fun restoreRelapse(record: RelapseRecord) = viewModelScope.launch { c.relapses.save(record) }

    fun setHabitName(v: String) = viewModelScope.launch { c.settings.setHabitName(v) }
    fun setJourneyStart(v: Long) = viewModelScope.launch { c.settings.setJourneyStart(v) }
    fun setThemeMode(v: ThemeMode) = viewModelScope.launch { c.settings.setThemeMode(v) }
    fun setDynamicColor(v: Boolean) = viewModelScope.launch { c.settings.setDynamicColor(v) }
    fun setAppLock(v: Boolean) = viewModelScope.launch { c.settings.setAppLock(v) }
    fun setHideInRecents(v: Boolean) = viewModelScope.launch { c.settings.setHideInRecents(v) }
    fun setHaptics(v: Boolean) = viewModelScope.launch { c.settings.setHaptics(v) }

    fun exportBackup(uri: Uri) = viewModelScope.launch {
        val result = runCatching {
            withContext(Dispatchers.IO) {
                val text = BackupCodec.encode(c.settings.current(), c.relapses.allEntities())
                getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use {
                    it.write(text.toByteArray(Charsets.UTF_8))
                } ?: error("Couldn't open the file")
            }
        }
        _events.emit(if (result.isSuccess) "Backup saved" else "Backup failed: ${result.exceptionOrNull()?.message}")
    }

    fun importBackup(uri: Uri) = viewModelScope.launch {
        val result = runCatching {
            withContext(Dispatchers.IO) {
                val text = getApplication<Application>().contentResolver.openInputStream(uri)?.use {
                    it.readBytes().toString(Charsets.UTF_8)
                } ?: error("Couldn't open the file")
                val file = BackupCodec.decode(text)
                c.relapses.replaceAll(BackupCodec.toEntities(file))
                val current = c.settings.current()
                c.settings.replaceAll(
                    current.copy(onboarded = true, habitName = file.habitName, journeyStart = file.journeyStart, themeMode = file.themeMode),
                )
                file.relapses.size
            }
        }
        _events.emit(
            result.fold(
                onSuccess = { "Restored backup with $it logged ${if (it == 1) "entry" else "entries"}" },
                onFailure = { (it as? InvalidBackupException)?.message ?: "Restore failed: ${it.message}" },
            ),
        )
    }

    fun resetEverything() = viewModelScope.launch {
        c.relapses.clear()
        c.settings.clear()
    }
    // endregion
}
