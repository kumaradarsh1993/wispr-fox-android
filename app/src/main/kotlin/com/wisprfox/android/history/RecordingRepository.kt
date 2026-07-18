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
    val targetPackage: String?,
    val clippyUsed: Boolean,
    val clippyNote: String?,
    val retryCount: Int,
    val error: String?,
    val sttProviderOverride: String? = null,
    val sttModelOverride: String? = null,
    val llmProviderOverride: String? = null,
    val llmModelOverride: String? = null,
    val imported: Boolean = false,
    /** Sync/accounts (schema v5). Null platform means "this device" (mobile). */
    val platform: String? = null,
    val deviceName: String? = null,
    /** True for a row pulled from another device — no local audio exists for
     *  it, so the UI hides play/retry for these. */
    val remote: Boolean = false,
) {
    /** Best text available for the row's own mode (for previews / delivery). */
    fun primaryText(): String? = when (mode) {
        DictationMode.RAW -> transcript
        DictationMode.CLEANED -> cleanedText ?: transcript
        DictationMode.ADVANCED -> cleanedText ?: transcript
        DictationMode.REFORMATTED -> draftedText ?: transcript
    }

    /** Badge label for [platform] — local rows (null) read as "Mobile". */
    fun platformLabel(): String = when (platform) {
        "desktop" -> "Desktop"
        "web" -> "Web"
        else -> "Mobile"
    }
}

/**
 * A pulled `notes` row, already time-parsed to epoch millis by
 * [com.wisprfox.android.sync.SyncEngine] (kept out of the `history` package so
 * it has no dependency on `sync`'s wire types). `deletedAtMillis != null` means
 * this is a tombstone.
 */
data class RemotePulledNote(
    val id: String,
    val platform: String,
    val origin: String,
    val title: String?,
    val transcript: String?,
    val cleanedText: String?,
    val draftedText: String?,
    val durationMs: Long,
    val sttProvider: String?,
    val llmProvider: String?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val deletedAtMillis: Long?,
)

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
    targetPackage = targetPackage,
    clippyUsed = clippyUsed,
    clippyNote = clippyNote,
    retryCount = retryCount,
    error = error,
    sttProviderOverride = sttProviderOverride,
    sttModelOverride = sttModelOverride,
    llmProviderOverride = llmProviderOverride,
    llmModelOverride = llmModelOverride,
    imported = imported,
    platform = platform,
    deviceName = deviceName,
    remote = remote,
)

/**
 * CRUD + retention over the recordings table. The audio WAV and its DB row are
 * deleted together (matching desktop locked decision #7).
 *
 * P-2 usage tracking is hooked HERE (not in the worker — that's Agent A's
 * scope) via [usage] + [activeModels]. The worker already calls
 * [setTranscript] on STT success and [setAlt] on LLM success; those methods now
 * also tally a usage bucket. Both hooks are optional (nullable) so the
 * repository still constructs in contexts that don't wire usage.
 */
