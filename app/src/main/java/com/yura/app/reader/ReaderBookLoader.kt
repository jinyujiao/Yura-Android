package com.yura.app.reader

import com.yura.app.data.YuraDao
import com.yura.app.library.ReadiumServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.allAreHtml
import org.readium.r2.shared.util.getOrElse

class ReaderBookLoader(
    private val dao: YuraDao,
    private val readium: ReadiumServices,
) {
    suspend fun open(bookId: Long, initialPreferences: EpubPreferences): ReaderState =
        withContext(Dispatchers.IO) {
            runCatching {
                val book = dao.book(bookId) ?: error("找不到这本书")
                dao.markBookRead(bookId, System.currentTimeMillis())
                val openedAsset = readium.assetRetriever.retrieve(book.url, book.mediaType)
                    .getOrElse { error("无法读取图书：${it.message}") }
                val openedPublication = readium.publicationOpener.open(
                    openedAsset,
                    allowUserInteraction = true,
                ).getOrElse { error("无法打开图书：${it.message}") }

                if (!openedPublication.conformsTo(Publication.Profile.EPUB) &&
                    !openedPublication.readingOrder.allAreHtml
                ) {
                    openedPublication.close()
                    openedAsset.close()
                    error("当前阅读器只支持 EPUB")
                }

                ReaderState.Ready(
                    book = book,
                    publication = openedPublication,
                    initialLocator = book.progression
                        .takeUnless { it.isBlank() || it == "{}" }
                        ?.let { Locator.fromJSON(JSONObject(it)) },
                    initialPreferences = initialPreferences,
                    navigatorFactory = EpubNavigatorFactory(openedPublication),
                    asset = openedAsset,
                )
            }.getOrElse {
                ReaderState.Error(it.message ?: "打开图书失败")
            }
        }
}
