package com.yura.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface YuraDao {
    @Query("SELECT * FROM ${Book.TABLE_NAME} ORDER BY ${Book.LAST_READ_DATE} DESC, ${Book.CREATION_DATE} DESC")
    fun books(): Flow<List<Book>>

    @Query("SELECT * FROM ${Book.TABLE_NAME} WHERE ${Book.ID} = :id")
    suspend fun book(id: Long): Book?

    @Query("SELECT * FROM ${Book.TABLE_NAME}")
    suspend fun allBooks(): List<Book>

    @Query("SELECT * FROM ${Book.TABLE_NAME} WHERE ${Book.IDENTIFIER} = :identifier LIMIT 1")
    suspend fun bookByIdentifier(identifier: String): Book?

    @Query("SELECT * FROM ${Book.TABLE_NAME} WHERE ${Book.TITLE} = :title AND ${Book.AUTHOR} = :author LIMIT 1")
    suspend fun bookByTitleAndAuthor(title: String, author: String): Book?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: Book): Long

    @Query("DELETE FROM ${Book.TABLE_NAME} WHERE ${Book.ID} = :id")
    suspend fun deleteBook(id: Long)

    @Query("DELETE FROM ${Book.TABLE_NAME} WHERE ${Book.IDENTIFIER} = :identifier")
    suspend fun deleteBookByIdentifier(identifier: String)

    @Query("SELECT * FROM ${DeletedBook.TABLE_NAME}")
    suspend fun deletedBooks(): List<DeletedBook>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDeletedBook(deletedBook: DeletedBook)

    @Query("DELETE FROM ${DeletedBook.TABLE_NAME} WHERE ${DeletedBook.IDENTIFIER} = :identifier")
    suspend fun clearDeletedBook(identifier: String)

    @Query("DELETE FROM ${DeletedBook.TABLE_NAME} WHERE ${DeletedBook.DELETED_AT} < :before")
    suspend fun deleteExpiredTombstones(before: Long)

    @Query("DELETE FROM ${Bookmark.TABLE_NAME} WHERE ${Bookmark.BOOK_ID} = :bookId")
    suspend fun deleteBookmarksForBook(bookId: Long)

    @Query("DELETE FROM ${ReaderAnnotation.TABLE_NAME} WHERE ${ReaderAnnotation.BOOK_ID} = :bookId")
    suspend fun deleteAnnotationsForBook(bookId: Long)

    @Query("UPDATE ${Book.TABLE_NAME} SET ${Book.PROGRESSION} = :locator, ${Book.LAST_READ_DATE} = :lastReadDate WHERE ${Book.ID} = :id")
    suspend fun saveProgression(id: Long, locator: String, lastReadDate: Long)

    @Query("UPDATE ${Book.TABLE_NAME} SET ${Book.LAST_READ_DATE} = :lastReadDate WHERE ${Book.ID} = :id")
    suspend fun markBookRead(id: Long, lastReadDate: Long)

    @Query("UPDATE ${Book.TABLE_NAME} SET ${Book.COVER} = :cover WHERE ${Book.ID} = :id")
    suspend fun updateBookCover(id: Long, cover: String)

    @Query("SELECT * FROM ${Bookmark.TABLE_NAME} ORDER BY ${Bookmark.CREATION_DATE} DESC")
    fun bookmarks(): Flow<List<Bookmark>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBookmark(bookmark: Bookmark): Long

    @Query("SELECT * FROM ${ReaderAnnotation.TABLE_NAME} ORDER BY ${ReaderAnnotation.CREATED_AT} DESC")
    fun annotations(): Flow<List<ReaderAnnotation>>

    @Query("SELECT * FROM ${ReaderAnnotation.TABLE_NAME} ORDER BY ${ReaderAnnotation.CREATED_AT} DESC")
    suspend fun allAnnotations(): List<ReaderAnnotation>

    @Query("SELECT * FROM ${ReaderAnnotation.TABLE_NAME} WHERE ${ReaderAnnotation.ID} = :annotationId LIMIT 1")
    suspend fun annotation(annotationId: String): ReaderAnnotation?

    @Query("SELECT * FROM ${ReaderAnnotation.TABLE_NAME} WHERE ${ReaderAnnotation.BOOK_ID} = :bookId ORDER BY ${ReaderAnnotation.CREATED_AT}")
    suspend fun annotationsForBook(bookId: Long): List<ReaderAnnotation>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAnnotation(annotation: ReaderAnnotation)

    @Query("DELETE FROM ${ReaderAnnotation.TABLE_NAME} WHERE ${ReaderAnnotation.ID} = :annotationId")
    suspend fun deleteAnnotation(annotationId: String)

    @Query("SELECT * FROM ${DeletedReaderAnnotation.TABLE_NAME}")
    suspend fun deletedReaderAnnotations(): List<DeletedReaderAnnotation>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDeletedReaderAnnotation(annotation: DeletedReaderAnnotation)

    @Query("DELETE FROM ${DeletedReaderAnnotation.TABLE_NAME} WHERE ${DeletedReaderAnnotation.DELETED_AT} < :before")
    suspend fun deleteExpiredReaderAnnotationTombstones(before: Long)
}
