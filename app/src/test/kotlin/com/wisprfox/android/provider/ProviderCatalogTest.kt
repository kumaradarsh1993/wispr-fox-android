package com.wisprfox.android.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards current catalog ids and stale-preference self-healing. */
class ProviderCatalogTest {
    @Test fun scribeV1CoercesToScribeV2() {
        assertEquals("scribe_v2", ProviderCatalog.sanitizeSttModel(ProviderCatalog.STT_ELEVENLABS, "scribe_v1"))
    }

    @Test fun distilWhisperCoercesToGroqDefault() {
        assertEquals(
            "whisper-large-v3-turbo",
            ProviderCatalog.sanitizeSttModel(ProviderCatalog.STT_GROQ, "distil-whisper-large-v3-en"),
        )
    }

    @Test fun retiredLlamaCoercesToCurrentGroqDefault() {
        assertEquals(
            "openai/gpt-oss-20b",
            ProviderCatalog.sanitizeLlmModel(ProviderCatalog.LLM_GROQ, "llama-4-maverick"),
        )
    }

    @Test fun deprecatedGeminiCoercesToCurrentDefault() {
        assertEquals(
            "gemini-3.8-flash",
            ProviderCatalog.sanitizeLlmModel(ProviderCatalog.LLM_GEMINI, "gemini-2.0-flash"),
        )
    }

    @Test fun staleOpenAiModelsCoerceToCurrentDefault() {
        // gpt-5.4/5.5 removed when the catalog moved to the gpt-5.6 family;
        // saved selections self-heal to the new default on load.
        assertEquals("gpt-5.6-terra", ProviderCatalog.sanitizeLlmModel(ProviderCatalog.LLM_OPENAI, "gpt-5.4-mini"))
        assertEquals("gpt-5.6-terra", ProviderCatalog.sanitizeLlmModel(ProviderCatalog.LLM_OPENAI, "gpt-5.5"))
    }

    @Test fun validCurrentModelsSurvive() {
        assertEquals(
            "openai/gpt-oss-120b",
            ProviderCatalog.sanitizeLlmModel(ProviderCatalog.LLM_GROQ, "openai/gpt-oss-120b"),
        )
        assertEquals(
            "gemini-3.1-pro-preview",
            ProviderCatalog.sanitizeLlmModel(ProviderCatalog.LLM_GEMINI, "gemini-3.1-pro-preview"),
        )
    }

    @Test fun currentDefaultsAreStable() {
        assertEquals("gemini-3.8-flash", ProviderCatalog.defaultLlmModel(ProviderCatalog.LLM_GEMINI))
        assertEquals("gpt-5.6-terra", ProviderCatalog.defaultLlmModel(ProviderCatalog.LLM_OPENAI))
        assertEquals("openai/gpt-oss-120b", ProviderCatalog.defaultStrongLlmModel(ProviderCatalog.LLM_GROQ))
        assertEquals("gpt-5.6-sol", ProviderCatalog.defaultStrongLlmModel(ProviderCatalog.LLM_OPENAI))
    }

    @Test fun removedIdsNotInLists() {
        assertFalse(ProviderCatalog.sttModelsFor(ProviderCatalog.STT_GROQ).any { it.id == "distil-whisper-large-v3-en" })
        assertFalse(ProviderCatalog.sttModelsFor(ProviderCatalog.STT_ELEVENLABS).any { it.id == "scribe_v1" })
        assertFalse(ProviderCatalog.llmModelsFor(ProviderCatalog.LLM_GROQ).any { it.id == "llama-4-maverick" })
    }

    @Test fun currentCatalogAdditionsPresent() {
        assertTrue(ProviderCatalog.llmModelsFor(ProviderCatalog.LLM_GROQ).any { it.id == "openai/gpt-oss-120b" })
        assertFalse(ProviderCatalog.llmModelsFor(ProviderCatalog.LLM_GROQ).any { it.id == "qwen/qwen3.6-27b" })
        assertEquals("qwen/qwen3.8-27b", ProviderCatalog.sanitizeLlmModel(ProviderCatalog.LLM_GROQ, "qwen/qwen3.6-27b"))
        assertFalse(ProviderCatalog.llmModelsFor(ProviderCatalog.LLM_GEMINI).any { it.id == "gemini-3.7-flash" || it.id == "gemini-3.5-flash" })
        assertTrue(ProviderCatalog.llmModelsFor(ProviderCatalog.LLM_GROQ).any { it.id == "qwen/qwen3.8-27b" })
        assertTrue(ProviderCatalog.llmModelsFor(ProviderCatalog.LLM_OPENAI).any { it.id == "gpt-5.6-terra" })
        assertTrue(ProviderCatalog.llmModelsFor(ProviderCatalog.LLM_GEMINI).any { it.id == "gemini-3.8-flash" })
        assertTrue(ProviderCatalog.llmModelsFor(ProviderCatalog.LLM_GEMINI).any { it.id == "gemini-3.6-flash" })
        assertTrue(ProviderCatalog.sttModelsFor(ProviderCatalog.STT_OPENAI).any { it.id == "gpt-4o-transcribe-diarize" })
    }

    @Test fun shortModelLabelsTrackCatalog() {
        assertEquals("GPT-OSS 120B", ProviderCatalog.shortModel("openai/gpt-oss-120b"))
        assertEquals("Qwen 3.8 27B", ProviderCatalog.shortModel("qwen/qwen3.8-27b"))
        assertEquals("GPT-5.6 Terra", ProviderCatalog.shortModel("gpt-5.6-terra"))
        assertEquals("Gemini 3.8 Flash", ProviderCatalog.shortModel("gemini-3.8-flash"))
        assertEquals("Gemini 3.6 Flash", ProviderCatalog.shortModel("gemini-3.6-flash"))
        assertEquals("Gemini 3.1 Pro", ProviderCatalog.shortModel("gemini-3.1-pro-preview"))
    }

    @Test fun diarizationFallsBackToDeepgramNova3() {
        assertEquals(
            ProviderCatalog.STT_DEEPGRAM to "nova-3",
            ProviderCatalog.compatibleDiarizationSelection(ProviderCatalog.STT_GROQ, "whisper-large-v3"),
        )
    }
}
