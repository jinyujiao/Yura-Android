package com.yura.tts.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class TtsRequestIdTest {
    private val identity = TtsRequestIdentity(
        sessionId = 42L,
        queueSequence = 12,
        chapterPosition = 4,
        textHash = TtsRequestId.textHash("“你好。”她说。"),
    )

    @Test
    fun roundTripsMediaAndSystemRequestIds() {
        assertEquals(identity, TtsRequestId.parseMedia(TtsRequestId.media(identity)))
        assertEquals(identity, TtsRequestId.parseSystem(TtsRequestId.system(identity)))
    }

    @Test
    fun rejectsWrongKindsAndInvalidRequestIds() {
        assertNull(TtsRequestId.parseMedia(TtsRequestId.system(identity)))
        assertNull(TtsRequestId.parseSystem("tts:v2:system:bad:12:4:abcd"))
        assertNull(TtsRequestId.parseMedia("1:12"))
        assertNull(TtsRequestId.parseSystem(null))
    }

    @Test
    fun textHashChangesWhenDialoguePunctuationChanges() {
        assertEquals(TtsRequestId.textHash("“你好。”她说。"), TtsRequestId.textHash("“你好。”她说。"))
        assertNotEquals(TtsRequestId.textHash("“你好。”她说。"), TtsRequestId.textHash("你好。她说。"))
    }
}
