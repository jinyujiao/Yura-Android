package com.yura.tts.core

object TtsPlaybackPolicy {
    fun providerSwitchResumeSequence(
        currentQueueSequence: Int,
        stateQueueSequence: Int,
        queueSize: Int,
        state: TtsState,
    ): Int? {
        if (queueSize <= 0 || state == TtsState.IDLE) return null
        return currentQueueSequence.takeIf { it in 0 until queueSize }
            ?: stateQueueSequence.takeIf { it in 0 until queueSize }
    }

    fun shouldStartPlayback(
        request: TtsRequestIdentity,
        lastStartedRequest: TtsRequestIdentity?,
        currentQueueSequence: Int,
    ): Boolean = request.queueSequence == currentQueueSequence && request != lastStartedRequest

}
