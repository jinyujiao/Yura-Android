package com.yura.app.ui.shelf

import com.yura.app.data.Book

enum class ShelfSort(val label: String) {
    RecentlyRead("最近阅读"),
    RecentlyImported("最近导入"),
    Title("书名"),
}

object ShelfBookFilter {
    fun filterAndSort(books: List<Book>, query: String, sort: ShelfSort): List<Book> {
        val normalizedQuery = query.trim()
        val filtered = if (normalizedQuery.isEmpty()) books else books.filter { book ->
            book.title.contains(normalizedQuery, ignoreCase = true) ||
                book.author.contains(normalizedQuery, ignoreCase = true)
        }
        return when (sort) {
            ShelfSort.RecentlyRead -> filtered.sortedByDescending(Book::lastReadDate)
            ShelfSort.RecentlyImported -> filtered.sortedByDescending(Book::creationDate)
            ShelfSort.Title -> filtered.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        }
    }
}
