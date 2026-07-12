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
}
