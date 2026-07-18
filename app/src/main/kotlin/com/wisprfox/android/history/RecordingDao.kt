package com.wisprfox.android.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rec: RecordingEntity)

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getById(id: String): RecordingEntity?

    @Query("SELECT * FROM recordings ORDER BY created_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings ORDER BY created_at DESC LIMIT :limit")
    suspend fun listRecent(limit: Int): List<RecordingEntity>

    @Query("UPDATE recordings SET status = :status, error = NULL WHERE id = :id")
    suspend fun setStatus(id: String, status: String)

    @Query("UPDATE recordings SET status = 'error', error = :error WHERE id = :id")
    suspend fun setError(id: String, error: String)

    @Query("UPDATE recordings SET duration_ms = :durationMs WHERE id = :id")
    suspend fun setDuration(id: String, durationMs: Long)

    @Query("UPDATE recordings SET transcript = :transcript, stt_provider = :provider WHERE id = :id")
    suspend fun setTranscript(id: String, transcript: String, provider: String)

    @Query(
        "UPDATE recordings SET cleaned_text = :text, llm_provider = :provider, " +
            "clippy_used = :used, clippy_note = :note WHERE id = :id"
    )
    suspend fun setCleaned(id: String, text: String, provider: String?, used: Boolean, note: String?)

    @Query(
        "UPDATE recordings SET drafted_text = :text, llm_provider = :provider, " +
            "clippy_used = :used, clippy_note = :note WHERE id = :id"
    )
    suspend fun setDrafted(id: String, text: String, provider: String?, used: Boolean, note: String?)

    @Query("UPDATE recordings SET retry_count = retry_count + 1 WHERE id = :id")
    suspend fun bumpRetry(id: String)

    @Query("UPDATE recordings SET error = NULL WHERE id = :id")
    suspend fun clearError(id: String)

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun delete(id: String)

    /** All audio paths — for bulk-delete to wipe the WAVs from disk first. */
    @Query("SELECT id, audio_path, created_at FROM recordings")
    suspend fun listAll(): List<PurgeRow>

    /** Bulk-delete by id (rows only; caller is responsible for the WAVs). */
    @Query("DELETE FROM recordings WHERE id IN (:ids)")
    suspend fun deleteIds(ids: List<String>)

    @Query("DELETE FROM recordings")
    suspend fun deleteAll()

    @Query("SELECT id, audio_path, created_at FROM recordings WHERE id IN (:ids)")
    suspend fun getPathsForIds(ids: List<String>): List<PurgeRow>

    @Query("SELECT id, audio_path, created_at FROM recordings WHERE created_at < :olderThan")
    suspend fun listPurgeable(olderThan: Long): List<PurgeRow>

    @Query("SELECT id, audio_path, created_at FROM recordings ORDER BY created_at ASC")
    suspend fun listAllAscending(): List<PurgeRow>

    /**
     * On launch, fail any rows stranded mid-pipeline (app killed during
     * transcribe/clean/inject). Lets the History UI surface them with a Retry.
     */
    @Query(
        "UPDATE recordings SET status = 'error', " +
            "error = COALESCE(error, 'Interrupted — app exited before this recording finished. Tap Retry to resume.') " +
            "WHERE status IN ('recording','transcribing','cleaning','injecting')"
    )
    suspend fun recoverStranded(): Int

    // ─── Sync (schema v5) ────────────────────────────────────────────────────

    /** Mark a row's synced fields changed. Push only picks up dirty+done rows,
     *  so marking this on any text mutation (regardless of current status) is
     *  harmless — it just sits until the row reaches DONE. */
    @Query("UPDATE recordings SET dirty = 1, updated_at = :now WHERE id = :id")
    suspend fun markDirty(id: String, now: Long)

    /** First-sign-in bootstrap: every already-finished row becomes push-worthy. */
    @Query("UPDATE recordings SET dirty = 1 WHERE status = 'done'")
    suspend fun markAllDoneDirty()

    @Query("UPDATE recordings SET dirty = 0 WHERE id IN (:ids)")
    suspend fun clearDirty(ids: List<String>)

    /** Rows to push: locally changed, terminal status, not a read-only row
     *  pulled from another device. */
    @Query("SELECT * FROM recordings WHERE dirty = 1 AND status = 'done' AND remote = 0")
    suspend fun listDirtyDone(): List<RecordingEntity>

    @Query("SELECT updated_at FROM recordings WHERE id = :id")
    suspend fun getUpdatedAt(id: String): Long?

    @Query("UPDATE recordings SET platform = :platform, device_name = :deviceName WHERE id = :id")
    suspend fun setDeviceLabel(id: String, platform: String, deviceName: String?)

    // ─── Ownership-scoped delete (SYNC_DESIGN.md "Delete — ownership-scoped") ──
    // A client may only delete rows it originated. On Android "originated here"
    // is exactly `remote = 0`: a pulled row is stamped remote=1 by
    // RecordingRepository.applyRemoteNote and never flips back, while every
    // locally-recorded/imported row stays remote=0. These filter to that set so
    // the policy is enforced at the DB, not just hidden in the UI.

    /** Ids of rows THIS device originated — the only ones it may delete. */
    @Query("SELECT id FROM recordings WHERE remote = 0")
    suspend fun listOwnedIds(): List<String>

    /** Of [ids], just the ones THIS device originated (drops others' rows). */
    @Query("SELECT id FROM recordings WHERE id IN (:ids) AND remote = 0")
    suspend fun ownedAmong(ids: List<String>): List<String>
}

data class PurgeRow(
    val id: String,
    @androidx.room.ColumnInfo(name = "audio_path") val audioPath: String,
    @androidx.room.ColumnInfo(name = "created_at") val createdAt: Long = 0,
)
