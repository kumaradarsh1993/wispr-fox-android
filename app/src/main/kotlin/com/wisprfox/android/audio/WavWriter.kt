package com.wisprfox.android.audio

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Writes PCM 16-bit mono audio to a RIFF/WAVE file. Header sizes are
 * placeholder bytes during recording and patched on close so the file
 * stays valid even if the process is killed mid-recording (the partial
 * data is still PCM-decodable; the header just reports zero bytes,
 * which a recovery tool can fix).
 */
class WavWriter(
    private val file: File,
    private val sampleRate: Int,
    private val channels: Int = 1,
    private val bitsPerSample: Int = 16,
) : AutoCloseable {

    private val raf = RandomAccessFile(file, "rw")
    private var dataBytesWritten: Long = 0

    init {
        writePlaceholderHeader()
    }

    fun write(buffer: ShortArray, offset: Int, length: Int) {
        val byteBuffer = ByteBuffer.allocate(length * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until length) {
            byteBuffer.putShort(buffer[offset + i])
        }
        raf.write(byteBuffer.array())
        dataBytesWritten += length * 2
    }

    override fun close() {
        patchHeader()
        raf.close()
    }

    private fun writePlaceholderHeader() {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8

        val header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(0) // chunk size — patched on close
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16) // fmt chunk size for PCM
        header.putShort(1) // audio format: 1 = PCM
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(blockAlign.toShort())
        header.putShort(bitsPerSample.toShort())
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(0) // data size — patched on close
        raf.write(header.array())
    }

    private fun patchHeader() {
        val totalDataLen = dataBytesWritten + 36
        raf.seek(4)
        raf.write(intToLittleEndian(totalDataLen.toInt()))
        raf.seek(40)
        raf.write(intToLittleEndian(dataBytesWritten.toInt()))
    }

    private fun intToLittleEndian(value: Int): ByteArray =
        byteArrayOf(
            (value and 0xff).toByte(),
            ((value shr 8) and 0xff).toByte(),
            ((value shr 16) and 0xff).toByte(),
            ((value shr 24) and 0xff).toByte(),
        )

    companion object {
        private const val HEADER_SIZE = 44
    }
}
