package com.wisprfox.android.history

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        RecordingEntity::class,
        UsageBucketEntity::class,
        SyncMetaEntity::class,
        SyncExclusionEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
    abstract fun usageBucketDao(): UsageBucketDao
    abstract fun syncMetaDao(): SyncMetaDao
    abstract fun syncExclusionDao(): SyncExclusionDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "wisprfox_history.db",
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
                .also { instance = it }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recordings ADD COLUMN target_package TEXT")
            }
        }

        /**
         * P-2 usage tracking: add the per-day per-model usage buckets table.
         * Composite PK (day, stage, provider, model) matches the entity so the
         * increment upsert (seed-then-bump) targets exactly one row.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `usage_buckets` (" +
                        "`day` TEXT NOT NULL, " +
                        "`stage` TEXT NOT NULL, " +
                        "`provider` TEXT NOT NULL, " +
                        "`model` TEXT NOT NULL, " +
                        "`calls` INTEGER NOT NULL, " +
                        "`audio_seconds` REAL NOT NULL, " +
                        "`input_tokens` INTEGER NOT NULL, " +
                        "`output_tokens` INTEGER NOT NULL, " +
                        "`total_tokens` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`day`, `stage`, `provider`, `model`))"
                )
            }
        }

        /**
         * Audio-file import (v1.4): per-recording provider/model overrides so an
         * imported file can be transcribed/cleaned with models chosen on the
         * import sheet without changing the user's live-dictation defaults, plus
         * an `imported` flag. All nullable/defaulted — existing rows migrate to
         * the live-dictation behaviour (overrides null, imported = 0).
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recordings ADD COLUMN stt_provider_override TEXT")
                db.execSQL("ALTER TABLE recordings ADD COLUMN stt_model_override TEXT")
                db.execSQL("ALTER TABLE recordings ADD COLUMN llm_provider_override TEXT")
                db.execSQL("ALTER TABLE recordings ADD COLUMN llm_model_override TEXT")
                db.execSQL("ALTER TABLE recordings ADD COLUMN imported INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Accounts + cross-device sync (v2.0): per-row sync bookkeeping on
         * `recordings` (platform/device label, dirty/remote flags, an LWW
         * `updated_at` clock defaulted to the existing `created_at` so old rows
         * don't all collide at the same timestamp), plus two small new tables —
         * `sync_meta` (generic KV: pull cursor, per-key push timestamps) and
         * `sync_exclusions` (ids deleted "this device only" while signed in, so
         * a pull doesn't resurrect them). All nullable/defaulted/off — existing
         * rows migrate to signed-out-equivalent behaviour (platform null reads
         * as "this device" in the UI, dirty/remote both 0).
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recordings ADD COLUMN platform TEXT")
                db.execSQL("ALTER TABLE recordings ADD COLUMN device_name TEXT")
                db.execSQL("ALTER TABLE recordings ADD COLUMN dirty INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE recordings ADD COLUMN remote INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE recordings ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
                // SQLite can't reference another column in ADD COLUMN's DEFAULT,
                // so backfill updated_at = created_at for existing rows in a
                // second pass.
                db.execSQL("UPDATE recordings SET updated_at = created_at")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sync_meta` (" +
                        "`key` TEXT NOT NULL, " +
                        "`value` TEXT NOT NULL, " +
                        "PRIMARY KEY(`key`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sync_exclusions` (" +
                        "`id` TEXT NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
            }
        }
    }
}