class RecordingRepository(
    private val dao: RecordingDao,
    private val usage: UsageRepository? = null,
    /**
     * Supplies the active (sttProvider, sttModel, llmProvider, llmModel) for
     * tallying. The worker doesn't pass the model to setTranscript/setAlt (and
     * we can't edit the worker), so we read it from settings at tally time.
     * Returns null when settings aren't available (usage tally is skipped).
     */
    private val activeModels: (suspend () -> ActiveModels?)? = null,
    /** Best-effort cloud tombstone (set `deleted_at` + null out the text
     *  columns), invoked BEFORE the local rows are removed. Deleting an
     *  owned row is always "delete everywhere" now (SYNC_DESIGN.md
     *  ownership-scoped delete): the tombstone propagates the deletion to every
     *  other signed-in device on their next pull. Supplied by
     *  [com.wisprfox.android.core.AppContainer] as a lambda into
     *  [com.wisprfox.android.sync.SyncEngine] so this package doesn't need a
     *  compile-time dependency on `sync`. Null in contexts that don't wire sync
     *  (e.g. tests) — every use is guarded so the repository still works as pure
     *  local storage without it. */
    private val tombstoneRemote: (suspend (List<String>) -> Unit)? = null,
) {

    data class ActiveModels(
        val sttProvider: String,
        val sttModel: String,
        val llmProvider: String,
        val llmModel: String,
    )

    suspend fun newRecording(audioPath: String, mode: DictationMode, targetPackage: String?): String {
        val id = UUID.randomUUID().toString()
        dao.insert(
            RecordingEntity(
                id = id,
                createdAt = System.currentTimeMillis(),
                audioPath = audioPath,
                mode = mode.toRaw(),
                status = RecordingStatus.RECORDING.raw,
                targetPackage = targetPackage,
            )
        )
        return id
    }

    /**
     * Create a row for an imported audio file. The [audioPath] is the WAV target
     * the [com.wisprfox.android.queue.ImportWorker] will decode into; the row
     * starts in [RecordingStatus.IMPORTING] so it shows in History as "importing"
     * while the decode runs. The chosen models are stored as per-recording
     * overrides so the worker uses them without touching global settings.
     */
    suspend fun newImportRecording(
        audioPath: String,
        mode: DictationMode,
        sttProvider: String,
        sttModel: String,
        llmProvider: String,
        llmModel: String,
    ): String {
        val id = UUID.randomUUID().toString()
        dao.insert(
            RecordingEntity(
                id = id,
                createdAt = System.currentTimeMillis(),
                audioPath = audioPath,
                mode = mode.toRaw(),
                status = RecordingStatus.IMPORTING.raw,
                targetPackage = null,
                sttProviderOverride = sttProvider,
                sttModelOverride = sttModel,
                llmProviderOverride = llmProvider,
                llmModelOverride = llmModel,
                imported = true,
            )
        )
        return id
    }

    fun observeRecent(limit: Int = 200): Flow<List<Recording>> =
        dao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    suspend fun get(id: String): Recording? = dao.getById(id)?.toDomain()

    suspend fun setStatus(id: String, status: RecordingStatus) {
        dao.setStatus(id, status.raw)
        // Sync/accounts: a row becomes push-worthy the moment it lands DONE
        // (push itself additionally filters status='done', so this is safe
        // even if the row later errors out and gets retried — it'll just be
        // re-marked dirty when it reaches DONE again).
        if (status == RecordingStatus.DONE) dao.markDirty(id, System.currentTimeMillis())
    }
    suspend fun setError(id: String, error: String) = dao.setError(id, error)
    suspend fun setDuration(id: String, durationMs: Long) = dao.setDuration(id, durationMs)
    suspend fun setTranscript(id: String, transcript: String, provider: String) {
        dao.setTranscript(id, transcript, provider)
        dao.markDirty(id, System.currentTimeMillis())
        tallyStt(id, provider)
    }
    suspend fun bumpRetry(id: String) = dao.bumpRetry(id)

    suspend fun setAlt(
        id: String,
        kind: AltKind,
        text: String,
        provider: String?,
        used: Boolean,
        note: String?,
    ) {
        when (kind) {
            AltKind.CLEANED -> dao.setCleaned(id, text, provider, used, note)
            AltKind.DRAFTED -> dao.setDrafted(id, text, provider, used, note)
        }
        dao.markDirty(id, System.currentTimeMillis())
        // Only a real LLM call counts; a graceful degrade to raw (missing key /
        // provider down) sets used=false and must NOT inflate the usage meter.
        if (used) tallyLlm(provider)
    }

    /**
     * P-2: tally an STT bucket for a successful transcription. Audio seconds
     * come from the row's stored duration (the worker already set it before
     * enqueuing). The model isn't passed to setTranscript (we can't touch the
     * worker), so we read the active STT model from settings; the provider from
     * the tally must match settings for the model to be meaningful — if the user
     * switched providers mid-flight we still record under the passed provider so
     * the count is never lost.
     */
    private suspend fun tallyStt(id: String, provider: String) {
        val usage = usage ?: return
        val models = runCatching { activeModels?.invoke() }.getOrNull() ?: return
        val durationMs = runCatching { dao.getById(id)?.durationMs ?: 0L }.getOrDefault(0L)
        val audioSeconds = durationMs / 1000.0
        val model = if (provider == models.sttProvider) models.sttModel else ""
        runCatching { usage.recordStt(provider, model, audioSeconds, System.currentTimeMillis()) }
    }

    /**
     * P-2: tally an LLM bucket for a successful cleanup/draft. TODO(token
     * plumbing): the LlmProvider.complete() contract returns only text, so we
     * can't record input/output tokens without touching the provider clients
     * AND the worker's clean() call — both out of scope for this batch. Calls
     * are tallied now (matching the desktop call-count fallback); token columns
     * stay 0 and can be populated later with no migration.
     */
    private suspend fun tallyLlm(provider: String?) {
        val usage = usage ?: return
        val prov = provider ?: return
        val models = runCatching { activeModels?.invoke() }.getOrNull() ?: return
        val model = if (prov == models.llmProvider) models.llmModel else ""
        runCatching { usage.recordLlm(prov, model, inTok = 0, outTok = 0, totTok = 0, nowMillis = System.currentTimeMillis()) }
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

    // ─── Sync (schema v5) ────────────────────────────────────────────────────

    /** First sign-in: every already-finished row is worth pushing once. */
    suspend fun markAllDoneDirtyForInitialSync() = dao.markAllDoneDirty()

    /** Rows [SyncEngine] should push this round. */
    suspend fun dirtyDoneRows(): List<RecordingEntity> = dao.listDirtyDone()

    suspend fun clearDirty(ids: List<String>) = dao.clearDirty(ids)

    /** Stamp a freshly-created local row with this device's label so other
     *  clients' History shows where it came from once it syncs up. */
    suspend fun labelAsThisDevice(id: String, deviceName: String?) =
        dao.setDeviceLabel(id, "mobile", deviceName)

    /**
     * Apply one pulled `notes` row, last-write-wins by `updated_at`: a
     * tombstone always wins (deletes the local row + its WAV, same as a local
     * delete); otherwise the pulled row only overwrites local state if it's
     * strictly newer, so a local edit racing a pull never gets clobbered by a
     * stale remote copy.
     *
     * Cloud rows have no local WAV (audio never syncs), so [audioPath] is left
     * empty — HistoryScreen checks [Recording.remote] to hide play/retry.
     * The cloud `notes` schema has no `mode` column, so it's inferred from
     * which text column is populated (documented deviation — see HANDOVER).
     */
    suspend fun applyRemoteNote(note: RemotePulledNote) {
        if (note.deletedAtMillis != null) {
            applyTombstone(note.id)
            return
        }
        val localUpdatedAt = dao.getUpdatedAt(note.id)
        if (localUpdatedAt != null && localUpdatedAt >= note.updatedAtMillis) return

        val mode = when {
            !note.draftedText.isNullOrBlank() -> DictationMode.REFORMATTED
            !note.cleanedText.isNullOrBlank() -> DictationMode.CLEANED
            else -> DictationMode.RAW
        }
        dao.insert(
            RecordingEntity(
                id = note.id,
                createdAt = note.createdAtMillis,
                audioPath = "",
                durationMs = note.durationMs,
                mode = mode.toRaw(),
                status = RecordingStatus.DONE.raw,
                transcript = note.transcript,
                cleanedText = note.cleanedText,
                draftedText = note.draftedText,
                sttProvider = note.sttProvider,
                llmProvider = note.llmProvider,
                imported = note.origin == "upload",
                platform = note.platform,
                deviceName = null,
                dirty = false,
                remote = true,
                updatedAt = note.updatedAtMillis,
            )
        )
    }

    /** A row is gone everywhere: remove it locally (row + WAV, same effect as
     *  a local delete) so this client converges with the tombstone. */
    suspend fun applyTombstone(id: String) = delete(id)

    /**
     * Ownership-scoped delete (SYNC_DESIGN.md "Delete — ownership-scoped").
     * A client may delete only the rows it originated, and doing so always
     * means "delete everywhere": tombstone the cloud copy FIRST (via
     * [tombstoneRemote]) so every other signed-in device drops it on next pull,
     * then remove the local row + its WAV. There is no this-device-only delete
     * and no audio-only delete any more — a transcript and its recording die
     * together.
     *
     * [ids] is filtered down to rows THIS device owns ([RecordingDao.ownedAmong],
     * `remote = 0`) — a `remote` row that slips in (it shouldn't; the UI hides
     * the affordance) is silently skipped, never tombstoned, so a client can
     * never delete another device's history. A row whose WAV is already gone
     * deletes quietly (the file delete is best-effort). Signed-out / BYOK mode:
     * [tombstoneRemote] is inert without a session, so this is a pure local
     * delete, exactly as before accounts existed.
     */
    suspend fun deleteOwned(ids: List<String>) {
        if (ids.isEmpty()) return
        val owned = dao.ownedAmong(ids)
        if (owned.isEmpty()) return
        runCatching { tombstoneRemote?.invoke(owned) }
        for (row in dao.getPathsForIds(owned)) {
            runCatching { File(row.audioPath).delete() }
        }
        dao.deleteIds(owned)
    }

    /** "Delete all" = only THIS device's rows (SYNC_DESIGN.md): other devices'
     *  transcripts survive locally and on the server. */
    suspend fun deleteAllOwned() = deleteOwned(dao.listOwnedIds())
}
