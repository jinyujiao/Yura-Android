package com.yura.tts.core

interface TtsTextProfile {
    fun prepare(text: String): String
}

class SystemTtsTextProfile(
    private val processor: TtsTextProcessor,
) : TtsTextProfile {
    override fun prepare(text: String): String = processor.clean(text)
}

class MicrosoftTtsTextProfile(
    private val processor: TtsTextProcessor,
) : TtsTextProfile {
    override fun prepare(text: String): String = processor.clean(text)
}

class MimoTtsTextProfile(
    private val processor: TtsTextProcessor,
) : TtsTextProfile {
    override fun prepare(text: String): String = processor.cleanForMimo(text)
}
