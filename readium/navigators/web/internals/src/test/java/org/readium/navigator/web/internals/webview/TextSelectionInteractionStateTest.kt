/*
 * Copyright 2024 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.navigator.web.internals.webview

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextSelectionInteractionStateTest {

    @Test
    fun staysActiveUntilAllSelectionSignalsEnd() {
        val changes = mutableListOf<Boolean>()
        val state = TextSelectionInteractionState(changes::add)

        state.setLongPressPending(true)
        state.setJavascriptSelection(true)
        state.setActionMode(true)
        state.setLongPressPending(false)
        state.setActionMode(false)

        assertTrue(state.isActive)
        assertEquals(listOf(true), changes)

        state.setJavascriptSelection(false)

        assertFalse(state.isActive)
        assertEquals(listOf(true, false), changes)
    }

    @Test
    fun ignoresRepeatedValues() {
        val changes = mutableListOf<Boolean>()
        val state = TextSelectionInteractionState(changes::add)

        state.setLongPressPending(true)
        state.setLongPressPending(true)
        state.setJavascriptSelection(true)
        state.setJavascriptSelection(true)
        state.setLongPressPending(false)
        state.setJavascriptSelection(false)
        state.setJavascriptSelection(false)

        assertEquals(listOf(true, false), changes)
    }

    @Test
    fun rejectedActionModeDoesNotKeepInteractionActive() {
        val changes = mutableListOf<Boolean>()
        val state = TextSelectionInteractionState(changes::add)

        state.setLongPressPending(true)
        state.setLongPressPending(false)
        state.setActionMode(false)

        assertFalse(state.isActive)
        assertEquals(listOf(true, false), changes)
    }
}
