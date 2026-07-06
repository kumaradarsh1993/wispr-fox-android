package com.wisprfox.android.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the RC-3 / P-1 catalog changes (ids verified live 2026-07-07). The
 * behavioural contract that matters: when a model id is REMOVED from the list
 * (a provider retires it), any stale saved pref must coerce to the provider
 * default so the pipeline never sends a dead id and 4xx's on every call — which
 * the user reads as "cleanup randomly does nothing".
 */
class ProviderCatalogTest {

    // ── Removed ids coerce to the provider default (first-in-list) ──────────

    @Test fun scribeV1CoercesToScribeV2() {
        // ElevenLabs deletes scribe_v1 on 2026-07-09; saved value must self-heal.
        assertEquals(
            "scribe_v2",
            ProviderCatalog.sanitizeSttModel(ProviderCatalog.STT_ELEVENLABS, "scribe_v1"),
        )
    }

    @Test fun distilWhisperCoercesToGroqDefault() {
        // distil-whisper-large-v3-en no longer listed by Groq.
        assertEquals(
            "whisper-large-v3-turbo",
            ProviderCatalog.sanitizeSttModel(ProviderCatalog.STT_GROQ, "distil-whisper-large-v3-en"),
        )
    }

    @Test fun llamaMaverickCoercesToGroqLlmDefault() {
        // The invalid "llama-4-maverick" id was replaced with the real Scout id.
        assertEquals(
            "llama-3.3-70b-versatile",
            ProviderCatalog.sanitizeLlmModel(ProviderCatalog.LLM_GROQ, "llama-4-maverick"),
        )
    }

    @Test fun deprecatedGeminiCoercesToNewDefault() {
        // gemini-2.0-flash is in the deprecated set → new default gemini-3.5-flash.
        assertEquals(
            "gemini-3.5-flash",
            ProviderCatalog.sanitizeLlmModel(ProviderCatalog.LLM_GEMINI, "gemini-2.0-flash"),
        )
    }

    // ── Valid ids survive sanitisation unchanged ────────────────────────────

    @Test fun validGroqLlm4ScoutSurvives() {
        val id = "meta-llama/llama-4-scout-17b-16e-instruct"
        assertEquals(id, ProviderCatalog.sanitizeLlmModel(ProviderCatalog.LLM_GROQ, id))
    }

    @Test fun validGemini31PreviewSurvives() {
        assertEquals(
            "gemini-3.1-pro-preview",
            ProviderCatalog.sanitizeLlmModel(ProviderCatalog.LLM_GEMINI, "gemini-3.1-pro-preview"),
        )
    }

    @Test fun gemini35FlashIsTheNewDefault() {
        assertEquals("gemini-3.5-flash", ProviderCatalog.defaultLlmModel(ProviderCatalog.LLM_GEMINI))
    }

    // ── List membership: removed ids are truly gone ─────────────────────────

    @Test fun removedIdsNotInLists() {
        assertFalse(ProviderCatalog.sttModelsFor(ProviderCatalog.STT_GROQ).any { it.id == "distil-whisper-large-v3-en" })
        assertFalse(ProviderCatalog.sttModelsFor(ProviderCatalog.STT_ELEVENLABS).any { it.id == "scribe_v1" })
        assertFalse(ProviderCatalog.llmModelsFor(ProviderCatalog.LLM_GROQ).any { it.id == "llama-4-maverick" })
    }

    @Test fun scoutAndGeminiAdditionsPresent() {
        assertTrue(ProviderCatalog.llmModelsFor(ProviderCatalog.LLM_GROQ).any { it.id == "meta-llama/llama-4-scout-17b-16e-instruct" })
        assertTrue(ProviderCatalog.llmModelsFor(ProviderCatalog.LLM_GEMINI).any { it.id == "gemini-3.5-flash" })
        assertTrue(ProviderCatalog.llmModelsFor(ProviderCatalog.LLM_GEMINI).any { it.id == "gemini-3.1-pro-preview" })
    }

    // ── shortModel labels track the catalog ─────────────────────────────────

    @Test fun shortModelLabelsForNewIds() {
        assertEquals("Llama 4 Scout", ProviderCatalog.shortModel("meta-llama/llama-4-scout-17b-16e-instruct"))
        assertEquals("Gemini 3.5 Flash", ProviderCatalog.shortModel("gemini-3.5-flash"))
        assertEquals("Gemini 3.1 Pro", ProviderCatalog.shortModel("gemini-3.1-pro-preview"))
    }
}
