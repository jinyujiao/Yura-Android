package com.yura.tts

import com.yura.tts.core.TtsProvider
import com.yura.tts.core.MicrosoftVoice

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

internal class TtsPreferencesStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun provider(): TtsProvider =
        runCatching { TtsProvider.valueOf(prefs.getString(KEY_PROVIDER, TtsProvider.SYSTEM.name).orEmpty()) }
            .getOrDefault(TtsProvider.SYSTEM)

    fun setProvider(provider: TtsProvider) {
        prefs.edit { putString(KEY_PROVIDER, provider.name) }
    }

    fun mimoVoice(): String =
        prefs.getString(KEY_MIMO_VOICE, SimpleTtsController.DEFAULT_MIMO_VOICE).orEmpty()
            .ifBlank { SimpleTtsController.DEFAULT_MIMO_VOICE }

    fun setMimoVoice(voice: String) {
        prefs.edit { putString(KEY_MIMO_VOICE, voice) }
    }

    fun mimoApiKey(): String = TtsSecureSettings.migrateString(appContext, PREFS_NAME, KEY_MIMO_API_KEY)

    fun setMimoApiKey(value: String) {
        TtsSecureSettings.putString(appContext, KEY_MIMO_API_KEY, value.trim())
    }

    fun microsoftVoice(): String =
        prefs.getString(KEY_MICROSOFT_VOICE, SimpleTtsController.DEFAULT_MICROSOFT_VOICE).orEmpty()
            .ifBlank { SimpleTtsController.DEFAULT_MICROSOFT_VOICE }

    fun setMicrosoftVoice(voice: String) {
        prefs.edit {
            putString(KEY_MICROSOFT_VOICE, voice.trim().ifBlank { SimpleTtsController.DEFAULT_MICROSOFT_VOICE })
        }
    }

    fun microsoftVoices(): List<MicrosoftVoice> {
        val raw = prefs.getString(KEY_MICROSOFT_VOICES, "").orEmpty()
        val fetched = runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                MicrosoftVoice(
                    shortName = item.optString("shortName"),
                    displayName = item.optString("displayName"),
                    locale = item.optString("locale"),
                )
            }.filter { it.shortName.isNotBlank() && it.displayName.isNotBlank() }
        }.getOrDefault(emptyList())
        return fetched.ifEmpty { SimpleTtsController.MICROSOFT_VOICES }
    }

    fun saveMicrosoftVoices(voices: List<MicrosoftVoice>, selectedVoice: String) {
        prefs.edit {
            putString(KEY_MICROSOFT_VOICES, JSONArray().also { array ->
                voices.forEach { voice ->
                    array.put(JSONObject().put("shortName", voice.shortName).put("displayName", voice.displayName).put("locale", voice.locale))
                }
            }.toString())
            putString(KEY_MICROSOFT_VOICE, selectedVoice)
        }
    }

    fun microsoftRegion(): String = prefs.getString(KEY_MICROSOFT_REGION, "").orEmpty()

    fun setMicrosoftRegion(region: String) {
        prefs.edit { putString(KEY_MICROSOFT_REGION, region.trim()) }
    }

    fun microsoftApiKey(): String = TtsSecureSettings.migrateString(appContext, PREFS_NAME, KEY_MICROSOFT_API_KEY)

    fun setMicrosoftApiKey(value: String) {
        TtsSecureSettings.putString(appContext, KEY_MICROSOFT_API_KEY, value.trim())
    }

    fun playbackSpeed(): Float {
        val saved = prefs.getFloat(KEY_PLAYBACK_SPEED, SimpleTtsController.DEFAULT_PLAYBACK_SPEED)
        return SimpleTtsController.PLAYBACK_SPEEDS.minBy { kotlin.math.abs(it - saved) }
    }

    fun setPlaybackSpeed(speed: Float) {
        prefs.edit { putFloat(KEY_PLAYBACK_SPEED, speed) }
    }

    private companion object {
        const val PREFS_NAME = "reader-tts"
        const val KEY_PROVIDER = "provider"
        const val KEY_MIMO_VOICE = "mimo_voice"
        const val KEY_MIMO_API_KEY = "mimo_api_key"
        const val KEY_MICROSOFT_VOICE = "microsoft_voice"
        const val KEY_MICROSOFT_VOICES = "microsoft_voices"
        const val KEY_MICROSOFT_REGION = "microsoft_region"
        const val KEY_MICROSOFT_API_KEY = "microsoft_api_key"
        const val KEY_PLAYBACK_SPEED = "playback_speed"
    }
}
