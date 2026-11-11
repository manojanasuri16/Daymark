package com.daymark.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.daymark.app.domain.EventFilter
import com.daymark.app.domain.EventSort
import com.daymark.app.domain.ItemSize
import com.daymark.app.domain.NotifType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
data class Settings(
    val onboarded: Boolean = false,
    val journeyStart: Long = 0L,
    val habitName: String = "",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val appLock: Boolean = false,
    val hideInRecents: Boolean = false,
    val haptics: Boolean = true,
    /** 0 = first tracker. */
    val selectedHabitId: Long = 0L,
    val accent: String = "dusk",
    /** Keep tracker names out of notifications, the Quick Settings tile and share cards. */
    val discreet: Boolean = true,
    val checkInReminder: Boolean = false,
    /** Minutes after local midnight. */
    val checkInMinutes: Int = 21 * 60,
    val milestoneAlerts: Boolean = false,
    /** Persisted SAF tree URI, or "" when automatic backups are off. */
    val autoBackupUri: String = "",
    /** Encrypts automatic backups when not blank. */
    val autoBackupPassword: String = "",
    /** Include journal photos in exported and automatic backups (bigger files). */
    val backupPhotos: Boolean = true,
    // Events
    /** The four calendar-period events have been added once. */
    val eventsSeeded: Boolean = false,
    val eventSort: EventSort = EventSort.CREATED,
    val eventSortAsc: Boolean = false,
    val eventFilter: EventFilter = EventFilter.ALL,
    val eventCardSize: ItemSize = ItemSize.MEDIUM,
    /** 15 or 30. */
    val widgetRefreshMinutes: Int = 15,
    /** Show a smaller unit when a single-unit amount is below 2. */
    val precisionCountdown: Boolean = true,
    val eventCompleted: Boolean = false,
    val eventOneDayBefore: Boolean = false,
    val eventOneWeekBefore: Boolean = false,
    val eventEveryDayAfter: Boolean = false,
    val eventEveryWeekAfter: Boolean = false,
    val eventEveryMonthAfter: Boolean = false,
    val eventEveryYearAfter: Boolean = false,
    val batteryNagDismissed: Boolean = false,
    /** Dashboard opens counted toward the one-time "long-press to add to home screen" tip; -1 = tip finished. */
    val homeTipViews: Int = 0,
) {
    /** Is the notification of this [type] switched on? */
    fun notifies(type: NotifType): Boolean = when (type) {
        NotifType.COMPLETED -> eventCompleted
        NotifType.ONE_DAY_BEFORE -> eventOneDayBefore
        NotifType.ONE_WEEK_BEFORE -> eventOneWeekBefore
        NotifType.DAILY_AFTER -> eventEveryDayAfter
        NotifType.WEEKLY_AFTER -> eventEveryWeekAfter
        NotifType.MONTHLY_AFTER -> eventEveryMonthAfter
        NotifType.YEARLY_AFTER -> eventEveryYearAfter
    }
}

