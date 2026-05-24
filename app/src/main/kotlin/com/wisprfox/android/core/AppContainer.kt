package com.wisprfox.android.core

import android.content.Context
import com.wisprfox.android.delivery.DeliveryManager
import com.wisprfox.android.history.AppDatabase
import com.wisprfox.android.history.RecordingRepository
import com.wisprfox.android.settings.AppSettings
import com.wisprfox.android.settings.SecureKeyStore
import com.wisprfox.android.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
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

    val http: OkHttpClient by lazy { OkHttpClient.Builder().build() }
    val secrets: SecureKeyStore by lazy { SecureKeyStore(app) }
    val settingsStore: SettingsStore by lazy { SettingsStore(app) }
    val database: AppDatabase by lazy { AppDatabase.get(app) }
    val recordings: RecordingRepository by lazy { RecordingRepository(database.recordingDao()) }
    val providerFactory: ProviderFactory by lazy { ProviderFactory(http, secrets) }
    val delivery: DeliveryManager by lazy { DeliveryManager(app) }
    val controller: RecordingController by lazy {
        RecordingController(app, recordings, settingsStore, applicationScope)
    }

    fun audioDir(): File = File(app.getExternalFilesDir(null), "audio").apply { mkdirs() }

    suspend fun currentSettings(): AppSettings = settingsStore.settings.first()
}
