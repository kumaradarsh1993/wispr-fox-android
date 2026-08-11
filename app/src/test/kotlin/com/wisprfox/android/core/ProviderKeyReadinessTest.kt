package com.wisprfox.android.core

import com.wisprfox.android.provider.ProviderCatalog
import com.wisprfox.android.settings.SecureKeyStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderKeyReadinessTest {
    @Test fun openAiSpeechAndLlmKeysFallbackToEachOther() {
        val llmOnly = ProviderKeyReadiness { key -> if (key == SecureKeyStore.Key.OpenAiLlm) "shared" else null }
        assertTrue(llmOnly.stt(ProviderCatalog.STT_OPENAI).ready)

        val sttOnly = ProviderKeyReadiness { key -> if (key == SecureKeyStore.Key.OpenAiStt) "shared" else null }
        assertTrue(sttOnly.llm(ProviderCatalog.LLM_OPENAI).ready)
    }

    @Test fun groqLlmFallsBackToSpeechKeyButSpeechDoesNotUseLlmKey() {
        val llmOnly = ProviderKeyReadiness { key -> if (key == SecureKeyStore.Key.GroqLlm) "llm" else null }
        assertFalse(llmOnly.stt(ProviderCatalog.STT_GROQ).ready)

        val sttOnly = ProviderKeyReadiness { key -> if (key == SecureKeyStore.Key.GroqStt) "speech" else null }
        assertTrue(sttOnly.llm(ProviderCatalog.LLM_GROQ).ready)
        assertEquals("speech", sttOnly.llm(ProviderCatalog.LLM_GROQ).value)
    }

    @Test fun dedicatedProvidersRequireTheirOwnKeys() {
        val empty = ProviderKeyReadiness { null }
        assertFalse(empty.stt(ProviderCatalog.STT_DEEPGRAM).ready)
        assertFalse(empty.stt(ProviderCatalog.STT_ELEVENLABS).ready)
        assertFalse(empty.llm(ProviderCatalog.LLM_GEMINI).ready)
    }
}
