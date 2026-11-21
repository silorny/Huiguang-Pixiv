package moe.huiguang.pixiv

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import moe.huiguang.pixiv.data.store.KeyValueStore
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts the Pixiv `session_cookie` at rest using an Android Keystore
 * AES-256-GCM key while delegating all other preferences unchanged.
 *
 * Legacy plaintext values already stored on disk are transparently upgraded to
 * encrypted form on first read.
 */
internal class CookieEncryptingKeyValueStore(
    private val delegate: KeyValueStore,
) : KeyValueStore by delegate {

    override fun getString(key: String): String? {
        val raw = delegate.getString(key) ?: return null
        if (key != KEY_SESSION_COOKIE) return raw
        if (!raw.startsWith(ENC_PREFIX)) {
            // Transparently upgrade legacy plaintext cookie on first read.
            putString(key, raw)
            return raw
        }
        return decrypt(raw)
    }

    override fun putString(key: String, value: String) {
        if (key != KEY_SESSION_COOKIE) {
            delegate.putString(key, value)
            return
        }
        val encrypted = encrypt(value)
        if (encrypted != null) {
            delegate.putString(key, encrypted)
        } else {
            delegate.remove(key)
        }
    }

    private fun encrypt(plaintext: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val ctB64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        "$ENC_PREFIX$ivB64:$ctB64"
    }.getOrNull()

    private fun decrypt(encoded: String): String? = runCatching {
        val payload = encoded.removePrefix(ENC_PREFIX)
        val sep = payload.indexOf(':')
        if (sep <= 0) return null
        val iv = Base64.decode(payload.substring(0, sep), Base64.NO_WRAP)
        val ciphertext = Base64.decode(payload.substring(sep + 1), Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getExistingKey() ?: return null, GCMParameterSpec(GCM_TAG_BITS, iv))
        String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }.getOrNull()

    private fun getExistingKey(): SecretKey? {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
    }

    private fun getOrCreateKey(): SecretKey {
        getExistingKey()?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEY_SESSION_COOKIE = "session_cookie"
        const val KEY_ALIAS = "huiguang_session_cookie_v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val ENC_PREFIX = "enc1:"
    }
}
