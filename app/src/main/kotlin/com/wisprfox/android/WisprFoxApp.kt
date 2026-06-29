package com.wisprfox.android

import android.app.Application
import android.content.Context
import com.wisprfox.android.core.AppContainer
import com.wisprfox.android.settings.SecureKeyStore
import kotlinx.coroutines.launch

/**
 * Application entry. Keep [onCreate] light — no network, no DB reads on the
 * main thread (cold-launch budget). The only work is constructing the lazy
 * [AppContainer] and kicking a background pass to fail any recordings that
 * were stranded mid-pipeline by a previous process death.
 */
class WisprFoxApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        seedDevKeysIfNeeded()
        container.applicationScope.launch {
            runCatching { container.recordings.recoverStranded() }
        }
    }

    /**
     * Debug convenience: if a dev key was provided via keys.properties (baked
     * into BuildConfig) and no key is stored yet, seed it. Lets iterative debug
     * installs skip onboarding without re-pasting. Release builds carry empty
     * BuildConfig keys, so this is a no-op there.
     */
    private fun seedDevKeysIfNeeded() {
        val secrets = container.secrets
        if (!secrets.has(SecureKeyStore.Key.GroqStt) && BuildConfig.DEV_GROQ_KEY.isNotBlank()) {
            secrets.put(SecureKeyStore.Key.GroqStt, BuildConfig.DEV_GROQ_KEY)
        }
        if (!secrets.has(SecureKeyStore.Key.OpenAiStt) && BuildConfig.DEV_OPENAI_KEY.isNotBlank()) {
            secrets.put(SecureKeyStore.Key.OpenAiStt, BuildConfig.DEV_OPENAI_KEY)
        }
        if (!secrets.has(SecureKeyStore.Key.DeepgramStt) && BuildConfig.DEV_DEEPGRAM_KEY.isNotBlank()) {
            secrets.put(SecureKeyStore.Key.DeepgramStt, BuildConfig.DEV_DEEPGRAM_KEY)
        }
        if (!secrets.has(SecureKeyStore.Key.ElevenLabsStt) && BuildConfig.DEV_ELEVENLABS_KEY.isNotBlank()) {
            secrets.put(SecureKeyStore.Key.ElevenLabsStt, BuildConfig.DEV_ELEVENLABS_KEY)
        }
        if (!secrets.has(SecureKeyStore.Key.GeminiLlm) && BuildConfig.DEV_GEMINI_KEY.isNotBlank()) {
            secrets.put(SecureKeyStore.Key.GeminiLlm, BuildConfig.DEV_GEMINI_KEY)
        }
    }

    companion object {
        fun container(context: Context): AppContainer =
            (context.applicationContext as WisprFoxApp).container
    }
}
