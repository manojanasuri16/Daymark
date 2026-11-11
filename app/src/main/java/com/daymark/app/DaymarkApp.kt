package com.daymark.app

import android.app.Application
import android.content.Context
import com.daymark.app.data.DaymarkDatabase
import com.daymark.app.data.CompletionRepository
import com.daymark.app.data.HabitRepository
import com.daymark.app.data.JournalRepository
import com.daymark.app.data.RelapseRepository
import com.daymark.app.data.NoteRepository
import com.daymark.app.data.UrgeRepository
import com.daymark.app.data.SettingsRepository
import com.daymark.app.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

/** Minimal manual dependency container — the app is small enough that a DI framework isn't worth it. */
class AppContainer(val context: Context) {
    val database: DaymarkDatabase = DaymarkDatabase.build(context)
    val relapses = RelapseRepository(database)
    val habits = HabitRepository(database)
    val urges = UrgeRepository(database)
    val notes = NoteRepository(database)
    val completions = CompletionRepository(database)
    val journal = JournalRepository(database)
    val settings = SettingsRepository(context)
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

class DaymarkApp : Application() {
    lateinit var container: AppContainer
        private set

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        keepWorkManagerAlive()
        // Keep home-screen widgets in sync with any data change, wherever it originates.
        container.appScope.launch {
            container.habits.ensureLegacyHabit(container.settings.current())
            // Remove photo files no journal entry refers to any more.
            runCatching {
                com.daymark.app.data.PhotoStore.sweep(this@DaymarkApp, container.journal.allPhotos().map { it.fileName }.toSet())
            }
            com.daymark.app.data.AutoBackup.schedule(this@DaymarkApp, enabled = container.settings.current().autoBackupUri.isNotEmpty())
            // Anything a widget shows: trackers (incl. money/reasons/limit), entries, urges, discreet mode.
            combine(container.habits.habits, container.relapses.relapses, container.urges.urges, container.settings.settings, container.completions.completions) { h, r, u, s, c ->
                listOf(h, r.map { it.habitId to it.timestamp }, u.map { it.habitId to it.timestamp }, s.discreet, s.selectedHabitId, c)
            }.distinctUntilChanged().debounce(300).collect {
                WidgetUpdater.updateAll(this@DaymarkApp)
                // Milestone timing depends on the latest slip; keep the next alert in step.
                runCatching { com.daymark.app.notify.Reminders.reschedule(this@DaymarkApp) }
                runCatching { com.daymark.app.notify.HabitReminders.rescheduleAll(this@DaymarkApp) }
            }
        }
    }
}

/**
 * WorkManager disables its own receivers whenever no work is pending; that component change makes some launchers
 * reset every home-screen widget to its initial layout until the next update. A far-future no-op job keeps it from
 * ever going idle. (Known Glance + WorkManager issue.)
 */
private fun Context.keepWorkManagerAlive() {
    val req = androidx.work.OneTimeWorkRequestBuilder<KeepAliveWorker>()
        .setInitialDelay(3650, java.util.concurrent.TimeUnit.DAYS)
        .build()
    androidx.work.WorkManager.getInstance(this)
        .enqueueUniqueWork("daymark-keep-alive", androidx.work.ExistingWorkPolicy.KEEP, req)
}

class KeepAliveWorker(context: Context, params: androidx.work.WorkerParameters) : androidx.work.Worker(context, params) {
    override fun doWork(): Result = Result.success()
}

val Context.container: AppContainer get() = (applicationContext as DaymarkApp).container
