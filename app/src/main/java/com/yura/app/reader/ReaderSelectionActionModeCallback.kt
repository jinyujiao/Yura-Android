package com.yura.app.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.view.ActionMode
import android.view.HapticFeedbackConstants
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.yura.app.data.ReaderAnnotation
import com.yura.app.data.YuraDao
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

internal class ReaderSelectionActionModeCallback(
    private val context: Context,
    private val bookId: Long,
    private val dao: YuraDao,
    private val scope: CoroutineScope,
    private val navigator: () -> EpubNavigatorFragment?,
    private val publication: () -> Publication?,
    private val onNoteRequested: (Locator) -> Unit,
    private val onCorrectionRequested: (Locator) -> Unit,
) : ActionMode.Callback {

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        (context as? android.app.Activity)?.window?.decorView
            ?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        menu.clear()
        menu.add(Menu.NONE, ACTION_COPY, 0, "复制")
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        menu.add(Menu.NONE, ACTION_NOTE, 1, "笔记")
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        menu.add(Menu.NONE, ACTION_HIGHLIGHT, 2, "高亮")
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        menu.add(Menu.NONE, ACTION_CORRECTION, 3, "修订")
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        if (item.itemId !in setOf(ACTION_COPY, ACTION_NOTE, ACTION_HIGHLIGHT, ACTION_CORRECTION)) return false
        scope.launch {
            val activeNavigator = navigator()
            val selection = activeNavigator?.currentSelection()
            if (selection == null) {
                Toast.makeText(context, "无法读取当前选区", Toast.LENGTH_SHORT).show()
                mode.finish()
                return@launch
            }

            when (item.itemId) {
                ACTION_COPY -> {
                    val selectedText = selection.locator.text.highlight.orEmpty()
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard.setPrimaryClip(ClipData.newPlainText("Yura 选中文字", selectedText))
                    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                }
                ACTION_HIGHLIGHT -> {
                    saveAnnotation(ReaderAnnotation.TYPE_HIGHLIGHT, selection.locator, note = "")
                    refreshDecorations()
                    Toast.makeText(context, "已高亮", Toast.LENGTH_SHORT).show()
                }
                ACTION_NOTE -> onNoteRequested(selection.locator)
                ACTION_CORRECTION -> onCorrectionRequested(selection.locator)
            }
            activeNavigator.clearSelection()
            mode.finish()
        }
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode) = Unit

    fun refreshDecorations() {
        scope.launch {
            val decorations = dao.annotationsForBook(bookId).mapNotNull { annotation ->
                val locator = annotation.locator ?: return@mapNotNull null
                val style = when (annotation.type) {
                    ReaderAnnotation.TYPE_NOTE -> Decoration.Style.Underline(NOTE_COLOR)
                    ReaderAnnotation.TYPE_HIGHLIGHT -> Decoration.Style.Highlight(HIGHLIGHT_COLOR)
                    ReaderAnnotation.TYPE_CORRECTION -> Decoration.Style.Highlight(CORRECTION_COLOR)
                    else -> return@mapNotNull null
                }
                Decoration(id = annotation.id, locator = locator, style = style)
            }
            navigator()?.applyDecorations(decorations, DECORATION_GROUP)
        }
    }

    fun saveNote(locator: Locator, note: String) {
        scope.launch {
            saveAnnotation(ReaderAnnotation.TYPE_NOTE, locator, note)
            refreshDecorations()
            Toast.makeText(context, "笔记已保存", Toast.LENGTH_SHORT).show()
        }
    }

    fun saveCorrection(locator: Locator, replacement: String) {
        scope.launch {
            saveAnnotation(ReaderAnnotation.TYPE_CORRECTION, locator, replacement)
            refreshDecorations()
            Toast.makeText(context, "修订已保存到笔记", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun saveAnnotation(type: String, locator: Locator, note: String) {
        val chapter = publication()?.let { ReaderChapterTitleResolver.resolveInfo(it, locator) }
        val now = System.currentTimeMillis()
        dao.upsertAnnotation(
            ReaderAnnotation(
                id = UUID.randomUUID().toString(),
                bookId = bookId,
                type = type,
                locatorJson = locator.toJSON().toString(),
                note = note,
                createdAt = now,
                updatedAt = now,
                chapterIndex = chapter?.index ?: -1,
                chapterTitle = chapter?.title.orEmpty(),
                chapterHref = chapter?.href ?: locator.href.toString().substringBefore('#'),
            ),
        )
    }

    private companion object {
        const val ACTION_COPY = 1
        const val ACTION_NOTE = 2
        const val ACTION_HIGHLIGHT = 3
        const val ACTION_CORRECTION = 4
        const val DECORATION_GROUP = "reader-annotations"
        val HIGHLIGHT_COLOR: Int = Color.rgb(255, 213, 79)
        val NOTE_COLOR: Int = Color.rgb(255, 143, 0)
        val CORRECTION_COLOR: Int = Color.rgb(206, 147, 216)
    }
}
