package com.yura.app.reader

import com.yura.app.data.Book
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

sealed interface ReaderState {
    data object Loading : ReaderState
    data class Error(val message: String) : ReaderState
    data class Ready(
        val book: Book,
        val publication: Publication,
        val initialLocator: Locator?,
        val initialPreferences: EpubPreferences,
        val navigatorFactory: EpubNavigatorFactory,
    ) : ReaderState
}
