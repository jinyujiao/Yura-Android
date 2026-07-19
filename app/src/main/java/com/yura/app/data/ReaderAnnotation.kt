package com.yura.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator

@Entity(
    tableName = ReaderAnnotation.TABLE_NAME,
    indices = [Index(value = [ReaderAnnotation.BOOK_ID])],
)
data class ReaderAnnotation(
    @PrimaryKey
    @ColumnInfo(name = ID)
    val id: String,
    @ColumnInfo(name = BOOK_ID)
    val bookId: Long,
    @ColumnInfo(name = TYPE)
    val type: String,
    @ColumnInfo(name = LOCATOR)
    val locatorJson: String,
    @ColumnInfo(name = NOTE)
    val note: String,
    @ColumnInfo(name = CREATED_AT)
    val createdAt: Long,
    @ColumnInfo(name = UPDATED_AT, defaultValue = "0")
    val updatedAt: Long = createdAt,
    @ColumnInfo(name = CHAPTER_INDEX, defaultValue = "-1")
    val chapterIndex: Int = -1,
    @ColumnInfo(name = CHAPTER_TITLE, defaultValue = "''")
    val chapterTitle: String = "",
    @ColumnInfo(name = CHAPTER_HREF, defaultValue = "''")
    val chapterHref: String = "",
) {
    val locator: Locator?
        get() = runCatching { Locator.fromJSON(JSONObject(locatorJson)) }.getOrNull()

    companion object {
        const val TABLE_NAME = "reader_annotations"
        const val ID = "id"
        const val BOOK_ID = "book_id"
        const val TYPE = "type"
        const val LOCATOR = "locator"
        const val NOTE = "note"
        const val CREATED_AT = "created_at"
        const val UPDATED_AT = "updated_at"
        const val CHAPTER_INDEX = "chapter_index"
        const val CHAPTER_TITLE = "chapter_title"
        const val CHAPTER_HREF = "chapter_href"
        const val TYPE_HIGHLIGHT = "highlight"
        const val TYPE_NOTE = "note"
        const val TYPE_CORRECTION = "correction"
    }
}
