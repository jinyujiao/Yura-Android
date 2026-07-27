package com.yura.app.stats

import android.content.Context
import android.os.SystemClock
import com.yura.app.data.Book
import com.yura.app.data.ReadingSession
import com.yura.app.data.YuraDatabase
import com.yura.tts.android.MediaService
import com.yura.tts.core.TtsState
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ReadingStatsCoordinator(context: Context) {
    private val appContext = context.applicationContext
    private val dao = YuraDatabase.get(appContext).yuraDao()
    private val ttsController = MediaService.controller(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val databaseMutex = Mutex()
    private val started = AtomicBoolean(false)

    private val lock = Any()
    private var activeBook: BookRef? = null
    private var readerVisible = false
    private var readerPreview = false
    private var readerOverlayVisible = false
    private var lastReaderInteractionElapsed = 0L
    private var ttsPlaying = false
    private var readingSession: ActiveSession? = null
    private var listeningSession: ActiveSession? = null

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            ttsController.state.collectLatest { state ->
                onTtsState(state.state)
            }
        }
        scope.launch {
            while (true) {
                delay(CHECKPOINT_INTERVAL_MS)
                reconcile(checkpointActive = true)
            }
        }
    }

    fun setActiveBook(book: Book) {
        synchronized(lock) {
            activeBook = BookRef(book.identifier)
        }
        reconcile()
    }

    fun onReaderResumed(book: Book, preview: Boolean) {
        synchronized(lock) {
            activeBook = BookRef(book.identifier)
            readerVisible = true
            readerPreview = preview
            readerOverlayVisible = false
            lastReaderInteractionElapsed = SystemClock.elapsedRealtime()
        }
        reconcile()
    }

    fun onReaderPaused() {
        synchronized(lock) {
            readerVisible = false
        }
        reconcile(finalizeReading = true)
    }

    fun onReaderDestroyed() {
        onReaderPaused()
    }

    fun onReaderInteraction() {
        synchronized(lock) {
            lastReaderInteractionElapsed = SystemClock.elapsedRealtime()
        }
        reconcile()
    }

    fun onReaderOverlayChanged(visible: Boolean) {
        synchronized(lock) {
            readerOverlayVisible = visible
        }
        reconcile(finalizeReading = visible)
    }

    private fun onTtsState(state: TtsState) {
        synchronized(lock) {
            ttsPlaying = state == TtsState.PLAYING
        }
        reconcile(finalizeReading = state == TtsState.PLAYING, finalizeListening = state != TtsState.PLAYING)
    }

    private fun reconcile(
        finalizeReading: Boolean = false,
        finalizeListening: Boolean = false,
        checkpointActive: Boolean = false,
    ) {
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()
        val snapshots = mutableListOf<ReadingSession>()

        synchronized(lock) {
            val readingEligible = readerVisible &&
                !readerPreview &&
                !readerOverlayVisible &&
                !ttsPlaying &&
                nowElapsed - lastReaderInteractionElapsed <= IDLE_TIMEOUT_MS &&
                activeBook != null
            if (readingEligible) {
                if (readingSession == null) {
                    readingSession = ActiveSession.create(
                        mode = ReadingSession.MODE_READING,
                        bookIdentifier = activeBook!!.identifier,
                        nowWall = nowWall,
                        nowElapsed = nowElapsed,
                    )
                }
                if (checkpointActive) readingSession?.let { snapshots += it.snapshot(nowElapsed, nowWall) }
            } else if (readingSession != null) {
                val idleCutoffElapsed = lastReaderInteractionElapsed + IDLE_TIMEOUT_MS
                val endedElapsed = if (readerVisible && nowElapsed > idleCutoffElapsed) idleCutoffElapsed else nowElapsed
                val endedWall = nowWall - (nowElapsed - endedElapsed).coerceAtLeast(0L)
                snapshots += readingSession!!.snapshot(endedElapsed, endedWall)
                readingSession = null
            }

            val listeningEligible = ttsPlaying && activeBook != null
            if (listeningEligible) {
                if (listeningSession == null) {
                    listeningSession = ActiveSession.create(
                        mode = ReadingSession.MODE_LISTENING,
                        bookIdentifier = activeBook!!.identifier,
                        nowWall = nowWall,
                        nowElapsed = nowElapsed,
                    )
                }
                if (checkpointActive) listeningSession?.let { snapshots += it.snapshot(nowElapsed, nowWall) }
            } else if (listeningSession != null) {
                snapshots += listeningSession!!.snapshot(nowElapsed, nowWall)
                listeningSession = null
            }

            if (finalizeReading && readingSession != null) {
                snapshots += readingSession!!.snapshot(nowElapsed, nowWall)
                readingSession = null
            }
            if (finalizeListening && listeningSession != null) {
                snapshots += listeningSession!!.snapshot(nowElapsed, nowWall)
                listeningSession = null
            }
        }

        if (snapshots.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            databaseMutex.withLock {
                snapshots.forEach { session ->
                    if (session.bookIdentifier.isNotBlank() && session.durationMs > 0L) {
                        dao.upsertReadingSession(session)
                    }
                }
            }
        }
    }

    private fun ActiveSession.snapshot(nowElapsed: Long, nowWall: Long): ReadingSession {
        val elapsedDelta = (nowElapsed - lastElapsedRealtime).coerceAtLeast(0L)
        val totalDuration = accumulatedDurationMs + elapsedDelta
        lastElapsedRealtime = nowElapsed
        accumulatedDurationMs = totalDuration
        return ReadingSession(
            id = id,
            bookIdentifier = bookIdentifier,
            mode = mode,
            startedAt = startedAt,
            endedAt = nowWall,
            durationMs = totalDuration,
            updatedAt = nowWall,
        )
    }

    private data class BookRef(val identifier: String)

    private data class ActiveSession(
        val id: String,
        val bookIdentifier: String,
        val mode: String,
        val startedAt: Long,
        var lastElapsedRealtime: Long,
        var accumulatedDurationMs: Long,
    ) {
        companion object {
            fun create(mode: String, bookIdentifier: String, nowWall: Long, nowElapsed: Long) = ActiveSession(
                id = UUID.randomUUID().toString(),
                bookIdentifier = bookIdentifier,
                mode = mode,
                startedAt = nowWall,
                lastElapsedRealtime = nowElapsed,
                accumulatedDurationMs = 0L,
            )
        }
    }

    private companion object {
        const val CHECKPOINT_INTERVAL_MS = 60_000L
        const val IDLE_TIMEOUT_MS = 5 * 60_000L
    }
}
