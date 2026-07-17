package com.densmac.dashcam.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.densmac.dashcam.domain.model.DashcamCamera
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userPreferencesStore by preferencesDataStore("user_preferences")

@Singleton
class UserPreferencesDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val preferredCamera = stringPreferencesKey("preferred_camera")
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColorEnabled = booleanPreferencesKey("dynamic_color_enabled")
        val hapticsEnabled = booleanPreferencesKey("haptics_enabled")
        val autoStartLivePreview = booleanPreferencesKey("auto_start_live_preview")
        val showDebugDiagnostics = booleanPreferencesKey("show_debug_diagnostics")
        val cameraMappingSwapped = booleanPreferencesKey("camera_mapping_swapped")
        val lastKnownDeviceUuid = stringPreferencesKey("last_known_device_uuid")
        val externalPlayerPackage = stringPreferencesKey("external_player_package")
    }

    val preferences: Flow<UserPreferences> = context.userPreferencesStore.data.map { prefs ->
        UserPreferences(
            preferredCamera = DashcamCamera.fromPreference(prefs[Keys.preferredCamera]),
            themeMode = prefs[Keys.themeMode]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            dynamicColorEnabled = prefs[Keys.dynamicColorEnabled] ?: false,
            hapticsEnabled = prefs[Keys.hapticsEnabled] ?: true,
            autoStartLivePreview = prefs[Keys.autoStartLivePreview] ?: false,
            showDebugDiagnostics = prefs[Keys.showDebugDiagnostics] ?: false,
            cameraMappingSwapped = prefs[Keys.cameraMappingSwapped] ?: false,
            lastKnownDeviceUuid = prefs[Keys.lastKnownDeviceUuid],
            externalPlayerPackage = prefs[Keys.externalPlayerPackage]
        )
    }

    suspend fun setThemeMode(value: ThemeMode) = context.userPreferencesStore.edit { it[Keys.themeMode] = value.name }
    suspend fun setDynamicColorEnabled(value: Boolean) = context.userPreferencesStore.edit { it[Keys.dynamicColorEnabled] = value }
    suspend fun setHapticsEnabled(value: Boolean) = context.userPreferencesStore.edit { it[Keys.hapticsEnabled] = value }
    suspend fun setAutoStartLivePreview(value: Boolean) = context.userPreferencesStore.edit { it[Keys.autoStartLivePreview] = value }
    suspend fun setShowDebugDiagnostics(value: Boolean) = context.userPreferencesStore.edit { it[Keys.showDebugDiagnostics] = value }
    suspend fun setCameraMappingSwapped(value: Boolean) = context.userPreferencesStore.edit { it[Keys.cameraMappingSwapped] = value }
    suspend fun setPreferredCamera(value: DashcamCamera) = context.userPreferencesStore.edit { it[Keys.preferredCamera] = value.name }
    suspend fun setLastKnownDeviceUuid(value: String) = context.userPreferencesStore.edit { it[Keys.lastKnownDeviceUuid] = value }
    suspend fun setExternalPlayerPackage(value: String?) = context.userPreferencesStore.edit {
        if (value == null) it.remove(Keys.externalPlayerPackage) else it[Keys.externalPlayerPackage] = value
    }
}
