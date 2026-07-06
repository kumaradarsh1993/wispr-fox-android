package com.wisprfox.android.history

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

/**
 * Pure usage-meter math, ported from the desktop sidebar (`+layout.svelte`
 * usage section) so it can be unit-tested without a device. Covers:
 *  - the Groq free-tier caps and the <50 / <85 / ≥85% ok/warn/danger bands,
 *  - the Deepgram lifetime spend estimate ($0.0092/min vs $200 credit),
 *  - the "resets at HH:MM local (Xh Ym)" label derived from UTC midnight.
 *
 * All percentages clamp to 0..100. Which providers get a *bar* (vs number-only)
 * is a UI rule: bars only for groq + deepgram (desktop rule) — see
 * [hasSttMeter] / [hasLlmMeter].
 */
object UsageMath {

    // Groq free-tier caps (desktop numbers). audio-seconds cap is a daily proxy.
    const val STT_AUDIO_SECONDS_CAP = 3600.0
    const val STT_CALLS_CAP = 2000.0
    const val LLM_TOKENS_CAP = 200_000.0
    const val LLM_CALLS_CAP = 1000.0

    // Deepgram Nova-3 multilingual pre-recorded pricing vs the free credit.
    const val DEEPGRAM_USD_PER_MIN = 0.0092
    const val DEEPGRAM_FREE_CREDIT_USD = 200.0

    /** ok / warn / danger banding at <50 / <85 / ≥85 percent (desktop pctClass). */
    enum class Band { OK, WARN, DANGER }

    fun band(pct: Int): Band = when {
        pct < 50 -> Band.OK
        pct < 85 -> Band.WARN
        else -> Band.DANGER
    }

    private fun pct(value: Double, cap: Double): Int {
        if (cap <= 0.0) return 0
        return (value / cap * 100.0).roundToInt().coerceIn(0, 100)
    }

    /**
     * STT bar percent. Deepgram uses its lifetime credit spend; every other
     * provider uses today's audio-seconds, falling back to call-count when no
     * audio has been tallied (mirrors desktop `sttPct`).
     */
    fun sttPercent(provider: String, audioSeconds: Double, calls: Long, deepgramSpendUsd: Double): Int {
        if (provider == "deepgram") return deepgramPercent(deepgramSpendUsd)
        val audio = pct(audioSeconds, STT_AUDIO_SECONDS_CAP)
        return if (audio > 0) audio else pct(calls.toDouble(), STT_CALLS_CAP)
    }

    /** LLM bar percent: tokens when we have them, else call-count (desktop `llmPct`). */
    fun llmPercent(totalTokens: Long, calls: Long): Int {
        val tok = pct(totalTokens.toDouble(), LLM_TOKENS_CAP)
        return if (tok > 0) tok else pct(calls.toDouble(), LLM_CALLS_CAP)
    }

    fun deepgramSpendUsd(lifetimeAudioSeconds: Double): Double =
        (lifetimeAudioSeconds / 60.0) * DEEPGRAM_USD_PER_MIN

    fun deepgramPercent(spendUsd: Double): Int = pct(spendUsd, DEEPGRAM_FREE_CREDIT_USD)

    /** Bars only for groq + deepgram (desktop `sttHasMeter`). */
    fun hasSttMeter(provider: String): Boolean = provider == "groq" || provider == "deepgram"

    /** Bars only for groq (desktop `llmHasMeter`). */
    fun hasLlmMeter(provider: String): Boolean = provider == "groq"

    /** Compact audio label: "45s" / "3.2m" / "12m" (desktop formatAudio). */
    fun formatAudio(seconds: Double): String {
        if (seconds < 60) return "${seconds.roundToInt()}s"
        val minutes = seconds / 60.0
        return if (minutes < 10) String.format(Locale.US, "%.1fm", minutes)
        else "${minutes.roundToInt()}m"
    }

    /** Compact token label: "850" / "3.2k" / "1.4M" (desktop formatTokens). */
    fun formatTokens(tokens: Long): String = when {
        tokens >= 1_000_000 -> String.format(Locale.US, if (tokens >= 10_000_000) "%.0fM" else "%.1fM", tokens / 1_000_000.0)
        tokens >= 1_000 -> String.format(Locale.US, if (tokens >= 10_000) "%.0fk" else "%.1fk", tokens / 1_000.0)
        else -> tokens.toString()
    }

    fun formatCalls(calls: Long): String = if (calls == 1L) "1 call" else "$calls calls"

    /** The `yyyy-MM-dd` UTC day key an event at [epochMillis] belongs to. */
    fun utcDay(epochMillis: Long): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = epochMillis
        return String.format(
            Locale.US, "%04d-%02d-%02d",
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH),
        )
    }

    /**
     * "resets at HH:MM (Xh Ym)" — the next UTC midnight rendered in the given
     * local timezone, with a countdown. Ported from desktop `nextUtcMidnightLocal`.
     * [localZone] is injectable so the test can pin IST regardless of the CI box.
     */
    fun resetLabel(nowMillis: Long, localZone: TimeZone): String {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        utc.timeInMillis = nowMillis
        utc.add(Calendar.DAY_OF_MONTH, 1)
        utc.set(Calendar.HOUR_OF_DAY, 0)
        utc.set(Calendar.MINUTE, 0)
        utc.set(Calendar.SECOND, 0)
        utc.set(Calendar.MILLISECOND, 0)
        val nextMidnightMillis = utc.timeInMillis

        val remaining = nextMidnightMillis - nowMillis
        val hours = (remaining / 3_600_000L).toInt()
        val mins = ((remaining / 60_000L) % 60L).toInt()

        val local = Calendar.getInstance(localZone)
        local.timeInMillis = nextMidnightMillis
        val timeStr = String.format(
            Locale.US, "%02d:%02d",
            local.get(Calendar.HOUR_OF_DAY), local.get(Calendar.MINUTE),
        )
        return if (hours >= 1) "resets at $timeStr ($hours" + "h $mins" + "m)"
        else "resets at $timeStr ($mins" + "m)"
    }
}
