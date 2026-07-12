package com.yura.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = DeletedBook.TABLE_NAME)
data class DeletedBook(
    @PrimaryKey
    @ColumnInfo(name = IDENTIFIER)
    val identifier: String,
    @ColumnInfo(name = DELETED_AT)
    val deletedAt: Long,
) {
    companion object {
        const val TABLE_NAME = "deleted_books"
        const val IDENTIFIER = "identifier"
        const val DELETED_AT = "deleted_at"
    }
}
