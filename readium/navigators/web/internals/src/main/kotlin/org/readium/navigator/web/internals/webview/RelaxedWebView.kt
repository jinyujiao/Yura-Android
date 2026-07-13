/*
 * Copyright 2024 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.navigator.web.internals.webview

import android.content.Context
import android.graphics.Rect
import android.view.ActionMode
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView

/**
 * WebView allowing access to some protected fields.
 */
public class RelaxedWebView(context: Context) : WebView(context) {

    private companion object {
        const val LONG_PRESS_SELECTION_GRACE_PERIOD_MS = 200L
    }

    public val maxScrollX: Int get() =
        horizontalScrollRange - horizontalScrollExtent

    public val maxScrollY: Int get() =
        verticalScrollRange - verticalScrollExtent

    public val canScrollRight: Boolean get() =
        scrollX < maxScrollX

    public val canScrollLeft: Boolean get() =
        scrollX > 0

    public val canScrollTop: Boolean get() =
        scrollY > 0

    public val canScrollBottom: Boolean get() =
        scrollY < maxScrollY

    public val verticalScrollRange: Int get() =
        computeVerticalScrollRange()

    public val horizontalScrollRange: Int get() =
        computeHorizontalScrollRange()

    public val verticalScrollExtent: Int get() =
        computeVerticalScrollExtent()

    public val horizontalScrollExtent: Int get() =
        computeHorizontalScrollExtent()

    private var nextLayoutListener: (() -> Unit) = {}

    public fun setNextLayoutListener(block: () -> Unit) {
        nextLayoutListener = block
    }

    private var actionModeCallback: ActionMode.Callback? = null

    private var textSelectionInteractionListener: ((Boolean) -> Unit)? = null

    private val textSelectionInteractionState = TextSelectionInteractionState { active ->
        textSelectionInteractionListener?.invoke(active)
    }

    private val releaseLongPressPending = Runnable {
        textSelectionInteractionState.setLongPressPending(false)
    }

    public val isTextSelectionInteractionActive: Boolean
        get() = textSelectionInteractionState.isActive

    public fun setCustomSelectionActionModeCallback(
        callback: ActionMode.Callback?,
    ) {
        actionModeCallback = callback
    }

    public fun setTextSelectionInteractionListener(listener: ((Boolean) -> Unit)?) {
        textSelectionInteractionListener = listener
        listener?.invoke(isTextSelectionInteractionActive)
    }

    public fun setJavascriptSelectionActive(active: Boolean) {
        textSelectionInteractionState.setJavascriptSelection(active)
    }

    override fun performLongClick(): Boolean {
        removeCallbacks(releaseLongPressPending)
        textSelectionInteractionState.setLongPressPending(true)
        return super.performLongClick().also { handled ->
            if (!handled) {
                textSelectionInteractionState.setLongPressPending(false)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return super.onTouchEvent(event).also {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                removeCallbacks(releaseLongPressPending)
                postDelayed(releaseLongPressPending, LONG_PRESS_SELECTION_GRACE_PERIOD_MS)
            }
        }
    }

    @Suppress("Deprecation")
    @Deprecated("Deprecated in Java")
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        nextLayoutListener.invoke()
        nextLayoutListener = {}
    }

    override fun onOverScrolled(scrollX: Int, scrollY: Int, clampedX: Boolean, clampedY: Boolean) {
        // Workaround addressing a bug in the Android WebView where the viewport is scrolled while
        // dragging the text selection handles.
        // See https://github.com/readium/kotlin-toolkit/issues/325
        if (textSelectionInteractionState.hasActionMode) {
            return
        }

        super.onOverScrolled(scrollX, scrollY, clampedX, clampedY)
    }

    override fun startActionMode(callback: ActionMode.Callback): ActionMode? {
        return startActionMode(callback, ActionMode.TYPE_PRIMARY)
    }

    override fun startActionMode(callback: ActionMode.Callback, type: Int): ActionMode? {
        val decoratedCallback = CallbackDecorator(
            callback = actionModeCallback ?: callback,
            onCreateActionModeCallback = { textSelectionInteractionState.setActionMode(true) },
            onDestroyActionModeCallback = { textSelectionInteractionState.setActionMode(false) }
        )

        val wrapper = Callback2Wrapper(
            decoratedCallback,
            callback2 = callback as? ActionMode.Callback2
        )

        if (actionModeCallback == null) {
            return super.startActionMode(wrapper, type)
        }

        val parent = parent ?: return null
        return parent.startActionModeForChild(this, wrapper, type)
    }
}

private class Callback2Wrapper(
    val callback: ActionMode.Callback,
    val callback2: ActionMode.Callback2?,
) : ActionMode.Callback by callback, ActionMode.Callback2() {

    override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) =
        callback2?.onGetContentRect(mode, view, outRect)
            ?: super.onGetContentRect(mode, view, outRect)
}

private class CallbackDecorator(
    private val callback: ActionMode.Callback,
    private val onCreateActionModeCallback: () -> Unit,
    private val onDestroyActionModeCallback: () -> Unit,
) : ActionMode.Callback by callback {

    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        val created = callback.onCreateActionMode(mode, menu)
        if (created) {
            onCreateActionModeCallback()
        }
        return created
    }

    override fun onDestroyActionMode(mode: ActionMode?) {
        callback.onDestroyActionMode(mode)
        onDestroyActionModeCallback()
    }
}

/**
 * Best effort to delay the execution of a block until the Webview
 * has received data up-to-date at the moment when the call occurs or newer.
 */
public fun RelaxedWebView.invokeOnWebViewUpToDate(block: WebView.() -> Unit) {
    requestLayout()
    setNextLayoutListener {
        invokeOnReadyToBeDrawn(block)
    }
}
