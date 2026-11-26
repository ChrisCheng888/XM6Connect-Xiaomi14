package com.xm6.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.xm6DataStore: DataStore<Preferences> by preferencesDataStore(name = "xm6_prefs")

class UserPreferencesStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val BOUND_MAC = stringPreferencesKey("bound_mac")
        val BOUND_NAME = stringPreferencesKey("bound_name")
        val SHIZUKU_AUTO_LDAC = booleanPreferencesKey("shizuku_auto_ldac")
        val LDAC_BITRATE = intPreferencesKey("ldac_bitrate")
        val SHOW_EXPERIMENTAL = booleanPreferencesKey("show_experimental")
        val CUSTOM_EQ_PRESETS = stringPreferencesKey("custom_eq_presets")
        val ALL_EQ_PRESETS = stringPreferencesKey("all_eq_presets")
        val EQ_PRESETS_INITIALIZED = booleanPreferencesKey("eq_presets_initialized")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    private fun resolvePresets(p: Preferences): List<EqPreset> {
        val initialized = p[Keys.EQ_PRESETS_INITIALIZED] ?: false
        if (initialized) {
            return p[Keys.ALL_EQ_PRESETS]?.let { raw ->
                runCatching { json.decodeFromString<List<EqPreset>>(raw) }.getOrDefault(emptyList())
            }?.map { it.copy(builtIn = false) }.orEmpty()
        }
        // First-time migration: merge initial defaults + any previously saved custom presets
        val legacyCustom = p[Keys.CUSTOM_EQ_PRESETS]?.let { raw ->
            runCatching { json.decodeFromString<List<EqPreset>>(raw) }.getOrDefault(emptyList())
        }.orEmpty().map { it.copy(builtIn = false) }
        val defaultIds = Xm6Defaults.BUILTIN_EQ_PRESETS.map { it.id }.toSet()
        return Xm6Defaults.BUILTIN_EQ_PRESETS + legacyCustom.filterNot { it.id in defaultIds }
    }

    val prefsFlow: Flow<UserPrefs> = context.xm6DataStore.data.map { p ->
        val bitrateInt = p[Keys.LDAC_BITRATE] ?: LdacBitrateMode.QUALITY_990.aospValue
        val bitrate = LdacBitrateMode.entries.firstOrNull { it.aospValue == bitrateInt }
            ?: LdacBitrateMode.QUALITY_990

        val themeMode = p[Keys.THEME_MODE]?.let { raw ->
            runCatching { AppThemeMode.valueOf(raw) }.getOrNull()
        } ?: AppThemeMode.AUTO_TIME

        UserPrefs(
            boundMac = p[Keys.BOUND_MAC],
            boundName = p[Keys.BOUND_NAME],
            shizukuAutoLdac = p[Keys.SHIZUKU_AUTO_LDAC] ?: true,
            ldacBitrateMode = bitrate,
            showExperimental = p[Keys.SHOW_EXPERIMENTAL] ?: true,
            eqPresets = resolvePresets(p),
            themeMode = themeMode
        )
    }

    suspend fun setBoundDevice(mac: String, name: String) {
        context.xm6DataStore.edit {
            it[Keys.BOUND_MAC] = mac
            it[Keys.BOUND_NAME] = name
        }
    }

    suspend fun setShizukuAutoLdac(enabled: Boolean) {
        context.xm6DataStore.edit { it[Keys.SHIZUKU_AUTO_LDAC] = enabled }
    }

    suspend fun setLdacBitrate(mode: LdacBitrateMode) {
        context.xm6DataStore.edit { it[Keys.LDAC_BITRATE] = mode.aospValue }
    }

    suspend fun setShowExperimental(show: Boolean) {
        context.xm6DataStore.edit { it[Keys.SHOW_EXPERIMENTAL] = show }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.xm6DataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun saveOrUpdatePreset(preset: EqPreset) {
        context.xm6DataStore.edit { prefs ->
            val current = resolvePresets(prefs)
            val normalized = preset.copy(builtIn = false)
            val idx = current.indexOfFirst { it.id == normalized.id }
            val updated = if (idx >= 0) {
                current.toMutableList().apply { set(idx, normalized) }
            } else {
                current + normalized
            }
            prefs[Keys.EQ_PRESETS_INITIALIZED] = true
            prefs[Keys.ALL_EQ_PRESETS] = json.encodeToString(updated)
        }
    }

    suspend fun deletePreset(presetId: String) {
        context.xm6DataStore.edit { prefs ->
            val updated = resolvePresets(prefs).filterNot { it.id == presetId }
            prefs[Keys.EQ_PRESETS_INITIALIZED] = true
            prefs[Keys.ALL_EQ_PRESETS] = json.encodeToString(updated)
        }
    }

    suspend fun restoreDefaultPresets() {
        context.xm6DataStore.edit { prefs ->
            val current = resolvePresets(prefs)
            val existingIds = current.map { it.id }.toSet()
            val missingDefaults = Xm6Defaults.BUILTIN_EQ_PRESETS.filterNot { it.id in existingIds }
            val merged = missingDefaults + current
            prefs[Keys.EQ_PRESETS_INITIALIZED] = true
            prefs[Keys.ALL_EQ_PRESETS] = json.encodeToString(merged)
        }
    }
}
