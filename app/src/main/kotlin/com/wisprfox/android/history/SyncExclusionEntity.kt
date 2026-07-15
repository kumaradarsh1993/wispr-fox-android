package com.wisprfox.android.history

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Recording ids deleted "this device only" while signed in (schema v5,
 * sync/accounts — `../wispr-fox-web/docs/SYNC_DESIGN.md` "Delete rework").
 * The cloud copy (and other devices' copies) survive; this table stops the
 * next pull from resurrecting the row locally. Deliberately never pruned
 * automatically — an id only leaves this table if the user later deletes it
 * "everywhere" (the tombstone path removes the exclusion's reason to exist,
 * but leaving a stale id here is harmless, just a few bytes).
 */
@Entity(tableName = "sync_exclusions")
data class SyncExclusionEntity(@PrimaryKey val id: String)

@Dao
interface SyncExclusionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(row: SyncExclusionEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM sync_exclusions WHERE id = :id)")
    suspend fun exists(id: String): Boolean

    suspend fun insertAll(ids: List<String>) {
        for (id in ids) insert(SyncExclusionEntity(id))
    }
}
