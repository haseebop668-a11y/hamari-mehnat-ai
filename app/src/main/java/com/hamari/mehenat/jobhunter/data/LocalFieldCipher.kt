package com.hamari.mehenat.jobhunter.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts individual local database fields with an Android Keystore AES-GCM key. */
internal class LocalFieldCipher {
    fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val payload = byteArrayOf(cipher.iv.size.toByte()) + cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    fun encryptIfNeeded(value: String): String {
        if (value.startsWith(PREFIX) && runCatching { decrypt(value) }.isSuccess) return value
        return encrypt(value)
    }

    fun decrypt(value: String): String {
        if (!value.startsWith(PREFIX)) return value
        val payload = Base64.decode(value.removePrefix(PREFIX), Base64.NO_WRAP)
        require(payload.size > 13) { "Encrypted local field is truncated." }
        val ivSize = payload[0].toInt() and 0xff
        require(ivSize in 12..16 && payload.size > 1 + ivSize) { "Encrypted local field header is invalid." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, payload.copyOfRange(1, 1 + ivSize)))
        return String(cipher.doFinal(payload.copyOfRange(1 + ivSize, payload.size)), Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val KEY_ALIAS = "hamari_mehnat_local_fields_key"
        private const val PREFIX = "enc:v1:"
    }
}
