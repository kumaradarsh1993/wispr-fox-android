package com.wisprfox.android.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wisprfox.android.provider.DictationMode
import com.wisprfox.android.provider.ProviderCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Non-secret user settings, backed by DataStore Preferences. Mirrors the subset
 * of the desktop `AppSettings` that's relevant on Android. Secrets (API keys)
 * live in [SecureKeyStore], not here.
 */
data class AppSettings(
    val sttProvider: String = ProviderCatalog.STT_GROQ,
    val sttModel: String = ProviderCatalog.defaultSttModel(ProviderCatalog.STT_GROQ),
    val llmProvider: String = ProviderCatalog.LLM_GROQ,
    val groqLlmModel: String = ProviderCatalog.defaultLlmModel(ProviderCatalog.LLM_GROQ),
    val openAiLlmModel: String = ProviderCatalog.defaultLlmModel(ProviderCatalog.LLM_OPENAI),
    val geminiModel: String = ProviderCatalog.defaultLlmModel(ProviderCatalog.LLM_GEMINI),
    /** What a single tap on the avatar does. Long-press always offers all three. */
    val defaultMode: DictationMode = DictationMode.CLEANED,
    val retentionDays: Int = 7,
    val retentionMaxMb: Int = 500,
    /** Auto-paste into the focused field via AccessibilityService (sideload: free). */
    val autoPasteEnabled: Boolean = true,
    /** Show the floating avatar overlay (needs SYSTEM_ALERT_WINDOW). */
    val overlayBubbleEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val avatar: Avatar = Avatar.FOX,
    /** Floating-avatar size preset (S/M/L). Applied to the overlay footprint. */
    val avatarScale: AvatarScale = AvatarScale.MEDIUM,
    /** null = let Whisper auto-detect (the locked English↔Hindi decision). */
    val languageHint: String? = null,
    /** Editable label for this install shown on synced rows from other devices
     *  (sync/accounts, v2.0). Defaults to the phone model. */
    val deviceName: String = android.os.Build.MODEL ?: "Android phone",
    /** Epoch millis of the last successful sync, or null if never synced. */
    val lastSyncedAt: Long? = null,
) {
    /** The model name to use for the currently selected LLM provider. */
    val activeLlmModel: String get() = when (llmProvider) {
        ProviderCatalog.LLM_GEMINI -> geminiModel
        ProviderCatalog.LLM_OPENAI -> openAiLlmModel
        else -> groqLlmModel
    }

    companion object {
        const val PROVIDER_GROQ = ProviderCatalog.LLM_GROQ
        const val PROVIDER_OPENAI = ProviderCatalog.LLM_OPENAI
        const val PROVIDER_GEMINI = ProviderCatalog.LLM_GEMINI
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "wisprfox_settings")

class SettingsStore(private val context: Context) {

    private object Keys {
        val sttProvider = stringPreferencesKey("stt_provider")
        val sttModel = stringPreferencesKey("stt_model")
        val llmProvider = stringPreferencesKey("llm_provider")
        val groqLlmModel = stringPreferencesKey("groq_llm_model")
        val openAiLlmModel = stringPreferencesKey("openai_llm_model")
        val geminiModel = stringPreferencesKey("gemini_model")
        val defaultMode = stringPreferencesKey("default_mode")
        val retentionDays = intPreferencesKey("retention_days")
        val retentionMaxMb = intPreferencesKey("retention_max_mb")
        val autoPaste = booleanPreferencesKey("auto_paste")
        val overlayBubble = booleanPreferencesKey("overlay_bubble")
        val haptics = booleanPreferencesKey("haptics")
        val avatar = stringPreferencesKey("avatar")
        val avatarScale = stringPreferencesKey("avatar_scale")
        val languageHint = stringPreferencesKey("language_hint")
        val deviceName = stringPreferencesKey("device_name")
        val lastSyncedAt = longPreferencesKey("last_synced_at")
        val deviceId = stringPreferencesKey("device_id")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p -> p.toSettings() }

    private fun Preferences.toSettings(): AppSettings {
        val defaults = AppSettings()
        val sttProvider = ProviderCatalog.sanitizeSttProvider(this[Keys.sttProvider] ?: defaults.sttProvider)
        val llmProvider = ProviderCatalog.sanitizeLlmProvider(this[Keys.llmProvider] ?: defaults.llmProvider)
        return AppSettings(
            sttProvider = sttProvider,
            sttModel = ProviderCatalog.sanitizeSttModel(sttProvider, this[Keys.sttModel] ?: defaults.sttModel),
            llmProvider = llmProvider,
            groqLlmModel = ProviderCatalog.sanitizeLlmModel(ProviderCatalog.LLM_GROQ, this[Keys.groqLlmModel] ?: defaults.groqLlmModel),
            openAiLlmModel = ProviderCatalog.sanitizeLlmModel(ProviderCatalog.LLM_OPENAI, this[Keys.openAiLlmModel] ?: defaults.openAiLlmModel),
            geminiModel = ProviderCatalog.sanitizeLlmModel(ProviderCatalog.LLM_GEMINI, this[Keys.geminiModel] ?: defaults.geminiModel),
            defaultMode = this[Keys.defaultMode]?.let { runCatching { DictationMode.valueOf(it) }.getOrNull() }
                ?: defaults.defaultMode,
            retentionDays = this[Keys.retentionDays] ?: defaults.retentionDays,
            retentionMaxMb = this[Keys.retentionMaxMb] ?: defaults.retentionMaxMb,
            autoPasteEnabled = this[Keys.autoPaste] ?: defaults.autoPasteEnabled,
            overlayBubbleEnabled = this[Keys.overlayBubble] ?: defaults.overlayBubbleEnabled,
            hapticsEnabled = this[Keys.haptics] ?: defaults.hapticsEnabled,
            avatar = this[Keys.avatar]?.let { runCatching { Avatar.valueOf(it) }.getOrNull() } ?: defaults.avatar,
            avatarScale = this[Keys.avatarScale]?.let { runCatching { AvatarScale.valueOf(it) }.getOrNull() } ?: defaults.avatarScale,
            languageHint = this[Keys.languageHint]?.takeIf { it.isNotBlank() } ?: defaults.languageHint,
            deviceName = this[Keys.deviceName]?.takeIf { it.isNotBlank() } ?: defaults.deviceName,
            lastSyncedAt = this[Keys.lastSyncedAt],
        )
    }

    suspend fun setSttProvider(v: String) = edit {
        val provider = ProviderCatalog.sanitizeSttProvider(v)
        it[Keys.sttProvider] = provider
        it[Keys.sttModel] = ProviderCatalog.defaultSttModel(provider)
    }
    suspend fun setSttModel(v: String) = edit { it[Keys.sttModel] = v }
    suspend fun setLlmProvider(v: String) = edit {
        it[Keys.llmProvider] = ProviderCatalog.sanitizeLlmProvider(v)
    }
    suspend fun setGroqLlmModel(v: String) = edit { it[Keys.groqLlmModel] = v }
    suspend fun setOpenAiLlmModel(v: String) = edit { it[Keys.openAiLlmModel] = v }
    suspend fun setGeminiModel(v: String) = edit { it[Keys.geminiModel] = v }
    suspend fun setDefaultMode(v: DictationMode) = edit { it[Keys.defaultMode] = v.name }
    suspend fun setRetentionDays(v: Int) = edit { it[Keys.retentionDays] = v }
    suspend fun setRetentionMaxMb(v: Int) = edit { it[Keys.retentionMaxMb] = v }
    suspend fun setAutoPaste(v: Boolean) = edit { it[Keys.autoPaste] = v }
    suspend fun setOverlayBubble(v: Boolean) = edit { it[Keys.overlayBubble] = v }
    suspend fun setHaptics(v: Boolean) = edit { it[Keys.haptics] = v }
    suspend fun setAvatar(v: Avatar) = edit { it[Keys.avatar] = v.name }
    suspend fun setAvatarScale(v: AvatarScale) = edit { it[Keys.avatarScale] = v.name }
    suspend fun setDeviceName(v: String) = edit { it[Keys.deviceName] = v }
    suspend fun setLastSyncedAt(v: Long) = edit { it[Keys.lastSyncedAt] = v }

    /**
     * Stable per-install id (sync/accounts, v2.0), generated once and persisted
     * in DataStore per [com.wisprfox.android.sync.SyncEngine]'s device-registration
     * contract. Safe to call repeatedly/concurrently — DataStore's `edit` is
     * transactional so a racing pair of first-callers still converge on one id.
     */
    suspend fun deviceId(): String {
        val existing = context.dataStore.data.first()[Keys.deviceId]
        if (existing != null) return existing
        val fresh = UUID.randomUUID().toString()
        var winner = fresh
        context.dataStore.edit { p ->
            val current = p[Keys.deviceId]
            if (current != null) {
                winner = current
            } else {
                p[Keys.deviceId] = fresh
                winner = fresh
            }
        }
        return winner
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
