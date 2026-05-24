package com.wisprfox.android.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * PCM 16 kHz mono recorder driving a [WavWriter]. Held across foreground
 * service lifetime; do NOT recreate per-segment on Android (unlike the
 * desktop wispr-fox which uses a fresh cpal stream per recording — that
 * pattern triggers OS mic-release here).
 */
class AudioRecorder(
    private val outputFile: File,
    private val onSamples: (totalBytes: Long, elapsedMs: Long) -> Unit = { _, _ -> },
) {

    private val running = AtomicBoolean(false)
    private var recordThread: Thread? = null
    private var startedAt: Long = 0
    @Volatile private var totalBytes: Long = 0

    @SuppressLint("MissingPermission") // caller verifies RECORD_AUDIO
    fun start() {
        if (!running.compareAndSet(false, true)) return

        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        require(minBuf > 0) { "AudioRecord.getMinBufferSize returned $minBuf" }
        val bufferSize = minBuf * BUFFER_MULTIPLIER

        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
        )
        check(record.state == AudioRecord.STATE_INITIALIZED) {
            "AudioRecord failed to initialise (state=${record.state})"
        }

        startedAt = System.currentTimeMillis()
        totalBytes = 0
        recordThread = thread(name = "audio-recorder", isDaemon = true) {
            val writer = WavWriter(outputFile, SAMPLE_RATE)
            try {
                record.startRecording()
                val samples = ShortArray(bufferSize / 2)
                var lastHeartbeat = startedAt
                while (running.get()) {
                    val n = record.read(samples, 0, samples.size)
                    if (n > 0) {
                        writer.write(samples, 0, n)
                        totalBytes += n.toLong() * 2
                        val now = System.currentTimeMillis()
                        if (now - lastHeartbeat >= HEARTBEAT_INTERVAL_MS) {
                            val elapsed = now - startedAt
                            Log.i(TAG, "heartbeat elapsedMs=$elapsed bytes=$totalBytes")
                            onSamples(totalBytes, elapsed)
                            lastHeartbeat = now
                        }
                    } else if (n < 0) {
                        Log.w(TAG, "AudioRecord.read returned $n; stopping")
                        running.set(false)
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "recorder thread crashed", t)
            } finally {
                try { record.stop() } catch (_: Throwable) {}
                record.release()
                writer.close()
                Log.i(TAG, "recorder stopped totalBytes=$totalBytes durationMs=${System.currentTimeMillis() - startedAt}")
            }
        }
    }

    fun stop() {
        running.set(false)
        recordThread?.join(2_000)
        recordThread = null
    }

    fun isRunning(): Boolean = running.get()

    fun elapsedMs(): Long = if (startedAt == 0L) 0 else System.currentTimeMillis() - startedAt

    fun totalBytes(): Long = totalBytes

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val BUFFER_MULTIPLIER = 4
        private const val HEARTBEAT_INTERVAL_MS = 30_000L
        private const val TAG = "SPIKE/AudioRecorder"
    }
}
