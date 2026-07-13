package com.yura.app.reader.tts

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import java.io.File
import kotlin.math.roundToInt

internal class TtsAudioCache(context: Context) {
    private val audioDir = File(context.applicationContext.cacheDir, "tts-audio").apply { mkdirs() }

    fun fileFor(generation: Int, sentenceIndex: Int): File = File(audioDir, "tts-$generation-$sentenceIndex.wav")

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
        const val SPEECH_VOLUME_GAIN = 1.6f
        const val TAG = "YuraTts"
    }
}
