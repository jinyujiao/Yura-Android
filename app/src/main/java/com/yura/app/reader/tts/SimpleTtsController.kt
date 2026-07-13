package com.yura.app.reader.tts

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import com.yura.app.reader.MediaService
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class SimpleTtsController(context: Context) : TextToSpeech.OnInitListener {

    enum class State {
        IDLE,
        LOADING,
        PLAYING,
        PAUSED,
        ERROR,
    }

    enum class Provider(val label: String) {
        SYSTEM("\u7cfb\u7edf\u6717\u8bfb"),
        MIMO("MiMo \u6717\u8bfb"),
        MICROSOFT("Microsoft \u6717\u8bfb"),
    }

    data class MicrosoftVoice(
        val shortName: String,
        val displayName: String,
        val locale: String,
    )

    data class UiState(
        val state: State = State.IDLE,
        val provider: Provider = Provider.SYSTEM,
        val paragraphIndex: Int = -1,
        val paragraphTotal: Int = 0,
        val sentenceIndex: Int = -1,
        val sentenceTotal: Int = 0,
        val sentencePositionMs: Long = 0,
        val sentenceDurationMs: Long = 0,
        val currentSentence: String = "",
        val currentParagraph: String = "",
        val engineName: String = "",
        val mimoVoice: String = DEFAULT_MIMO_VOICE,
        val microsoftVoice: String = DEFAULT_MICROSOFT_VOICE,
        val microsoftVoices: List<MicrosoftVoice> = MICROSOFT_VOICES,
        val microsoftVoicesLoading: Boolean = false,
        val microsoftRegion: String = "",
        val hasMimoApiKey: Boolean = false,
        val hasMicrosoftApiKey: Boolean = false,
        val playbackSpeed: Float = DEFAULT_PLAYBACK_SPEED,
        val sleepTimerMinutes: Int = 0,
        val errorMessage: String? = null,
    )

    private data class SpeechItem(
        val paragraphIndex: Int,
        val sentenceIndexInParagraph: Int,
        val sentenceGlobalIndex: Int,
        val text: String,
    )

    private val appContext = context.applicationContext
    private val locale = Locale.getDefault()
    private val textProcessor = TtsTextProcessor(locale)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val audioCache = TtsAudioCache(appContext)
    private val preferencesStore = TtsPreferencesStore(appContext)
    private val cloudTtsClient = CloudTtsClient()
    private val wakeLockManager = TtsWakeLockManager(appContext)

    private val _state = MutableStateFlow(
        UiState(
            state = State.LOADING,
            provider = preferencesStore.provider(),
            mimoVoice = preferencesStore.mimoVoice(),
            microsoftVoice = preferencesStore.microsoftVoice(),
            microsoftVoices = preferencesStore.microsoftVoices(),
            microsoftRegion = preferencesStore.microsoftRegion(),
            hasMimoApiKey = preferencesStore.mimoApiKey().isNotBlank(),
            hasMicrosoftApiKey = preferencesStore.microsoftApiKey().isNotBlank(),
            playbackSpeed = preferencesStore.playbackSpeed(),
        )
    )
    val state: StateFlow<UiState> = _state

    private lateinit var tts: TextToSpeech
    private val player = ExoPlayer.Builder(appContext).build()
    private val mediaSessionId = "yura-tts-${System.identityHashCode(this)}"
    private val mediaSessionManager = TtsMediaSessionManager(
        context = appContext,
        player = player,
        sessionId = mediaSessionId,
        onPrevious = { mainHandler.post(::previous) },
        onNext = { mainHandler.post(::next) },
        onStop = { mainHandler.post(::stop) },
    )
    private val paragraphs = mutableListOf<String>()
    private val speechItems = mutableListOf<SpeechItem>()
    private val prefetchingIndices = mutableSetOf<Int>()
    private val prefetchedIndices = mutableSetOf<Int>()
    private var initialized = false
    private var pendingParagraphIndex: Int? = null
    private var activeParagraphIndex = -1
    private var currentSentenceIndex = -1
    private var synthesizingIndex = -1
    private var pendingPrefetchPlaybackIndex = -1
    private var generation = 0
    private var stopping = false
    private var progressTickerRunning = false
    private var testPlayback = false
    private var mediaTitle = "Yura 朗读"
    private var mediaSubtitle = ""
    private var mediaArtworkUri: Uri? = null
    private var mediaArtworkData: ByteArray? = null
    private val sleepTimer = TtsSleepTimer {
        stop()
        _state.update {
            it.copy(
                sleepTimerMinutes = 0,
                errorMessage = "Sleep timer stopped playback.",
            )
        }
    }
    private val audioRouteListener = TtsAudioRouteListener(appContext, ::pause)

    var onParagraphChanged: ((Int) -> Unit)? = null
    var onQueueEnded: (() -> Unit)? = null

    init {
        Log.d(TAG, "init provider=${preferencesStore.provider()} speed=${preferencesStore.playbackSpeed()}")
        createSystemTts()
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true
        )
        player.playbackParameters = PlaybackParameters(preferencesStore.playbackSpeed())
        player.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (stopping) return
                    when (playbackState) {
                        Player.STATE_READY -> {
                            if (player.playWhenReady) {
                                markSentencePlaying(currentSentenceIndex)
                                startProgressTicker()
                            }
                        }
                        Player.STATE_ENDED -> playNextSentence()
                        Player.STATE_BUFFERING, Player.STATE_IDLE -> Unit
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (stopping || currentSentenceIndex < 0) return
                    _state.update {
                        it.copy(
                            state = if (isPlaying) State.PLAYING else State.PAUSED,
                            errorMessage = null,
                        )
                    }
                    if (isPlaying) startProgressTicker()
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (currentSentenceIndex < 0 || stopping) return
                    fail("Audio stream failed: ${error.errorCodeName}.")
                }
            }
        )
        installTtsListener()
        audioRouteListener.register()
    }

    private fun createSystemTts(engine: String? = null) {
        initialized = false
        if (::tts.isInitialized) {
            runCatching { tts.stop() }
            runCatching { tts.shutdown() }
        }
        Log.d(TAG, "createSystemTts engine=${engine.orEmpty()}")
        tts = if (engine.isNullOrBlank()) {
            TextToSpeech(appContext, this)
        } else {
            TextToSpeech(appContext, this, engine)
        }
        installTtsListener()
    }

    private fun installTtsListener() {
        if (!::tts.isInitialized) return
        tts.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    val request = TtsRequestId.parseCloud(utteranceId) ?: return
                    mainHandler.post {
                        if (request.first != generation || request.second != synthesizingIndex || stopping) {
                            return@post
                        }
                        audioCache.boostPcmWavVolume(audioCache.fileFor(generation, request.second))
                        playSynthesizedFile(request.second)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    fail("\u7cfb\u7edf\u6717\u8bfb\u65e0\u6cd5\u64ad\u653e\u8fd9\u4e00\u53e5\u3002")
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    fail("\u7cfb\u7edf\u6717\u8bfb\u5931\u8d25\uff08$errorCode\uff09\u3002")
                }
            }
        )
    }

    override fun onInit(status: Int) {
        Log.d(TAG, "onInit status=$status")
        initialized = status == TextToSpeech.SUCCESS
        if (!initialized) {
            _state.update {
                it.copy(
                    state = State.IDLE,
                    engineName = "\u7cfb\u7edf\u6717\u8bfb\u4e0d\u53ef\u7528",
                    errorMessage = if (it.provider == Provider.SYSTEM) "\u7cfb\u7edf\u6717\u8bfb\u4e0d\u53ef\u7528\u3002" else null,
                )
            }
            pendingParagraphIndex?.takeIf { _state.value.provider != Provider.SYSTEM }?.let { startParagraph ->
                pendingParagraphIndex = null
                playFromParagraph(startParagraph)
            }
            return
        }

        val languageResult = tts.setLanguage(locale)
        Log.d(TAG, "setLanguage locale=$locale result=$languageResult")
        if (
            languageResult == TextToSpeech.LANG_MISSING_DATA ||
            languageResult == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            _state.update {
                it.copy(
                    state = State.IDLE,
                    engineName = displayEngineName(),
                    errorMessage = if (it.provider == Provider.SYSTEM) {
                        "\u7cfb\u7edf\u6717\u8bfb\u4e0d\u652f\u6301 ${locale.displayLanguage}\u3002"
                    } else {
                        null
                    },
                )
            }
            pendingParagraphIndex?.takeIf { _state.value.provider != Provider.SYSTEM }?.let { startParagraph ->
                pendingParagraphIndex = null
                playFromParagraph(startParagraph)
            }
            return
        }

        _state.update {
            it.copy(
                state = State.IDLE,
                engineName = displayEngineName(),
                errorMessage = null,
            )
        }

        pendingParagraphIndex?.let { startParagraph ->
            pendingParagraphIndex = null
            playFromParagraph(startParagraph)
        }
    }

    fun selectProvider(provider: Provider) {
        Log.d(TAG, "selectProvider provider=$provider")
        preferencesStore.setProvider(provider)
        stop()
        if (provider == Provider.SYSTEM && !initialized) {
            createSystemTts()
        }
        _state.update {
            it.copy(
                provider = provider,
                engineName = displayEngineName(provider),
                errorMessage = null,
            )
        }
    }

    fun setMimoVoice(voice: String) {
        preferencesStore.setMimoVoice(voice)
        _state.update { it.copy(mimoVoice = voice, engineName = displayEngineName()) }
    }

    fun setMimoApiKey(value: String) {
        val trimmed = value.trim()
        preferencesStore.setMimoApiKey(trimmed)
        _state.update { it.copy(hasMimoApiKey = trimmed.isNotBlank()) }
    }

    fun currentMimoApiKey(): String = preferencesStore.mimoApiKey()

    fun setMicrosoftVoice(voice: String) {
        preferencesStore.setMicrosoftVoice(voice)
        _state.update { it.copy(microsoftVoice = preferencesStore.microsoftVoice(), engineName = displayEngineName()) }
    }

    fun setMicrosoftRegion(region: String) {
        preferencesStore.setMicrosoftRegion(region)
        _state.update { it.copy(microsoftRegion = region.trim()) }
    }

    fun setMicrosoftApiKey(value: String) {
        val trimmed = value.trim()
        preferencesStore.setMicrosoftApiKey(trimmed)
        _state.update { it.copy(hasMicrosoftApiKey = trimmed.isNotBlank()) }
    }

    fun currentMicrosoftApiKey(): String = preferencesStore.microsoftApiKey()

    fun refreshMicrosoftVoices() {
        val apiKey = preferencesStore.microsoftApiKey()
        val region = preferencesStore.microsoftRegion()
        if (apiKey.isBlank() || region.isBlank()) {
            _state.update {
                it.copy(
                    microsoftVoicesLoading = false,
                    errorMessage = "请先填写 Azure Region 和 Speech Key。",
                )
            }
            return
        }

        _state.update { it.copy(microsoftVoicesLoading = true, errorMessage = null) }
        executor.execute {
            runCatching {
                cloudTtsClient.fetchMicrosoftVoices(region, apiKey)
            }.onSuccess { voices ->
                val nextVoices = voices.ifEmpty { MICROSOFT_VOICES }
                val selected = preferencesStore.microsoftVoice()
                    .takeIf { saved -> nextVoices.any { it.shortName == saved } }
                    ?: nextVoices.first().shortName
                preferencesStore.saveMicrosoftVoices(nextVoices, selected)
                _state.update {
                    it.copy(
                        microsoftVoice = selected,
                        microsoftVoices = nextVoices,
                        microsoftVoicesLoading = false,
                        engineName = displayEngineName(Provider.MICROSOFT),
                        errorMessage = null,
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        microsoftVoicesLoading = false,
                        errorMessage = error.message ?: "刷新 Microsoft 音色失败。",
                    )
                }
            }
        }
    }

    fun setSleepTimer(minutes: Int) {
        val scheduledMinutes = sleepTimer.schedule(minutes)
        _state.update { it.copy(sleepTimerMinutes = scheduledMinutes) }
    }

    fun setPlaybackSpeed(speed: Float) {
        val nearest = PLAYBACK_SPEEDS.minBy { kotlin.math.abs(it - speed) }
        preferencesStore.setPlaybackSpeed(nearest)
        player.playbackParameters = PlaybackParameters(nearest)
        tts.setSpeechRate(nearest)
        _state.update { it.copy(playbackSpeed = nearest) }
    }

    fun setMediaInfo(
        title: String,
        subtitle: String = "",
        artworkUri: Uri? = null,
        artworkData: ByteArray? = null,
    ) {
        mediaTitle = title.ifBlank { "Yura 朗读" }
        mediaSubtitle = subtitle
        mediaArtworkUri = artworkUri
        mediaArtworkData = artworkData
    }

    fun speak(items: List<String>, startParagraphIndex: Int = 0) {
        speakInternal(items, startParagraphIndex, keepPlaybackSession = false)
    }

    fun speakContinuing(items: List<String>, startParagraphIndex: Int = 0) {
        speakInternal(items, startParagraphIndex, keepPlaybackSession = true)
    }

    private fun speakInternal(
        items: List<String>,
        startParagraphIndex: Int,
        keepPlaybackSession: Boolean,
    ) {
        Log.d(
            TAG,
            "speak provider=${_state.value.provider} items=${items.size} start=$startParagraphIndex keepSession=$keepPlaybackSession initialized=$initialized"
        )
        testPlayback = false
        generation++
        stopping = false
        if (!keepPlaybackSession) {
            player.stop()
            tts.stop()
        }
        audioCache.clear()
        if (!keepPlaybackSession) {
            ensureMediaControls()
        }

        paragraphs.clear()
        paragraphs.addAll(items.map { textProcessor.clean(it) }.filter { it.isNotBlank() })
        prefetchingIndices.clear()
        prefetchedIndices.clear()
        pendingPrefetchPlaybackIndex = -1
        rebuildSpeechItems()
        Log.d(TAG, "speak split paragraphs=${paragraphs.size} speechItems=${speechItems.size}")

        val safeParagraph = startParagraphIndex.coerceIn(
            0,
            (paragraphs.size - 1).coerceAtLeast(0)
        )
        val initialItem = speechItems.firstOrNull { it.paragraphIndex >= safeParagraph }
        _state.value = _state.value.copy(
            state = State.LOADING,
            paragraphIndex = initialItem?.paragraphIndex ?: safeParagraph,
            paragraphTotal = paragraphs.size,
            sentenceIndex = initialItem?.sentenceGlobalIndex ?: -1,
            sentenceTotal = speechItems.size,
            sentencePositionMs = 0,
            sentenceDurationMs = 0,
            currentSentence = initialItem?.text.orEmpty(),
            currentParagraph = paragraphs.getOrNull(safeParagraph).orEmpty(),
            engineName = displayEngineName(),
            errorMessage = null,
        )

        if (paragraphs.isEmpty() || speechItems.isEmpty()) {
            Log.w(TAG, "speak empty paragraphs=${paragraphs.size} speechItems=${speechItems.size}")
            stop()
        } else if (canStartCurrentProvider()) {
            Log.d(TAG, "speak start paragraph=$safeParagraph speechItems=${speechItems.size}")
            playFromParagraph(safeParagraph, resetPlayer = !keepPlaybackSession)
        } else {
            Log.d(TAG, "speak pending paragraph=$safeParagraph provider=${_state.value.provider}")
            pendingParagraphIndex = safeParagraph
            if (_state.value.provider == Provider.SYSTEM && !initialized) {
                createSystemTts()
            }
        }
    }

    fun play() {
        if (speechItems.isEmpty()) return
        if (_state.value.state == State.PAUSED && player.playbackState != Player.STATE_IDLE) {
            player.play()
            _state.update { it.copy(state = State.PLAYING, errorMessage = null) }
            return
        }
        val currentSentence = _state.value.sentenceIndex.coerceAtLeast(0)
        playFromSentence(currentSentence)
    }

    fun testVoice(text: String = "这是一段测试朗读，用来确认当前语音和音色。") {
        Log.d(TAG, "testVoice provider=${_state.value.provider} initialized=$initialized hasMimo=${_state.value.hasMimoApiKey} hasMs=${_state.value.hasMicrosoftApiKey}")
        testPlayback = true
        generation++
        stopping = false
        player.stop()
        tts.stop()
        audioCache.clear()
        ensureMediaControls()

        paragraphs.clear()
        paragraphs += textProcessor.clean(text)
        prefetchingIndices.clear()
        prefetchedIndices.clear()
        rebuildSpeechItems()
        _state.value = _state.value.copy(
            state = State.LOADING,
            paragraphIndex = 0,
            paragraphTotal = 1,
            sentenceIndex = 0,
            sentenceTotal = speechItems.size,
            sentencePositionMs = 0,
            sentenceDurationMs = 0,
            currentSentence = text,
            currentParagraph = text,
            engineName = displayEngineName(),
            errorMessage = null,
        )

        if (canStartCurrentProvider() && speechItems.isNotEmpty()) {
            Log.d(TAG, "testVoice start speechItems=${speechItems.size}")
            playFromSentence(0)
        } else {
            Log.d(TAG, "testVoice pending canStart=${canStartCurrentProvider()} speechItems=${speechItems.size}")
            pendingParagraphIndex = 0
            if (_state.value.provider == Provider.SYSTEM && !initialized) {
                createSystemTts()
            }
        }
    }

    fun pause() {
        tts.stop()
        player.pause()
        synthesizingIndex = -1
        if (_state.value.state != State.IDLE) {
            _state.update { it.copy(state = State.PAUSED) }
        }
    }

    fun stop() {
        Log.d(TAG, "stop")
        wakeLockManager.release()
        generation++
        pendingParagraphIndex = null
        activeParagraphIndex = -1
        currentSentenceIndex = -1
        synthesizingIndex = -1
        prefetchingIndices.clear()
        prefetchedIndices.clear()
        stopping = true
        tts.stop()
        player.stop()
        player.clearMediaItems()
        audioRouteListener.unregister()
        releaseMediaControls()
        stopping = false
        audioCache.clear()
        onParagraphChanged?.invoke(-1)
        _state.value = _state.value.copy(
            state = State.IDLE,
            paragraphIndex = -1,
            sentenceIndex = -1,
            sentencePositionMs = 0,
            sentenceDurationMs = 0,
            currentSentence = "",
            currentParagraph = "",
            paragraphTotal = paragraphs.size,
            sentenceTotal = speechItems.size,
            engineName = displayEngineName(),
            errorMessage = null,
        )
    }

    fun previous() {
        if (speechItems.isEmpty()) return
        val target = (_state.value.sentenceIndex - 1).coerceAtLeast(0)
        playFromSentence(target)
    }

    fun next() {
        if (speechItems.isEmpty()) return
        val target = (_state.value.sentenceIndex + 1).coerceAtMost(speechItems.lastIndex)
        playFromSentence(target)
    }

    fun shutdown() {
        stop()
        sleepTimer.cancel()
        executor.shutdownNow()
        releaseMediaControls()
        player.release()
        tts.shutdown()
    }

    private fun rebuildSpeechItems() {
        speechItems.clear()
        var globalIndex = 0
        paragraphs.forEachIndexed { paragraphIndex, paragraph ->
            textProcessor.splitSentences(textProcessor.clean(paragraph)).forEachIndexed { sentenceIndex, sentence ->
                speechItems += SpeechItem(
                    paragraphIndex = paragraphIndex,
                    sentenceIndexInParagraph = sentenceIndex,
                    sentenceGlobalIndex = globalIndex,
                    text = textProcessor.clean(sentence),
                )
                globalIndex++
            }
        }
    }

    private fun ensureMediaControls() = mediaSessionManager.ensure()

    private fun releaseMediaControls() = mediaSessionManager.release()

    private fun playFromParagraph(paragraphIndex: Int, resetPlayer: Boolean = true) {
        val item = speechItems.firstOrNull { it.paragraphIndex >= paragraphIndex } ?: return
        playFromSentence(item.sentenceGlobalIndex, resetPlayer = resetPlayer)
    }

    private fun playFromSentence(sentenceIndex: Int, resetPlayer: Boolean = true) {
        Log.d(
            TAG,
            "playFromSentence index=$sentenceIndex resetPlayer=$resetPlayer provider=${_state.value.provider} initialized=$initialized items=${speechItems.size}"
        )
        if (speechItems.isEmpty()) return
        if (_state.value.provider == Provider.SYSTEM && !initialized) {
            fail("\u7cfb\u7edf\u6717\u8bfb\u4e0d\u53ef\u7528\u3002")
            return
        }

        val safeIndex = sentenceIndex.coerceIn(0, speechItems.lastIndex)
        generation++
        stopping = false
        if (resetPlayer) {
            tts.stop()
            player.stop()
            player.clearMediaItems()
        }
        prefetchingIndices.clear()
        prefetchedIndices.clear()
        pendingPrefetchPlaybackIndex = -1
        currentSentenceIndex = safeIndex
        synthesizeSentence(safeIndex)
    }

    private fun playNextSentence() {
        if (_state.value.state == State.PAUSED || currentSentenceIndex < 0) return
        val nextIndex = currentSentenceIndex + 1
        if (nextIndex > speechItems.lastIndex) {
            finishQueue()
            return
        }
        currentSentenceIndex = nextIndex
        val file = audioCache.fileFor(generation, nextIndex)
        if (file.exists() && file.length() > 0L) {
            Log.d(TAG, "playNextSentence prefetch hit index=$nextIndex file=${file.length()}")
            prefetchedIndices.remove(nextIndex)
            playSynthesizedFile(nextIndex)
        } else if (nextIndex in prefetchingIndices) {
            Log.d(TAG, "playNextSentence waiting for prefetch index=$nextIndex")
            pendingPrefetchPlaybackIndex = nextIndex
            synthesizingIndex = nextIndex
            speechItems.getOrNull(nextIndex)?.let { markSentenceLoading(it) }
        } else {
            Log.d(TAG, "playNextSentence prefetch miss index=$nextIndex")
            synthesizeSentence(nextIndex)
        }
    }

    private fun finishQueue() {
        if (testPlayback) {
            testPlayback = false
            stop()
            return
        }
        val queueEnded = onQueueEnded
        if (queueEnded == null) {
            stop()
        } else {
            queueEnded.invoke()
        }
    }

    private fun synthesizeSentence(sentenceIndex: Int) {
        val item = speechItems.getOrNull(sentenceIndex) ?: return
        Log.d(TAG, "synthesizeSentence index=$sentenceIndex provider=${_state.value.provider} textLength=${item.text.length}")
        synthesizingIndex = sentenceIndex
        markSentenceLoading(item)
        val file = audioCache.fileFor(generation, sentenceIndex)
        if (file.exists()) file.delete()

        when (_state.value.provider) {
            Provider.SYSTEM -> {
                if (initialized) {
                    synthesizeSystem(item, file)
                } else {
                    fail("\u7cfb\u7edf\u6717\u8bfb\u4e0d\u53ef\u7528\u3002")
                }
            }
            Provider.MIMO -> synthesizeCloud(sentenceIndex, file) {
                synthesizeMimo(item.text, file)
            }
            Provider.MICROSOFT -> synthesizeCloud(sentenceIndex, file) {
                synthesizeMicrosoft(item.text, file)
            }
        }
    }

    private fun synthesizeSystem(item: SpeechItem, file: File) {
        val utteranceId = TtsRequestId.cloud(generation, item.sentenceGlobalIndex)
        tts.setSpeechRate(preferencesStore.playbackSpeed())
        val result = tts.synthesizeToFile(item.text, Bundle(), file, utteranceId)
        Log.d(TAG, "synthesizeSystem utterance=$utteranceId result=$result textLength=${item.text.length} file=${file.absolutePath}")
        if (result == TextToSpeech.ERROR) {
            fail("\u7cfb\u7edf\u6717\u8bfb\u65e0\u6cd5\u5f00\u59cb\u751f\u6210\u97f3\u9891\u3002")
        }
    }

    private fun synthesizeCloud(
        sentenceIndex: Int,
        file: File,
        block: () -> Unit,
    ) {
        val requestGeneration = generation
        executor.execute {
            runCatching { block() }
                .onSuccess {
                    audioCache.boostPcmWavVolume(file)
                    Log.d(TAG, "synthesizeCloud success sentence=$sentenceIndex file=${file.length()}")
                    mainHandler.post {
                        if (requestGeneration == generation && sentenceIndex == synthesizingIndex && !stopping) {
                            if (file.exists() && file.length() > 0L) {
                                playSynthesizedFile(sentenceIndex)
                            } else {
                                fail("${_state.value.provider.label}\u751f\u6210\u4e86\u7a7a\u97f3\u9891\u3002")
                            }
                        }
                    }
                }
                .onFailure { error ->
                    Log.e(TAG, "synthesizeCloud failed sentence=$sentenceIndex", error)
                    fail(error.message ?: "\u4e91\u7aef\u6717\u8bfb\u5931\u8d25\u3002")
                }
        }
    }

    private fun synthesizeMimo(text: String, file: File) {
        val apiKey = preferencesStore.mimoApiKey()
        Log.d(TAG, "synthesizeMimo voice=${preferencesStore.mimoVoice()} textLength=${text.length} hasKey=${apiKey.isNotBlank()}")
        cloudTtsClient.synthesizeMimo(text, apiKey, preferencesStore.mimoVoice(), file)
    }

    private fun synthesizeMicrosoft(text: String, file: File) {
        val apiKey = preferencesStore.microsoftApiKey()
        val region = preferencesStore.microsoftRegion()
        Log.d(TAG, "synthesizeMicrosoft voice=${preferencesStore.microsoftVoice()} region=$region textLength=${text.length} hasKey=${apiKey.isNotBlank()}")
        cloudTtsClient.synthesizeMicrosoft(text, apiKey, region, preferencesStore.microsoftVoice(), file)
    }
    private fun playSynthesizedFile(sentenceIndex: Int) {
        val file = audioCache.fileFor(generation, sentenceIndex)
        Log.d(TAG, "playSynthesizedFile index=$sentenceIndex exists=${file.exists()} length=${file.length()}")
        if (!file.exists() || file.length() == 0L) {
            fail("${_state.value.provider.label}\u751f\u6210\u4e86\u7a7a\u97f3\u9891\u3002")
            return
        }

        val item = speechItems.getOrNull(sentenceIndex)
        currentSentenceIndex = sentenceIndex
        player.setMediaItem(
            MediaItem.Builder()
                .setUri(Uri.fromFile(file))
                .setMediaMetadata(
                    MediaMetadata.Builder().apply {
                        setTitle(mediaTitle)
                        setArtist(mediaSubtitle.ifBlank { displayEngineName() })
                        setDescription(item?.text.orEmpty())
                        mediaArtworkData?.let {
                            setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                        }
                        mediaArtworkUri?.let { setArtworkUri(it) }
                    }.build()
                )
                .build()
        )
        player.playbackParameters = PlaybackParameters(preferencesStore.playbackSpeed())
        player.prepare()
        player.play()
        markSentencePlaying(sentenceIndex, durationMs = audioCache.durationMs(file))
        wakeLockManager.release()
        prefetchCloudSentences(sentenceIndex + 1)
    }

    private fun prefetchCloudSentences(startSentenceIndex: Int) {
        repeat(PREFETCH_AHEAD_COUNT) { offset ->
            prefetchCloudSentence(startSentenceIndex + offset)
        }
    }

    private fun prefetchCloudSentence(sentenceIndex: Int) {
        if (testPlayback || stopping) return
        if (_state.value.provider == Provider.SYSTEM) return
        val item = speechItems.getOrNull(sentenceIndex) ?: return
        val file = audioCache.fileFor(generation, sentenceIndex)
        if (file.exists() && file.length() > 0L) {
            Log.d(TAG, "prefetch already cached index=$sentenceIndex file=${file.length()}")
            prefetchedIndices += sentenceIndex
            return
        }
        if (sentenceIndex in prefetchingIndices || sentenceIndex in prefetchedIndices) return

        val requestGeneration = generation
        prefetchingIndices += sentenceIndex
        Log.d(TAG, "prefetch start index=$sentenceIndex textLength=${item.text.length}")
        executor.execute {
            runCatching {
                when (_state.value.provider) {
                    Provider.MIMO -> synthesizeMimo(item.text, file)
                    Provider.MICROSOFT -> synthesizeMicrosoft(item.text, file)
                    Provider.SYSTEM -> Unit
                }
                audioCache.boostPcmWavVolume(file)
            }.onSuccess {
                mainHandler.post {
                    prefetchingIndices.remove(sentenceIndex)
                    if (requestGeneration == generation && !stopping && file.exists() && file.length() > 0L) {
                        Log.d(TAG, "prefetch success index=$sentenceIndex file=${file.length()}")
                        prefetchedIndices += sentenceIndex
                        if (pendingPrefetchPlaybackIndex == sentenceIndex) {
                            pendingPrefetchPlaybackIndex = -1
                            prefetchedIndices.remove(sentenceIndex)
                            playSynthesizedFile(sentenceIndex)
                        }
                    }
                }
            }.onFailure { error ->
                Log.e(TAG, "prefetch failed index=$sentenceIndex", error)
                mainHandler.post {
                    prefetchingIndices.remove(sentenceIndex)
                    file.delete()
                    if (pendingPrefetchPlaybackIndex == sentenceIndex && requestGeneration == generation && !stopping) {
                        pendingPrefetchPlaybackIndex = -1
                        synthesizeSentence(sentenceIndex)
                    }
                }
            }
        }
    }

    private fun markSentenceLoading(item: SpeechItem) {
        if (activeParagraphIndex != item.paragraphIndex) {
            activeParagraphIndex = item.paragraphIndex
            onParagraphChanged?.invoke(item.paragraphIndex)
        }
        _state.update {
            it.copy(
                state = State.LOADING,
                paragraphIndex = item.paragraphIndex,
                sentenceIndex = item.sentenceGlobalIndex,
                sentencePositionMs = 0,
                sentenceDurationMs = 0,
                currentSentence = item.text,
                currentParagraph = paragraphs.getOrNull(item.paragraphIndex).orEmpty(),
                paragraphTotal = paragraphs.size,
                sentenceTotal = speechItems.size,
                engineName = displayEngineName(),
                errorMessage = null,
            )
        }
    }

    private fun markSentencePlaying(sentenceIndex: Int, durationMs: Long = _state.value.sentenceDurationMs) {
        val item = speechItems.getOrNull(sentenceIndex) ?: return
        if (activeParagraphIndex != item.paragraphIndex) {
            activeParagraphIndex = item.paragraphIndex
            onParagraphChanged?.invoke(item.paragraphIndex)
        }
        _state.update {
            it.copy(
                state = State.PLAYING,
                paragraphIndex = item.paragraphIndex,
                sentenceIndex = item.sentenceGlobalIndex,
                sentencePositionMs = player.currentPosition.coerceAtLeast(0),
                sentenceDurationMs = durationMs,
                currentSentence = item.text,
                currentParagraph = paragraphs.getOrNull(item.paragraphIndex).orEmpty(),
                paragraphTotal = paragraphs.size,
                sentenceTotal = speechItems.size,
                engineName = displayEngineName(),
                errorMessage = null,
            )
        }
    }

    private fun startProgressTicker() {
        if (progressTickerRunning) return
        progressTickerRunning = true
        mainHandler.post(
            object : Runnable {
                override fun run() {
                    val playing = player.isPlaying && !stopping
                    if (playing) {
                        val duration = player.duration.takeIf { it > 0 } ?: _state.value.sentenceDurationMs
                        _state.update {
                            it.copy(
                                sentencePositionMs = player.currentPosition.coerceAtLeast(0),
                                sentenceDurationMs = duration.coerceAtLeast(0),
                            )
                        }
                        mainHandler.postDelayed(this, 200)
                    } else {
                        progressTickerRunning = false
                    }
                }
            }
        )
    }

    private fun canStartCurrentProvider(): Boolean =
        _state.value.provider != Provider.SYSTEM || initialized

    private fun fail(message: String) {
        Log.e(TAG, "fail: $message")
        if (_state.value.provider != Provider.SYSTEM && initialized && speechItems.isNotEmpty()) {
            val resumeAt = currentSentenceIndex.coerceAtLeast(0)
            mainHandler.post {
                preferencesStore.setProvider(Provider.SYSTEM)
                player.stop()
                _state.update { it.copy(provider = Provider.SYSTEM, errorMessage = "Cloud TTS failed; switched to system TTS.") }
                playFromSentence(resumeAt)
            }
            return
        }
        mainHandler.post {
            wakeLockManager.release()
            tts.stop()
            player.stop()
            pendingParagraphIndex = null
            activeParagraphIndex = -1
            synthesizingIndex = -1
            onParagraphChanged?.invoke(-1)
            _state.update {
                it.copy(
                    state = State.ERROR,
                    errorMessage = message,
                    engineName = displayEngineName(),
                )
            }
        }
    }

    fun holdPlaybackWakeLock(timeoutMs: Long = BACKGROUND_CONTINUATION_WAKE_LOCK_MS) {
        wakeLockManager.acquire(timeoutMs)
    }

    private fun displayEngineName(provider: Provider = _state.value.provider): String =
        when (provider) {
            Provider.SYSTEM -> tts.defaultEngine.orEmpty()
            Provider.MIMO -> "MiMo ${preferencesStore.mimoVoice()}"
            Provider.MICROSOFT -> "Microsoft ${preferencesStore.microsoftVoice()}"
        }

    private fun escapeXml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    companion object {
        const val DEFAULT_MIMO_VOICE = "茉莉"
        const val DEFAULT_MICROSOFT_VOICE = "zh-CN-XiaoxiaoNeural"
        const val DEFAULT_PLAYBACK_SPEED = 1.0f
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

        private const val MIMO_ENDPOINT = "https://api.xiaomimimo.com/v1/chat/completions"
        private const val MIMO_MODEL = "mimo-v2.5-tts"
        private const val PREFETCH_AHEAD_COUNT = 2
        private const val BACKGROUND_CONTINUATION_WAKE_LOCK_MS = 2 * 60 * 1000L
        private const val TAG = "YuraTts"
    }
}

