package com.yura.app.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderTtsParagraphParserTest {
    @Test
    fun extractsReadableParagraphsAndIgnoresNavigation() {
        val html = """
            <html><body>
              <nav><p>目录不应朗读</p></nav>
              <script>ignore()</script>
              <h1>第一章</h1><p>正文第一段。</p><blockquote>引用内容！</blockquote>
            </body></html>
        """.trimIndent()

        assertEquals(listOf("第一章", "正文第一段。", "引用内容！"), ReaderTtsParagraphParser.parse(html))
    }

    @Test
    fun fallsBackToPlainBodyTextWhenNoParagraphTagsExist() {
        val html = "<html><body>第一句。第二句！</body></html>"

        assertEquals(listOf("第一句。", "第二句！"), ReaderTtsParagraphParser.parse(html))
    }
    @Test
    fun nestedReadableContainersDoNotDuplicateChildParagraphs() {
        val html = """
            <html><body>
              <blockquote><p>引用内容。</p></blockquote>
              <ul><li><p>列表内容。</p></li></ul>
            </body></html>
        """.trimIndent()

        assertEquals(listOf("引用内容。", "列表内容。"), ReaderTtsParagraphParser.parse(html))
    }

    @Test
    fun nestedListsKeepOuterTextWithoutRepeatingInnerText() {
        val html = """
            <html><body>
              <ul><li>外层内容<ul><li>内层内容</li></ul></li></ul>
            </body></html>
        """.trimIndent()

        assertEquals(listOf("外层内容", "内层内容"), ReaderTtsParagraphParser.parse(html))
    }


    @Test
    fun preservesProsodyForProviderSpecificCleaning() {
        val source = "“咿哈哈……等等？！——”"

        assertEquals("“咿哈哈 等等！ ”", ReaderTtsParagraphParser.clean(source))
        assertEquals("“咿哈哈……等等？！——”", ReaderTtsParagraphParser.cleanForTts(source))
        assertEquals(
            listOf("“咿哈哈……等等？！——”"),
            ReaderTtsParagraphParser.parse("<html><body><p>$source</p></body></html>"),
        )
    }

}
