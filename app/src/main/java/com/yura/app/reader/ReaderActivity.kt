@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package com.yura.app.reader

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commitNow
import androidx.lifecycle.lifecycleScope
import com.yura.app.data.Book
import com.yura.app.data.YuraDatabase
import com.yura.app.library.ReadiumServices
import com.yura.app.reader.tts.SimpleTtsController
import com.yura.app.ui.theme.YuraTheme
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.json.JSONArray
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.preferences.Spread
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.allAreHtml
import org.readium.r2.shared.util.asset.Asset
import org.readium.r2.shared.util.getOrElse

class ReaderActivity : FragmentActivity() {
    private val dao by lazy { YuraDatabase.get(this).yuraDao() }
    private val readium by lazy { ReadiumServices(this) }

    private var asset: Asset? = null
    private var publication: Publication? = null
    private var navigatorFragment: EpubNavigatorFragment? = null
    private var showControlsCallback: (() -> Unit)? = null
    private var lastSavedProgressionJson: String? = null
    private var currentTtsWebView: WebView? = null
    private var currentTtsDomIndexByParagraph = emptyList<Int>()
    private var currentLocator: Locator? = null
    private var activeReaderPreferences: EpubPreferences = ReaderPreferencesStore.defaults
    private var activeTtsReadingOrderIndex = -1
    private var readerForeground = false
    private var pendingForegroundTtsLocator: Locator? = null
    private var pendingForegroundTtsParagraphIndex = -1
    private val ttsController by lazy { SimpleTtsController(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val bookId = intent.getLongExtra(EXTRA_BOOK_ID, -1L)
        if (bookId <= 0L) {
            finish()
            return
        }

        ttsController.onParagraphChanged = { index ->
            runOnUiThread {
                if (readerForeground) {
                    highlightTtsParagraph(index)
                } else {
                    pendingForegroundTtsParagraphIndex = index
                }
            }
        }
        ttsController.onQueueEnded = {
            runOnUiThread { continueTtsToNextChapter() }
        }

        setContent {
            YuraTheme {
                ReaderScreen(bookId = bookId)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        readerForeground = true
        pendingForegroundTtsLocator?.let { locator ->
            pendingForegroundTtsLocator = null
            navigatorFragment?.go(locator, animated = false)
            scheduleForegroundTtsSync(900)
        } ?: scheduleForegroundTtsSync(300)
    }

    override fun onStop() {
        readerForeground = false
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        ttsController.onParagraphChanged = null
        ttsController.onQueueEnded = null
        navigatorFragment = null
        publication?.close()
        asset?.close()
        ttsController.shutdown()
        publication = null
        asset = null
    }

    @Composable
    private fun ReaderScreen(bookId: Long) {
        var state by remember { mutableStateOf<ReaderState>(ReaderState.Loading) }
        var controlsVisible by remember { mutableStateOf(false) }
        var currentPage by remember { mutableIntStateOf(1) }
        var totalPages by remember { mutableIntStateOf(1) }
        var progressLabel by remember { mutableStateOf("0%") }
        var chapterTitle by remember { mutableStateOf("") }
        var tocVisible by remember { mutableStateOf(false) }
        var settingsVisible by remember { mutableStateOf(false) }
        var ttsVisible by remember { mutableStateOf(false) }
        var readerPreferences by remember { mutableStateOf(loadReaderPreferences()) }
        var autoReaderTheme by remember { mutableStateOf(ReaderPreferencesStore.isAutoTheme(this)) }
        val systemDark = isSystemInDarkTheme()
        val effectiveReaderPreferences = remember(readerPreferences, autoReaderTheme, systemDark) {
            ReaderPreferencesStore.resolveTheme(readerPreferences, autoReaderTheme, systemDark)
        }
        val ttsUiState by ttsController.state.collectAsState()
        val ttsActive = ttsUiState.state == SimpleTtsController.State.LOADING ||
            ttsUiState.state == SimpleTtsController.State.PLAYING ||
            ttsUiState.state == SimpleTtsController.State.PAUSED
        activeReaderPreferences = effectiveReaderPreferences

        DisposableEffect(Unit) {
            showControlsCallback = {
                controlsVisible = !controlsVisible
            }
            onDispose { showControlsCallback = null }
        }

        LaunchedEffect(bookId) {
            state = openBook(bookId, effectiveReaderPreferences)
        }

        LaunchedEffect(effectiveReaderPreferences) {
            activeReaderPreferences = effectiveReaderPreferences
            navigatorFragment?.submitPreferences(effectiveReaderPreferences)
            applyMediaIndentFix(effectiveReaderPreferences)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            when (val current = state) {
                ReaderState.Loading -> LoadingReader()
                is ReaderState.Error -> ErrorReader(message = current.message, onBack = ::finish)
                is ReaderState.Ready -> ReadyReader(
                    data = current,
                    controlsVisible = controlsVisible,
                    currentPage = currentPage,
                    totalPages = totalPages,
                    progressLabel = progressLabel,
                    chapterTitle = chapterTitle,
                    onControlsVisibleChange = { controlsVisible = it },
                    onToc = {
                        controlsVisible = false
                        tocVisible = true
                    },
                    onSettings = {
                        controlsVisible = false
                        settingsVisible = true
                    },
                    onTts = {
                        if (ttsActive) {
                            ttsController.stop()
                            ttsVisible = false
                            controlsVisible = false
                        } else {
                            controlsVisible = false
                            ttsVisible = true
                            val ready = state as? ReaderState.Ready
                            if (ready != null) {
                                val coverFile = File(ready.book.cover).takeIf { it.exists() && it.length() > 0L }
                                ttsController.setMediaInfo(
                                    title = ready.book.title,
                                    subtitle = ready.book.author,
                                    artworkUri = coverFile?.let { Uri.fromFile(it) },
                                    artworkData = coverFile?.readBytes(),
                                )
                                activeTtsReadingOrderIndex = currentReadingOrderIndex(ready.publication)
                            }
                            startTtsFromTaggedPage()
                        }
                    },
                    onPageChanged = { page, total, locator ->
                        currentLocator = locator
                        currentPage = page + 1
                        totalPages = total.coerceAtLeast(1)
                        progressLabel = "${((locator.locations.totalProgression ?: 0.0) * 100).toInt()}%"
                        chapterTitle = locator.title.orEmpty()
                    },
                )
            }

            val ready = state as? ReaderState.Ready
            if (settingsVisible && ready != null) {
                ReaderSettingsSheet(
                    preferences = readerPreferences,
                    autoTheme = autoReaderTheme,
                    onDismiss = { settingsVisible = false },
                    onAutoThemeChange = { enabled ->
                        autoReaderTheme = enabled
                        ReaderPreferencesStore.saveAutoTheme(this@ReaderActivity, enabled)
                    },
                    onPreferencesChange = { updated ->
                        readerPreferences = updated
                        val effective = ReaderPreferencesStore.resolveTheme(updated, autoReaderTheme, systemDark)
                        activeReaderPreferences = effective
                        saveReaderPreferences(updated)
                        navigatorFragment?.submitPreferences(effective)
                        applyMediaIndentFix(effective)
                    },
                )
            }
            TtsPanel(
                controller = ttsController,
                visible = ttsVisible,
                chapterTitle = chapterTitle,
                onDismiss = { ttsVisible = false },
            )
            if (ttsActive && !ttsVisible) {
                DraggableTtsFloatingButton(
                    onClick = {
                        controlsVisible = false
                        ttsVisible = true
                        syncForegroundTtsHighlight()
                    },
                    controlsVisible = controlsVisible,
                    loadPosition = ::loadTtsFloatingPosition,
                    savePosition = ::saveTtsFloatingPosition,
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding(),
                )
            }
            if (tocVisible && ready != null) {
                TocSheet(
                    links = ready.publication.tableOfContents,
                    publication = ready.publication,
                    currentLocator = currentLocator,
                    onDismiss = { tocVisible = false },
                    onGo = { link ->
                        ready.publication.locatorFromLink(link)?.let { locator ->
                            navigatorFragment?.go(locator, animated = true)
                        }
                        tocVisible = false
                    },
                )
            }
        }
    }

    @Composable
    private fun ReadyReader(
        data: ReaderState.Ready,
        controlsVisible: Boolean,
        currentPage: Int,
        totalPages: Int,
        progressLabel: String,
        chapterTitle: String,
        onControlsVisibleChange: (Boolean) -> Unit,
        onToc: () -> Unit,
        onSettings: () -> Unit,
        onTts: () -> Unit,
        onPageChanged: (Int, Int, Locator) -> Unit,
    ) {
        Box(Modifier.fillMaxSize()) {
            EpubNavigatorHost(data = data, onPageChanged = onPageChanged)
            ReaderHud(currentPage, totalPages, progressLabel, chapterTitle)

            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.18f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onControlsVisibleChange(false) },
                )
            }

            AnimatedVisibility(
                visible = controlsVisible,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                ReaderTopBar(title = data.book.title, onBack = ::finish)
            }

            AnimatedVisibility(
                visible = controlsVisible,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                ReaderControlBar(
                    onToc = onToc,
                    onSettings = onSettings,
                    onTts = onTts,
                )
            }
        }
    }

