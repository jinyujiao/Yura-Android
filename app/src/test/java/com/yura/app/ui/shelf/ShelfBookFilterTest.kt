package com.yura.app.ui.shelf

import com.yura.app.data.Book
import org.junit.Assert.assertEquals
import org.junit.Test

class ShelfBookFilterTest {
    @Test
    fun filtersByTitleAndSortsByRecentlyRead() {
        val books = listOf(
            book(id = 1, title = "Android", lastReadDate = 10),
            book(id = 2, title = "Android Design", lastReadDate = 20),
            book(id = 3, title = "Kotlin", lastReadDate = 30),
        )

        val result = ShelfBookFilter.filterAndSort(books, "android", ShelfSort.RecentlyRead)

        assertEquals(listOf(2L, 1L), result.map(Book::id))
    }

    @Test
    fun sortsTitlesIgnoringCase() {
        val result = ShelfBookFilter.filterAndSort(
            listOf(book(id = 1, title = "zebra"), book(id = 2, title = "Apple")),
            "",
            ShelfSort.Title,
        )

        assertEquals(listOf(2L, 1L), result.map(Book::id))
    }

    private fun book(id: Long, title: String, lastReadDate: Long = 0) = Book(
        id = id,
        creationDate = 1,
        lastReadDate = lastReadDate,
        href = "file:///book$id.epub",
        title = title,
        author = "Author",
        identifier = "id-$id",
        progression = "{}",
        rawMediaType = "application/epub+zip",
        cover = "",
    )
}
