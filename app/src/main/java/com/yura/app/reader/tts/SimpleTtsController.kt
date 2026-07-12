package com.yura.app.reader.tts

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.content.edit
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
import com.yura.app.security.SecureSettings
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.BreakIterator
import java.util.Base64
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
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val audioDir = File(appContext.cacheDir, "tts-audio")
    private val prefs = appContext.getSharedPreferences("reader-tts", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        UiState(
            state = State.LOADING,
            provider = savedProvider(),
            mimoVoice = savedMimoVoice(),
            microsoftVoice = savedMicrosoftVoice(),
            microsoftVoices = savedMicrosoftVoices(),
            microsoftRegion = savedMicrosoftRegion(),
            hasMimoApiKey = savedMimoApiKey().isNotBlank(),
            hasMicrosoftApiKey = savedMicrosoftApiKey().isNotBlank(),
            playbackSpeed = savedPlaybackSpeed(),
        )
    )
    val state: StateFlow<UiState> = _state

    private lateinit var tts: TextToSpeech
    private val player = ExoPlayer.Builder(appContext).build()
    private val mediaSessionId = "yura-tts-${System.identityHashCode(this)}"
    private val paragraphs = mutableListOf<String>()
    private val speechItems = mutableListOf<SpeechItem>()
    private val prefetchingIndices = mutableSetOf<Int>()
    private val prefetchedIndices = mutableSetOf<Int>()
    private var mediaServiceBinder: MediaService.Binder? = null
    private var mediaServiceBound = false
    private var mediaServiceConnecting = false
    private val mediaServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            mediaServiceBinder = service as? MediaService.Binder
            mediaServiceBound = mediaServiceBinder != null
            mediaServiceConnecting = false
            registerTtsSession()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mediaServiceBinder = null
            mediaServiceBound = false
            mediaServiceConnecting = false
        }
    }
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
    private var playbackWakeLock: PowerManager.WakeLock? = null
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
        Log.d(TAG, "init provider=${savedProvider()} speed=${savedPlaybackSpeed()}")
        audioDir.mkdirs()
        createSystemTts()
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true
        )
        player.playbackParameters = PlaybackParameters(savedPlaybackSpeed())
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
                    val request = parseRequest(utteranceId) ?: return
                    mainHandler.post {
                        if (request.first != generation || request.second != synthesizingIndex || stopping) {
                            return@post
                        }
                        boostPcmWavVolume(audioFile(request.second))
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
        prefs.edit { putString(KEY_PROVIDER, provider.name) }
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
        prefs.edit { putString(KEY_MIMO_VOICE, voice) }
        _state.update { it.copy(mimoVoice = voice, engineName = displayEngineName()) }
    }

    fun setMimoApiKey(value: String) {
        val trimmed = value.trim()
        SecureSettings.putString(appContext, KEY_MIMO_API_KEY, trimmed)
        _state.update { it.copy(hasMimoApiKey = trimmed.isNotBlank()) }
    }

    fun currentMimoApiKey(): String = savedMimoApiKey()

    fun setMicrosoftVoice(voice: String) {
        prefs.edit { putString(KEY_MICROSOFT_VOICE, voice.trim().ifBlank { DEFAULT_MICROSOFT_VOICE }) }
        _state.update { it.copy(microsoftVoice = savedMicrosoftVoice(), engineName = displayEngineName()) }
    }

    fun setMicrosoftRegion(region: String) {
        prefs.edit { putString(KEY_MICROSOFT_REGION, region.trim()) }
        _state.update { it.copy(microsoftRegion = region.trim()) }
    }

    fun setMicrosoftApiKey(value: String) {
        val trimmed = value.trim()
        SecureSettings.putString(appContext, KEY_MICROSOFT_API_KEY, trimmed)
        _state.update { it.copy(hasMicrosoftApiKey = trimmed.isNotBlank()) }
    }

    fun currentMicrosoftApiKey(): String = savedMicrosoftApiKey()

    fun refreshMicrosoftVoices() {
        val apiKey = savedMicrosoftApiKey()
        val region = savedMicrosoftRegion()
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
                fetchMicrosoftVoices(region, apiKey)
            }.onSuccess { voices ->
                val nextVoices = voices.ifEmpty { MICROSOFT_VOICES }
                val selected = savedMicrosoftVoice()
                    .takeIf { saved -> nextVoices.any { it.shortName == saved } }
                    ?: nextVoices.first().shortName
                prefs.edit {
                    putString(KEY_MICROSOFT_VOICES, JSONArray().also { array ->
                        nextVoices.forEach { voice ->
                            array.put(
                                JSONObject()
                                    .put("shortName", voice.shortName)
                                    .put("displayName", voice.displayName)
                                    .put("locale", voice.locale)
                            )
                        }
                    }.toString())
                    putString(KEY_MICROSOFT_VOICE, selected)
                }
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
        prefs.edit { putFloat(KEY_PLAYBACK_SPEED, nearest) }
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
        cleanAudioFiles()
        if (!keepPlaybackSession) {
            ensureMediaControls()
        }

        paragraphs.clear()
        paragraphs.addAll(items.map { cleanTextForSpeech(it) }.filter { it.isNotBlank() })
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
        cleanAudioFiles()
        ensureMediaControls()

        paragraphs.clear()
        paragraphs += cleanTextForSpeech(text)
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
        releasePlaybackWakeLock()
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
        cleanAudioFiles()
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
            splitSentences(cleanTextForSpeech(paragraph)).forEachIndexed { sentenceIndex, sentence ->
                speechItems += SpeechItem(
                    paragraphIndex = paragraphIndex,
                    sentenceIndexInParagraph = sentenceIndex,
                    sentenceGlobalIndex = globalIndex,
                    text = cleanTextForSpeech(sentence),
                )
                globalIndex++
            }
        }
    }

    private fun splitSentences(text: String): List<String> {
        val cleaned = cleanTextForSpeech(text)
        val iterator = BreakIterator.getSentenceInstance(locale)
        iterator.setText(cleaned)

        val sentences = mutableListOf<String>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            cleaned.substring(start, end)
                .trim()
                .takeIf { it.isNotBlank() }
                ?.let { sentences += it }
            start = end
            end = iterator.next()
        }

        val baseSentences = sentences.ifEmpty {
            cleaned.split(Regex("(?<=[。！？!?\\.])\\s+|(?<=[。！？!?])"))
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .ifEmpty { listOf(cleaned.trim()) }
        }
        return baseSentences.flatMap { splitLongSpeechChunk(it) }
    }

    private fun splitLongSpeechChunk(text: String): List<String> {
        val cleaned = cleanTextForSpeech(text)
        if (cleaned.length <= MAX_TTS_CHUNK_CHARS) return listOf(cleaned)

        val chunks = mutableListOf<String>()
        var remaining = cleaned
        while (remaining.length > MAX_TTS_CHUNK_CHARS) {
            val splitAt = bestSpeechSplitIndex(remaining)
            chunks += remaining.substring(0, splitAt).trim()
            remaining = remaining.substring(splitAt).trimStart()
        }
        if (remaining.isNotBlank()) chunks += remaining.trim()
        return chunks.filter { it.isNotBlank() }.also {
            Log.d(
                TAG,
                "splitLongSpeechChunk length=${cleaned.length} chunks=${it.size} sizes=${it.joinToString { chunk -> chunk.length.toString() }}"
            )
        }
    }

    private fun bestSpeechSplitIndex(text: String): Int {
        val limit = MAX_TTS_CHUNK_CHARS.coerceAtMost(text.length)
        val min = MIN_TTS_CHUNK_CHARS.coerceAtMost(limit)
        val preferred = Regex("[，,、；;：:]")
        val loose = Regex("\\s+")

        preferred.findAll(text.substring(0, limit))
            .map { it.range.last + 1 }
            .filter { it >= min }
            .lastOrNull()
            ?.let { return it }

        loose.findAll(text.substring(0, limit))
            .map { it.range.last + 1 }
            .filter { it >= min }
            .lastOrNull()
            ?.let { return it }

        return limit
    }

    private fun cleanTextForSpeech(text: String): String {
        val cleaned = text
            .replace(Regex("[“”\"‘’']\\s*(?:[.．｡﹒․·•・…⋯︙︰]\\s*){2,}[“”\"‘’']"), " ")
            .replace(Regex("(?:[.．｡﹒․·•・…⋯︙︰]\\s*){2,}"), " ")
            .replace(Regex("[—–-]{2,}"), " ")
            .replace(Regex("[~～_＿=＝*＊#＃]{2,}"), " ")
            .replace(Regex("[“”\"‘’']\\s+[“”\"‘’']"), " ")
            .replace(Regex("([。！？!?，,、；;：:])\\1+"), "$1")
            .replace(Regex("[\\u200B-\\u200D\\uFEFF]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        return cleaned.takeUnless { value -> value.isBlank() || value.none { it.isLetterOrDigit() } }.orEmpty()
    }

    private fun ensureMediaControls() {
        if (mediaServiceBound) {
            registerTtsSession()
            return
        }
        if (mediaServiceConnecting) return

        mediaServiceConnecting = true
        val intent = Intent(MediaService.SERVICE_INTERFACE)
            .setClass(appContext, MediaService::class.java)
        runCatching { appContext.startService(intent) }
        runCatching {
            mediaServiceBound = appContext.bindService(intent, mediaServiceConnection, Context.BIND_AUTO_CREATE)
            if (!mediaServiceBound) {
                mediaServiceConnecting = false
            }
        }.onFailure {
            mediaServiceConnecting = false
        }
    }

    private fun registerTtsSession() {
        mediaServiceBinder?.openTtsSession(
            player = player,
            sessionId = mediaSessionId,
            onPrevious = { mainHandler.post { previous() } },
            onNext = { mainHandler.post { next() } },
            onStop = { mainHandler.post { stop() } },
        )
    }

    private fun releaseMediaControls() {
        mediaServiceBinder?.closeTtsSession()
        if (mediaServiceBound) {
            runCatching { appContext.unbindService(mediaServiceConnection) }
        }
        mediaServiceBinder = null
        mediaServiceBound = false
        mediaServiceConnecting = false
    }

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
        val file = audioFile(nextIndex)
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
        val file = audioFile(sentenceIndex)
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
        val utteranceId = requestId(generation, item.sentenceGlobalIndex)
        tts.setSpeechRate(savedPlaybackSpeed())
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
                    boostPcmWavVolume(file)
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
        val apiKey = savedMimoApiKey()
        Log.d(TAG, "synthesizeMimo voice=${savedMimoVoice()} textLength=${text.length} hasKey=${apiKey.isNotBlank()}")
        require(apiKey.isNotBlank()) { "MiMo API key is empty." }

        val body = JSONObject()
            .put("model", MIMO_MODEL)
            .put(
                "messages",
                org.json.JSONArray()
                    .put(
                        JSONObject()
                            .put("role", "user")
                            .put("content", "请用自然、清晰、适合阅读的语气朗读。")
                    )
                    .put(JSONObject().put("role", "assistant").put("content", text))
            )
            .put(
                "audio",
                JSONObject()
                    .put("voice", savedMimoVoice())
                    .put("format", "wav")
            )
            .toString()

        val json = postJson(
            url = MIMO_ENDPOINT,
            apiKey = apiKey,
            body = body,
        )
        val audioBase64 = json
            .optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optJSONObject("audio")
            ?.optString("data")
            .orEmpty()
        require(audioBase64.isNotBlank()) { "MiMo \u6ca1\u6709\u8fd4\u56de\u97f3\u9891\u6570\u636e\u3002" }
        file.writeBytes(Base64.getDecoder().decode(audioBase64))
    }

    private fun synthesizeMicrosoft(text: String, file: File) {
        val apiKey = savedMicrosoftApiKey()
        val region = savedMicrosoftRegion()
        Log.d(TAG, "synthesizeMicrosoft voice=${savedMicrosoftVoice()} region=$region textLength=${text.length} hasKey=${apiKey.isNotBlank()}")
        require(apiKey.isNotBlank()) { "Microsoft Speech key is empty." }
        require(region.isNotBlank()) { "Microsoft Speech region is empty." }

        val connection = URL("https://$region.tts.speech.microsoft.com/cognitiveservices/v1")
            .openConnection() as HttpURLConnection
        val ssml = """
            <speak version="1.0" xml:lang="zh-CN">
                <voice xml:lang="zh-CN" name="${escapeXml(savedMicrosoftVoice())}">${escapeXml(text)}</voice>
            </speak>
        """.trimIndent()
        connection.requestMethod = "POST"
        connection.connectTimeout = 15000
        connection.readTimeout = 45000
        connection.doOutput = true
        connection.setRequestProperty("Ocp-Apim-Subscription-Key", apiKey)
        connection.setRequestProperty("Content-Type", "application/ssml+xml")
        connection.setRequestProperty("X-Microsoft-OutputFormat", "riff-24khz-16bit-mono-pcm")
        connection.setRequestProperty("User-Agent", "Yura")
        connection.outputStream.use { it.write(ssml.toByteArray(Charsets.UTF_8)) }
        if (connection.responseCode !in 200..299) {
            val message = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            throw IllegalStateException("Microsoft \u6717\u8bfb\u5931\u8d25\uff08${connection.responseCode}\uff09\u3002$message")
        }
        connection.inputStream.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
    }

    private fun fetchMicrosoftVoices(region: String, apiKey: String): List<MicrosoftVoice> {
        val connection = URL("https://$region.tts.speech.microsoft.com/cognitiveservices/voices/list")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 12_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("Ocp-Apim-Subscription-Key", apiKey)

        val body = runCatching {
            if (connection.responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }
        }.getOrDefault("")

        if (connection.responseCode !in 200..299) {
            throw IllegalStateException("刷新 Microsoft 音色失败 (${connection.responseCode})。${body.take(160)}")
        }

        val array = JSONArray(body)
        return List(array.length()) { index -> array.getJSONObject(index) }
            .mapNotNull { item ->
                val shortName = item.optString("ShortName").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val locale = item.optString("Locale")
                if (!locale.startsWith("zh-", ignoreCase = true) && !locale.startsWith("en-", ignoreCase = true)) {
                    return@mapNotNull null
                }
                val localName = item.optString("LocalName").takeIf { it.isNotBlank() }
                val displayName = item.optString("DisplayName").takeIf { it.isNotBlank() }
                MicrosoftVoice(
                    shortName = shortName,
                    displayName = listOfNotNull(localName ?: displayName, locale)
                        .joinToString(" · "),
                    locale = locale,
                )
            }
            .distinctBy { it.shortName }
            .sortedWith(compareBy<MicrosoftVoice> { if (it.locale.startsWith("zh-", ignoreCase = true)) 0 else 1 }
                .thenBy { it.locale }
                .thenBy { it.displayName })
    }

    private fun playSynthesizedFile(sentenceIndex: Int) {
        val file = audioFile(sentenceIndex)
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
        player.playbackParameters = PlaybackParameters(savedPlaybackSpeed())
        player.prepare()
        player.play()
        markSentencePlaying(sentenceIndex, durationMs = audioDurationMs(file))
        releasePlaybackWakeLock()
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
        val file = audioFile(sentenceIndex)
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
                boostPcmWavVolume(file)
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

    private fun audioDurationMs(file: File): Long {
        return runCatching {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val duration = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L
            retriever.release()
            duration
        }.getOrDefault(0L)
    }

    private fun boostPcmWavVolume(file: File) {
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

            if (audioFormat != 1 || bitsPerSample != 16 || dataOffset < 0 || dataSize <= 0) {
                return
            }

            var index = dataOffset
            val end = (dataOffset + dataSize).coerceAtMost(bytes.size - 1)
            var peakBefore = 0
            var peakAfter = 0
            while (index + 1 <= end) {
                val sample = bytes.leShort(index)
                peakBefore = maxOf(peakBefore, kotlin.math.abs(sample.toInt()))
                val boosted = (sample * SPEECH_VOLUME_GAIN)
                    .roundToInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                peakAfter = maxOf(peakAfter, kotlin.math.abs(boosted))
                bytes.writeLeShort(index, boosted.toShort())
                index += 2
            }

            file.writeBytes(bytes)
            Log.d(TAG, "boostPcmWavVolume file=${file.name} gain=$SPEECH_VOLUME_GAIN peak=$peakBefore->$peakAfter")
        }.onFailure {
            Log.w(TAG, "boostPcmWavVolume skipped: ${it.message}")
        }
    }

    private fun ByteArray.hasAscii(offset: Int, value: String): Boolean =
        offset >= 0 &&
            offset + value.length <= size &&
            value.indices.all { index -> this[offset + index].toInt().toChar() == value[index] }

    private fun ByteArray.ascii(offset: Int, length: Int): String =
        buildString(length) {
            repeat(length) { append(this@ascii[offset + it].toInt().toChar()) }
        }

    private fun ByteArray.leInt(offset: Int): Int =
        (this[offset].toInt() and 0xff) or
            ((this[offset + 1].toInt() and 0xff) shl 8) or
            ((this[offset + 2].toInt() and 0xff) shl 16) or
            ((this[offset + 3].toInt() and 0xff) shl 24)

    private fun ByteArray.leUShort(offset: Int): Int =
        (this[offset].toInt() and 0xff) or ((this[offset + 1].toInt() and 0xff) shl 8)

    private fun ByteArray.leShort(offset: Int): Short =
        leUShort(offset).toShort()

    private fun ByteArray.writeLeShort(offset: Int, value: Short) {
        val intValue = value.toInt()
        this[offset] = (intValue and 0xff).toByte()
        this[offset + 1] = ((intValue shr 8) and 0xff).toByte()
    }

    private fun audioFile(sentenceIndex: Int): File =
        File(audioDir, "tts-${generation}-$sentenceIndex.wav")

    private fun canStartCurrentProvider(): Boolean =
        _state.value.provider != Provider.SYSTEM || initialized

    private fun requestId(generation: Int, sentenceIndex: Int): String =
        "$generation:$sentenceIndex"

    private fun systemRequestId(generation: Int, sentenceIndex: Int): String =
        "system:$generation:$sentenceIndex"

    private fun parseRequest(utteranceId: String?): Pair<Int, Int>? {
        val parts = utteranceId?.split(":") ?: return null
        if (parts.size != 2) return null
        return Pair(parts[0].toIntOrNull() ?: return null, parts[1].toIntOrNull() ?: return null)
    }

    private fun parseSystemRequest(utteranceId: String?): Pair<Int, Int>? {
        val parts = utteranceId?.split(":") ?: return null
        if (parts.size != 3 || parts[0] != "system") return null
        return Pair(parts[1].toIntOrNull() ?: return null, parts[2].toIntOrNull() ?: return null)
    }

    private fun cleanAudioFiles() {
        audioDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.startsWith("tts-")) {
                file.delete()
            }
        }
    }

    private fun fail(message: String) {
        Log.e(TAG, "fail: $message")
        if (_state.value.provider != Provider.SYSTEM && initialized && speechItems.isNotEmpty()) {
            val resumeAt = currentSentenceIndex.coerceAtLeast(0)
            mainHandler.post {
                prefs.edit { putString(KEY_PROVIDER, Provider.SYSTEM.name) }
                player.stop()
                _state.update { it.copy(provider = Provider.SYSTEM, errorMessage = "Cloud TTS failed; switched to system TTS.") }
                playFromSentence(resumeAt)
            }
            return
        }
        mainHandler.post {
            releasePlaybackWakeLock()
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
        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        val wakeLock = playbackWakeLock ?: powerManager
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$TAG:background-continuation")
            .also {
                it.setReferenceCounted(false)
                playbackWakeLock = it
            }
        runCatching {
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
            wakeLock.acquire(timeoutMs)
            Log.d(TAG, "holdPlaybackWakeLock timeoutMs=$timeoutMs")
        }.onFailure {
            Log.w(TAG, "holdPlaybackWakeLock failed: ${it.message}")
        }
    }

    private fun releasePlaybackWakeLock() {
        val wakeLock = playbackWakeLock ?: return
        runCatching {
            if (wakeLock.isHeld) {
                wakeLock.release()
                Log.d(TAG, "releasePlaybackWakeLock")
            }
        }
    }

    private fun postJson(url: String, apiKey: String, body: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 15000
        connection.readTimeout = 45000
        connection.doOutput = true
        connection.setRequestProperty("api-key", apiKey)
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json")
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val responseText = if (connection.responseCode in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            val message = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            throw IllegalStateException("MiMo \u6717\u8bfb\u5931\u8d25\uff08${connection.responseCode}\uff09\u3002${message.ifBlank { "\u6ca1\u6709\u9519\u8bef\u8be6\u60c5\u3002" }}")
        }
        return JSONObject(responseText)
    }

    private fun displayEngineName(provider: Provider = _state.value.provider): String =
        when (provider) {
            Provider.SYSTEM -> tts.defaultEngine.orEmpty()
            Provider.MIMO -> "MiMo ${savedMimoVoice()}"
            Provider.MICROSOFT -> "Microsoft ${savedMicrosoftVoice()}"
        }

    private fun savedProvider(): Provider =
        runCatching { Provider.valueOf(prefs.getString(KEY_PROVIDER, Provider.SYSTEM.name).orEmpty()) }
            .getOrDefault(Provider.SYSTEM)

    private fun savedMimoVoice(): String =
        prefs.getString(KEY_MIMO_VOICE, DEFAULT_MIMO_VOICE).orEmpty().ifBlank { DEFAULT_MIMO_VOICE }

    private fun savedMimoApiKey(): String =
        SecureSettings.migrateString(appContext, TTS_PREFS_NAME, KEY_MIMO_API_KEY)

    private fun savedMicrosoftVoice(): String =
        prefs.getString(KEY_MICROSOFT_VOICE, DEFAULT_MICROSOFT_VOICE).orEmpty().ifBlank { DEFAULT_MICROSOFT_VOICE }

    private fun savedMicrosoftVoices(): List<MicrosoftVoice> {
        val raw = prefs.getString(KEY_MICROSOFT_VOICES, "").orEmpty()
        val fetched = runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                MicrosoftVoice(
                    shortName = item.optString("shortName"),
                    displayName = item.optString("displayName"),
                    locale = item.optString("locale"),
                )
            }.filter { it.shortName.isNotBlank() && it.displayName.isNotBlank() }
        }.getOrDefault(emptyList())
        return fetched.ifEmpty { MICROSOFT_VOICES }
    }

    private fun savedMicrosoftRegion(): String =
        prefs.getString(KEY_MICROSOFT_REGION, "").orEmpty()

    private fun savedMicrosoftApiKey(): String =
        SecureSettings.migrateString(appContext, TTS_PREFS_NAME, KEY_MICROSOFT_API_KEY)

    private fun savedPlaybackSpeed(): Float {
        val saved = prefs.getFloat(KEY_PLAYBACK_SPEED, DEFAULT_PLAYBACK_SPEED)
        return PLAYBACK_SPEEDS.minBy { kotlin.math.abs(it - saved) }
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
        private const val TTS_PREFS_NAME = "reader-tts"
        private const val KEY_PROVIDER = "provider"
        private const val KEY_MIMO_VOICE = "mimo_voice"
        private const val KEY_MIMO_API_KEY = "mimo_api_key"
        private const val KEY_MICROSOFT_VOICE = "microsoft_voice"
        private const val KEY_MICROSOFT_VOICES = "microsoft_voices"
        private const val KEY_MICROSOFT_REGION = "microsoft_region"
        private const val KEY_MICROSOFT_API_KEY = "microsoft_api_key"
        private const val KEY_PLAYBACK_SPEED = "playback_speed"
        private const val MAX_TTS_CHUNK_CHARS = 160
        private const val MIN_TTS_CHUNK_CHARS = 70
        private const val PREFETCH_AHEAD_COUNT = 2
        private const val SPEECH_VOLUME_GAIN = 1.6f
        private const val BACKGROUND_CONTINUATION_WAKE_LOCK_MS = 2 * 60 * 1000L
        private const val TAG = "YuraTts"
    }
}

