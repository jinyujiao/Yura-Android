package com.yura.app.reader

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderProgressionSaverTest {
    @Test
    fun savesOnlyLatestProgressionAfterDebounce() = runTest {
        val saved = mutableListOf<String>()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val saver = ReaderProgressionSaver(
            scope = this,
            saveProgression = { _, json, _ -> saved += json },
            dispatcher = dispatcher,
            debounceMs = 400L,
            now = { 1L },
        )

        saver.schedule(1L, "first")
        advanceTimeBy(200L)
        saver.schedule(1L, "latest")
        advanceTimeBy(399L)
        assertEquals(emptyList<String>(), saved)
        advanceUntilIdle()

        assertEquals(listOf("latest"), saved)
    }

    @Test
    fun flushPersistsImmediately() = runTest {
        val saved = mutableListOf<String>()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val saver = ReaderProgressionSaver(
            scope = this,
            saveProgression = { _, json, _ -> saved += json },
            dispatcher = dispatcher,
            debounceMs = 10_000L,
            now = { 1L },
        )

        saver.schedule(1L, "current")
        saver.flush()
        advanceUntilIdle()

        assertEquals(listOf("current"), saved)
    }
}
