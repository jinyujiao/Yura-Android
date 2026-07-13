/*
 * Copyright 2025 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.navigator.web.reflowable.resource

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class TextSelectionTouchPolicyTest {

    @Test
    fun selectionInteractionLetsWebViewHandleMoveEvents() {
        assertFalse(
            shouldConsumeWebViewMove(
                isHorizontalPagination = true,
                isMoveEvent = true,
                isTextSelectionInteractionActive = true,
            )
        )
    }

    @Test
    fun idleHorizontalPaginationStillConsumesMoveEvents() {
        assertTrue(
            shouldConsumeWebViewMove(
                isHorizontalPagination = true,
                isMoveEvent = true,
                isTextSelectionInteractionActive = false,
            )
        )
    }

    @Test
    fun verticalAndNonMoveEventsAreNotConsumed() {
        assertFalse(shouldConsumeWebViewMove(false, true, false))
        assertFalse(shouldConsumeWebViewMove(true, false, false))
    }
}
