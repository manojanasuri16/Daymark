package com.daymark.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
)

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
    suspend fun setMilestoneAlerts(v: Boolean) = context.dataStore.edit { it[Keys.milestoneAlerts] = v }
    suspend fun setAutoBackup(uri: String, password: String) = context.dataStore.edit {
        it[Keys.autoBackupUri] = uri
        it[Keys.autoBackupPassword] = password
    }

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
    }

    suspend fun clear() = context.dataStore.edit { it.clear() }
}
