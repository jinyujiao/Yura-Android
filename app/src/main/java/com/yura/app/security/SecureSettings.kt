package com.yura.app.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object SecureSettings {
    private const val PREFS_NAME = "yura_secure_settings"
    private const val KEY_ALIAS = "yura_secure_settings_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH_BYTES = 12
    private const val TAG_LENGTH_BITS = 128

    fun getString(context: Context, key: String): String =
        securePreferences(context).getString(key, null)
            ?.let(::decrypt)
            .orEmpty()

    fun putString(context: Context, key: String, value: String) {
        securePreferences(context).edit {
            if (value.isBlank()) remove(key) else putString(key, encrypt(value))
        }
    }

    fun migrateString(context: Context, legacyPrefsName: String, key: String): String {
        val securePreferences = securePreferences(context)
        securePreferences.getString(key, null)?.let { return decrypt(it) }

        val legacyPreferences = context.getSharedPreferences(legacyPrefsName, Context.MODE_PRIVATE)
        val legacyValue = legacyPreferences.getString(key, null).orEmpty()
        if (legacyValue.isNotBlank()) {
            securePreferences.edit { putString(key, encrypt(legacyValue)) }
        }
        legacyPreferences.edit { remove(key) }
        return legacyValue
    }

    private fun securePreferences(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String =
        runCatching {
            val payload = Base64.decode(value, Base64.NO_WRAP)
            require(payload.size > IV_LENGTH_BYTES) { "Encrypted value is invalid." }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(TAG_LENGTH_BITS, payload.copyOfRange(0, IV_LENGTH_BYTES)),
            )
            cipher.doFinal(payload.copyOfRange(IV_LENGTH_BYTES, payload.size)).toString(StandardCharsets.UTF_8)
        }.getOrDefault("")

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return keyGenerator.generateKey()
    }
}