private val Context.dataStore by preferencesDataStore(name = "daymark_settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val onboarded = booleanPreferencesKey("onboarded")
        val journeyStart = longPreferencesKey("journey_start")
        val habitName = stringPreferencesKey("habit_name")
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val appLock = booleanPreferencesKey("app_lock")
        val hideInRecents = booleanPreferencesKey("hide_in_recents")
        val haptics = booleanPreferencesKey("haptics")
        val selectedHabit = longPreferencesKey("selected_habit")
        val accent = stringPreferencesKey("accent")
        val discreet = booleanPreferencesKey("discreet")
        val checkInReminder = booleanPreferencesKey("check_in_reminder")
        val checkInMinutes = intPreferencesKey("check_in_minutes")
        val milestoneAlerts = booleanPreferencesKey("milestone_alerts")
        val autoBackupUri = stringPreferencesKey("auto_backup_uri")
        val autoBackupPassword = stringPreferencesKey("auto_backup_password")
        val backupPhotos = booleanPreferencesKey("backup_photos")
        val eventsSeeded = booleanPreferencesKey("events_seeded")
        val eventSort = stringPreferencesKey("event_sort")
        val eventSortAsc = booleanPreferencesKey("event_sort_asc")
        val eventFilter = stringPreferencesKey("event_filter")
        val eventCardSize = stringPreferencesKey("event_card_size")
        val widgetRefreshMinutes = intPreferencesKey("widget_refresh_minutes")
        val precisionCountdown = booleanPreferencesKey("precision_countdown")
        val eventCompleted = booleanPreferencesKey("notif_event_completed")
        val eventOneDayBefore = booleanPreferencesKey("notif_one_day_before")
        val eventOneWeekBefore = booleanPreferencesKey("notif_one_week_before")
        val eventEveryDayAfter = booleanPreferencesKey("notif_every_day_after")
        val eventEveryWeekAfter = booleanPreferencesKey("notif_every_week_after")
        val eventEveryMonthAfter = booleanPreferencesKey("notif_every_month_after")
        val eventEveryYearAfter = booleanPreferencesKey("notif_every_year_after")
        val batteryNagDismissed = booleanPreferencesKey("battery_nag_dismissed")
        val homeTipViews = intPreferencesKey("home_tip_views")

        fun notif(type: NotifType) = when (type) {
            NotifType.COMPLETED -> eventCompleted
            NotifType.ONE_DAY_BEFORE -> eventOneDayBefore
            NotifType.ONE_WEEK_BEFORE -> eventOneWeekBefore
            NotifType.DAILY_AFTER -> eventEveryDayAfter
            NotifType.WEEKLY_AFTER -> eventEveryWeekAfter
            NotifType.MONTHLY_AFTER -> eventEveryMonthAfter
            NotifType.YEARLY_AFTER -> eventEveryYearAfter
        }
    }

    val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): Settings = settings.first()

    private fun Preferences.toSettings() = Settings(
        onboarded = this[Keys.onboarded] ?: false,
        journeyStart = this[Keys.journeyStart] ?: 0L,
        habitName = this[Keys.habitName] ?: "",
        themeMode = this[Keys.themeMode]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
        dynamicColor = this[Keys.dynamicColor] ?: false,
        appLock = this[Keys.appLock] ?: false,
        hideInRecents = this[Keys.hideInRecents] ?: false,
        haptics = this[Keys.haptics] ?: true,
        selectedHabitId = this[Keys.selectedHabit] ?: 0L,
        accent = this[Keys.accent] ?: "dusk",
        discreet = this[Keys.discreet] ?: true,
        checkInReminder = this[Keys.checkInReminder] ?: false,
        checkInMinutes = this[Keys.checkInMinutes] ?: (21 * 60),
        milestoneAlerts = this[Keys.milestoneAlerts] ?: false,
        autoBackupUri = this[Keys.autoBackupUri] ?: "",
        autoBackupPassword = this[Keys.autoBackupPassword] ?: "",
        backupPhotos = this[Keys.backupPhotos] ?: true,
        eventsSeeded = this[Keys.eventsSeeded] ?: false,
        eventSort = this[Keys.eventSort]?.let { runCatching { EventSort.valueOf(it) }.getOrNull() } ?: EventSort.CREATED,
        eventSortAsc = this[Keys.eventSortAsc] ?: false,
        eventFilter = this[Keys.eventFilter]?.let { runCatching { EventFilter.valueOf(it) }.getOrNull() } ?: EventFilter.ALL,
        eventCardSize = this[Keys.eventCardSize]?.let { runCatching { ItemSize.valueOf(it) }.getOrNull() } ?: ItemSize.MEDIUM,
        widgetRefreshMinutes = (this[Keys.widgetRefreshMinutes] ?: 15).let { if (it == 30) 30 else 15 },
        precisionCountdown = this[Keys.precisionCountdown] ?: true,
        eventCompleted = this[Keys.eventCompleted] ?: false,
        eventOneDayBefore = this[Keys.eventOneDayBefore] ?: false,
        eventOneWeekBefore = this[Keys.eventOneWeekBefore] ?: false,
        eventEveryDayAfter = this[Keys.eventEveryDayAfter] ?: false,
        eventEveryWeekAfter = this[Keys.eventEveryWeekAfter] ?: false,
        eventEveryMonthAfter = this[Keys.eventEveryMonthAfter] ?: false,
        eventEveryYearAfter = this[Keys.eventEveryYearAfter] ?: false,
        batteryNagDismissed = this[Keys.batteryNagDismissed] ?: false,
        homeTipViews = this[Keys.homeTipViews] ?: 0,
    )

    suspend fun completeOnboarding(firstHabitId: Long) = context.dataStore.edit {
        it[Keys.selectedHabit] = firstHabitId
        it[Keys.onboarded] = true
    }

    suspend fun setSelectedHabit(id: Long) = context.dataStore.edit { it[Keys.selectedHabit] = id }
    suspend fun setAccent(id: String) = context.dataStore.edit { it[Keys.accent] = id }

    suspend fun setHabitName(v: String) = context.dataStore.edit { it[Keys.habitName] = v.trim() }
    suspend fun setJourneyStart(v: Long) = context.dataStore.edit { it[Keys.journeyStart] = v }
    suspend fun setThemeMode(v: ThemeMode) = context.dataStore.edit { it[Keys.themeMode] = v.name }
    suspend fun setDynamicColor(v: Boolean) = context.dataStore.edit { it[Keys.dynamicColor] = v }
    suspend fun setAppLock(v: Boolean) = context.dataStore.edit { it[Keys.appLock] = v }
    suspend fun setHideInRecents(v: Boolean) = context.dataStore.edit { it[Keys.hideInRecents] = v }
    suspend fun setHaptics(v: Boolean) = context.dataStore.edit { it[Keys.haptics] = v }
    suspend fun setDiscreet(v: Boolean) = context.dataStore.edit { it[Keys.discreet] = v }
    suspend fun setCheckInReminder(enabled: Boolean, minutes: Int) = context.dataStore.edit {
        it[Keys.checkInReminder] = enabled
        it[Keys.checkInMinutes] = minutes.coerceIn(0, 24 * 60 - 1)
    }
    suspend fun setBackupPhotos(v: Boolean) = context.dataStore.edit { it[Keys.backupPhotos] = v }
    suspend fun setMilestoneAlerts(v: Boolean) = context.dataStore.edit { it[Keys.milestoneAlerts] = v }
    suspend fun setAutoBackup(uri: String, password: String) = context.dataStore.edit {
        it[Keys.autoBackupUri] = uri
        it[Keys.autoBackupPassword] = password
    }

    /** Atomically claims the one-time seeding of the period events. True only for the caller that flipped the flag. */
    suspend fun claimEventSeed(): Boolean {
        var claimed = false
        context.dataStore.edit {
            if (it[Keys.eventsSeeded] != true) { it[Keys.eventsSeeded] = true; claimed = true }
        }
        return claimed
    }

    suspend fun setEventSort(sort: EventSort, asc: Boolean) = context.dataStore.edit {
        it[Keys.eventSort] = sort.name
        it[Keys.eventSortAsc] = asc
    }
    suspend fun setEventFilter(v: EventFilter) = context.dataStore.edit { it[Keys.eventFilter] = v.name }
    suspend fun setEventCardSize(v: ItemSize) = context.dataStore.edit { it[Keys.eventCardSize] = v.name }
    suspend fun setWidgetRefreshMinutes(v: Int) = context.dataStore.edit { it[Keys.widgetRefreshMinutes] = if (v >= 30) 30 else 15 }
    suspend fun setPrecisionCountdown(v: Boolean) = context.dataStore.edit { it[Keys.precisionCountdown] = v }
    suspend fun setEventNotification(type: NotifType, on: Boolean) = context.dataStore.edit { it[Keys.notif(type)] = on }
    suspend fun setBatteryNagDismissed(v: Boolean) = context.dataStore.edit { it[Keys.batteryNagDismissed] = v }
    suspend fun setHomeTipViews(v: Int) = context.dataStore.edit { it[Keys.homeTipViews] = v }

    suspend fun replaceAll(s: Settings) = context.dataStore.edit {
        it[Keys.onboarded] = s.onboarded
        it[Keys.journeyStart] = s.journeyStart
        it[Keys.habitName] = s.habitName
        it[Keys.themeMode] = s.themeMode.name
        it[Keys.dynamicColor] = s.dynamicColor
        it[Keys.appLock] = s.appLock
        it[Keys.hideInRecents] = s.hideInRecents
        it[Keys.haptics] = s.haptics
        it[Keys.selectedHabit] = s.selectedHabitId
        it[Keys.accent] = s.accent
        it[Keys.discreet] = s.discreet
        it[Keys.checkInReminder] = s.checkInReminder
        it[Keys.checkInMinutes] = s.checkInMinutes
        it[Keys.milestoneAlerts] = s.milestoneAlerts
        it[Keys.autoBackupUri] = s.autoBackupUri
        it[Keys.autoBackupPassword] = s.autoBackupPassword
        it[Keys.backupPhotos] = s.backupPhotos
        it[Keys.eventsSeeded] = s.eventsSeeded
        it[Keys.eventSort] = s.eventSort.name
        it[Keys.eventSortAsc] = s.eventSortAsc
        it[Keys.eventFilter] = s.eventFilter.name
        it[Keys.eventCardSize] = s.eventCardSize.name
        it[Keys.widgetRefreshMinutes] = s.widgetRefreshMinutes
        it[Keys.precisionCountdown] = s.precisionCountdown
        it[Keys.eventCompleted] = s.eventCompleted
        it[Keys.eventOneDayBefore] = s.eventOneDayBefore
        it[Keys.eventOneWeekBefore] = s.eventOneWeekBefore
        it[Keys.eventEveryDayAfter] = s.eventEveryDayAfter
        it[Keys.eventEveryWeekAfter] = s.eventEveryWeekAfter
        it[Keys.eventEveryMonthAfter] = s.eventEveryMonthAfter
        it[Keys.eventEveryYearAfter] = s.eventEveryYearAfter
        it[Keys.batteryNagDismissed] = s.batteryNagDismissed
        it[Keys.homeTipViews] = s.homeTipViews
    }

    suspend fun clear() = context.dataStore.edit { it.clear() }
}
