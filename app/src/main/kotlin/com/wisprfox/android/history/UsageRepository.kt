package com.wisprfox.android.history

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.wisprfox.android.provider.ProviderCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * The live view of today's usage for the active provider/model, plus the
 * Deepgram lifetime spend estimate. Records usage on pipeline success (called
 * from [RecordingRepository]) and exposes a [UsageSnapshot] flow the Home strip
 * and Settings detail render. Ported from the desktop usage store (P-2).
 */

/** What the UI needs to draw one usage line for a given stage. */
data class UsageLine(
    val provider: String,
    val model: String,
    val calls: Long,
    val audioSeconds: Double,
    val totalTokens: Long,
    /** 0..100. Meaning depends on stage/provider (audio vs tokens vs Deepgram $). */
    val percent: Int,
    /** Whether this provider gets a bar (groq/deepgram) or number-only. */
    val hasMeter: Boolean,
    val band: UsageMath.Band,
    /** Short human label, e.g. "3.2m", "1.4k tok", "$1.20/$200". */
    val label: String,
)

data class UsageSnapshot(
    val stt: UsageLine,
    val llm: UsageLine,
    val deepgramSpendUsd: Double,
    val resetLabel: String,
)

// Lifetime Deepgram audio-seconds live outside the daily Room buckets — the
// spend estimate is cumulative against the $200 credit, not a "today" number.
private val Context.usageDataStore: DataStore<Preferences> by preferencesDataStore(name = "wisprfox_usage")

class UsageRepository(
    private val context: Context,
    private val dao: UsageBucketDao,
) {
    private object Keys {
        val deepgramLifetimeSeconds = doublePreferencesKey("deepgram_lifetime_seconds")
    }

    private val deepgramLifetimeSeconds: Flow<Double> =
        context.usageDataStore.data.map { it[Keys.deepgramLifetimeSeconds] ?: 0.0 }

    // ── Recording (called on pipeline success) ──────────────────────────────

    /** STT success: bump today's bucket (+1 call, + audio seconds). Deepgram
     *  also accrues lifetime seconds for the credit-spend estimate. */
    suspend fun recordStt(provider: String, model: String, audioSeconds: Double, nowMillis: Long) {
        val day = UsageMath.utcDay(nowMillis)
        dao.addStt(day, provider, model, calls = 1, audioSeconds = audioSeconds)
        if (provider == ProviderCatalog.STT_DEEPGRAM && audioSeconds > 0) {
            context.usageDataStore.edit {
                it[Keys.deepgramLifetimeSeconds] = (it[Keys.deepgramLifetimeSeconds] ?: 0.0) + audioSeconds
            }
        }
    }

    /**
     * LLM success: bump today's bucket (+1 call, + tokens). Token counts are 0
     * until provider-response token plumbing is wired (see the TODO in
     * [RecordingRepository.recordLlmUsage]); calls are tallied now.
     */
    suspend fun recordLlm(provider: String, model: String, inTok: Long, outTok: Long, totTok: Long, nowMillis: Long) {
        val day = UsageMath.utcDay(nowMillis)
        dao.addLlm(day, provider, model, calls = 1, inTok = inTok, outTok = outTok, totTok = totTok)
    }

    // ── Reading (for the UI) ─────────────────────────────────────────────────

    /**
     * A live snapshot for the active STT + LLM provider/model. The day key and
     * reset label are recomputed from [nowMillisProvider] on each emission so
     * the countdown and the day rollover both stay current without a timer here
     * (the UI re-collects; the caller can also pass a ticking clock).
     */
    fun snapshot(
        sttProvider: String,
        sttModel: String,
        llmProvider: String,
        llmModel: String,
        localZone: java.util.TimeZone,
        nowMillisProvider: () -> Long,
    ): Flow<UsageSnapshot> {
        val day = UsageMath.utcDay(nowMillisProvider())
        return combine(dao.observeForDay(day), deepgramLifetimeSeconds) { buckets, dgSeconds ->
            val now = nowMillisProvider()
            val deepgramSpend = UsageMath.deepgramSpendUsd(dgSeconds)

            val sttRow = buckets.firstOrNull {
                it.stage == UsageStage.STT && it.provider == sttProvider && it.model == sttModel
            } ?: buckets.firstOrNull { it.stage == UsageStage.STT && it.provider == sttProvider }
            val llmRow = buckets.firstOrNull {
                it.stage == UsageStage.LLM && it.provider == llmProvider && it.model == llmModel
            } ?: buckets.firstOrNull { it.stage == UsageStage.LLM && it.provider == llmProvider }

            val sttPct = UsageMath.sttPercent(
                provider = sttProvider,
                audioSeconds = sttRow?.audioSeconds ?: 0.0,
                calls = sttRow?.calls ?: 0,
                deepgramSpendUsd = deepgramSpend,
            )
            val llmPct = UsageMath.llmPercent(llmRow?.totalTokens ?: 0, llmRow?.calls ?: 0)

            val sttLabel = when {
                sttProvider == "deepgram" ->
                    "$" + String.format(java.util.Locale.US, "%.2f", deepgramSpend) + "/$" + UsageMath.DEEPGRAM_FREE_CREDIT_USD.toInt()
                (sttRow?.audioSeconds ?: 0.0) > 0 -> UsageMath.formatAudio(sttRow!!.audioSeconds)
                else -> UsageMath.formatCalls(sttRow?.calls ?: 0)
            }
            val llmLabel = if ((llmRow?.totalTokens ?: 0) > 0) "${UsageMath.formatTokens(llmRow!!.totalTokens)} tok"
            else UsageMath.formatCalls(llmRow?.calls ?: 0)

            UsageSnapshot(
                stt = UsageLine(
                    provider = sttProvider,
                    model = sttModel,
                    calls = sttRow?.calls ?: 0,
                    audioSeconds = sttRow?.audioSeconds ?: 0.0,
                    totalTokens = 0,
                    percent = sttPct,
                    hasMeter = UsageMath.hasSttMeter(sttProvider),
                    band = UsageMath.band(sttPct),
                    label = sttLabel,
                ),
                llm = UsageLine(
                    provider = llmProvider,
                    model = llmModel,
                    calls = llmRow?.calls ?: 0,
                    audioSeconds = 0.0,
                    totalTokens = llmRow?.totalTokens ?: 0,
                    percent = llmPct,
                    hasMeter = UsageMath.hasLlmMeter(llmProvider),
                    band = UsageMath.band(llmPct),
                    label = llmLabel,
                ),
                deepgramSpendUsd = deepgramSpend,
                resetLabel = UsageMath.resetLabel(now, localZone),
            )
        }
    }
}
