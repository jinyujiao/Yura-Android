package com.yura.app.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yura.app.data.Book
import com.yura.app.data.ReadingSession
import com.yura.app.data.YuraDatabase
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReadingDayStat(
    val date: LocalDate,
    val readingMs: Long,
    val listeningMs: Long,
) {
    val totalMs: Long get() = readingMs + listeningMs
}

data class ReadingBookStat(
    val book: Book,
    val readingMs: Long,
    val listeningMs: Long,
    val lastReadAt: Long,
    val recentDays: List<ReadingDayStat>,
) {
    val totalMs: Long get() = readingMs + listeningMs
}

data class ReadingStatsUiState(
    val isLoading: Boolean = true,
    val today: ReadingDayStat? = null,
    val recentDays: List<ReadingDayStat> = emptyList(),
    val currentStreak: Int = 0,
    val totalReadingMs: Long = 0L,
    val totalListeningMs: Long = 0L,
    val activeDays: Int = 0,
    val books: List<ReadingBookStat> = emptyList(),
)

class ReadingStatsViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = YuraDatabase.get(application).yuraDao()
    private val zone = ZoneId.systemDefault()

    val uiState: StateFlow<ReadingStatsUiState> = combine(
        dao.books(),
        dao.readingSessions(),
    ) { books, sessions ->
        buildState(books, sessions)
    }
        .map { it.copy(isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReadingStatsUiState())

    fun clearAll() {
        viewModelScope.launch { dao.deleteAllReadingSessions() }
    }

    private fun buildState(books: List<Book>, sessions: List<ReadingSession>): ReadingStatsUiState {
        val today = LocalDate.now(zone)
        val firstDate = sessions.minOfOrNull { session ->
            Instant.ofEpochMilli(session.startedAt).atZone(zone).toLocalDate()
        } ?: today
        val allDays = generateSequence(firstDate) { date ->
            date.plusDays(1).takeIf { it <= today }
        }.map { date -> dayStat(date, sessions) }.toList()
        val recentDays = allDays.takeLast(30).let { days ->
            if (days.size == 30) days else {
                val missing = 30 - days.size
                (missing downTo 1).map { offset -> dayStat(firstDate.minusDays(offset.toLong()), sessions) } + days
            }
        }
        val bookMap = books.associateBy { it.identifier }
        val bookStats = sessions
            .groupBy { it.bookIdentifier }
            .mapNotNull { (identifier, bookSessions) ->
                val book = bookMap[identifier] ?: return@mapNotNull null
                ReadingBookStat(
                    book = book,
                    readingMs = bookSessions.filter { it.mode == ReadingSession.MODE_READING }.sumOf { it.durationMs },
                    listeningMs = bookSessions.filter { it.mode == ReadingSession.MODE_LISTENING }.sumOf { it.durationMs },
                    lastReadAt = bookSessions.maxOfOrNull { it.endedAt } ?: 0L,
                    recentDays = recentDays.map { day -> dayStat(day.date, bookSessions) },
                )
            }
            .sortedWith(compareByDescending<ReadingBookStat> { it.totalMs }.thenByDescending { it.lastReadAt })
        val streak = calculateStreak(allDays)
        return ReadingStatsUiState(
            today = recentDays.lastOrNull(),
            recentDays = recentDays,
            currentStreak = streak,
            totalReadingMs = sessions.filter { it.mode == ReadingSession.MODE_READING }.sumOf { it.durationMs },
            totalListeningMs = sessions.filter { it.mode == ReadingSession.MODE_LISTENING }.sumOf { it.durationMs },
            activeDays = allDays.count { it.totalMs >= ACTIVE_DAY_THRESHOLD_MS },
            books = bookStats,
        )
    }

    private fun dayStat(date: LocalDate, sessions: List<ReadingSession>): ReadingDayStat {
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        var reading = 0L
        var listening = 0L
        sessions.forEach { session ->
            val overlapStart = maxOf(start, session.startedAt)
            val overlapEnd = minOf(end, session.endedAt)
            if (overlapEnd <= overlapStart) return@forEach
            val wallDuration = (session.endedAt - session.startedAt).coerceAtLeast(1L)
            val overlap = overlapEnd - overlapStart
            val duration = (session.durationMs * overlap / wallDuration).coerceAtLeast(0L)
            if (session.mode == ReadingSession.MODE_LISTENING) listening += duration else reading += duration
        }
        return ReadingDayStat(date = date, readingMs = reading, listeningMs = listening)
    }

    private fun calculateStreak(days: List<ReadingDayStat>): Int {
        var streak = 0
        for (day in days.asReversed()) {
            if (day.totalMs < ACTIVE_DAY_THRESHOLD_MS) break
            streak++
        }
        return streak
    }

    private companion object {
        const val ACTIVE_DAY_THRESHOLD_MS = 5 * 60_000L
    }
}
