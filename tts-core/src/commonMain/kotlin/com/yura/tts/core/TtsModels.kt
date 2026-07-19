package com.yura.tts.core

enum class TtsState { IDLE, LOADING, PLAYING, PAUSED, ERROR }

enum class TtsProvider(val label: String) {
    SYSTEM("系统朗读"), MIMO("MiMo 朗读"), MICROSOFT("Microsoft 朗读"),
}

data class MicrosoftVoice(val shortName: String, val displayName: String, val locale: String)

data class TtsUiState(
    val state: TtsState = TtsState.IDLE,
    val provider: TtsProvider = TtsProvider.SYSTEM,
    val paragraphIndex: Int = -1,
    val paragraphTotal: Int = 0,
    val sentenceIndex: Int = -1,
    val sentenceTotal: Int = 0,
    val sentencePositionMs: Long = 0,
    val sentenceDurationMs: Long = 0,
    val currentSentence: String = "",
    val currentParagraph: String = "",
    val engineName: String = "",
    val mimoVoice: String = TtsDefaults.MIMO_VOICE,
    val microsoftVoice: String = TtsDefaults.MICROSOFT_VOICE,
    val microsoftVoices: List<MicrosoftVoice> = TtsDefaults.MICROSOFT_VOICES,
    val microsoftVoicesLoading: Boolean = false,
    val microsoftRegion: String = "",
    val hasMimoApiKey: Boolean = false,
    val hasMicrosoftApiKey: Boolean = false,
    val playbackSpeed: Float = TtsDefaults.PLAYBACK_SPEED,
    val sleepTimerMinutes: Int = 0,
    val errorMessage: String? = null,
)

object TtsDefaults {
    const val MIMO_VOICE = "茉莉"
    const val MICROSOFT_VOICE = "zh-CN-XiaoxiaoNeural"
    const val PLAYBACK_SPEED = 1.0f
    val MIMO_VOICES = listOf("冰糖", "茉莉", "苏打", "白桦", "Mia", "Chloe", "Milo", "Dean")
    val MICROSOFT_VOICES = listOf(
        MicrosoftVoice("zh-CN-XiaoxiaoNeural", "晓晓 · zh-CN", "zh-CN"),
        MicrosoftVoice("zh-CN-YunxiNeural", "云希 · zh-CN", "zh-CN"),
        MicrosoftVoice("zh-CN-XiaoyiNeural", "晓伊 · zh-CN", "zh-CN"),
        MicrosoftVoice("zh-CN-YunjianNeural", "云健 · zh-CN", "zh-CN"),
        MicrosoftVoice("zh-CN-XiaochenNeural", "晓辰 · zh-CN", "zh-CN"),
        MicrosoftVoice("en-US-JennyNeural", "Jenny · en-US", "en-US"),
        MicrosoftVoice("en-US-GuyNeural", "Guy · en-US", "en-US"),
    )
    val PLAYBACK_SPEEDS = listOf(0.8f, 1.0f, 1.2f, 1.4f, 1.6f, 1.8f, 2.0f)
}
