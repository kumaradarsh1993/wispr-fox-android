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

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun delete(id: String)

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
}

data class PurgeRow(
    val id: String,
    @androidx.room.ColumnInfo(name = "audio_path") val audioPath: String,
    @androidx.room.ColumnInfo(name = "created_at") val createdAt: Long = 0,
)
