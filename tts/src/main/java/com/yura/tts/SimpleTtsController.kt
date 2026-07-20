package com.yura.tts

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
import com.yura.tts.core.MicrosoftTtsTextProfile
import com.yura.tts.core.MicrosoftVoice
import com.yura.tts.core.MimoTtsTextProfile
import com.yura.tts.core.SystemTtsTextProfile
import com.yura.tts.core.TtsTextProfile
import com.yura.tts.core.TtsDefaults
import com.yura.tts.core.TtsPlaybackPolicy
import com.yura.tts.core.TtsProvider
import com.yura.tts.core.TtsRequestIdentity
import com.yura.tts.core.TtsRequestId
import com.yura.tts.core.TtsState
import com.yura.tts.core.TtsTextProcessor
import com.yura.tts.core.TtsUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import com.yura.tts.android.MediaService
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class SimpleTtsController(context: Context) : TextToSpeech.OnInitListener {

    private data class SpeechSource(
        val paragraphIndex: Int,
        val sentenceIndexInParagraph: Int,
        val sentenceGlobalIndex: Int,
        val sourceText: String,
        val fallbackText: String,
    )

    private data class SpeechItem(
        val paragraphIndex: Int,
        val sentenceIndexInParagraph: Int,
        val sentenceGlobalIndex: Int,
        val text: String,
        val textHash: String,
    )

    private val appContext = context.applicationContext
    private val locale = Locale.getDefault()
    private val textProcessor = TtsTextProcessor()
    private val systemTextProfile = SystemTtsTextProfile(textProcessor)
    private val microsoftTextProfile = MicrosoftTtsTextProfile(textProcessor)
    private val mimoTextProfile = MimoTtsTextProfile(textProcessor)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val prefetchExecutor = Executors.newFixedThreadPool(PREFETCH_AHEAD_COUNT)
    private val audioCache = TtsAudioCache(appContext)
    private val preferencesStore = TtsPreferencesStore(appContext)
    private val cloudTtsClient = CloudTtsClient()
    private val wakeLockManager = TtsWakeLockManager(appContext)

    private val _state = MutableStateFlow(
        TtsUiState(
            state = TtsState.LOADING,
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
    val state: StateFlow<TtsUiState> = _state

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
    private val sourceParagraphs = mutableListOf<String>()
    private val paragraphs = mutableListOf<String>()
    private val speechSources = mutableListOf<SpeechSource>()
    private val speechItems = mutableListOf<SpeechItem>()
    private val prefetchingIndices = mutableSetOf<Int>()
    private val prefetchedIndices = mutableSetOf<Int>()
    private var initialized = false
    private var pendingParagraphIndex: Int? = null
    private var pendingSentenceIndex: Int? = null
    private var activeParagraphIndex = -1
    private var currentSentenceIndex = -1
    private var synthesizingIndex = -1
    private var pendingPrefetchPlaybackIndex = -1
    private var lastStartedPlaybackRequest: TtsRequestIdentity? = null
    private var sessionId = 0L
    private var chapterPosition = -1
    private var stopping = false
    private var userPaused = false
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
                                activePlayerSentenceIndex()?.let(::markSentencePlaying)
                                startProgressTicker()
                            }
                        }
                        Player.STATE_ENDED -> {
                            if (activePlayerSentenceIndex() == currentSentenceIndex && !userPaused) {
                                Log.d(TAG, "player ended index=$currentSentenceIndex; advancing")
                                playNextSentence()
                            }
                        }
                        Player.STATE_BUFFERING, Player.STATE_IDLE -> Unit
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (stopping || currentSentenceIndex < 0) return
                    if (isPlaying) {
                        userPaused = false
                        _state.update { it.copy(state = TtsState.PLAYING, errorMessage = null) }
                        startProgressTicker()
                        return
                    }
                    val playbackState = player.playbackState
                    if (userPaused && playbackState == Player.STATE_READY) {
                        _state.update { it.copy(state = TtsState.PAUSED, errorMessage = null) }
                    } else if (playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_IDLE) {
                        _state.update { it.copy(state = TtsState.LOADING, errorMessage = null) }
                    }
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
                    val request = TtsRequestId.parseSystem(utteranceId) ?: return
                    mainHandler.post {
                        if (!isCurrentRequest(request, synthesizingIndex) || stopping) {
                            return@post
                        }
                        audioCache.boostPcmWavVolume(audioCache.fileFor(request.sessionId, request.queueSequence))
                        playSynthesizedFile(request.queueSequence)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    handleSystemError(utteranceId, "\u7cfb\u7edf\u6717\u8bfb\u65e0\u6cd5\u64ad\u653e\u8fd9\u4e00\u53e5\u3002")
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    handleSystemError(utteranceId, "\u7cfb\u7edf\u6717\u8bfb\u5931\u8d25\uff08$errorCode\uff09\u3002")
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
                    state = TtsState.IDLE,
                    engineName = "\u7cfb\u7edf\u6717\u8bfb\u4e0d\u53ef\u7528",
                    errorMessage = if (it.provider == TtsProvider.SYSTEM) "\u7cfb\u7edf\u6717\u8bfb\u4e0d\u53ef\u7528\u3002" else null,
                )
            }
            pendingParagraphIndex?.takeIf { _state.value.provider != TtsProvider.SYSTEM }?.let { startParagraph ->
                pendingParagraphIndex = null
                pendingSentenceIndex = null
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
                    state = TtsState.IDLE,
                    engineName = displayEngineName(),
                    errorMessage = if (it.provider == TtsProvider.SYSTEM) {
                        "\u7cfb\u7edf\u6717\u8bfb\u4e0d\u652f\u6301 ${locale.displayLanguage}\u3002"
                    } else {
                        null
                    },
                )
            }
            pendingParagraphIndex?.takeIf { _state.value.provider != TtsProvider.SYSTEM }?.let { startParagraph ->
                pendingParagraphIndex = null
                pendingSentenceIndex = null
                playFromParagraph(startParagraph)
            }
            return
        }

        _state.update {
            it.copy(
                state = TtsState.IDLE,
                engineName = displayEngineName(),
                errorMessage = null,
            )
        }

        pendingSentenceIndex?.let { startSentence ->
            pendingSentenceIndex = null
            playFromSentence(startSentence)
        } ?: pendingParagraphIndex?.let { startParagraph ->
            pendingParagraphIndex = null
            pendingSentenceIndex = null
            playFromParagraph(startParagraph)
        }
    }

    fun selectProvider(provider: TtsProvider) {
        Log.d(TAG, "selectProvider provider=$provider")
        if (provider == _state.value.provider) return

        val requestedResumeSentence = TtsPlaybackPolicy.providerSwitchResumeSequence(
            currentQueueSequence = currentSentenceIndex,
            stateQueueSequence = _state.value.sentenceIndex,
            queueSize = speechItems.size,
            state = _state.value.state,
        )
        preferencesStore.setProvider(provider)
        cancelPlaybackForProviderSwitch()
        rebuildSpeechItems(provider)
        val resumeSentence = requestedResumeSentence?.takeIf { it in speechItems.indices }
        _state.update {
            it.copy(
                state = if (resumeSentence == null) TtsState.IDLE else TtsState.LOADING,
                provider = provider,
                sentenceTotal = speechItems.size,
                currentSentence = resumeSentence?.let { index -> speechItems[index].text }.orEmpty(),
                engineName = displayEngineName(provider),
                errorMessage = null,
            )
        }

        if (resumeSentence != null) {
            currentSentenceIndex = resumeSentence
            pendingSentenceIndex = resumeSentence
            if (provider == TtsProvider.SYSTEM && !initialized) {
                createSystemTts()
            } else {
                pendingSentenceIndex = null
                playFromSentence(resumeSentence)
            }
        } else if (provider == TtsProvider.SYSTEM && !initialized) {
            createSystemTts()
        }
    }

    private fun cancelPlaybackForProviderSwitch() {
        sessionId++
        stopping = true
        tts.stop()
        player.stop()
        player.clearMediaItems()
        stopping = false
        pendingParagraphIndex = null
        pendingSentenceIndex = null
        synthesizingIndex = -1
        pendingPrefetchPlaybackIndex = -1
        prefetchingIndices.clear()
        prefetchedIndices.clear()
        audioCache.clear()
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
                        engineName = displayEngineName(TtsProvider.MICROSOFT),
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

    fun speak(items: List<String>, startParagraphIndex: Int = 0, chapterPosition: Int = -1) {
        speakInternal(items, startParagraphIndex, chapterPosition, keepPlaybackSession = false)
    }

    fun speakContinuing(items: List<String>, startParagraphIndex: Int = 0, chapterPosition: Int = -1) {
        speakInternal(items, startParagraphIndex, chapterPosition, keepPlaybackSession = true)
    }

    private fun speakInternal(
        items: List<String>,
        startParagraphIndex: Int,
        chapterPosition: Int,
        keepPlaybackSession: Boolean,
    ) {
        Log.d(
            TAG,
            "speak provider=${_state.value.provider} items=${items.size} start=$startParagraphIndex keepSession=$keepPlaybackSession initialized=$initialized"
        )
        testPlayback = false
        userPaused = false
        wakeLockManager.acquire(BACKGROUND_CONTINUATION_WAKE_LOCK_MS)
        sessionId++
        this.chapterPosition = chapterPosition
        stopping = false
        if (!keepPlaybackSession) {
            player.stop()
            tts.stop()
        }
        audioCache.clear()
        if (!keepPlaybackSession) {
            ensureMediaControls()
        }

        replaceParagraphs(items)
        prefetchingIndices.clear()
        prefetchedIndices.clear()
        pendingPrefetchPlaybackIndex = -1
        Log.d(TAG, "speak split paragraphs=${paragraphs.size} speechItems=${speechItems.size}")

        val safeParagraph = startParagraphIndex.coerceIn(
            0,
            (paragraphs.size - 1).coerceAtLeast(0)
        )
        val initialItem = speechItems.firstOrNull { it.paragraphIndex >= safeParagraph }
        _state.value = _state.value.copy(
            state = TtsState.LOADING,
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
            if (_state.value.provider == TtsProvider.SYSTEM && !initialized) {
                createSystemTts()
            }
        }
    }

    fun play() {
        if (speechItems.isEmpty()) return
        userPaused = false
        if (_state.value.state == TtsState.PAUSED && player.playbackState != Player.STATE_IDLE) {
            player.play()
            _state.update { it.copy(state = TtsState.PLAYING, errorMessage = null) }
            return
        }
        val currentSentence = _state.value.sentenceIndex.coerceAtLeast(0)
        playFromSentence(currentSentence)
    }

    fun testVoice(text: String = "这是一段测试朗读，用来确认当前语音和音色。") {
        Log.d(TAG, "testVoice provider=${_state.value.provider} initialized=$initialized hasMimo=${_state.value.hasMimoApiKey} hasMs=${_state.value.hasMicrosoftApiKey}")
        testPlayback = true
        userPaused = false
        sessionId++
        chapterPosition = -1
        stopping = false
        player.stop()
        tts.stop()
        audioCache.clear()
        ensureMediaControls()

        replaceParagraphs(listOf(text))
        prefetchingIndices.clear()
        prefetchedIndices.clear()
        _state.value = _state.value.copy(
            state = TtsState.LOADING,
            paragraphIndex = 0,
            paragraphTotal = paragraphs.size,
            sentenceIndex = 0,
            sentenceTotal = speechItems.size,
            sentencePositionMs = 0,
            sentenceDurationMs = 0,
            currentSentence = speechItems.firstOrNull()?.text.orEmpty(),
            currentParagraph = paragraphs.firstOrNull().orEmpty(),
            engineName = displayEngineName(),
            errorMessage = null,
        )

        if (canStartCurrentProvider() && speechItems.isNotEmpty()) {
            Log.d(TAG, "testVoice start speechItems=${speechItems.size}")
            playFromSentence(0)
        } else {
            Log.d(TAG, "testVoice pending canStart=${canStartCurrentProvider()} speechItems=${speechItems.size}")
            pendingParagraphIndex = 0
            if (_state.value.provider == TtsProvider.SYSTEM && !initialized) {
                createSystemTts()
            }
        }
    }

    fun pause() {
        userPaused = true
        wakeLockManager.release()
        tts.stop()
        player.pause()
        synthesizingIndex = -1
        if (_state.value.state != TtsState.IDLE) {
            _state.update { it.copy(state = TtsState.PAUSED) }
        }
    }

    fun stop() {
        Log.d(TAG, "stop")
        userPaused = false
        wakeLockManager.release()
        sessionId++
        pendingParagraphIndex = null
        pendingSentenceIndex = null
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
            state = TtsState.IDLE,
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
        prefetchExecutor.shutdownNow()
        releaseMediaControls()
        player.release()
        tts.shutdown()
    }

    private fun replaceParagraphs(items: List<String>) {
        sourceParagraphs.clear()
        paragraphs.clear()
        items.forEach { source ->
            val displayText = textProcessor.clean(source)
            if (displayText.isNotBlank()) {
                sourceParagraphs += source
                paragraphs += displayText
            }
        }
        rebuildSpeechSources()
        rebuildSpeechItems()
    }

    private fun rebuildSpeechSources() {
        speechSources.clear()
        var globalIndex = 0
        sourceParagraphs.forEachIndexed { paragraphIndex, paragraph ->
            var sentenceIndexInParagraph = 0
            textProcessor.splitSourceSentences(paragraph).forEach { sourceSentence ->
                val fallbackText = textProcessor.clean(sourceSentence)
                if (fallbackText.isBlank()) return@forEach
                speechSources += SpeechSource(
                    paragraphIndex = paragraphIndex,
                    sentenceIndexInParagraph = sentenceIndexInParagraph,
                    sentenceGlobalIndex = globalIndex,
                    sourceText = sourceSentence,
                    fallbackText = fallbackText,
                )
                sentenceIndexInParagraph++
                globalIndex++
            }
        }
    }

    private fun rebuildSpeechItems(provider: TtsProvider = _state.value.provider) {
        val profile = textProfile(provider)
        speechItems.clear()
        speechSources.forEach { source ->
            val preparedText = profile.prepare(source.sourceText).ifBlank { source.fallbackText }
            speechItems += SpeechItem(
                paragraphIndex = source.paragraphIndex,
                sentenceIndexInParagraph = source.sentenceIndexInParagraph,
                sentenceGlobalIndex = source.sentenceGlobalIndex,
                text = preparedText,
                textHash = TtsRequestId.textHash(preparedText),
            )
        }
    }

    private fun textProfile(provider: TtsProvider): TtsTextProfile = when (provider) {
        TtsProvider.SYSTEM -> systemTextProfile
        TtsProvider.MIMO -> mimoTextProfile
        TtsProvider.MICROSOFT -> microsoftTextProfile
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
        if (_state.value.provider == TtsProvider.SYSTEM && !initialized) {
            fail("\u7cfb\u7edf\u6717\u8bfb\u4e0d\u53ef\u7528\u3002")
            return
        }

        val safeIndex = sentenceIndex.coerceIn(0, speechItems.lastIndex)
        wakeLockManager.acquire(BACKGROUND_CONTINUATION_WAKE_LOCK_MS)
        sessionId++
        userPaused = false
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
        if (_state.value.state == TtsState.PAUSED || currentSentenceIndex < 0) return
        val nextIndex = currentSentenceIndex + 1
        if (nextIndex > speechItems.lastIndex) {
            finishQueue()
            return
        }
        currentSentenceIndex = nextIndex
        val file = audioCache.fileFor(sessionId, nextIndex)
        if (nextIndex in prefetchingIndices) {
            Log.d(TAG, "playNextSentence waiting for prefetch index=$nextIndex")
            pendingPrefetchPlaybackIndex = nextIndex
            synthesizingIndex = nextIndex
            speechItems.getOrNull(nextIndex)?.let { markSentenceLoading(it) }
        } else if (file.exists() && file.length() > 0L) {
            Log.d(TAG, "playNextSentence prefetch hit index=$nextIndex file=${file.length()}")
            prefetchedIndices.remove(nextIndex)
            playSynthesizedFile(nextIndex)
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
        Log.d(TAG, "synthesizeSentence index=$sentenceIndex provider=${_state.value.provider} textLength=${item.text.length} textHash=${item.text.hashCode()}")
        synthesizingIndex = sentenceIndex
        markSentenceLoading(item)
        val file = audioCache.fileFor(sessionId, sentenceIndex)
        if (file.exists()) file.delete()

        when (_state.value.provider) {
            TtsProvider.SYSTEM -> {
                if (initialized) {
                    synthesizeSystem(item, file)
                } else {
                    fail("\u7cfb\u7edf\u6717\u8bfb\u4e0d\u53ef\u7528\u3002")
                }
            }
            TtsProvider.MIMO -> synthesizeCloud(sentenceIndex, file) {
                synthesizeMimo(item.text, file)
            }
            TtsProvider.MICROSOFT -> synthesizeCloud(sentenceIndex, file) {
                synthesizeMicrosoft(item.text, file)
            }
        }
    }

    private fun synthesizeSystem(item: SpeechItem, file: File) {
        val utteranceId = TtsRequestId.system(requestIdentity(item))
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
        val request = requestIdentity(speechItems.getOrNull(sentenceIndex) ?: return)
        executor.execute {
            runCatching { block() }
                .onSuccess {
                    audioCache.boostPcmWavVolume(file)
                    Log.d(TAG, "synthesizeCloud success sentence=$sentenceIndex file=${file.length()}")
                    mainHandler.post {
                        if (isCurrentRequest(request, synthesizingIndex) && !stopping) {
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
                    mainHandler.post {
                        if (isCurrentRequest(request, synthesizingIndex) && !stopping) {
                            file.delete()
                            _state.update { it.copy(errorMessage = "${_state.value.provider.label}正在重试…") }
                            executor.execute {
                                runCatching { block() }
                                    .onSuccess {
                                        audioCache.boostPcmWavVolume(file)
                                        mainHandler.post {
                                            if (isCurrentRequest(request, synthesizingIndex) && !stopping && file.exists() && file.length() > 0L) {
                                                playSynthesizedFile(sentenceIndex)
                                            } else if (isCurrentRequest(request) && !stopping) {
                                                fail("${_state.value.provider.label}生成了空音频。")
                                            }
                                        }
                                    }
                                    .onFailure { retryError ->
                                        Log.e(TAG, "synthesizeCloud retry failed sentence=$sentenceIndex", retryError)
                                        mainHandler.post {
                                            if (isCurrentRequest(request, synthesizingIndex) && !stopping) {
                                                fail(retryError.message ?: "云端朗读失败。")
                                            }
                                        }
                                    }
                            }
                        }
                    }
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
    private fun activePlayerSentenceIndex(): Int? {
        val request = TtsRequestId.parseMedia(player.currentMediaItem?.mediaId) ?: return null
        return request.queueSequence.takeIf { isCurrentRequest(request) }
    }
    private fun playSynthesizedFile(sentenceIndex: Int) {
        val file = audioCache.fileFor(sessionId, sentenceIndex)
        Log.d(TAG, "playSynthesizedFile index=$sentenceIndex exists=${file.exists()} length=${file.length()}")
        if (!file.exists() || file.length() == 0L) {
            fail("${_state.value.provider.label}\u751f\u6210\u4e86\u7a7a\u97f3\u9891\u3002")
            return
        }

        val item = speechItems.getOrNull(sentenceIndex) ?: return
        val request = requestIdentity(item)
        if (!isCurrentRequest(request, currentSentenceIndex) ||
            !TtsPlaybackPolicy.shouldStartPlayback(request, lastStartedPlaybackRequest, currentSentenceIndex)
        ) {
            Log.d(TAG, "playSynthesizedFile ignored duplicate or stale request=$request current=$currentSentenceIndex")
            return
        }
        lastStartedPlaybackRequest = request
        synthesizingIndex = -1
        pendingPrefetchPlaybackIndex = -1
        currentSentenceIndex = sentenceIndex
        player.setMediaItem(
            MediaItem.Builder()
                .setMediaId(TtsRequestId.media(request))
                .setUri(Uri.fromFile(file))
                .setMediaMetadata(
                    MediaMetadata.Builder().apply {
                        setTitle(mediaTitle)
                        setArtist(mediaSubtitle.ifBlank { displayEngineName() })
                        setDescription(item.text)
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
        wakeLockManager.acquire(BACKGROUND_CONTINUATION_WAKE_LOCK_MS)
        prefetchCloudSentences(sentenceIndex + 1)
    }

    private fun prefetchCloudSentences(startSentenceIndex: Int) {
        repeat(PREFETCH_AHEAD_COUNT) { offset ->
            prefetchCloudSentence(startSentenceIndex + offset)
        }
    }

    private fun prefetchCloudSentence(sentenceIndex: Int) {
        if (testPlayback || stopping) return
        if (_state.value.provider == TtsProvider.SYSTEM) return
        val item = speechItems.getOrNull(sentenceIndex) ?: return
        val file = audioCache.fileFor(sessionId, sentenceIndex)
        if (file.exists() && file.length() > 0L) {
            Log.d(TAG, "prefetch already cached index=$sentenceIndex file=${file.length()}")
            prefetchedIndices += sentenceIndex
            return
        }
        if (sentenceIndex in prefetchingIndices || sentenceIndex in prefetchedIndices) return

        val request = requestIdentity(item)
        prefetchingIndices += sentenceIndex
        Log.d(TAG, "prefetch start index=$sentenceIndex textLength=${item.text.length} textHash=${item.text.hashCode()}")
        prefetchExecutor.execute {
            runCatching {
                when (_state.value.provider) {
                    TtsProvider.MIMO -> synthesizeMimo(item.text, file)
                    TtsProvider.MICROSOFT -> synthesizeMicrosoft(item.text, file)
                    TtsProvider.SYSTEM -> Unit
                }
                audioCache.boostPcmWavVolume(file)
            }.onSuccess {
                mainHandler.post {
                    if (!isCurrentRequest(request)) return@post
                    prefetchingIndices.remove(sentenceIndex)
                    if (!stopping && file.exists() && file.length() > 0L) {
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
                    if (!isCurrentRequest(request)) return@post
                    prefetchingIndices.remove(sentenceIndex)
                    file.delete()
                    if (pendingPrefetchPlaybackIndex == sentenceIndex && !stopping) {
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
                state = TtsState.LOADING,
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
                state = TtsState.PLAYING,
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
        _state.value.provider != TtsProvider.SYSTEM || initialized

    private fun requestIdentity(item: SpeechItem): TtsRequestIdentity = TtsRequestIdentity(
        sessionId = sessionId,
        queueSequence = item.sentenceGlobalIndex,
        chapterPosition = chapterPosition,
        textHash = item.textHash,
    )

    private fun isCurrentRequest(request: TtsRequestIdentity, expectedQueueSequence: Int? = null): Boolean {
        val item = speechItems.getOrNull(request.queueSequence) ?: return false
        return request.sessionId == sessionId &&
            request.chapterPosition == chapterPosition &&
            request.textHash == item.textHash &&
            (expectedQueueSequence == null || request.queueSequence == expectedQueueSequence)
    }

    private fun handleSystemError(utteranceId: String?, message: String) {
        val request = TtsRequestId.parseSystem(utteranceId) ?: return
        mainHandler.post {
            if (isCurrentRequest(request, synthesizingIndex) && !stopping) {
                fail(message)
            }
        }
    }

    private fun fail(message: String) {
        val failedSessionId = sessionId
        val failedProvider = _state.value.provider
        Log.e(TAG, "fail provider=$failedProvider retained=true message=$message")
        mainHandler.post {
            if (failedSessionId != sessionId) return@post
            sessionId++
            wakeLockManager.release()
            stopping = true
            tts.stop()
            player.stop()
            player.clearMediaItems()
            stopping = false
            pendingParagraphIndex = null
            pendingSentenceIndex = null
            synthesizingIndex = -1
            pendingPrefetchPlaybackIndex = -1
            prefetchingIndices.clear()
            prefetchedIndices.clear()
            _state.update {
                it.copy(
                    state = TtsState.ERROR,
                    provider = failedProvider,
                    errorMessage = message,
                    engineName = displayEngineName(failedProvider),
                )
            }
        }
    }

    fun holdPlaybackWakeLock(timeoutMs: Long = BACKGROUND_CONTINUATION_WAKE_LOCK_MS) {
        wakeLockManager.acquire(timeoutMs)
    }

    private fun displayEngineName(provider: TtsProvider = _state.value.provider): String =
        when (provider) {
            TtsProvider.SYSTEM -> tts.defaultEngine.orEmpty()
            TtsProvider.MIMO -> "MiMo ${preferencesStore.mimoVoice()}"
            TtsProvider.MICROSOFT -> "Microsoft ${preferencesStore.microsoftVoice()}"
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
        private const val BACKGROUND_CONTINUATION_WAKE_LOCK_MS = 10 * 60 * 1000L
        private const val TAG = "YuraTts"
    }
}

