package com.wisprfox.android.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.wisprfox.android.MainActivity
import com.wisprfox.android.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs

/**
 * One status line per in-flight recording ("Preparing audio 40%", "Uploading
 * 3.2 of 7.0 MB", "Transcribing part 2 of 3", "Writing notes…"), shown in
 * three places at once: the live [AppState] message (when that recording owns
 * the live state), an ongoing progress notification, and the History /
 * Conversations row via [lines].
 */
object PipelineProgress {

    data class Line(val text: String, val percent: Int? = null)

    private val _lines = MutableStateFlow<Map<String, Line>>(emptyMap())
    val lines: StateFlow<Map<String, Line>> = _lines.asStateFlow()

    private const val CHANNEL_ID = "progress"
    private const val NOTIF_BASE = 3000

    fun report(context: Context, recordingId: String, text: String, percent: Int? = null, conversation: Boolean = false) {
        val line = Line(text, percent?.coerceIn(0, 100))
        if (_lines.value[recordingId] == line) return
        _lines.update { it + (recordingId to line) }
        if (AppState.state.value.activeRecordingId == recordingId) {
            AppState.update { copy(message = text, messageIsError = false) }
        }
        notify(context.applicationContext, recordingId, line, conversation)
    }

    fun clear(context: Context, recordingId: String) {
        _lines.update { it - recordingId }
        val nm = context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { nm.cancel(notificationId(recordingId)) }
    }

    private fun notificationId(recordingId: String) = NOTIF_BASE + abs(recordingId.hashCode() % 1000)

    private fun notify(context: Context, recordingId: String, line: Line, conversation: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Processing progress", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shows how far an uploaded recording has got"
                    setShowBadge(false)
                },
            )
        }
        val open = PendingIntent.getActivity(
            context,
            4,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN, if (conversation) MainActivity.OPEN_CONVERSATIONS else MainActivity.OPEN_HISTORY)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.fox_favicon)
            .setContentTitle(if (conversation) "Processing conversation" else "Processing recording")
            .setContentText(line.text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(100, line.percent ?: 0, line.percent == null)
        runCatching { nm.notify(notificationId(recordingId), builder.build()) }
    }
}
