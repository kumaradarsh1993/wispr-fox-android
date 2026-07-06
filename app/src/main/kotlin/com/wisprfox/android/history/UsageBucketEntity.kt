package com.wisprfox.android.history

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * One per-day, per-(stage, provider, model) usage bucket — the Android port of
 * the desktop `usage.json` model buckets (`wispr-fox` sidebar usage meters,
 * `usage-store.svelte.ts`). Ported for P-2 (see docs/AUDIT_2026-07-06_FABLE.md).
 *
 * Day is stored as `yyyy-MM-dd` in **UTC** so the "resets at UTC midnight"
 * contract matches the desktop free-tier reset (Groq/OpenAI reset on UTC day
 * boundaries, not the user's local midnight).
 *
 * STT rows accumulate `calls` + `audioSeconds`; LLM rows accumulate `calls` +
 * token counts. Token plumbing from the provider clients isn't wired yet (the
 * LlmProvider.complete() contract returns only text — see the TODO in
 * [RecordingRepository.recordLlmUsage]); until then LLM rows carry calls only
 * and the token columns stay 0. Bumping them later needs no migration.
 */
@Entity(tableName = "usage_buckets", primaryKeys = ["day", "stage", "provider", "model"])
data class UsageBucketEntity(
    val day: String,
    /** "stt" or "llm". */
    val stage: String,
    val provider: String,
    val model: String,
    val calls: Long = 0,
    @ColumnInfo(name = "audio_seconds") val audioSeconds: Double = 0.0,
    @ColumnInfo(name = "input_tokens") val inputTokens: Long = 0,
    @ColumnInfo(name = "output_tokens") val outputTokens: Long = 0,
    @ColumnInfo(name = "total_tokens") val totalTokens: Long = 0,
)

object UsageStage {
    const val STT = "stt"
    const val LLM = "llm"
}
