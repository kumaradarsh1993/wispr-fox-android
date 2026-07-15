package com.wisprfox.android.history

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

/**
 * Tiny local KV table (schema v5, sync/accounts) for sync bookkeeping that
 * doesn't belong on any one recording row: the notes-pull cursor, per-key
 * "when did we last push this API key" timestamps (so [RecordingRepository]
 * key sync doesn't need its own table), and anything else sync needs to
 * remember between runs. Deliberately generic — a single `key`/`value` pair
 * per row — so adding a new bit of sync state never needs another migration.
 */
@Entity(tableName = "sync_meta")
data class SyncMetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)

object SyncMetaKeys {
    /** Max `updated_at` (epoch millis, as a string) seen across pulled notes. */
    const val NOTES_CURSOR = "notes_pull_cursor"
    fun keyPushedAt(providerKeyName: String) = "keypush_$providerKeyName"
}

@Dao
interface SyncMetaDao {
    @Upsert
    suspend fun set(row: SyncMetaEntity)

    @Query("SELECT value FROM sync_meta WHERE `key` = :key")
    suspend fun get(key: String): String?

    suspend fun getLong(key: String): Long? = get(key)?.toLongOrNull()

    suspend fun setLong(key: String, value: Long) = set(SyncMetaEntity(key, value.toString()))
}
