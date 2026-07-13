/*
 * Copyright 2024 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.navigator.web.internals.webview

internal class TextSelectionInteractionState(
    private val onActiveChanged: (Boolean) -> Unit,
) {
    var isLongPressPending: Boolean = false
        private set

    var hasJavascriptSelection: Boolean = false
        private set

    var hasActionMode: Boolean = false
        private set

    val isActive: Boolean
        get() = isLongPressPending || hasJavascriptSelection || hasActionMode

    fun setLongPressPending(active: Boolean) = update { isLongPressPending = active }

    fun setJavascriptSelection(active: Boolean) = update { hasJavascriptSelection = active }

    fun setActionMode(active: Boolean) = update { hasActionMode = active }

    private inline fun update(block: () -> Unit) {
        val wasActive = isActive
        block()
        if (isActive != wasActive) {
            onActiveChanged(isActive)
        }
    }
}

