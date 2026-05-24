package com.wisprfox.android.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wisprfox.android.provider.DictationMode
import com.wisprfox.android.provider.gemini.GeminiClient
import com.wisprfox.android.provider.groq.GroqChatClient
import com.wisprfox.android.provider.groq.GroqWhisperClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Non-secret user settings, backed by DataStore Preferences. Mirrors the subset
 * of the desktop `AppSettings` that's relevant on Android. Secrets (API keys)
 * live in [SecureKeyStore], not here.
 */
data class AppSettings(
    val sttModel: String = GroqWhisperClient.DEFAULT_MODEL,
    val llmProvider: String = PROVIDER_GROQ,            // "groq" | "gemini"
    val groqLlmModel: String = GroqChatClient.DEFAULT_MODEL,
    val geminiModel: String = GeminiClient.DEFAULT_MODEL,
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
    /** null = let Whisper auto-detect (the locked English↔Hindi decision). */
    val languageHint: String? = null,
) {
    /** The model name to use for the currently selected LLM provider. */
    val activeLlmModel: String get() = if (llmProvider == PROVIDER_GEMINI) geminiModel else groqLlmModel

    companion object {
        const val PROVIDER_GROQ = "groq"
        const val PROVIDER_GEMINI = "gemini"
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "wisprfox_settings")

class SettingsStore(private val context: Context) {

    private object Keys {
        val sttModel = stringPreferencesKey("stt_model")
        val llmProvider = stringPreferencesKey("llm_provider")
        val groqLlmModel = stringPreferencesKey("groq_llm_model")
        val geminiModel = stringPreferencesKey("gemini_model")
        val defaultMode = stringPreferencesKey("default_mode")
        val retentionDays = intPreferencesKey("retention_days")
        val retentionMaxMb = intPreferencesKey("retention_max_mb")
        val autoPaste = booleanPreferencesKey("auto_paste")
        val overlayBubble = booleanPreferencesKey("overlay_bubble")
        val haptics = booleanPreferencesKey("haptics")
        val avatar = stringPreferencesKey("avatar")
        val languageHint = stringPreferencesKey("language_hint")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p -> p.toSettings() }

    private fun Preferences.toSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            sttModel = this[Keys.sttModel] ?: defaults.sttModel,
            llmProvider = this[Keys.llmProvider] ?: defaults.llmProvider,
            groqLlmModel = this[Keys.groqLlmModel] ?: defaults.groqLlmModel,
            geminiModel = this[Keys.geminiModel] ?: defaults.geminiModel,
            defaultMode = this[Keys.defaultMode]?.let { runCatching { DictationMode.valueOf(it) }.getOrNull() }
                ?: defaults.defaultMode,
            retentionDays = this[Keys.retentionDays] ?: defaults.retentionDays,
            retentionMaxMb = this[Keys.retentionMaxMb] ?: defaults.retentionMaxMb,
            autoPasteEnabled = this[Keys.autoPaste] ?: defaults.autoPasteEnabled,
            overlayBubbleEnabled = this[Keys.overlayBubble] ?: defaults.overlayBubbleEnabled,
            hapticsEnabled = this[Keys.haptics] ?: defaults.hapticsEnabled,
            avatar = this[Keys.avatar]?.let { runCatching { Avatar.valueOf(it) }.getOrNull() } ?: defaults.avatar,
            languageHint = this[Keys.languageHint]?.takeIf { it.isNotBlank() } ?: defaults.languageHint,
        )
    }

    suspend fun setSttModel(v: String) = edit { it[Keys.sttModel] = v }
    suspend fun setLlmProvider(v: String) = edit { it[Keys.llmProvider] = v }
    suspend fun setGroqLlmModel(v: String) = edit { it[Keys.groqLlmModel] = v }
    suspend fun setGeminiModel(v: String) = edit { it[Keys.geminiModel] = v }
    suspend fun setDefaultMode(v: DictationMode) = edit { it[Keys.defaultMode] = v.name }
    suspend fun setRetentionDays(v: Int) = edit { it[Keys.retentionDays] = v }
    suspend fun setRetentionMaxMb(v: Int) = edit { it[Keys.retentionMaxMb] = v }
    suspend fun setAutoPaste(v: Boolean) = edit { it[Keys.autoPaste] = v }
    suspend fun setOverlayBubble(v: Boolean) = edit { it[Keys.overlayBubble] = v }
    suspend fun setHaptics(v: Boolean) = edit { it[Keys.haptics] = v }
    suspend fun setAvatar(v: Avatar) = edit { it[Keys.avatar] = v.name }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
