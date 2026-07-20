package com.yura.tts.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TtsPlaybackPolicyTest {
    @Test
    fun providerSwitchResumesCurrentQueueSequence() {
        assertEquals(
            7,
            TtsPlaybackPolicy.providerSwitchResumeSequence(
                currentQueueSequence = 7,
                stateQueueSequence = 5,
                queueSize = 20,
                state = TtsState.PLAYING,
            ),
        )
    }

    @Test
    fun providerSwitchUsesVisibleStateWhileCloudRequestIsLoadingOrFailed() {
        assertEquals(5, TtsPlaybackPolicy.providerSwitchResumeSequence(-1, 5, 20, TtsState.LOADING))
        assertEquals(5, TtsPlaybackPolicy.providerSwitchResumeSequence(-1, 5, 20, TtsState.ERROR))
    }

    @Test
    fun idleOrEmptyQueueDoesNotRestartFromChapterBeginning() {
        assertNull(TtsPlaybackPolicy.providerSwitchResumeSequence(-1, -1, 20, TtsState.IDLE))
        assertNull(TtsPlaybackPolicy.providerSwitchResumeSequence(0, 0, 0, TtsState.PLAYING))
    }

    @Test
    fun playbackRequestStartsOnlyOncePerSessionAndQueueSequence() {
        val request = TtsRequestIdentity(7L, 3, 2, "hash")

        assertEquals(true, TtsPlaybackPolicy.shouldStartPlayback(request, null, 3))
        assertEquals(false, TtsPlaybackPolicy.shouldStartPlayback(request, request, 3))
        assertEquals(false, TtsPlaybackPolicy.shouldStartPlayback(request, null, 4))
        assertEquals(
            true,
            TtsPlaybackPolicy.shouldStartPlayback(request.copy(sessionId = 8L), request, 3),
        )
    }
}
