package com.yura.tts

import java.io.File

internal object TtsAtomicFileWriter {
    fun write(output: File, block: (File) -> Unit) {
        output.parentFile?.mkdirs()
        val partial = File(output.parentFile, "${output.name}.${System.nanoTime()}.part")
        try {
            block(partial)
            require(partial.exists() && partial.length() > 0L) { "TTS generated an empty audio file." }
            if (output.exists() && !output.delete()) {
                error("Unable to replace cached TTS audio.")
            }
            if (!partial.renameTo(output)) {
                error("Unable to finalize cached TTS audio.")
            }
        } finally {
            partial.delete()
        }
    }
}
