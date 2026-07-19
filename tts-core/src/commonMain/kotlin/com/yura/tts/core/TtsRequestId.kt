package com.yura.tts.core

data class TtsRequestIdentity(
    val sessionId: Long,
    val queueSequence: Int,
    val chapterPosition: Int,
    val textHash: String,
)

object TtsRequestId {
    private const val VERSION = "v2"
    private const val SYSTEM_KIND = "system"
    private const val MEDIA_KIND = "media"

    fun system(identity: TtsRequestIdentity): String = encode(SYSTEM_KIND, identity)

    fun media(identity: TtsRequestIdentity): String = encode(MEDIA_KIND, identity)

    fun parseSystem(value: String?): TtsRequestIdentity? = parse(value, SYSTEM_KIND)

    fun parseMedia(value: String?): TtsRequestIdentity? = parse(value, MEDIA_KIND)

    fun textHash(text: String): String = text.hashCode().toUInt().toString(16).padStart(8, '0')

    private fun encode(kind: String, identity: TtsRequestIdentity): String = listOf(
        "tts",
        VERSION,
        kind,
        identity.sessionId,
        identity.queueSequence,
        identity.chapterPosition,
        identity.textHash,
    ).joinToString(":")

    private fun parse(value: String?, expectedKind: String): TtsRequestIdentity? {
        val parts = value?.split(":") ?: return null
        if (parts.size != 7 || parts[0] != "tts" || parts[1] != VERSION || parts[2] != expectedKind) {
            return null
        }
        val textHash = parts[6].takeIf { it.isNotBlank() } ?: return null
        return TtsRequestIdentity(
            sessionId = parts[3].toLongOrNull() ?: return null,
            queueSequence = parts[4].toIntOrNull() ?: return null,
            chapterPosition = parts[5].toIntOrNull() ?: return null,
            textHash = textHash,
        )
    }
}
