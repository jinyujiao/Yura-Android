package com.yura.tts.core

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

class TtsRequestIdTest {
    @Test
    fun roundTripsCloudAndSystemRequestIds() {
        assertEquals(4 to 12, TtsRequestId.parseCloud(TtsRequestId.cloud(4, 12)))
        assertEquals(8 to 3, TtsRequestId.parseSystem(TtsRequestId.system(8, 3)))
    }

    @Test
    fun rejectsInvalidRequestIds() {
        assertNull(TtsRequestId.parseCloud("system:1:2"))
        assertNull(TtsRequestId.parseCloud("1:bad"))
        assertNull(TtsRequestId.parseSystem("1:2"))
        assertNull(TtsRequestId.parseSystem("other:1:2"))
    }
}