    private fun cleanTtsTextForSync(text: String): String {
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

    private fun startTtsFromTaggedPage(startParagraphOverride: Int? = null) {
        if (activeTtsReadingOrderIndex < 0) {
            activeTtsReadingOrderIndex = currentReadingOrderIndex()
        }
        val webView = navigatorFragment?.publicationView?.let { findWebView(it) }
        if (webView == null) {
            Toast.makeText(this, "\u5f53\u524d\u9875\u9762\u8fd8\u6ca1\u6709\u51c6\u5907\u597d\u6717\u8bfb", Toast.LENGTH_SHORT).show()
            return
        }
        currentTtsWebView = webView

        webView.evaluateJavascript(
            """
            (function() {
                if (!document.getElementById('tts-style')) {
                    var s = document.createElement('style');
                    s.id = 'tts-style';
                    s.textContent = '.tts-active { background: linear-gradient(90deg, rgba(244,211,94,0.34), rgba(244,211,94,0.16)) !important; box-shadow: inset 4px 0 0 #f4d35e, 0 0 0 2px rgba(244,211,94,0.25) !important; border-radius: 6px !important; transition: background-color 160ms ease, box-shadow 160ms ease; }';
                    document.head.appendChild(s);
                }
                function cleanTtsText(text) {
                    var cleaned = (text || '')
                        .replace(/[“”"'‘’]\s*(?:[.．｡﹒․·•・…⋯︙︰]\s*){2,}[“”"'‘’]/g, ' ')
                        .replace(/(?:[.．｡﹒․·•・…⋯︙︰]\s*){2,}/g, ' ')
                        .replace(/[—–-]{2,}/g, ' ')
                        .replace(/[~～_＿=＝*＊#＃]{2,}/g, ' ')
                        .replace(/[“”"'‘’]\s+[“”"'‘’]/g, ' ')
                        .replace(/([。！？!?，,、；;：:])\1+/g, '$1')
                        .replace(/[\u200B-\u200D\uFEFF]/g, '')
                        .replace(/\s+/g, ' ')
                        .trim();
                    return /[0-9A-Za-z\u4E00-\u9FFF\u3040-\u30FF\uAC00-\uD7AF]/.test(cleaned) ? cleaned : '';
                }
                function visibleRect(element) {
                    var rects = element.getClientRects();
                    var best = null;
                    for (var j = 0; j < rects.length; j++) {
                        var rect = rects[j];
                        if (rect.width <= 0 || rect.height <= 0) continue;
                        if (rect.right <= 0 || rect.left >= window.innerWidth || rect.bottom <= 0 || rect.top >= window.innerHeight) continue;
                        var top = Math.max(0, rect.top);
                        var left = Math.max(0, rect.left);
                        var bestTop = best === null ? Number.POSITIVE_INFINITY : Math.max(0, best.top);
                        var bestLeft = best === null ? Number.POSITIVE_INFINITY : Math.max(0, best.left);
                        if (top < bestTop || (top === bestTop && left < bestLeft)) best = rect;
                    }
                    return best;
                }
                var nodes = Array.prototype.slice.call(
                    document.querySelectorAll('p, h1, h2, h3, h4, h5, h6, li, blockquote')
                );
                var readable = [];
                var firstVisible = 0;
                var foundVisible = false;
                var bestTop = Number.POSITIVE_INFINITY;
                var bestLeft = Number.POSITIVE_INFINITY;
                var readableIndex = 0;
                for (var i = 0; i < nodes.length; i++) {
                    nodes[i].removeAttribute('data-tts-readable-idx');
                    var text = cleanTtsText(nodes[i].innerText);
                    if (!text || text.length < 2) continue;
                    nodes[i].setAttribute('data-tts-readable-idx', readableIndex);
                    readable.push(text);
                    var rect = visibleRect(nodes[i]);
                    if (rect) {
                        var top = Math.max(0, rect.top);
                        var left = Math.max(0, rect.left);
                        if (top < bestTop || (top === bestTop && left < bestLeft)) {
                            bestTop = top;
                            bestLeft = left;
                            firstVisible = readableIndex;
                            foundVisible = true;
                        }
                    }
                    readableIndex++;
                }
                if (readable.length === 0 && document.body) {
                    var bodyText = cleanTtsText(document.body.innerText);
                    if (bodyText) readable.push(bodyText);
                }
                return JSON.stringify({ paragraphs: readable, firstVisible: firstVisible, foundVisible: foundVisible });
            })();
            """.trimIndent(),
        ) { raw ->
            val payload = runCatching {
                raw
                    ?.takeIf { it != "null" }
                    ?.let { JSONObject(JSONArray("[$it]").getString(0)) }
            }.getOrNull()
            val rawParagraphs = payload
                ?.optJSONArray("paragraphs")
                ?.let { array -> List(array.length()) { index -> array.optString(index) } }
                .orEmpty()
            val firstVisibleRaw = payload?.optInt("firstVisible", 0) ?: 0
            val paragraphs = mutableListOf<String>()
            rawParagraphs.forEach { rawText ->
                val cleaned = cleanTtsTextForSync(rawText)
                if (cleaned.isNotBlank()) {
                    paragraphs += cleaned
                }
            }
            currentTtsDomIndexByParagraph = paragraphs.indices.toList()
            val firstVisibleCleaned = firstVisibleRaw.coerceIn(0, (paragraphs.size - 1).coerceAtLeast(0))
            val firstVisible = (startParagraphOverride ?: firstVisibleCleaned)
                .coerceIn(0, (paragraphs.size - 1).coerceAtLeast(0))
            android.util.Log.d(
                "YuraTts",
                "extractTagged raw=${rawParagraphs.size} paragraphs=${paragraphs.size} firstRaw=$firstVisibleRaw firstVisible=$firstVisible dom=${currentTtsDomIndexByParagraph.getOrNull(firstVisible)} found=${payload?.optBoolean("foundVisible", false)} first=${paragraphs.getOrNull(firstVisible)?.take(40)}",
            )

            if (paragraphs.isEmpty()) {
                Toast.makeText(this, "\u6ca1\u6709\u53ef\u6717\u8bfb\u7684\u6587\u5b57", Toast.LENGTH_SHORT).show()
            } else {
                ttsController.speak(paragraphs, firstVisible)
            }
        }
    }

    private fun highlightTtsParagraph(index: Int) {
        val webView = currentTtsWebView ?: return
        if (index < 0) {
            webView.evaluateJavascript(
                """
                (function() {
                    var els = document.querySelectorAll('.tts-active');
                    for (var i = 0; i < els.length; i++) els[i].classList.remove('tts-active');
                })();
                """.trimIndent(),
                null,
            )
            return
        }
        val domIndex = currentTtsDomIndexByParagraph.getOrNull(index) ?: index
        webView.evaluateJavascript(
            """
            (function() {
                var els = document.querySelectorAll('.tts-active');
                for (var i = 0; i < els.length; i++) els[i].classList.remove('tts-active');
                var target = document.querySelector('[data-tts-readable-idx="$domIndex"]');
                if (target) {
                    target.classList.add('tts-active');
                    var rects = target.getClientRects();
                    var best = null;
                    var bestArea = -1;
                    var comfortable = false;
                    for (var i = 0; i < rects.length; i++) {
                        var rect = rects[i];
                        if (rect.width <= 0 || rect.height <= 0) continue;
                        var left = Math.max(rect.left, 0);
                        var top = Math.max(rect.top, 0);
                        var right = Math.min(rect.right, window.innerWidth);
                        var bottom = Math.min(rect.bottom, window.innerHeight);
                        var area = Math.max(0, right - left) * Math.max(0, bottom - top);
                        if (area > bestArea) {
                            bestArea = area;
                            best = rect;
                        }
                        if (
                            rect.left >= 12 &&
                            rect.right <= window.innerWidth - 12 &&
                            rect.top >= window.innerHeight * 0.12 &&
                            rect.bottom <= window.innerHeight * 0.82
                        ) {
                            comfortable = true;
                        }
                    }
                    if (!comfortable && best) {
                        var scroller = document.scrollingElement || document.documentElement;
                        scroller.scrollTo({
                            left: Math.max(0, scroller.scrollLeft + best.left - Math.max(24, (window.innerWidth - best.width) / 2)),
                            top: Math.max(0, scroller.scrollTop + best.top - window.innerHeight * 0.32),
                            behavior: 'smooth'
                        });
                    }
                }
            })();
            """.trimIndent(),
            null,
        )
    }

    private fun syncForegroundTtsHighlight() {
        if (!readerForeground) return
        val state = ttsController.state.value
        if (state.state != SimpleTtsController.State.PLAYING &&
            state.state != SimpleTtsController.State.PAUSED &&
            state.state != SimpleTtsController.State.LOADING
        ) {
            return
        }
        val targetIndex = state.paragraphIndex
            .takeIf { it >= 0 }
            ?: pendingForegroundTtsParagraphIndex.takeIf { it >= 0 }
            ?: return
        tagCurrentPageForTtsHighlight(targetIndex)
    }

    private fun scheduleForegroundTtsSync(delayMillis: Long) {
        lifecycleScope.launch {
            kotlinx.coroutines.delay(delayMillis)
            syncForegroundTtsHighlight()
            kotlinx.coroutines.delay(450)
            syncForegroundTtsHighlight()
        }
    }

    private fun tagCurrentPageForTtsHighlight(targetParagraphIndex: Int) {
        val webView = navigatorFragment?.publicationView?.let { findWebView(it) } ?: return
        currentTtsWebView = webView
        webView.evaluateJavascript(
            """
            (function() {
                if (!document.getElementById('tts-style')) {
                    var s = document.createElement('style');
                    s.id = 'tts-style';
                    s.textContent = '.tts-active { background: linear-gradient(90deg, rgba(244,211,94,0.34), rgba(244,211,94,0.16)) !important; box-shadow: inset 4px 0 0 #f4d35e, 0 0 0 2px rgba(244,211,94,0.25) !important; border-radius: 6px !important; transition: background-color 160ms ease, box-shadow 160ms ease; }';
                    document.head.appendChild(s);
                }
                function cleanTtsText(text) {
                    var cleaned = (text || '')
                        .replace(/[“”"'‘’]\s*(?:[.．｡﹒․·•・…⋯︙︰]\s*){2,}[“”"'‘’]/g, ' ')
                        .replace(/(?:[.．｡﹒․·•・…⋯︙︰]\s*){2,}/g, ' ')
                        .replace(/[—–-]{2,}/g, ' ')
                        .replace(/[~～_＿=＝*＊#＃]{2,}/g, ' ')
                        .replace(/[“”"'‘’]\s+[“”"'‘’]/g, ' ')
                        .replace(/([。！？!?，,、；;：:])\1+/g, '$1')
                        .replace(/[\u200B-\u200D\uFEFF]/g, '')
                        .replace(/\s+/g, ' ')
                        .trim();
                    return /[0-9A-Za-z\u4E00-\u9FFF\u3040-\u30FF\uAC00-\uD7AF]/.test(cleaned) ? cleaned : '';
                }
                var nodes = Array.prototype.slice.call(
                    document.querySelectorAll('p, h1, h2, h3, h4, h5, h6, li, blockquote')
                );
                var readableIndex = 0;
                for (var i = 0; i < nodes.length; i++) {
                    nodes[i].removeAttribute('data-tts-readable-idx');
                    var text = cleanTtsText(nodes[i].innerText);
                    if (!text || text.length < 2) continue;
                    nodes[i].setAttribute('data-tts-readable-idx', readableIndex);
                    readableIndex++;
                }
                return readableIndex;
            })();
            """.trimIndent(),
        ) {
            val count = it.trim().toIntOrNull() ?: 0
            currentTtsDomIndexByParagraph = (0 until count).toList()
            highlightTtsParagraph(targetParagraphIndex)
        }
    }

    private fun currentReadingOrderIndex(publication: Publication? = this.publication): Int {
        val activePublication = publication ?: return -1
        val href = currentLocator?.href?.toString()?.substringBefore('#') ?: return -1
        return activePublication.readingOrder.indexOfFirst { link ->
            val linkHref = link.href.toString().substringBefore('#')
            linkHref == href || href.startsWith(linkHref) || linkHref.startsWith(href)
        }
    }

    private fun continueTtsToNextChapter() {
        val publication = publication ?: return
        val nextIndex = activeTtsReadingOrderIndex + 1
        val link = publication.readingOrder.getOrNull(nextIndex)
        if (link == null) {
            activeTtsReadingOrderIndex = -1
            currentTtsDomIndexByParagraph = emptyList()
            highlightTtsParagraph(-1)
            ttsController.stop()
            return
        }
        val locator = publication.locatorFromLink(link)
        if (locator == null) {
            activeTtsReadingOrderIndex = nextIndex
            continueTtsToNextChapter()
            return
        }
        if (!readerForeground) {
            continueTtsToNextChapterInBackground(publication, link, locator, nextIndex)
            return
        }
        currentTtsWebView = null
        currentTtsDomIndexByParagraph = emptyList()
        highlightTtsParagraph(-1)
        val moved = navigatorFragment?.go(locator, animated = true) == true
        if (!moved) {
            activeTtsReadingOrderIndex = nextIndex
            continueTtsToNextChapter()
            return
        }
        lifecycleScope.launch {
            kotlinx.coroutines.delay(900)
            activeTtsReadingOrderIndex = nextIndex
            startTtsFromTaggedPage(startParagraphOverride = 0)
        }
    }

    private fun continueTtsToNextChapterInBackground(
        publication: Publication,
        link: Link,
        locator: Locator,
        nextIndex: Int,
    ) {
        pendingForegroundTtsLocator = locator
        activeTtsReadingOrderIndex = nextIndex
        currentTtsWebView = null
        currentTtsDomIndexByParagraph = emptyList()
        lifecycleScope.launch {
            val paragraphs = withContext(Dispatchers.IO) {
                extractTtsParagraphsFromResource(publication, link)
            }
            if (paragraphs.isEmpty()) {
                continueTtsToNextChapter()
            } else {
                ttsController.speak(paragraphs, 0)
            }
        }
    }

    private suspend fun extractTtsParagraphsFromResource(publication: Publication, link: Link): List<String> {
        val resource = publication.get(link) ?: return emptyList()
        val bytes = try {
            resource.read().getOrElse { ByteArray(0) }
        } finally {
            resource.close()
        }
        val html = bytes.toString(Charsets.UTF_8)
        val document = Jsoup.parse(html)
        val nodes = document.select("p, h1, h2, h3, h4, h5, h6, li, blockquote")
        val paragraphs = nodes
            .map { cleanTtsTextForSync(it.text()) }
            .filter { it.length >= 2 }
        return paragraphs.ifEmpty {
            listOf(cleanTtsTextForSync(document.body().text()))
                .filter { it.length >= 2 }
        }
    }

    private fun findWebView(view: View): WebView? {
        val candidates = mutableListOf<WebView>()
        collectWebViews(view, candidates)
        return candidates.maxByOrNull { webView ->
            val rect = Rect()
            if (webView.isShown && webView.getGlobalVisibleRect(rect)) {
                rect.width() * rect.height()
            } else {
                0
            }
        }
    }

    private fun collectWebViews(view: View?, result: MutableList<WebView>) {
        if (view is WebView) {
            result += view
            return
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                collectWebViews(view.getChildAt(index), result)
            }
        }
    }

