package com.wisprfox.android.history

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [RecordingEntity::class, UsageBucketEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
    abstract fun usageBucketDao(): UsageBucketDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "wisprfox_history.db",
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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
    }
}
