package com.yura.tts

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

internal class TtsAudioCache(context: Context) {
    private val audioDir = File(context.applicationContext.cacheDir, "tts-audio").apply { mkdirs() }

    fun fileFor(sessionId: Long, queueSequence: Int): File = File(audioDir, "tts-$sessionId-$queueSequence.wav")

    fun waitingAudioFile(): File {
        val file = File(audioDir, WAITING_AUDIO_FILE_NAME)
        if (file.exists() && file.length() == WAITING_AUDIO_FILE_SIZE.toLong()) return file

        val dataSize = WAITING_AUDIO_SAMPLE_RATE * WAITING_AUDIO_CHANNELS * WAITING_AUDIO_BYTES_PER_SAMPLE
        val bytes = ByteBuffer.allocate(WAV_HEADER_SIZE + dataSize)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                put("RIFF".toByteArray(Charsets.US_ASCII))
                putInt(WAV_HEADER_SIZE - 8 + dataSize)
                put("WAVE".toByteArray(Charsets.US_ASCII))
                put("fmt ".toByteArray(Charsets.US_ASCII))
                putInt(16)
                putShort(1)
                putShort(WAITING_AUDIO_CHANNELS.toShort())
                putInt(WAITING_AUDIO_SAMPLE_RATE)
                putInt(WAITING_AUDIO_SAMPLE_RATE * WAITING_AUDIO_CHANNELS * WAITING_AUDIO_BYTES_PER_SAMPLE)
                putShort((WAITING_AUDIO_CHANNELS * WAITING_AUDIO_BYTES_PER_SAMPLE).toShort())
                putShort((WAITING_AUDIO_BYTES_PER_SAMPLE * 8).toShort())
                put("data".toByteArray(Charsets.US_ASCII))
                putInt(dataSize)
                position(WAV_HEADER_SIZE + dataSize)
            }
            .array()
        file.writeBytes(bytes)
        return file
    }

    fun clear() {
        audioDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.startsWith("tts-")) file.delete()
        }
    }

    fun durationMs(file: File): Long = runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        }
    }.getOrDefault(0L)

    fun boostPcmWavVolume(file: File) {
        if (!file.exists() || file.length() <= 44L) return
        runCatching {
            val bytes = file.readBytes()
            if (!bytes.hasAscii(0, "RIFF") || !bytes.hasAscii(8, "WAVE")) return
            var offset = 12
            var audioFormat = -1
            var bitsPerSample = -1
            var dataOffset = -1
            var dataSize = 0
            while (offset + 8 <= bytes.size) {
                val chunkId = bytes.ascii(offset, 4)
                val chunkSize = bytes.leInt(offset + 4)
                val chunkDataOffset = offset + 8
                if (chunkSize < 0 || chunkDataOffset + chunkSize > bytes.size) break
                when (chunkId) {
                    "fmt " -> if (chunkSize >= 16) {
                        audioFormat = bytes.leUShort(chunkDataOffset)
                        bitsPerSample = bytes.leUShort(chunkDataOffset + 14)
                    }
                    "data" -> {
                        dataOffset = chunkDataOffset
                        dataSize = chunkSize
                    }
                }
                offset = chunkDataOffset + chunkSize + (chunkSize and 1)
            }
            if (audioFormat != 1 || bitsPerSample != 16 || dataOffset < 0 || dataSize <= 0) return
            var index = dataOffset
            val end = (dataOffset + dataSize).coerceAtMost(bytes.size - 1)
            var peakBefore = 0
            var peakAfter = 0
            while (index + 1 <= end) {
                val sample = bytes.leShort(index)
                peakBefore = maxOf(peakBefore, kotlin.math.abs(sample.toInt()))
                val boosted = (sample * SPEECH_VOLUME_GAIN).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                peakAfter = maxOf(peakAfter, kotlin.math.abs(boosted))
                bytes.writeLeShort(index, boosted.toShort())
                index += 2
            }
            file.writeBytes(bytes)
            Log.d(TAG, "boostPcmWavVolume file=${file.name} gain=$SPEECH_VOLUME_GAIN peak=$peakBefore->$peakAfter")
        }.onFailure { error ->
            Log.w(TAG, "boostPcmWavVolume skipped: ${error.message}")
        }
    }

    private fun ByteArray.hasAscii(offset: Int, value: String): Boolean =
        offset >= 0 && offset + value.length <= size && value.indices.all { index -> this[offset + index].toInt().toChar() == value[index] }

    private fun ByteArray.ascii(offset: Int, length: Int): String =
        buildString(length) { repeat(length) { append(this@ascii[offset + it].toInt().toChar()) } }

    private fun ByteArray.leInt(offset: Int): Int =
        (this[offset].toInt() and 0xff) or ((this[offset + 1].toInt() and 0xff) shl 8) or
            ((this[offset + 2].toInt() and 0xff) shl 16) or ((this[offset + 3].toInt() and 0xff) shl 24)

    private fun ByteArray.leUShort(offset: Int): Int =
        (this[offset].toInt() and 0xff) or ((this[offset + 1].toInt() and 0xff) shl 8)

    private fun ByteArray.leShort(offset: Int): Short = leUShort(offset).toShort()

    private fun ByteArray.writeLeShort(offset: Int, value: Short) {
        val intValue = value.toInt()
        this[offset] = (intValue and 0xff).toByte()
        this[offset + 1] = ((intValue shr 8) and 0xff).toByte()
    }

    private companion object {
        const val WAV_HEADER_SIZE = 44
        const val WAITING_AUDIO_SAMPLE_RATE = 8_000
        const val WAITING_AUDIO_CHANNELS = 1
        const val WAITING_AUDIO_BYTES_PER_SAMPLE = 2
        const val WAITING_AUDIO_FILE_NAME = "tts-waiting.wav"
        const val WAITING_AUDIO_FILE_SIZE = WAV_HEADER_SIZE +
            WAITING_AUDIO_SAMPLE_RATE * WAITING_AUDIO_CHANNELS * WAITING_AUDIO_BYTES_PER_SAMPLE
        const val SPEECH_VOLUME_GAIN = 1.6f
        const val TAG = "YuraTts"
    }
}
