package com.wisprfox.android.core

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.wisprfox.android.delivery.DeliveryManager
import com.wisprfox.android.history.AppDatabase
import com.wisprfox.android.history.RecordingRepository
import com.wisprfox.android.history.UsageRepository
import com.wisprfox.android.settings.AppSettings
import com.wisprfox.android.settings.SecureKeyStore
import com.wisprfox.android.settings.SettingsStore
import com.wisprfox.android.sync.AuthManager
import com.wisprfox.android.sync.SyncEngine
import com.wisprfox.android.sync.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File

/**
 * Manual dependency container (no Hilt — locked decision). Everything is lazy
 * so `Application.onCreate` stays cheap (cold-launch budget). Reached via
 * [com.wisprfox.android.WisprFoxApp.container].
 */
class AppContainer(context: Context) {

    private val app: Context = context.applicationContext

    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        // Track whether any activity is in the foreground so delivery can decide
        // whether a clipboard-only outcome needs a notification (RC-2.4). A
        // simple resume/pause counter is enough — we don't need per-activity
        // detail, just "is the user looking at us right now".
        (app as? Application)?.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                private var resumed = 0
                override fun onActivityResumed(activity: Activity) {
                    resumed++
                    AppState.setAppForeground(true)
                }
                override fun onActivityPaused(activity: Activity) {
                    resumed = (resumed - 1).coerceAtLeast(0)
                    if (resumed == 0) AppState.setAppForeground(false)
                }
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
                override fun onActivityStarted(activity: Activity) {}
                override fun onActivityStopped(activity: Activity) {}
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
                override fun onActivityDestroyed(activity: Activity) {}
            }
        )
    }

    val http: OkHttpClient by lazy { OkHttpClient.Builder().build() }
    val secrets: SecureKeyStore by lazy { SecureKeyStore(app) }
    val settingsStore: SettingsStore by lazy { SettingsStore(app) }
    val database: AppDatabase by lazy { AppDatabase.get(app) }
    // P-2 usage tracking (per-day per-model buckets + Deepgram lifetime spend).
    val usage: UsageRepository by lazy { UsageRepository(app, database.usageBucketDao()) }
    // Accounts + cross-device sync (v2.0). authManager/syncEngine are inert
    // (never make a network call, never crash) when SupabaseConfig isn't
    // baked with real values — see SupabaseConfig.isConfigured().
    val authManager: AuthManager by lazy { AuthManager(http, secrets) }
    val recordings: RecordingRepository by lazy {
        RecordingRepository(
            dao = database.recordingDao(),
            usage = usage,
            // Read the active models at tally time so STT/LLM buckets are keyed
            // by the model actually in use, without the worker having to plumb it.
            activeModels = {
                val s = settingsStore.settings.first()
                RecordingRepository.ActiveModels(
                    sttProvider = s.sttProvider,
                    sttModel = s.sttModel,
                    llmProvider = s.llmProvider,
                    llmModel = s.activeLlmModel,
                )
            },
            // `syncEngine` is a `by lazy` property below referencing `recordings`
            // right back — safe because neither is evaluated until first
            // accessed, and nothing here touches `syncEngine` until a delete
            // actually needs to tombstone (same trick as `activeModels` above).
            tombstoneRemote = { ids -> syncEngine.tombstoneNotes(ids) },
        )
    }
    val syncEngine: SyncEngine by lazy {
        SyncEngine(
            baseClient = http,
            authManager = authManager,
            settingsStore = settingsStore,
            recordings = recordings,
            syncMetaDao = database.syncMetaDao(),
            secrets = secrets,
        )
    }
    val providerFactory: ProviderFactory by lazy { ProviderFactory(http, secrets) }
    val delivery: DeliveryManager by lazy { DeliveryManager(app) }
    val controller: RecordingController by lazy {
        RecordingController(app, recordings, settingsStore, applicationScope)
    }
    val importController: ImportController by lazy {
        ImportController(app, recordings, applicationScope)
    }

    fun audioDir(): File = File(app.getExternalFilesDir(null), "audio").apply { mkdirs() }

    suspend fun currentSettings(): AppSettings = settingsStore.settings.first()

    /** Re-arm the periodic background sync job if the user is signed in.
     *  Safe to call on every process start (idempotent via `KEEP`). */
    fun ensureBackgroundSyncScheduled() {
        applicationScope.launch {
            if (authManager.isSignedIn()) SyncWorker.ensurePeriodic(app)
        }
    }
}
