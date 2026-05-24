package com.wisprfox.android.provider

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeLlm(
    override val name: String = "fake",
    val behaviour: suspend (system: String, user: String) -> String,
) : LlmProvider {
    override suspend fun complete(system: String, user: String, temperature: Float): String =
        behaviour(system, user)
}

class CleanupOrchestratorTest {

    private val orch = CleanupOrchestrator()

    @Test fun driftZeroForIdentical() {
        assertTrue(CleanupOrchestrator.lengthDrift("hello world", "hello world") < 1e-6)
    }

    @Test fun driftHandlesEmpty() {
        assertEquals(0f, CleanupOrchestrator.lengthDrift("", ""))
        assertEquals(1f, CleanupOrchestrator.lengthDrift("", "out"))
    }

    @Test fun driftDetectsBigChange() {
        assertTrue(CleanupOrchestrator.lengthDrift("hello there", "pwned") > 0.40f)
    }

    @Test fun rawModeShortCircuits() = runTest {
        val provider = FakeLlm { _, _ -> error("should not be called") }
        val r = orch.clean("  hi there  ", DictationMode.RAW, null, null, provider)
        assertEquals("hi there", r.text)
        assertFalse(r.usedLlm)
        assertEquals(null, r.note)
    }

    @Test fun emptyInputReturnsEmpty() = runTest {
        val provider = FakeLlm { _, _ -> error("should not be called") }
        val r = orch.clean("    ", DictationMode.CLEANED, null, null, provider)
        assertEquals("", r.text)
        assertFalse(r.usedLlm)
    }

    @Test fun cleanedTripwireFallsBackToRaw() = runTest {
        // A long input where the model returns a tiny injected string → big
        // drift → must discard and return raw with the security note.
        val longInput = "please clean up this fairly long sentence about my day at work"
        val provider = FakeLlm { _, _ -> "pwned" }
        val r = orch.clean(longInput, DictationMode.CLEANED, null, null, provider)
        assertEquals(longInput, r.text)
        assertFalse(r.usedLlm)
        assertEquals("light_length_drift", r.note)
    }

    @Test fun cleanedAcceptsModeratePolish() = runTest {
        val input = "hello there how are you today"
        val provider = FakeLlm { _, _ -> "Hello there. How are you today?" }
        val r = orch.clean(input, DictationMode.CLEANED, null, null, provider)
        assertEquals("Hello there. How are you today?", r.text)
        assertTrue(r.usedLlm)
        assertEquals(null, r.note)
    }

    @Test fun timeoutReturnsRawWithNote() = runTest {
        val input = "this is my transcript"
        val provider = FakeLlm { _, _ -> throw LlmError.Timeout }
        val r = orch.clean(input, DictationMode.REFORMATTED, null, null, provider)
        assertEquals(input, r.text)
        assertFalse(r.usedLlm)
        assertEquals("clippy_timeout", r.note)
    }

    @Test fun http401MapsToAuthNote() = runTest {
        val input = "this is my transcript"
        val provider = FakeLlm { _, _ -> throw LlmError.Http(401, "unauthorized") }
        val r = orch.clean(input, DictationMode.CLEANED, null, null, provider)
        assertEquals(input, r.text)
        assertEquals("clippy_auth", r.note)
    }

    @Test fun http429MapsToRateLimit() = runTest {
        val provider = FakeLlm { _, _ -> throw LlmError.Http(429, "slow down") }
        val r = orch.clean("text here", DictationMode.REFORMATTED, null, null, provider)
        assertEquals("clippy_rate_limited", r.note)
    }

    @Test fun reformattedHasNoTripwire() = runTest {
        // Reformatted is allowed to expand a brief into a long draft.
        val brief = "tell the team I'm late"
        val longDraft = "Hi team, apologies but I'm running about ten minutes late to our " +
            "meeting this morning. Please go ahead and start without me and I'll catch up " +
            "as soon as I arrive. Thanks for your understanding."
        val provider = FakeLlm { _, _ -> longDraft }
        val r = orch.clean(brief, DictationMode.REFORMATTED, null, null, provider)
        assertEquals(longDraft, r.text)
        assertTrue(r.usedLlm)
        assertEquals(null, r.note)
    }

    @Test fun cleanedWrapsUserMessageInTranscriptTags() = runTest {
        var capturedUser: String? = null
        val provider = FakeLlm { _, user -> capturedUser = user; "ok output here yes" }
        orch.clean("my words", DictationMode.CLEANED, null, null, provider)
        assertEquals("<transcript>my words</transcript>", capturedUser)
    }

    @Test fun systemOverrideUsedWhenNonBlank() = runTest {
        var capturedSystem: String? = null
        val provider = FakeLlm { system, _ -> capturedSystem = system; "polished output text" }
        orch.clean("words here", DictationMode.REFORMATTED, "MY CUSTOM PROMPT", null, provider)
        assertEquals("MY CUSTOM PROMPT", capturedSystem)
    }

    @Test fun contextHintPrependedToSystem() = runTest {
        var capturedSystem: String? = null
        val provider = FakeLlm { system, _ -> capturedSystem = system; "polished output text" }
        orch.clean("words here", DictationMode.REFORMATTED, null, "REGISTER: casual", provider)
        assertTrue(capturedSystem!!.startsWith("REGISTER: casual\n\n"))
    }
}