    @Composable
    private fun EpubNavigatorHost(
        data: ReaderState.Ready,
        onPageChanged: (Int, Int, Locator) -> Unit,
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                FragmentContainerView(context).apply {
                    id = View.generateViewId()
                }
            },
            update = { container ->
                if (navigatorFragment == null) {
                    setupNavigatorFragment(container.id, data, onPageChanged)
                }
            },
        )
    }

    private suspend fun openBook(bookId: Long, initialPreferences: EpubPreferences): ReaderState =
        withContext(Dispatchers.IO) {
            runCatching {
                val book = dao.book(bookId) ?: error("\u627e\u4e0d\u5230\u8fd9\u672c\u4e66")
                dao.markBookRead(bookId, System.currentTimeMillis())
                val openedAsset = readium.assetRetriever.retrieve(book.url, book.mediaType)
                    .getOrElse { error("无法读取图书：${it.message}") }
                val openedPublication = readium.publicationOpener.open(
                    openedAsset,
                    allowUserInteraction = true,
                ).getOrElse { error("\u65e0\u6cd5\u6253\u5f00\u56fe\u4e66\uff1a${it.message}") }

                if (!openedPublication.conformsTo(Publication.Profile.EPUB) &&
                    !openedPublication.readingOrder.allAreHtml
                ) {
                    openedPublication.close()
                    openedAsset.close()
                    error("\u5f53\u524d\u9605\u8bfb\u5668\u53ea\u652f\u6301 EPUB")
                }

                asset = openedAsset
                publication = openedPublication
                ReaderState.Ready(
                    book = book,
                    publication = openedPublication,
                    initialLocator = book.progression
                        .takeUnless { it.isBlank() || it == "{}" }
                        ?.let { Locator.fromJSON(JSONObject(it)) },
                    initialPreferences = initialPreferences,
                    navigatorFactory = EpubNavigatorFactory(openedPublication),
                )
            }.getOrElse {
                ReaderState.Error(it.message ?: "\u6253\u5f00\u56fe\u4e66\u5931\u8d25")
            }
        }

    private fun setupNavigatorFragment(
        containerId: Int,
        data: ReaderState.Ready,
        onPageChanged: (Int, Int, Locator) -> Unit,
    ) {
        val fragmentFactory = data.navigatorFactory.createFragmentFactory(
            initialLocator = data.initialLocator,
            initialPreferences = data.initialPreferences,
            configuration = EpubNavigatorFragment.Configuration(),
            paginationListener = object : EpubNavigatorFragment.PaginationListener {
                override fun onPageChanged(pageIndex: Int, totalPages: Int, locator: Locator) {
                    onPageChanged(pageIndex, totalPages, locator)
                    applyMediaIndentFix(activeReaderPreferences)
                    val progressionJson = locator.toJSON().toString()
                    if (progressionJson != lastSavedProgressionJson) {
                        lastSavedProgressionJson = progressionJson
                        lifecycleScope.launch(Dispatchers.IO) {
                            dao.saveProgression(data.book.id, progressionJson, System.currentTimeMillis())
                        }
                    }
                }
            },
        )

        supportFragmentManager.fragmentFactory = fragmentFactory
        supportFragmentManager.commitNow {
            replace(containerId, EpubNavigatorFragment::class.java, Bundle(), NAVIGATOR_TAG)
        }

        navigatorFragment = supportFragmentManager.findFragmentByTag(NAVIGATOR_TAG) as? EpubNavigatorFragment
        navigatorFragment?.let { navigator ->
            navigator.addInputListener(
                object : InputListener {
                    private val delegate = DirectionalNavigationAdapter(
                        navigator = navigator,
                        tapEdges = setOf(DirectionalNavigationAdapter.TapEdge.Horizontal),
                        animatedTransition = true,
                    )

                    override fun onTap(event: TapEvent): Boolean {
                        return delegate.onTap(event)
                    }
                },
            )
            navigator.addInputListener(
                object : InputListener {
                    override fun onTap(event: TapEvent): Boolean {
                        val width = navigator.publicationView.width
                        val centerZone = width * 0.3f
                        if (kotlin.math.abs(event.point.x - width / 2f) < centerZone) {
                            lifecycleScope.launch {
                                showControlsCallback?.invoke()
                            }
                        }
                        return false
                    }
                },
            )
        }
        applyMediaIndentFix(data.initialPreferences)
    }

    private fun applyMediaIndentFix(preferences: EpubPreferences = activeReaderPreferences) {
        val publisherStylesEnabled = preferences.publisherStyles != false
        val script = if (publisherStylesEnabled) {
            """
            (function() {
                var style = document.getElementById('yura-media-indent-fix');
                if (style) style.remove();
                var blocks = document.querySelectorAll('.yura-media-block');
                for (var i = 0; i < blocks.length; i++) blocks[i].classList.remove('yura-media-block');
            })();
            """.trimIndent()
        } else {
            """
            (function() {
                var style = document.getElementById('yura-media-indent-fix');
                if (!style) {
                    style = document.createElement('style');
                    style.id = 'yura-media-indent-fix';
                    document.head.appendChild(style);
                }
                style.textContent = [
                    'img, svg, video, canvas, picture { text-indent: 0 !important; margin-inline-start: 0 !important; }',
                    'p:has(img), p:has(svg), p:has(picture), div:has(> img), figure:has(img) { text-indent: 0 !important; }',
                    'body > img, body > picture, body > svg { display: block !important; margin-inline-start: auto !important; margin-inline-end: auto !important; }',
                    '.yura-media-block, .yura-media-block * { text-indent: 0 !important; }',
                    '.yura-media-block { text-align: center !important; }'
                ].join('\n');

                var oldBlocks = document.querySelectorAll('.yura-media-block');
                for (var i = 0; i < oldBlocks.length; i++) oldBlocks[i].classList.remove('yura-media-block');

                function hasMeaningfulText(element) {
                    for (var i = 0; i < element.childNodes.length; i++) {
                        var node = element.childNodes[i];
                        if (node.nodeType === Node.TEXT_NODE && node.textContent.trim().length > 0) return true;
                    }
                    return false;
                }
                function isMediaOnly(element) {
                    if (!element) return false;
                    if (hasMeaningfulText(element)) return false;
                    var media = element.querySelectorAll('img, svg, video, canvas, picture');
                    if (media.length === 0) return false;
                    var text = (element.innerText || '').replace(/\s+/g, '').trim();
                    return text.length === 0;
                }
                var medias = document.querySelectorAll('img, svg, video, canvas, picture');
                for (var m = 0; m < medias.length; m++) {
                    var el = medias[m];
                    el.style.textIndent = '0';
                    el.style.marginInlineStart = '0';
                    var parent = el.parentElement;
                    var limit = 0;
                    while (parent && parent !== document.body && limit < 4) {
                        var tag = parent.tagName ? parent.tagName.toLowerCase() : '';
                        var compactText = (parent.innerText || '').replace(/\s+/g, '').trim();
                        var mostlyMedia = compactText.length <= 24 && parent.querySelector('img, svg, video, canvas, picture');
                        if ((tag === 'p' || tag === 'div' || tag === 'figure' || tag === 'section') && (isMediaOnly(parent) || mostlyMedia)) {
                            parent.classList.add('yura-media-block');
                            parent.style.textIndent = '0';
                        }
                        parent = parent.parentElement;
                        limit++;
                    }
                }
            })();
            """.trimIndent()
        }
        fun applyOnce() {
            val root = navigatorFragment?.publicationView ?: return
            val webViews = mutableListOf<WebView>()
            collectWebViews(root, webViews)
            webViews.forEach { webView ->
                webView.evaluateJavascript(script, null)
            }
        }
        applyOnce()
        window.decorView.postDelayed({ applyOnce() }, 120)
        window.decorView.postDelayed({ applyOnce() }, 360)
        window.decorView.postDelayed({ applyOnce() }, 800)
    }


    private sealed interface ReaderState {
        data object Loading : ReaderState
        data class Error(val message: String) : ReaderState
        data class Ready(
            val book: Book,
            val publication: Publication,
            val initialLocator: Locator?,
            val initialPreferences: EpubPreferences,
            val navigatorFactory: EpubNavigatorFactory,
        ) : ReaderState
    }

    private fun loadReaderPreferences(): EpubPreferences = ReaderPreferencesStore.load(this)

    private fun saveReaderPreferences(preferences: EpubPreferences) =
        ReaderPreferencesStore.save(this, preferences)

    private fun loadTtsFloatingPosition(): Pair<Float, Float> {
        val prefs = getSharedPreferences("reader-tts-floating", Context.MODE_PRIVATE)
        return Pair(
            prefs.getFloat("x_fraction", 1f).coerceIn(0f, 1f),
            prefs.getFloat("y_fraction", 1f).coerceIn(0f, 1f),
        )
    }

    private fun saveTtsFloatingPosition(position: Pair<Float, Float>) {
        getSharedPreferences("reader-tts-floating", Context.MODE_PRIVATE)
            .edit()
            .putFloat("x_fraction", position.first.coerceIn(0f, 1f))
            .putFloat("y_fraction", position.second.coerceIn(0f, 1f))
            .apply()
    }

    companion object {
        private const val EXTRA_BOOK_ID = "book_id"
        private const val NAVIGATOR_TAG = "navigator"
        fun intent(context: Context, bookId: Long): Intent =
            Intent(context, ReaderActivity::class.java)
                .putExtra(EXTRA_BOOK_ID, bookId)
    }
}

