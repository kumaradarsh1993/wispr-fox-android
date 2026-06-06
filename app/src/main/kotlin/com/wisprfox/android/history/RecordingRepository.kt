package com.wisprfox.android.history

import android.content.Context
import com.wisprfox.android.provider.DictationMode
import com.wisprfox.android.queue.TranscribeWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.UUID

/** Domain view of a recording row (enums resolved, paths as File). */
data class Recording(
    val id: String,
    val createdAt: Long,
    val audioPath: String,
    val durationMs: Long,
    val mode: DictationMode,
    val status: RecordingStatus,
    val transcript: String?,
    val cleanedText: String?,
    val draftedText: String?,
    val sttProvider: String?,
    val llmProvider: String?,
    val clippyUsed: Boolean,
    val clippyNote: String?,
    val retryCount: Int,
    val error: String?,
) {
    /** Best text available for the row's own mode (for previews / delivery). */
    fun primaryText(): String? = when (mode) {
        DictationMode.RAW -> transcript
        DictationMode.CLEANED -> cleanedText ?: transcript
        DictationMode.ADVANCED -> cleanedText ?: transcript
        DictationMode.REFORMATTED -> draftedText ?: transcript
    }
}

private fun RecordingEntity.toDomain() = Recording(
    id = id,
    createdAt = createdAt,
    audioPath = audioPath,
    durationMs = durationMs,
    mode = parseMode(mode),
    status = RecordingStatus.parse(status),
    transcript = transcript,
    cleanedText = cleanedText,
    draftedText = draftedText,
    sttProvider = sttProvider,
    llmProvider = llmProvider,
    clippyUsed = clippyUsed,
    clippyNote = clippyNote,
    retryCount = retryCount,
    error = error,
)

/**
 * CRUD + retention over the recordings table. The audio WAV and its DB row are
 * deleted together (matching desktop locked decision #7).
 */
class RecordingRepository(private val dao: RecordingDao) {

    suspend fun newRecording(audioPath: String, mode: DictationMode): String {
        val id = UUID.randomUUID().toString()
        dao.insert(
            RecordingEntity(
                id = id,
                createdAt = System.currentTimeMillis(),
                audioPath = audioPath,
                mode = mode.toRaw(),
                status = RecordingStatus.RECORDING.raw,
            )
        )
        return id
    }

    fun observeRecent(limit: Int = 200): Flow<List<Recording>> =
        dao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    suspend fun get(id: String): Recording? = dao.getById(id)?.toDomain()

    suspend fun setStatus(id: String, status: RecordingStatus) = dao.setStatus(id, status.raw)
    suspend fun setError(id: String, error: String) = dao.setError(id, error)
    suspend fun setDuration(id: String, durationMs: Long) = dao.setDuration(id, durationMs)
    suspend fun setTranscript(id: String, transcript: String, provider: String) =
        dao.setTranscript(id, transcript, provider)
    suspend fun bumpRetry(id: String) = dao.bumpRetry(id)

    suspend fun setAlt(
        id: String,
        kind: AltKind,
        text: String,
        provider: String?,
        used: Boolean,
        note: String?,
    ) = when (kind) {
        AltKind.CLEANED -> dao.setCleaned(id, text, provider, used, note)
        AltKind.DRAFTED -> dao.setDrafted(id, text, provider, used, note)
    }

    suspend fun delete(id: String) {
        dao.getById(id)?.let { runCatching { File(it.audioPath).delete() } }
        dao.delete(id)
    }

    /**
     * Idempotent retry, matching the desktop sibling's `retry_recording`:
     * bump retry_count, clear stale error, flip status back to TRANSCRIBING
     * so the row reads as "in flight" again, then enqueue the worker. Safe
     * to call regardless of current status (covers stranded rows the
     * recoverStranded sweep already moved into ERROR plus the rarer case
     * where a worker is genuinely still running — Worker queue is unique
     * per recording id, so the duplicate is dropped).
     */
    suspend fun retry(context: Context, id: String) {
        dao.bumpRetry(id)
        dao.clearError(id)
        dao.setStatus(id, RecordingStatus.TRANSCRIBING.raw)
        TranscribeWorker.enqueue(context, id)
    }

    /**
     * Bulk-delete the given rows AND their WAV files on disk. The user's
     * mental model is "delete = it's gone everywhere"; if we left the WAVs
     * behind, storage would silently bloat past the retention cap.
     */
    suspend fun deleteMany(ids: List<String>) {
        if (ids.isEmpty()) return
        for (row in dao.getPathsForIds(ids)) {
            runCatching { File(row.audioPath).delete() }
        }
        dao.deleteIds(ids)
    }

    /** Clear everything — DB rows AND the WAV files behind them. */
    suspend fun deleteAll() {
        for (row in dao.listAll()) {
            runCatching { File(row.audioPath).delete() }
        }
        dao.deleteAll()
    }

    suspend fun recoverStranded(): Int = dao.recoverStranded()

    /**
     * Retention sweep: drop rows (and their WAVs) older than [retentionDays],
     * then FIFO-evict oldest until total audio bytes are under [maxMb].
     */
    suspend fun enforceRetention(retentionDays: Int, maxMb: Int) {
        if (retentionDays > 0) {
            val cutoff = System.currentTimeMillis() - retentionDays.toLong() * 24 * 60 * 60 * 1000
            for (row in dao.listPurgeable(cutoff)) {
                runCatching { File(row.audioPath).delete() }
                dao.delete(row.id)
            }
        }
        val cap = maxMb.toLong() * 1024 * 1024
        if (cap > 0) {
            val rows = dao.listAllAscending() // oldest first
            var total = rows.sumOf { runCatching { File(it.audioPath).length() }.getOrDefault(0L) }
            for (row in rows) {
                if (total <= cap) break
                val size = runCatching { File(row.audioPath).length() }.getOrDefault(0L)
                runCatching { File(row.audioPath).delete() }
                dao.delete(row.id)
                total -= size
            }
        }
    }
}
