package com.twovi.assistant.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts user-supplied provider keys at rest with an app-specific Android Keystore key. */
class ProviderKeyVault(context: Context) {
    private val preferences = context.getSharedPreferences("provider_keys", Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    @Synchronized
    fun get(provider: AiProvider): String? = getSecret("provider.${provider.name}")

    @Synchronized
    fun save(provider: AiProvider, value: String) = saveSecret("provider.${provider.name}", value)

    @Synchronized
    fun remove(provider: AiProvider) = removeSecret("provider.${provider.name}")

    @Synchronized
    fun getCompanionCode(): String? = getSecret("pc.companion.code")

    @Synchronized
    fun saveCompanionCode(value: String) = saveSecret("pc.companion.code", value)

    @Synchronized
    fun removeCompanionCode() = removeSecret("pc.companion.code")

    private fun getSecret(name: String): String? {
        val stored = preferences.getString(name, null) ?: return null
        return try {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            if (bytes.size <= GCM_IV_BYTES) return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val iv = bytes.copyOfRange(0, GCM_IV_BYTES)
            cipher.init(Cipher.DECRYPT_MODE, key(name), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(bytes.copyOfRange(GCM_IV_BYTES, bytes.size)), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun saveSecret(name: String, value: String) {
        if (value.isBlank()) {
            removeSecret(name)
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key(name))
        val sealed = cipher.iv + cipher.doFinal(value.trim().toByteArray(Charsets.UTF_8))
        preferences.edit().putString(name, Base64.encodeToString(sealed, Base64.NO_WRAP)).apply()
    }

    private fun removeSecret(name: String) {
        preferences.edit().remove(name).apply()
        runCatching { keyStore.deleteEntry(alias(name)) }
    }

    private fun key(name: String): SecretKey {
        val keyAlias = alias(name)
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private fun alias(name: String) = "$KEY_ALIAS_PREFIX.${name.replace('.', '_')}"

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS_PREFIX = "com.twovi.assistant.api"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
    }
}
