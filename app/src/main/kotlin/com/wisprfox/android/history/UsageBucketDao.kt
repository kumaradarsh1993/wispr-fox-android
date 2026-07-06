package com.wisprfox.android.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Read + increment access to [UsageBucketEntity]. Increments use an
 * insert-then-update pattern inside a single transaction: seed a zero row if
 * absent (IGNORE on conflict), then bump the counters. This keeps the tally
 * atomic and avoids a read-modify-write race when two recordings for the same
 * day/model finish close together.
 */
@Dao
interface UsageBucketDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun seed(bucket: UsageBucketEntity)

    @Query(
        "UPDATE usage_buckets SET calls = calls + :calls, audio_seconds = audio_seconds + :audioSeconds " +
            "WHERE day = :day AND stage = :stage AND provider = :provider AND model = :model"
    )
    suspend fun bumpStt(day: String, stage: String, provider: String, model: String, calls: Long, audioSeconds: Double)

    @Query(
        "UPDATE usage_buckets SET calls = calls + :calls, input_tokens = input_tokens + :inTok, " +
            "output_tokens = output_tokens + :outTok, total_tokens = total_tokens + :totTok " +
            "WHERE day = :day AND stage = :stage AND provider = :provider AND model = :model"
    )
    suspend fun bumpLlm(day: String, stage: String, provider: String, model: String, calls: Long, inTok: Long, outTok: Long, totTok: Long)

    @Transaction
    suspend fun addStt(day: String, provider: String, model: String, calls: Long, audioSeconds: Double) {
        seed(UsageBucketEntity(day, UsageStage.STT, provider, model))
        bumpStt(day, UsageStage.STT, provider, model, calls, audioSeconds)
    }

    @Transaction
    suspend fun addLlm(day: String, provider: String, model: String, calls: Long, inTok: Long, outTok: Long, totTok: Long) {
        seed(UsageBucketEntity(day, UsageStage.LLM, provider, model))
        bumpLlm(day, UsageStage.LLM, provider, model, calls, inTok, outTok, totTok)
    }

    /** Today's buckets (all stages/providers/models) — the UI resolves the row it wants. */
    @Query("SELECT * FROM usage_buckets WHERE day = :day")
    fun observeForDay(day: String): Flow<List<UsageBucketEntity>>

    @Query("SELECT * FROM usage_buckets WHERE day = :day AND stage = :stage AND provider = :provider AND model = :model")
    suspend fun get(day: String, stage: String, provider: String, model: String): UsageBucketEntity?
}
