package com.yura.app.reader

import com.yura.app.data.YuraDao
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class ReaderProgressionSaver(
    private val scope: CoroutineScope,
    private val saveProgression: suspend (Long, String, Long) -> Unit,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val debounceMs: Long = DEFAULT_DEBOUNCE_MS,
    private val now: () -> Long = System::currentTimeMillis,
) {
    constructor(scope: CoroutineScope, dao: YuraDao) : this(scope, dao::saveProgression)

    private val stateLock = Any()
    private val saveMutex = Mutex()
    private var pending: Progression? = null
    private var lastSavedJson: String? = null
    private var saveJob: Job? = null

    fun schedule(bookId: Long, json: String) {
        synchronized(stateLock) {
            if (json == lastSavedJson) return
            pending = Progression(bookId, json, now())
            saveJob?.cancel()
            saveJob = scope.launch(dispatcher) {
                delay(debounceMs)
                persistLatest()
            }
        }
    }

    fun flush() {
        synchronized(stateLock) {
            saveJob?.cancel()
            saveJob = scope.launch(dispatcher) { persistLatest() }
        }
    }

    fun cancel() {
        synchronized(stateLock) {
            saveJob?.cancel()
            saveJob = null
        }
    }

    private suspend fun persistLatest() {
        saveMutex.withLock {
            while (true) {
                val progression = synchronized(stateLock) { pending } ?: return
                saveProgression(progression.bookId, progression.json, progression.updatedAt)
                val complete = synchronized(stateLock) {
                    lastSavedJson = progression.json
                    if (pending == progression) {
                        pending = null
                        true
                    } else {
                        false
                    }
                }
                if (complete) return
            }
        }
    }

    private data class Progression(
        val bookId: Long,
        val json: String,
        val updatedAt: Long,
    )

    private companion object {
        const val DEFAULT_DEBOUNCE_MS = 400L
    }
}
