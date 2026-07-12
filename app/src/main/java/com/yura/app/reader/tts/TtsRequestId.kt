package com.yura.app.reader.tts

internal object TtsRequestId {
    fun cloud(generation: Int, sentenceIndex: Int): String = "$generation:$sentenceIndex"

    fun system(generation: Int, sentenceIndex: Int): String = "system:$generation:$sentenceIndex"

    fun parseCloud(value: String?): Pair<Int, Int>? {
        val parts = value?.split(":") ?: return null
        if (parts.size != 2) return null
        return Pair(parts[0].toIntOrNull() ?: return null, parts[1].toIntOrNull() ?: return null)
    }

    fun parseSystem(value: String?): Pair<Int, Int>? {
        val parts = value?.split(":") ?: return null
        if (parts.size != 3 || parts[0] != "system") return null
        return Pair(parts[1].toIntOrNull() ?: return null, parts[2].toIntOrNull() ?: return null)
    }
}
