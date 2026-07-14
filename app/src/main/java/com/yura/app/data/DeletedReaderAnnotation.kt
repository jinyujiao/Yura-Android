package com.yura.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = DeletedReaderAnnotation.TABLE_NAME,
    indices = [Index(value = [DeletedReaderAnnotation.BOOK_IDENTIFIER])],
)
data class DeletedReaderAnnotation(
    @PrimaryKey
    @ColumnInfo(name = ID)
    val id: String,
    @ColumnInfo(name = BOOK_IDENTIFIER)
    val bookIdentifier: String,
    @ColumnInfo(name = DELETED_AT)
    val deletedAt: Long,
) {
    companion object {
        const val TABLE_NAME = "deleted_reader_annotations"
        const val ID = "id"
        const val BOOK_IDENTIFIER = "book_identifier"
        const val DELETED_AT = "deleted_at"
    }
}
