package com.wisprfox.android.audio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.wisprfox.android.MainActivity
import com.wisprfox.android.WisprFoxApp
import com.wisprfox.android.core.AppState
import com.wisprfox.android.core.PipelineState
import com.wisprfox.android.history.RecordingStatus
import com.wisprfox.android.provider.DictationMode
import com.wisprfox.android.queue.TranscribeWorker
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Foreground service that holds the mic for the duration of a recording
 * (foregroundServiceType="microphone"). Started/stopped by
 * [com.wisprfox.android.core.RecordingController] with the target WAV path +
 * recording id as extras.
 *
 * On STOP it finalises the WAV, writes the duration, discards too-short clips,
 * and enqueues the durable [TranscribeWorker] — so the finalise→enqueue
 * ordering can never race the way it would if the controller did it across the
 * intent boundary.
 */
class RecordingService : Service() {

    private var recorder: AudioRecorder? = null
    private var outputPath: String? = null
    private var recordingId: String? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording(
                path = intent.getStringExtra(EXTRA_PATH),
                id = intent.getStringExtra(EXTRA_ID),
            )
            ACTION_STOP -> {
                stopRecording()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startRecording(path: String?, id: String?) {
        if (recorder?.isRunning() == true) {
            Log.w(TAG, "startRecording called while already running")
            return
        }
        if (path == null) {
            Log.e(TAG, "startRecording with null path")
            stopSelf()
            return
        }
        outputPath = path
        recordingId = id

        ensureChannel()
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIF_ID, notification)
            }
        } catch (e: Exception) {
            failStartup("Could not start protected recording - open wispr-fox and try again.", e)
            return
        }

        val outFile = File(path).apply { parentFile?.mkdirs() }
        Log.i(TAG, "starting recording → ${outFile.absolutePath}")

        acquireWakeLock()
        try {
            recorder = AudioRecorder(outFile) { bytes, elapsed ->
                AppState.setMetrics(elapsed, bytes)
            }.also { it.start() }
        } catch (e: Exception) {
            failStartup("Microphone could not start - check permission and try again.", e)
        }
    }

    private fun stopRecording() {
        Log.i(TAG, "stopping recording")
        recorder?.stop()
        recorder = null
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)

        val path = outputPath
        val id = recordingId
        outputPath = null
        recordingId = null
        if (path == null || id == null) return

        val file = File(path)
        val dataBytes = (file.length() - WAV_HEADER_BYTES).coerceAtLeast(0)
        val durationMs = dataBytes * 1000 / (AudioRecorder.SAMPLE_RATE.toLong() * BYTES_PER_SAMPLE)

        val container = WisprFoxApp.container(applicationContext)
        runBlocking {
            container.recordings.setDuration(id, durationMs)
            if (durationMs < MIN_DURATION_MS) {
                Log.i(TAG, "discarding too-short recording ($durationMs ms)")
                container.recordings.setError(id, "Recording too short")
                runCatching { file.delete() }
                AppState.update {
                    copy(pipeline = PipelineState.IDLE, activeRecordingId = null, targetPackage = null, message = "Too short - hold a moment longer", messageIsError = true)
                }
                return@runBlocking
            }
            container.recordings.setStatus(id, RecordingStatus.TRANSCRIBING)
            AppState.setPipeline(PipelineState.TRANSCRIBING)
            TranscribeWorker.enqueue(applicationContext, id)
        }
    }

    override fun onDestroy() {
        if (recorder?.isRunning() == true) stopRecording()
        releaseWakeLock()
        super.onDestroy()
    }

    private fun failStartup(message: String, error: Exception) {
        Log.e(TAG, message, error)
        val id = recordingId
        val path = outputPath
        outputPath = null
        recordingId = null
        recorder = null
        releaseWakeLock()
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        if (id != null) {
            val container = WisprFoxApp.container(applicationContext)
            runBlocking {
                container.recordings.setError(id, message)
            }
        }
        path?.let { runCatching { File(it).delete() } }
        AppState.update {
            copy(pipeline = PipelineState.ERROR, activeRecordingId = null, targetPackage = null, message = message, messageIsError = true)
        }
        stopSelf()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:recording").apply {
            setReferenceCounted(false)
            acquire(30 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
        wakeLock = null
    }

    private fun ensureChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Recording", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Active dictation recording"
                    setShowBadge(false)
                }
            )
        }
    }

    private fun buildNotification(): android.app.Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopPi = PendingIntent.getService(
            this, 1,
            Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("wispr-fox is recording")
            .setContentText("Tap to open · use Stop to finish")
            .setContentIntent(openApp)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPi)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {
        const val ACTION_START = "com.wisprfox.android.ACTION_START"
        const val ACTION_STOP = "com.wisprfox.android.ACTION_STOP"
        private const val EXTRA_PATH = "extra_path"
        private const val EXTRA_ID = "extra_id"
        private const val EXTRA_MODE = "extra_mode"
        private const val CHANNEL_ID = "recording"
        private const val NOTIF_ID = 1001
        private const val TAG = "wisprfox/RecordingService"

        private const val MIN_DURATION_MS = 300L
        private const val WAV_HEADER_BYTES = 44L
        private const val BYTES_PER_SAMPLE = 2L

        fun startIntent(ctx: Context, path: String, id: String, mode: DictationMode): Intent =
            Intent(ctx, RecordingService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_PATH, path)
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_MODE, mode.name)

        fun stopIntent(ctx: Context): Intent =
            Intent(ctx, RecordingService::class.java).setAction(ACTION_STOP)
    }
}
