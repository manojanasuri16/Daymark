package com.daymark.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
    )

    suspend fun completeOnboarding(habitName: String, journeyStart: Long) = context.dataStore.edit {
        it[Keys.habitName] = habitName.trim()
        it[Keys.journeyStart] = journeyStart
        it[Keys.onboarded] = true
    }

    suspend fun setHabitName(v: String) = context.dataStore.edit { it[Keys.habitName] = v.trim() }
    suspend fun setJourneyStart(v: Long) = context.dataStore.edit { it[Keys.journeyStart] = v }
    suspend fun setThemeMode(v: ThemeMode) = context.dataStore.edit { it[Keys.themeMode] = v.name }
    suspend fun setDynamicColor(v: Boolean) = context.dataStore.edit { it[Keys.dynamicColor] = v }
    suspend fun setAppLock(v: Boolean) = context.dataStore.edit { it[Keys.appLock] = v }
    suspend fun setHideInRecents(v: Boolean) = context.dataStore.edit { it[Keys.hideInRecents] = v }
    suspend fun setHaptics(v: Boolean) = context.dataStore.edit { it[Keys.haptics] = v }

    suspend fun replaceAll(s: Settings) = context.dataStore.edit {
        it[Keys.onboarded] = s.onboarded
        it[Keys.journeyStart] = s.journeyStart
        it[Keys.habitName] = s.habitName
        it[Keys.themeMode] = s.themeMode.name
        it[Keys.dynamicColor] = s.dynamicColor
        it[Keys.appLock] = s.appLock
        it[Keys.hideInRecents] = s.hideInRecents
        it[Keys.haptics] = s.haptics
    }

    suspend fun clear() = context.dataStore.edit { it.clear() }
}