@Composable
private fun DraggableTtsFloatingButton(
    onClick: () -> Unit,
    controlsVisible: Boolean,
    loadPosition: () -> Pair<Float, Float>,
    savePosition: (Pair<Float, Float>) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        var position by remember { mutableStateOf(loadPosition()) }
        val maxX = with(density) { (maxWidth - 116.dp).toPx().coerceAtLeast(0f) }
        val bottomReserve = if (controlsVisible) 116.dp else 28.dp
        val maxY = with(density) { (maxHeight - bottomReserve - 56.dp).toPx().coerceAtLeast(0f) }

        TtsFloatingButton(
            onClick = onClick,
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (position.first.coerceIn(0f, 1f) * maxX).roundToInt(),
                        y = (position.second.coerceIn(0f, 1f) * maxY).roundToInt(),
                    )
                }
                .pointerInput(maxX, maxY) {
                    detectDragGestures(
                        onDragEnd = { savePosition(position) },
                        onDragCancel = { savePosition(position) },
                    ) { change, dragAmount ->
                        change.consume()
                        val currentX = position.first.coerceIn(0f, 1f) * maxX
                        val currentY = position.second.coerceIn(0f, 1f) * maxY
                        val nextX = (currentX + dragAmount.x).coerceIn(0f, maxX)
                        val nextY = (currentY + dragAmount.y).coerceIn(0f, maxY)
                        position = Pair(
                            if (maxX <= 0f) 0f else nextX / maxX,
                            if (maxY <= 0f) 0f else nextY / maxY,
                        )
                    }
                },
        )
    }
}

