package com.wisprfox.android.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.wisprfox.android.WisprFoxApp
import java.util.concurrent.TimeUnit

/**
 * Background durable sync job. Two shapes share this one worker class:
 *  - a **periodic** job (~15 minutes — WorkManager's floor for
 *    `PeriodicWorkRequest`, matching the spec's "the minimum") that keeps
 *    syncing even when the app isn't open;
 *  - **one-time** jobs enqueued right after a recording/import finishes, and
 *    on demand from "Sync now" in Settings.
 *
 * `doWork()` itself is a thin, always-safe wrapper: [SyncEngine.syncNow] is
 * already inert when unconfigured/signed-out, so this worker never needs to
 * check that itself, and it never fails the WorkManager job on a sync error —
 * sync is best-effort background work, not something that should show up as a
 * retry-storm in `adb shell dumpsys jobscheduler`.
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = WisprFoxApp.container(applicationContext)
        val initial = inputData.getBoolean(KEY_INITIAL, false)
        container.syncEngine.syncNow(initial = initial)
        return Result.success()
    }

    companion object {
        private const val KEY_INITIAL = "initial"
        private const val PERIODIC_NAME = "sync-periodic"
        private const val ONE_TIME_NAME = "sync-once"

        /** Fire-and-forget one-off sync — after a recording completes, on app
         *  foreground, from the 60s in-app ticker, or "Sync now" in Settings.
         *  `KEEP` collapses bursts of near-simultaneous triggers into one run. */
        fun enqueueOnce(context: Context, initial: Boolean = false) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setInputData(workDataOf(KEY_INITIAL to initial))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(ONE_TIME_NAME, ExistingWorkPolicy.KEEP, request)
        }

        /** Call on sign-in and on every app start while signed in; a no-op
         *  (via `KEEP`) if the periodic job is already scheduled. */
        fun ensurePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /** Call on sign-out so a stray periodic tick doesn't run against a
         *  cleared session (it would just no-op anyway since [SyncEngine] checks
         *  [AuthManager.isSignedIn], but there's no reason to keep it scheduled). */
        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_NAME)
        }
    }
}
