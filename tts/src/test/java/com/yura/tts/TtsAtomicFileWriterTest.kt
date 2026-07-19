package com.yura.tts

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsAtomicFileWriterTest {
    @Test
    fun publishesOnlyCompletedAudio() {
        val directory = Files.createTempDirectory("yura-tts-atomic").toFile()
        val output = directory.resolve("sentence.wav")
        try {
            TtsAtomicFileWriter.write(output) { partial ->
                partial.writeText("complete audio")
                assertFalse(output.exists())
            }

            assertEquals("complete audio", output.readText())
            assertTrue(directory.listFiles().orEmpty().none { it.extension == "part" })
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun removesPartialAudioAfterFailure() {
        val directory = Files.createTempDirectory("yura-tts-atomic-failure").toFile()
        val output = directory.resolve("sentence.wav")
        try {
            runCatching {
                TtsAtomicFileWriter.write(output) { partial ->
                    partial.writeText("partial")
                    error("network failed")
                }
            }

            assertFalse(output.exists())
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }
}
