package com.yura.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType

@Entity(
    tableName = Bookmark.TABLE_NAME,
    indices = [Index(value = [Bookmark.BOOK_ID, Bookmark.LOCATION], unique = true)],
)
data class Bookmark(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = ID)
    val id: Long = 0,
    @ColumnInfo(name = CREATION_DATE)
    val creationDate: Long,
    @ColumnInfo(name = BOOK_ID)
    val bookId: Long,
    @ColumnInfo(name = RESOURCE_INDEX)
    val resourceIndex: Long,
    @ColumnInfo(name = RESOURCE_HREF)
    val resourceHref: String,
    @ColumnInfo(name = RESOURCE_TYPE)
    val resourceType: String,
    @ColumnInfo(name = RESOURCE_TITLE)
    val resourceTitle: String,
    @ColumnInfo(name = LOCATION)
    val location: String,
    @ColumnInfo(name = LOCATOR_TEXT)
    val locatorText: String,
) {
    val locator: Locator
        get() = Locator(
            href = checkNotNull(Url(resourceHref)),
            mediaType = MediaType(resourceType) ?: MediaType.BINARY,
            title = resourceTitle,
            locations = Locator.Locations.fromJSON(JSONObject(location)),
            text = Locator.Text.fromJSON(JSONObject(locatorText)),
        )

    companion object {
        const val TABLE_NAME = "bookmarks"
        const val ID = "id"
        const val CREATION_DATE = "creation_date"
        const val BOOK_ID = "book_id"
        const val RESOURCE_INDEX = "resource_index"
        const val RESOURCE_HREF = "resource_href"
        const val RESOURCE_TYPE = "resource_type"
        const val RESOURCE_TITLE = "resource_title"
        const val LOCATION = "location"
        const val LOCATOR_TEXT = "locator_text"
    }
}
