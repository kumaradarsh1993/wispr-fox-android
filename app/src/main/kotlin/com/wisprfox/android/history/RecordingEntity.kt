package com.wisprfox.android.history

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wisprfox.android.provider.DictationMode

/**
 * One row per recording. Denormalised lifecycle tracked via [status]. Schema
 * mirrors the desktop sibling's `recordings` table (`history/mod.rs`) — three
 * text outputs (transcript / cleanedText / draftedText) so a single recording
 * can hold its Raw, Cleaned, and Reformatted versions simultaneously.
 */
@Entity(tableName = "recordings", indices = [Index("created_at")])
data class RecordingEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "audio_path") val audioPath: String,
    @ColumnInfo(name = "duration_ms") val durationMs: Long = 0,
    val mode: String,
    val status: String,
    val transcript: String? = null,
    @ColumnInfo(name = "cleaned_text") val cleanedText: String? = null,
    @ColumnInfo(name = "drafted_text") val draftedText: String? = null,
    @ColumnInfo(name = "stt_provider") val sttProvider: String? = null,
    @ColumnInfo(name = "llm_provider") val llmProvider: String? = null,
    @ColumnInfo(name = "target_package") val targetPackage: String? = null,
    @ColumnInfo(name = "clippy_used") val clippyUsed: Boolean = false,
    @ColumnInfo(name = "clippy_note") val clippyNote: String? = null,
    @ColumnInfo(name = "retry_count") val retryCount: Int = 0,
    val error: String? = null,
    /**
     * Per-recording provider/model overrides (schema v4). Live dictation leaves
     * these null and the pipeline reads the global [AppSettings]; an *imported*
     * file records the models the user picked on the import sheet here so the
     * worker honours them without mutating the user's live-dictation defaults.
     */
    @ColumnInfo(name = "stt_provider_override") val sttProviderOverride: String? = null,
    @ColumnInfo(name = "stt_model_override") val sttModelOverride: String? = null,
    @ColumnInfo(name = "llm_provider_override") val llmProviderOverride: String? = null,
    @ColumnInfo(name = "llm_model_override") val llmModelOverride: String? = null,
    /** True when this row came from an imported audio file (not the live mic). */
    val imported: Boolean = false,
    /**
     * Sync/accounts (schema v5). `platform` is "mobile" for rows created on
     * this device and "desktop"/"web"/"mobile" for rows pulled from another
     * device via the cloud; null on rows created before this migration until
     * they're touched again (display treats null as "mobile" — this device).
     * `device_name` is best-effort (only populated for locally-created rows;
     * pulled rows don't carry a device label in the `notes` table, see
     * SyncEngine). `dirty` marks a row with local changes not yet pushed.
     * `remote` marks a row that came from another device (no local audio —
     * HistoryScreen hides audio-dependent actions for these). `updated_at` is
     * the LWW clock for the sync protocol; defaults to `created_at`.
     */
    val platform: String? = null,
    @ColumnInfo(name = "device_name") val deviceName: String? = null,
    val dirty: Boolean = false,
    val remote: Boolean = false,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = createdAt,
)

enum class RecordingStatus(val raw: String) {
    RECORDING("recording"),
    /** Decoding an imported audio file into the pipeline's WAV format. */
    IMPORTING("importing"),
    TRANSCRIBING("transcribing"),
    CLEANING("cleaning"),
    INJECTING("injecting"),
    DONE("done"),
    ERROR("error");

    companion object {
        fun parse(s: String?): RecordingStatus =
            entries.firstOrNull { it.raw == s } ?: ERROR
    }
}

/** Which alt-output column an LLM result routes to (cleaned vs drafted). */
enum class AltKind { CLEANED, DRAFTED }

fun DictationMode.toRaw(): String = when (this) {
    DictationMode.RAW -> "raw"
    DictationMode.CLEANED -> "cleaned"
    DictationMode.ADVANCED -> "advanced"
    DictationMode.REFORMATTED -> "reformatted"
}

fun parseMode(s: String?): DictationMode = when (s) {
    "cleaned" -> DictationMode.CLEANED
    "advanced" -> DictationMode.ADVANCED
    "reformatted", "drafting" -> DictationMode.REFORMATTED
    else -> DictationMode.RAW
}
