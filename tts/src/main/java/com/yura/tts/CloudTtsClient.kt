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
    private val microsoftHttpClient = httpClient.newBuilder()
        .connectTimeout(MICROSOFT_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(MICROSOFT_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(MICROSOFT_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(MICROSOFT_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    fun synthesizeMimo(text: String, apiKey: String, voice: String, output: File) {
        require(apiKey.isNotBlank()) { "MiMo API key is empty." }
        val body = JSONObject()
            .put("model", MIMO_MODEL)
            .put(
                "messages",
                JSONArray().put(JSONObject().put("role", "assistant").put("content", text))
            )
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
        val safeRegion = normalizeMicrosoftRegion(region)
        require(voice.isNotBlank()) { "Microsoft Speech voice is empty." }
        val ssml = createMicrosoftSsml(text, voice)
        val request = Request.Builder()
            .url("https://$safeRegion.tts.speech.microsoft.com/cognitiveservices/v1")
            .header("Ocp-Apim-Subscription-Key", apiKey)
            .header("Accept", "audio/mpeg")
            .header("X-Microsoft-OutputFormat", MICROSOFT_OUTPUT_FORMAT)
            .header("User-Agent", "Yura")
            .post(ssml.toRequestBody(SSML_MEDIA_TYPE))
            .build()
        microsoftHttpClient.newCall(request).execute().use { response ->
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
        val safeRegion = normalizeMicrosoftRegion(region)
        require(apiKey.isNotBlank()) { "Microsoft Speech key is empty." }
        val request = Request.Builder()
            .url("https://$safeRegion.tts.speech.microsoft.com/cognitiveservices/voices/list")
            .header("Accept", "application/json")
            .header("Ocp-Apim-Subscription-Key", apiKey)
            .get()
            .build()
        val body = microsoftHttpClient.newCall(request).execute().use { response ->
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

    private companion object {
        const val MIMO_ENDPOINT = "https://api.xiaomimimo.com/v1/chat/completions"
        const val MICROSOFT_OUTPUT_FORMAT = "audio-24khz-48kbitrate-mono-mp3"
        const val CONNECT_TIMEOUT_SECONDS = 10L
        const val READ_TIMEOUT_SECONDS = 18L
        const val WRITE_TIMEOUT_SECONDS = 10L
        const val CALL_TIMEOUT_SECONDS = 20L
        const val MICROSOFT_CONNECT_TIMEOUT_SECONDS = 15L
        const val MICROSOFT_READ_TIMEOUT_SECONDS = 45L
        const val MICROSOFT_WRITE_TIMEOUT_SECONDS = 15L
        const val MICROSOFT_CALL_TIMEOUT_SECONDS = 60L
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val SSML_MEDIA_TYPE = "application/ssml+xml; charset=utf-8".toMediaType()
    }
}

internal fun createMicrosoftSsml(text: String, voice: String): String {
    val locale = microsoftVoiceLocale(voice)
    return """
        <speak version="1.0" xml:lang="$locale">
            <voice xml:lang="$locale" name="${escapeMicrosoftXml(voice)}">${escapeMicrosoftXml(text)}</voice>
        </speak>
    """.trimIndent()
}

internal fun microsoftVoiceLocale(voice: String): String =
    MICROSOFT_VOICE_LOCALE_REGEX.find(voice.trim())?.value ?: "zh-CN"

private fun normalizeMicrosoftRegion(region: String): String {
    val normalized = region.trim()
    require(normalized.matches(MICROSOFT_REGION_REGEX)) { "Microsoft Speech region is invalid." }
    return normalized
}

private fun escapeMicrosoftXml(value: String): String = value
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&apos;")

private const val MIMO_MODEL = "mimo-v2.5-tts"

private val MICROSOFT_VOICE_LOCALE_REGEX = Regex("""^[A-Za-z]{2,3}-[A-Za-z]{2,4}""")
private val MICROSOFT_REGION_REGEX = Regex("""[A-Za-z0-9-]{1,64}""")
