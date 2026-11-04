package com.daymark.app

import android.app.Application
import android.content.Context
import com.daymark.app.data.DaymarkDatabase
import com.daymark.app.data.HabitRepository
import com.daymark.app.data.RelapseRepository
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
class AppContainer(context: Context) {
    val database: DaymarkDatabase = DaymarkDatabase.build(context)
    val relapses = RelapseRepository(database)
    val habits = HabitRepository(database)
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
        // Keep home-screen widgets in sync with any data change, wherever it originates.
        container.appScope.launch {
            container.habits.ensureLegacyHabit(container.settings.current())
            combine(container.habits.habits, container.relapses.relapses) { h, r ->
                h to r.map { it.habitId to it.timestamp }
            }.distinctUntilChanged().debounce(300).collect {
                WidgetUpdater.updateAll(this@DaymarkApp)
            }
        }
    }
}

val Context.container: AppContainer get() = (applicationContext as DaymarkApp).container
