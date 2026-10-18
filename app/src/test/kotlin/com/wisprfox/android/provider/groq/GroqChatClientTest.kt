package com.wisprfox.android.provider.groq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the reasoning-model handling ported from desktop `llm/groq.rs`. */
class GroqChatClientTest {

    @Test fun reasoningFamilyDetection() {
        assertTrue(GroqChatClient.isGptOss("openai/gpt-oss-20b"))
        assertTrue(GroqChatClient.isGptOss("openai/gpt-oss-120b"))
        assertFalse(GroqChatClient.isGptOss("qwen/qwen3.8-27b"))
        assertTrue(GroqChatClient.isQwen("qwen/qwen3.6-27b"))
        assertTrue(GroqChatClient.isQwen("qwen/qwen3.8-27b"))
        assertFalse(GroqChatClient.isQwen("openai/gpt-oss-20b"))
    }

    @Test fun thinkBlockIsStripped() {
        assertEquals("Hello, world.", GroqChatClient.stripThinkBlock("<think>hmm, punctuation</think>Hello, world."))
        assertEquals("\nBody", GroqChatClient.stripThinkBlock("  <think>x</think>\nBody"))
        assertEquals("Plain reply", GroqChatClient.stripThinkBlock("Plain reply"))
        // Unterminated = the whole reply was thinking; must not be pasted.
        assertEquals("", GroqChatClient.stripThinkBlock("<think>still going"))
    }

    @Test fun completionBudgetIsLiftedForReasoningModels() {
        assertEquals(16_384, GroqChatClient.MAX_COMPLETION_TOKENS)
    }
}
