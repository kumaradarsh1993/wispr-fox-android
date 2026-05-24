package com.wisprfox.android

import android.app.Application
import android.content.Context
import com.wisprfox.android.core.AppContainer
import kotlinx.coroutines.launch

/**
 * Application entry. Keep [onCreate] light — no network, no DB reads on the
 * main thread (cold-launch budget). The only work is constructing the lazy
 * [AppContainer] and kicking a background pass to fail any recordings that
 * were stranded mid-pipeline by a previous process death.
 */
class WisprFoxApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.applicationScope.launch {
            runCatching { container.recordings.recoverStranded() }
        }
    }

    companion object {
        fun container(context: Context): AppContainer =
            (context.applicationContext as WisprFoxApp).container
    }
}
