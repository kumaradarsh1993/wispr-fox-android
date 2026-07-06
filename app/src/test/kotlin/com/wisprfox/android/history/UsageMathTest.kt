package com.wisprfox.android.history

import com.wisprfox.android.history.UsageMath.Band
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/** Covers the P-2 usage meter math: caps, ok/warn/danger bands, Deepgram
 *  spend estimate, and the UTC-midnight reset label. */
class UsageMathTest {

    // ── Threshold banding at <50 / <85 / ≥85 ────────────────────────────────

    @Test fun bandBoundaries() {
        assertEquals(Band.OK, UsageMath.band(0))
        assertEquals(Band.OK, UsageMath.band(49))
        assertEquals(Band.WARN, UsageMath.band(50))
        assertEquals(Band.WARN, UsageMath.band(84))
        assertEquals(Band.DANGER, UsageMath.band(85))
        assertEquals(Band.DANGER, UsageMath.band(100))
    }

    // ── STT percent: audio-seconds vs Groq cap, falls back to calls ─────────

    @Test fun sttPercentUsesAudioAgainst3600Cap() {
        // 1800s of a 3600s daily proxy = 50%.
        assertEquals(50, UsageMath.sttPercent("groq", audioSeconds = 1800.0, calls = 5, deepgramSpendUsd = 0.0))
    }

    @Test fun sttPercentFallsBackToCallsWhenNoAudio() {
        // 1000 calls / 2000 cap = 50% when audio is zero.
        assertEquals(50, UsageMath.sttPercent("groq", audioSeconds = 0.0, calls = 1000, deepgramSpendUsd = 0.0))
    }

    @Test fun sttPercentClampsAt100() {
        assertEquals(100, UsageMath.sttPercent("groq", audioSeconds = 99_999.0, calls = 0, deepgramSpendUsd = 0.0))
    }

    // ── Deepgram lifetime spend estimate ────────────────────────────────────

    @Test fun deepgramSpendEstimate() {
        // 60 minutes (3600s) × $0.0092/min = $0.552.
        assertEquals(0.552, UsageMath.deepgramSpendUsd(3600.0), 1e-9)
    }

    @Test fun deepgramPercentAgainst200Credit() {
        // $100 spend of $200 credit = 50%.
        assertEquals(50, UsageMath.deepgramPercent(100.0))
    }

    @Test fun sttPercentForDeepgramUsesSpendNotAudio() {
        // Deepgram ignores today's audio and reads its lifetime credit spend.
        assertEquals(50, UsageMath.sttPercent("deepgram", audioSeconds = 0.0, calls = 0, deepgramSpendUsd = 100.0))
    }

    // ── LLM percent: tokens vs calls fallback ───────────────────────────────

    @Test fun llmPercentUsesTokensAgainst200k() {
        assertEquals(50, UsageMath.llmPercent(totalTokens = 100_000, calls = 3))
    }

    @Test fun llmPercentFallsBackToCalls() {
        assertEquals(50, UsageMath.llmPercent(totalTokens = 0, calls = 500))
    }

    // ── Which providers get a bar ───────────────────────────────────────────

    @Test fun meterOnlyForGroqAndDeepgram() {
        assertTrue(UsageMath.hasSttMeter("groq"))
        assertTrue(UsageMath.hasSttMeter("deepgram"))
        assertFalse(UsageMath.hasSttMeter("openai"))
        assertFalse(UsageMath.hasSttMeter("elevenlabs"))
        assertTrue(UsageMath.hasLlmMeter("groq"))
        assertFalse(UsageMath.hasLlmMeter("openai"))
        assertFalse(UsageMath.hasLlmMeter("gemini"))
    }

    // ── Formatters ──────────────────────────────────────────────────────────

    @Test fun formatAudioBuckets() {
        assertEquals("45s", UsageMath.formatAudio(45.0))
        assertEquals("3.2m", UsageMath.formatAudio(192.0))
        assertEquals("12m", UsageMath.formatAudio(720.0))
    }

    @Test fun formatTokensBuckets() {
        assertEquals("850", UsageMath.formatTokens(850))
        assertEquals("3.2k", UsageMath.formatTokens(3200))
        assertEquals("12k", UsageMath.formatTokens(12000))
        assertEquals("1.4M", UsageMath.formatTokens(1_400_000))
    }

    // ── UTC day key ─────────────────────────────────────────────────────────

    @Test fun utcDayKeyIsUtcNotLocal() {
        // 2026-07-07 20:00 UTC → next day in IST but the bucket day stays UTC.
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2026, Calendar.JULY, 7, 20, 0, 0); set(Calendar.MILLISECOND, 0)
        }
        assertEquals("2026-07-07", UsageMath.utcDay(cal.timeInMillis))
    }

    // ── Reset label: UTC midnight rendered in IST with a countdown ──────────

    @Test fun resetLabelConvertsUtcMidnightToIst() {
        val ist = TimeZone.getTimeZone("Asia/Kolkata")
        // "Now" = 2026-07-07 21:30:00 UTC. Next UTC midnight = 2026-07-08 00:00 UTC,
        // which is 05:30 IST. Remaining = 2h 30m.
        val now = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2026, Calendar.JULY, 7, 21, 30, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        assertEquals("resets at 05:30 (2h 30m)", UsageMath.resetLabel(now, ist))
    }

    @Test fun resetLabelUnderOneHourOmitsHours() {
        val ist = TimeZone.getTimeZone("Asia/Kolkata")
        // "Now" = 2026-07-07 23:20 UTC → 40m to UTC midnight (05:30 IST).
        val now = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2026, Calendar.JULY, 7, 23, 20, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        assertEquals("resets at 05:30 (40m)", UsageMath.resetLabel(now, ist))
    }
}
