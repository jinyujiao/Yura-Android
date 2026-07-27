package com.yura.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = ReadingSession.TABLE_NAME,
    indices = [
        Index(value = [ReadingSession.BOOK_IDENTIFIER]),
        Index(value = [ReadingSession.STARTED_AT]),
        Index(value = [ReadingSession.MODE]),
    ],
)
data class ReadingSession(
    @PrimaryKey
    @ColumnInfo(name = ID)
    val id: String,
    @ColumnInfo(name = BOOK_IDENTIFIER)
    val bookIdentifier: String,
    @ColumnInfo(name = MODE)
    val mode: String,
    @ColumnInfo(name = STARTED_AT)
    val startedAt: Long,
    @ColumnInfo(name = ENDED_AT)
    val endedAt: Long,
    @ColumnInfo(name = DURATION_MS)
    val durationMs: Long,
    @ColumnInfo(name = UPDATED_AT)
    val updatedAt: Long,
) {
    companion object {
        const val TABLE_NAME = "reading_sessions"
        const val ID = "id"
        const val BOOK_IDENTIFIER = "book_identifier"
        const val MODE = "mode"
        const val STARTED_AT = "started_at"
        const val ENDED_AT = "ended_at"
        const val DURATION_MS = "duration_ms"
        const val UPDATED_AT = "updated_at"

        const val MODE_READING = "READING"
        const val MODE_LISTENING = "LISTENING"
    }
}
