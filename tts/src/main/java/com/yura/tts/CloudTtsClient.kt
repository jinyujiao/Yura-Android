package com.yura.tts

import com.yura.tts.core.MicrosoftVoice

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject

internal class CloudTtsClient {
    fun synthesizeMimo(text: String, apiKey: String, voice: String, output: File) {
        require(apiKey.isNotBlank()) { "MiMo API key is empty." }
        val body = JSONObject()
            .put("model", MIMO_MODEL)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "user").put("content", MIMO_READING_INSTRUCTION))
                .put(JSONObject().put("role", "assistant").put("content", text)))
            .put("audio", JSONObject().put("voice", voice).put("format", "wav"))
            .toString()
        val json = postJson(MIMO_ENDPOINT, apiKey, body)
        val audioBase64 = json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
            ?.optJSONObject("audio")?.optString("data").orEmpty()
        require(audioBase64.isNotBlank()) { "MiMo 没有返回音频数据。" }
        TtsAtomicFileWriter.write(output) { partial ->
            partial.writeBytes(Base64.getDecoder().decode(audioBase64))
        }
    }

    fun synthesizeMicrosoft(text: String, apiKey: String, region: String, voice: String, output: File) {
        require(apiKey.isNotBlank()) { "Microsoft Speech key is empty." }
        require(region.isNotBlank()) { "Microsoft Speech region is empty." }
        val connection = URL("https://$region.tts.speech.microsoft.com/cognitiveservices/v1").openConnection() as HttpURLConnection
        val ssml = """
            <speak version="1.0" xml:lang="zh-CN">
                <voice xml:lang="zh-CN" name="${escapeXml(voice)}">${escapeXml(text)}</voice>
            </speak>
        """.trimIndent()
        connection.requestMethod = "POST"
        connection.connectTimeout = 15_000
        connection.readTimeout = 45_000
        connection.doOutput = true
        connection.setRequestProperty("Ocp-Apim-Subscription-Key", apiKey)
        connection.setRequestProperty("Content-Type", "application/ssml+xml")
        connection.setRequestProperty("X-Microsoft-OutputFormat", "audio-24khz-48kbitrate-mono-mp3")
        connection.setRequestProperty("User-Agent", "Yura")
        connection.outputStream.use { it.write(ssml.toByteArray(Charsets.UTF_8)) }
        if (connection.responseCode !in 200..299) {
            val message = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            throw IllegalStateException("Microsoft 朗读失败（${connection.responseCode}）。$message")
        }
        TtsAtomicFileWriter.write(output) { partial ->
            connection.inputStream.use { input -> partial.outputStream().use(input::copyTo) }
        }
    }

    fun fetchMicrosoftVoices(region: String, apiKey: String): List<MicrosoftVoice> {
        val connection = URL("https://$region.tts.speech.microsoft.com/cognitiveservices/voices/list").openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 12_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("Ocp-Apim-Subscription-Key", apiKey)
        val body = runCatching {
            if (connection.responseCode in 200..299) connection.inputStream.bufferedReader().use { it.readText() }
            else connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        }.getOrDefault("")
        if (connection.responseCode !in 200..299) {
            throw IllegalStateException("刷新 Microsoft 音色失败 (${connection.responseCode}）。${body.take(160)}")
        }
        val array = JSONArray(body)
        return List(array.length()) { index -> array.getJSONObject(index) }
            .mapNotNull { item ->
                val shortName = item.optString("ShortName").takeIf(String::isNotBlank) ?: return@mapNotNull null
                val locale = item.optString("Locale")
                if (!locale.startsWith("zh-", ignoreCase = true) && !locale.startsWith("en-", ignoreCase = true)) return@mapNotNull null
                val localName = item.optString("LocalName").takeIf(String::isNotBlank)
                val displayName = item.optString("DisplayName").takeIf(String::isNotBlank)
                MicrosoftVoice(shortName, listOfNotNull(localName ?: displayName, locale).joinToString(" · "), locale)
            }
            .distinctBy { it.shortName }
            .sortedWith(compareBy<MicrosoftVoice> { if (it.locale.startsWith("zh-", true)) 0 else 1 }.thenBy { it.locale }.thenBy { it.displayName })
    }

    private fun postJson(url: String, apiKey: String, body: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 15_000
        connection.readTimeout = 45_000
        connection.doOutput = true
        connection.setRequestProperty("api-key", apiKey)
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json")
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val responseText = if (connection.responseCode in 200..299) connection.inputStream.bufferedReader().use { it.readText() }
        else {
            val message = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            throw IllegalStateException("MiMo 朗读失败（${connection.responseCode}）。${message.ifBlank { "没有错误详情。" }}")
        }
        return JSONObject(responseText)
    }

    private fun escapeXml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private companion object {
        const val MIMO_ENDPOINT = "https://api.xiaomimimo.com/v1/chat/completions"
        const val MIMO_READING_INSTRUCTION =
            "请严格逐字朗读 assistant 消息中的原文。只朗读原文，不要改写、补充、解释、省略或重复任何内容；使用自然、清晰、适合阅读的语气。"
        const val MIMO_MODEL = "mimo-v2.5-tts"
    }
}
