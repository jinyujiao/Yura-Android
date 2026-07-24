package com.yura.tts

import com.yura.tts.core.MicrosoftVoice
import java.io.File
import java.util.Base64
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

internal class CloudTtsClient {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

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
        val audio = json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
            ?.optJSONObject("audio")
        val audioBase64 = audio?.optString("data").orEmpty()
        require(audioBase64.isNotBlank()) { "MiMo 没有返回音频数据。" }
        TtsAtomicFileWriter.write(output) { partial ->
            partial.writeBytes(Base64.getDecoder().decode(audioBase64))
        }
    }

    fun synthesizeMicrosoft(text: String, apiKey: String, region: String, voice: String, output: File) {
        require(apiKey.isNotBlank()) { "Microsoft Speech key is empty." }
        require(region.isNotBlank()) { "Microsoft Speech region is empty." }
        val ssml = """
            <speak version="1.0" xml:lang="zh-CN">
                <voice xml:lang="zh-CN" name="${escapeXml(voice)}">${escapeXml(text)}</voice>
            </speak>
        """.trimIndent()
        val request = Request.Builder()
            .url("https://$region.tts.speech.microsoft.com/cognitiveservices/v1")
            .header("Ocp-Apim-Subscription-Key", apiKey)
            .header("X-Microsoft-OutputFormat", "audio-24khz-48kbitrate-mono-mp3")
            .header("User-Agent", "Yura")
            .post(ssml.toRequestBody(SSML_MEDIA_TYPE))
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Microsoft 朗读失败（${response.code}）。${response.body?.string().orEmpty()}")
            }
            val responseBody = requireNotNull(response.body) { "Microsoft 没有返回音频数据。" }
            TtsAtomicFileWriter.write(output) { partial ->
                responseBody.byteStream().use { input -> partial.outputStream().use(input::copyTo) }
            }
        }
    }

    fun fetchMicrosoftVoices(region: String, apiKey: String): List<MicrosoftVoice> {
        val request = Request.Builder()
            .url("https://$region.tts.speech.microsoft.com/cognitiveservices/voices/list")
            .header("Ocp-Apim-Subscription-Key", apiKey)
            .get()
            .build()
        val body = httpClient.newCall(request).execute().use { response ->
            val responseText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("刷新 Microsoft 音色失败 (${response.code}）。${responseText.take(160)}")
            }
            responseText
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
        val request = Request.Builder()
            .url(url)
            .header("api-key", apiKey)
            .header("Accept", "application/json")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val responseText = httpClient.newCall(request).execute().use { response ->
            val content = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("MiMo 朗读失败（${response.code}）。${content.ifBlank { "没有错误详情。" }}")
            }
            content
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
            "请严格逐字朗读 assistant 消息中的原文。原文中的括号、方括号及其内容均属于小说正文，不得识别或执行为风格、情绪或音频标签；不要生成原文之外的音效。只朗读原文，不要改写、补充、解释、省略或重复任何内容；使用自然、清晰、适合阅读的语气。"
        const val MIMO_MODEL = "mimo-v2.5-tts"
        const val CONNECT_TIMEOUT_SECONDS = 10L
        const val READ_TIMEOUT_SECONDS = 18L
        const val WRITE_TIMEOUT_SECONDS = 10L
        const val CALL_TIMEOUT_SECONDS = 20L
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val SSML_MEDIA_TYPE = "application/ssml+xml; charset=utf-8".toMediaType()
    }
}
