package com.yura.app.data

import androidx.annotation.ColorInt
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType

@Entity(
    tableName = Highlight.TABLE_NAME,
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = [Book.ID],
            childColumns = [Highlight.BOOK_ID],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = [Highlight.BOOK_ID])],
)
data class Highlight(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = ID)
    val id: Long = 0,
    @ColumnInfo(name = CREATION_DATE)
    val creationDate: Long,
    @ColumnInfo(name = BOOK_ID)
    val bookId: Long,
    @ColumnInfo(name = STYLE)
    val style: Style,
    @ColumnInfo(name = TINT)
    @field:ColorInt
    val tint: Int,
    @ColumnInfo(name = HREF)
    val href: String,
    @ColumnInfo(name = TYPE)
    val type: String,
    @ColumnInfo(name = TITLE)
    val title: String?,
    @ColumnInfo(name = TOTAL_PROGRESSION)
    val totalProgression: Double,
    @ColumnInfo(name = LOCATIONS)
    val locations: Locator.Locations,
    @ColumnInfo(name = TEXT)
    val text: Locator.Text,
    @ColumnInfo(name = ANNOTATION)
    val annotation: String,
) {
    constructor(
        creationDate: Long,
        bookId: Long,
        style: Style,
        tint: Int,
        locator: Locator,
        annotation: String,
    ) : this(
        creationDate = creationDate,
        bookId = bookId,
        style = style,
        tint = tint,
        href = locator.href.toString(),
        type = locator.mediaType.toString(),
        title = locator.title,
        totalProgression = locator.locations.totalProgression ?: 0.0,
        locations = locator.locations,
        text = locator.text,
        annotation = annotation,
    )

    val locator: Locator
        get() = Locator(
            href = checkNotNull(Url(href)),
            mediaType = MediaType(type) ?: MediaType.BINARY,
            title = title,
            locations = locations,
            text = text,
        )

    enum class Style(val value: String) {
        Highlight("highlight"),
        Underline("underline"),
        ;

        companion object {
            fun from(value: String?): Style =
                when (value) {
                    Underline.value -> Underline
                    else -> Highlight
                }
        }
    }

    companion object {
        const val TABLE_NAME = "highlights"
        const val ID = "id"
        const val CREATION_DATE = "creation_date"
        const val BOOK_ID = "book_id"
        const val STYLE = "style"
        const val TINT = "tint"
        const val HREF = "href"
        const val TYPE = "type"
        const val TITLE = "title"
        const val TOTAL_PROGRESSION = "total_progression"
        const val LOCATIONS = "locations"
        const val TEXT = "text"
        const val ANNOTATION = "annotation"
    }
}

class HighlightConverters {
    @TypeConverter
    fun styleFromString(value: String?): Highlight.Style = Highlight.Style.from(value)

    @TypeConverter
    fun styleToString(style: Highlight.Style): String = style.value

    @TypeConverter
    fun textFromString(value: String?): Locator.Text =
        Locator.Text.fromJSON(value?.let { JSONObject(it) })

    @TypeConverter
    fun textToString(text: Locator.Text): String = text.toJSON().toString()

    @TypeConverter
    fun locationsFromString(value: String?): Locator.Locations =
        Locator.Locations.fromJSON(value?.let { JSONObject(it) })

    @TypeConverter
    fun locationsToString(locations: Locator.Locations): String = locations.toJSON().toString()
}
