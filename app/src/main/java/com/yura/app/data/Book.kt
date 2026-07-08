package com.yura.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.mediatype.MediaType

@Entity(tableName = Book.TABLE_NAME)
data class Book(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = ID)
    val id: Long = 0,
    @ColumnInfo(name = CREATION_DATE)
    val creationDate: Long,
    @ColumnInfo(name = LAST_READ_DATE)
    val lastReadDate: Long,
    @ColumnInfo(name = HREF)
    val href: String,
    @ColumnInfo(name = TITLE)
    val title: String,
    @ColumnInfo(name = AUTHOR)
    val author: String,
    @ColumnInfo(name = IDENTIFIER)
    val identifier: String,
    @ColumnInfo(name = PROGRESSION)
    val progression: String,
    @ColumnInfo(name = MEDIA_TYPE)
    val rawMediaType: String,
    @ColumnInfo(name = COVER)
    val cover: String,
) {
    val url: AbsoluteUrl get() = checkNotNull(AbsoluteUrl(href))
    val mediaType: MediaType get() = checkNotNull(MediaType(rawMediaType))

    companion object {
        const val TABLE_NAME = "books"
        const val ID = "id"
        const val CREATION_DATE = "creation_date"
        const val LAST_READ_DATE = "last_read_date"
        const val HREF = "href"
        const val TITLE = "title"
        const val AUTHOR = "author"
        const val IDENTIFIER = "identifier"
        const val PROGRESSION = "progression"
        const val MEDIA_TYPE = "media_type"
        const val COVER = "cover"
    }
}