@Composable
private fun TtsFloatingButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        tonalElevation = 8.dp,
        shadowElevation = 10.dp,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("朗读中", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TtsPanel(
    controller: SimpleTtsController,
    visible: Boolean,
    chapterTitle: String,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val uiState by controller.state.collectAsState()
    val playing = uiState.state == SimpleTtsController.State.PLAYING
    val speedIndex = SimpleTtsController.PLAYBACK_SPEEDS
        .indexOf(uiState.playbackSpeed)
        .coerceAtLeast(0)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("\u6717\u8bfb\u63a7\u5236", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(
                chapterTitle.ifBlank { "\u5f53\u524d\u7ae0\u8282" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                uiState.currentSentence.ifBlank { uiState.errorMessage ?: "\u51c6\u5907\u6717\u8bfb" },
                style = MaterialTheme.typography.bodyMedium,
                color = if (uiState.errorMessage == null) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    MaterialTheme.colorScheme.error
                },
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SimpleTtsController.Provider.entries.forEach { provider ->
                    PreferenceChoice(
                        text = provider.label,
                        selected = uiState.provider == provider,
                        onClick = { controller.selectProvider(provider) },
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("\u500d\u901f", fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("${uiState.playbackSpeed.formatSpeed()}x", color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = speedIndex.toFloat(),
                onValueChange = { value ->
                    val index = value.toInt().coerceIn(0, SimpleTtsController.PLAYBACK_SPEEDS.lastIndex)
                    controller.setPlaybackSpeed(SimpleTtsController.PLAYBACK_SPEEDS[index])
                },
                valueRange = 0f..SimpleTtsController.PLAYBACK_SPEEDS.lastIndex.toFloat(),
                steps = (SimpleTtsController.PLAYBACK_SPEEDS.size - 2).coerceAtLeast(0),
            )

            Text(
                "\u53e5\u5b50 ${(uiState.sentenceIndex + 1).coerceAtLeast(0)} / ${uiState.sentenceTotal}  \u6bb5\u843d ${(uiState.paragraphIndex + 1).coerceAtLeast(0)} / ${uiState.paragraphTotal}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = controller::previous) { Text("\u4e0a\u4e00\u53e5") }
                TextButton(
                    onClick = {
                        if (playing) {
                            controller.pause()
                        } else {
                            controller.play()
                        }
                    },
                ) {
                    Text(if (playing) "\u6682\u505c" else "\u64ad\u653e")
                }
                TextButton(onClick = controller::next) { Text("\u4e0b\u4e00\u53e5") }
                TextButton(
                    onClick = {
                        controller.stop()
                        onDismiss()
                    },
                ) {
                    Text("\u505c\u6b62")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderSettingsSheet(
    preferences: EpubPreferences,
    autoTheme: Boolean,
    onDismiss: () -> Unit,
    onAutoThemeChange: (Boolean) -> Unit,
    onPreferencesChange: (EpubPreferences) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .heightIn(max = 620.dp)
                .padding(horizontal = 22.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Text(
                    "\u9605\u8bfb\u8bbe\u7f6e",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            item {
                PreferenceSectionTitle("\u4e3b\u9898")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PreferenceChoice(
                        text = "\u81ea\u52a8",
                        selected = autoTheme,
                        onClick = { onAutoThemeChange(true) },
                    )
                    PreferenceChoice(
                        text = "\u6d45\u8272",
                        selected = !autoTheme && preferences.theme == Theme.LIGHT,
                        onClick = {
                            onAutoThemeChange(false)
                            onPreferencesChange(preferences.copy(theme = Theme.LIGHT))
                        },
                    )
                    PreferenceChoice(
                        text = "\u6df1\u8272",
                        selected = !autoTheme && preferences.theme == Theme.DARK,
                        onClick = {
                            onAutoThemeChange(false)
                            onPreferencesChange(preferences.copy(theme = Theme.DARK))
                        },
                    )
                    PreferenceChoice(
                        text = "\u7c73\u8272",
                        selected = !autoTheme && preferences.theme == Theme.SEPIA,
                        onClick = {
                            onAutoThemeChange(false)
                            onPreferencesChange(preferences.copy(theme = Theme.SEPIA))
                        },
                    )
                }
            }
            item {
                PreferenceSlider(
                    title = "\u5b57\u53f7",
                    valueLabel = "${((preferences.fontSize ?: 1.0) * 100).toInt()}%",
                    value = (preferences.fontSize ?: 1.0).toFloat(),
                    valueRange = 0.8f..2.0f,
                    steps = 11,
                    onValueChange = { value ->
                        onPreferencesChange(preferences.copy(fontSize = roundToStep(value, 0.1f), publisherStyles = false))
                    },
                )
            }
            item {
                PreferenceSlider(
                    title = "\u884c\u9ad8",
                    valueLabel = String.format("%.1f", preferences.lineHeight ?: 1.5),
                    value = (preferences.lineHeight ?: 1.5).toFloat(),
                    valueRange = 1.0f..2.2f,
                    steps = 5,
                    onValueChange = { value ->
                        onPreferencesChange(preferences.copy(lineHeight = roundToStep(value, 0.2f), publisherStyles = false))
                    },
                )
            }
            item {
                PreferenceSlider(
                    title = "\u6bb5\u9996\u7f29\u8fdb",
                    valueLabel = "${(preferences.paragraphIndent ?: 0.0).toInt()} \u5b57",
                    value = (preferences.paragraphIndent ?: 0.0).toFloat(),
                    valueRange = 0f..4f,
                    steps = 3,
                    onValueChange = { value ->
                        onPreferencesChange(preferences.copy(paragraphIndent = value.toInt().toDouble(), publisherStyles = false))
                    },
                )
            }
            item {
                PreferenceSlider(
                    title = "\u6bb5\u95f4\u8ddd",
                    valueLabel = "${((preferences.paragraphSpacing ?: 0.0) * 100).toInt()}%",
                    value = (preferences.paragraphSpacing ?: 0.0).toFloat(),
                    valueRange = 0f..2f,
                    steps = 9,
                    onValueChange = { value ->
                        onPreferencesChange(preferences.copy(paragraphSpacing = roundToStep(value, 0.2f), publisherStyles = false))
                    },
                )
            }
            item {
                PreferenceSlider(
                    title = "\u5b57\u95f4\u8ddd",
                    valueLabel = "${((preferences.letterSpacing ?: 0.0) * 100).toInt()}%",
                    value = (preferences.letterSpacing ?: 0.0).toFloat(),
                    valueRange = 0f..1f,
                    steps = 9,
                    onValueChange = { value ->
                        onPreferencesChange(preferences.copy(letterSpacing = roundToStep(value, 0.1f), publisherStyles = false))
                    },
                )
            }
            item {
                PreferenceSwitch(
                    title = "\u6eda\u52a8\u9605\u8bfb",
                    subtitle = "\u5173\u95ed\u540e\u4f7f\u7528\u5206\u9875\u9605\u8bfb",
                    checked = preferences.scroll == true,
                    onCheckedChange = { checked -> onPreferencesChange(preferences.copy(scroll = checked)) },
                )
            }
            item {
                PreferenceSwitch(
                    title = "\u4f7f\u7528\u4e66\u7c4d\u81ea\u5e26\u7248\u5f0f",
                    subtitle = "\u5f00\u542f\u540e\uff0c\u90e8\u5206\u5b57\u53f7\u3001\u884c\u9ad8\u3001\u7f29\u8fdb\u548c\u5b57\u95f4\u8ddd\u4f1a\u7531 EPUB \u539f\u59cb\u6837\u5f0f\u51b3\u5b9a",
                    checked = preferences.publisherStyles != false,
                    onCheckedChange = { checked -> onPreferencesChange(preferences.copy(publisherStyles = checked)) },
                )
            }
            item {
                PreferenceSectionTitle("\u7248\u5f0f")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PreferenceChoice(
                        text = "\u81ea\u52a8",
                        selected = preferences.columnCount == null || preferences.columnCount == ColumnCount.AUTO,
                        onClick = {
                            onPreferencesChange(preferences.copy(columnCount = ColumnCount.AUTO, spread = Spread.NEVER, scroll = false, publisherStyles = false))
                        },
                    )
                    PreferenceChoice(
                        text = "\u5355\u680f",
                        selected = preferences.columnCount == ColumnCount.ONE,
                        onClick = {
                            onPreferencesChange(preferences.copy(columnCount = ColumnCount.ONE, spread = Spread.NEVER, scroll = false, publisherStyles = false))
                        },
                    )
                    PreferenceChoice(
                        text = "\u53cc\u680f",
                        selected = preferences.columnCount == ColumnCount.TWO,
                        onClick = {
                            onPreferencesChange(preferences.copy(columnCount = ColumnCount.TWO, spread = Spread.ALWAYS, scroll = false, publisherStyles = false))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PreferenceSlider(
    title: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PreferenceSectionTitle(title)
            Text(valueLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
        )
    }
}

@Composable
private fun PreferenceSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f),
        shape = RoundedCornerShape(22.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun PreferenceChoice(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.background(
            color = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f)
            },
            shape = RoundedCornerShape(18.dp),
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(
            text,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun PreferenceSectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun roundToStep(value: Float, step: Float): Double =
    (kotlin.math.round(value / step) * step).toDouble()

private fun Float.formatSpeed(): String =
    if (this % 1f == 0f) toInt().toString() else String.format("%.1f", this)

@Composable
private fun ReaderHud(
    currentPage: Int,
    totalPages: Int,
    progressLabel: String,
    chapterTitle: String,
) {
    Box(Modifier.fillMaxSize()) {
        Text(
            text = chapterTitle,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 56.dp, vertical = 14.dp)
                .align(Alignment.TopCenter),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "$currentPage / $totalPages",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
            )
            Text(
                progressLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
            )
        }
    }
}

@Composable
private fun LoadingReader() {
    val transition = rememberInfiniteTransition(label = "reader-loading")
    val pageLift by transition.animateFloat(
        initialValue = 0.74f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "reader-page-lift",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(22.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.size(width = 118.dp, height = 82.dp),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.88f),
                        shape = RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp, topEnd = 4.dp, bottomEnd = 4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp),
                    ) {}
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.58f),
                        shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 14.dp, bottomEnd = 14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .graphicsLayer {
                                scaleX = pageLift
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                            },
                    ) {}
                }
            }
            Text(
                text = "\u6b63\u5728\u7ffb\u5f00\u4e66\u9875",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "\u8bfb\u53d6\u8fdb\u5ea6\u548c\u6392\u7248\u504f\u597d",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorReader(message: String, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Text(message, textAlign = TextAlign.Center)
            Button(onClick = onBack) {
                Text("\u8fd4\u56de\u4e66\u67b6")
            }
        }
    }
}

@Composable
private fun ReaderTopBar(title: String, onBack: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 1.dp,
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text("\u2039", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.headlineSmall)
            }
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.padding(horizontal = 24.dp))
        }
    }
}

@Composable
private fun ReaderControlBar(
    onToc: () -> Unit,
    onSettings: () -> Unit,
    onTts: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 2.dp,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReaderControlAction(label = "\u76ee\u5f55", onClick = onToc)
            ReaderControlAction(label = "Aa", onClick = onSettings)
            ReaderControlAction(label = "\u6717\u8bfb", onClick = onTts)
        }
    }
}

@Composable
private fun ReaderControlAction(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(
            label,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TocSheet(
    links: List<Link>,
    publication: Publication,
    currentLocator: Locator?,
    onDismiss: () -> Unit,
    onGo: (Link) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val entries = remember(links) { flattenToc(links) }
    val activeIndex = remember(entries, currentLocator) {
        findActiveTocIndex(entries, publication, currentLocator)
    }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = activeIndex.coerceAtLeast(0),
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "\u76ee\u5f55",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
            if (entries.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "\u8fd9\u672c\u4e66\u6ca1\u6709\u63d0\u4f9b\u76ee\u5f55\u3002",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(entries, key = { _, entry -> "${entry.depth}-${entry.link.href}" }) { index, entry ->
                        TocRow(
                            link = entry.link,
                            depth = entry.depth,
                            selected = index == activeIndex,
                            onGo = onGo,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TocRow(
    link: Link,
    depth: Int,
    selected: Boolean,
    onGo: (Link) -> Unit,
) {
    TextButton(
        onClick = { onGo(link) },
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                } else if (depth == 0) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.36f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f)
                },
                shape = RoundedCornerShape(18.dp),
            ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 13.dp),
    ) {
        Text(
            text = link.title ?: link.href.toString(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = (depth * 18).dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
            fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private data class TocEntry(
    val link: Link,
    val depth: Int,
)

private fun flattenToc(links: List<Link>): List<TocEntry> {
    val result = mutableListOf<TocEntry>()
    fun append(items: List<Link>, depth: Int) {
        items.forEach { link ->
            result += TocEntry(link, depth)
            append(link.children, depth + 1)
        }
    }
    append(links, 0)
    return result
}

private fun findActiveTocIndex(
    entries: List<TocEntry>,
    publication: Publication,
    currentLocator: Locator?,
): Int {
    if (entries.isEmpty() || currentLocator == null) return -1
    val currentHref = currentLocator.href.toString().substringBefore('#')
    val currentProgression = currentLocator.locations.totalProgression ?: -1.0

    val hrefMatch = entries.indexOfLast { entry ->
        val entryHref = entry.link.href.toString().substringBefore('#')
        currentHref == entryHref || currentHref.startsWith(entryHref) || entryHref.startsWith(currentHref)
    }
    if (hrefMatch >= 0) return hrefMatch

    val currentReadingOrderIndex = publication.readingOrder.indexOfFirst { link ->
        val linkHref = link.href.toString().substringBefore('#')
        currentHref == linkHref || currentHref.startsWith(linkHref) || linkHref.startsWith(currentHref)
    }
    if (currentReadingOrderIndex >= 0) {
        var bestIndex = -1
        var bestReadingOrderIndex = -1
        entries.forEachIndexed { index, entry ->
            val entryHref = entry.link.href.toString().substringBefore('#')
            val entryReadingOrderIndex = publication.readingOrder.indexOfFirst { link ->
                val linkHref = link.href.toString().substringBefore('#')
                entryHref == linkHref || entryHref.startsWith(linkHref) || linkHref.startsWith(entryHref)
            }
            if (entryReadingOrderIndex in 0..currentReadingOrderIndex && entryReadingOrderIndex >= bestReadingOrderIndex) {
                bestReadingOrderIndex = entryReadingOrderIndex
                bestIndex = index
            }
        }
        if (bestIndex >= 0) return bestIndex
    }

    if (currentProgression < 0.0) return -1
    var bestIndex = -1
    var bestProgression = -1.0
    entries.forEachIndexed { index, entry ->
        val progression = publication.locatorFromLink(entry.link)?.locations?.totalProgression ?: return@forEachIndexed
        if (progression <= currentProgression && progression >= bestProgression) {
            bestProgression = progression
            bestIndex = index
        }
    }
    return bestIndex
}
