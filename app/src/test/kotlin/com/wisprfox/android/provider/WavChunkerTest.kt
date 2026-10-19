package com.wisprfox.android.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavChunkerTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun quietestWindowFindsTheGap() {
        val samples = ShortArray(4_000) { if (it in 2_600 until 2_900) 0 else 12_000 }
        val start = WavChunker.quietestWindowStart(samples, 200)
        assertTrue("window $start should sit inside the silent gap", start >= 2_600 && start + 200 <= 2_900)
    }

    @Test fun splitCutsInsideAPauseAndStaysUnderTheCap() {
        val rate = 16_000
        val seconds = 60
        val frames = rate * seconds
        // Loud tone everywhere except a 0.5 s pause at 27.0-27.5 s, a little
        // before the nominal half-way cut at 30 s.
        val pauseStart = (27.0 * rate).toInt()
        val pauseEnd = (27.5 * rate).toInt()
        val pcm = ShortArray(frames) { i ->
            if (i in pauseStart until pauseEnd) 0 else if (i % 2 == 0) 9_000 else -9_000
        }
        val src = writeWav(File(tmp.root, "long.wav"), rate, pcm)
        val cap = 44L + frames.toLong() * 2 * 6 / 10 // forces two chunks
        val chunks = WavChunker.splitIfNeeded(src, cap)
        assertEquals(2, chunks.size)
        chunks.forEach { assertTrue(it.length() <= cap) }
        val firstFrames = (chunks[0].length() - 44) / 2
        assertTrue("cut at frame $firstFrames should be in the pause", firstFrames in pauseStart..pauseEnd)
        assertEquals(frames.toLong(), chunks.sumOf { (it.length() - 44) / 2 })
        WavChunker.cleanup(chunks, src)
    }

    @Test fun smallFilesAreNotSplit() {
        val src = writeWav(File(tmp.root, "short.wav"), 16_000, ShortArray(1_000))
        assertEquals(listOf(src), WavChunker.splitIfNeeded(src, 1_000_000))
    }

    private fun writeWav(file: File, rate: Int, pcm: ShortArray): File {
        val data = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        pcm.forEach { data.putShort(it) }
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + pcm.size * 2); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(rate)
            putInt(rate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(pcm.size * 2)
        }
        RandomAccessFile(file, "rw").use { it.write(header.array()); it.write(data.array()) }
        return file
    }
}
