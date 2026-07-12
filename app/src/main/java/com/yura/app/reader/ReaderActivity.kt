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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.yura.app.util.applyDeviceOrientationPolicy
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
    private val bookLoader by lazy { ReaderBookLoader(dao, readium) }

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
        applyDeviceOrientationPolicy()
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

    override fun onResume() {
        super.onResume()
        readerForeground = true
        pendingForegroundTtsLocator?.let { locator ->
            pendingForegroundTtsLocator = null
            navigatorFragment?.go(locator, animated = false)
            scheduleForegroundTtsSync(900)
        } ?: scheduleForegroundTtsSync(300)
    }

    override fun onPause() {
        readerForeground = false
        super.onPause()
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
                    currentLocator = currentLocator,
                    onTocLink = { link ->
                        current.publication.locatorFromLink(link)?.let { locator ->
                            navigatorFragment?.go(locator, animated = true)
                        }
                    },
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
        currentLocator: Locator?,
        onTocLink: (Link) -> Unit,
        onControlsVisibleChange: (Boolean) -> Unit,
        onToc: () -> Unit,
        onSettings: () -> Unit,
        onTts: () -> Unit,
        onPageChanged: (Int, Int, Locator) -> Unit,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val tablet = maxWidth >= 600.dp
            if (tablet) {
                Row(Modifier.fillMaxSize()) {
                    TocPane(data.publication.tableOfContents, data.publication, currentLocator, onTocLink, Modifier.width(300.dp).fillMaxHeight())
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        EpubNavigatorHost(data = data, onPageChanged = onPageChanged)
                        ReaderHud(currentPage, totalPages, progressLabel, chapterTitle)
                    }
                }
            } else {
                EpubNavigatorHost(data = data, onPageChanged = onPageChanged)
                ReaderHud(currentPage, totalPages, progressLabel, chapterTitle)
            }

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
        ttsController.holdPlaybackWakeLock()
        lifecycleScope.launch {
            val paragraphs = withContext(Dispatchers.IO) {
                extractTtsParagraphsFromResource(publication, link)
            }
            if (paragraphs.isEmpty()) {
                continueTtsToNextChapter()
            } else {
                ttsController.speakContinuing(paragraphs, 0)
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
        if (bytes.isEmpty()) return emptyList()
        val html = runCatching { bytes.toString(Charsets.UTF_8) }
            .getOrDefault(bytes.toString(Charsets.ISO_8859_1))
        val document = Jsoup.parse(html, link.href.toString())
        document.select("script, style, nav[epub|type=toc], nav, aside, audio, video").remove()
        val nodes = document.select("p, h1, h2, h3, h4, h5, h6, li, blockquote")
        val paragraphs = nodes
            .map { cleanTtsTextForSync(it.text()) }
            .filter { it.length >= 2 }
        val fallbackText = document.body()?.text()?.takeIf { it.isNotBlank() }
            ?: html.replace(Regex("<[^>]+>"), " ")
        val fallback = fallbackText
            .split(Regex("(?<=[。！？!?])\\s*|\\n+"))
            .map { cleanTtsTextForSync(it) }
            .filter { it.length >= 2 }
        return paragraphs.ifEmpty {
            fallback.ifEmpty { listOf(cleanTtsTextForSync(fallbackText)) }
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

    private suspend fun openBook(bookId: Long, initialPreferences: EpubPreferences): ReaderState {
        val state = bookLoader.open(bookId, initialPreferences)
        if (state is ReaderState.Ready) {
            asset = state.asset
            publication = state.publication
        }
        return state
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
        fun applyOnce() {
            ReaderMediaStyleFixer.apply(navigatorFragment?.publicationView, preferences)
        }
        applyOnce()
        window.decorView.postDelayed({ applyOnce() }, 120)
        window.decorView.postDelayed({ applyOnce() }, 360)
        window.decorView.postDelayed({ applyOnce() }, 800)
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

