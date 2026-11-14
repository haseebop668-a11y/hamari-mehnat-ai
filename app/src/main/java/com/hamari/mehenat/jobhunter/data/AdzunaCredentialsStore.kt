package com.hamari.mehenat.jobhunter.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class AdzunaCredentials(val appId: String, val appKey: String)

class AdzunaCredentialsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("private_adzuna_credentials", Context.MODE_PRIVATE)
    private val alias = "hamari_mehnat_adzuna_key"

    fun save(appId: String, appKey: String) {
        val cleanId = appId.trim()
        val cleanKey = appKey.trim()
        require(cleanId.isNotBlank() && cleanId.length <= 200) { "A valid Adzuna app ID is required." }
        require(cleanKey.isNotBlank() && cleanKey.length <= 500) { "A valid Adzuna app key is required." }
        val plain = JSONObject().put("app_id", cleanId).put("app_key", cleanKey).toString().toByteArray(Charsets.UTF_8)
        val encrypted = encrypt(plain)
        check(prefs.edit().putString("credentials", Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit()) {
            "Could not save Adzuna credentials securely."
        }
    }

    fun load(): AdzunaCredentials? {
        val encoded = prefs.getString("credentials", null) ?: return null
        return runCatching {
            val json = JSONObject(String(decrypt(Base64.decode(encoded, Base64.NO_WRAP)), Charsets.UTF_8))
            val id = json.optString("app_id")
            val key = json.optString("app_key")
            if (id.isBlank() || key.isBlank()) null else AdzunaCredentials(id, key)
        }.getOrNull()
    }

    fun clear() {
        prefs.edit().remove("credentials").commit()
    }

    private fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        return byteArrayOf(cipher.iv.size.toByte()) + cipher.iv + cipher.doFinal(plain)
    }

    private fun decrypt(data: ByteArray): ByteArray {
        require(data.size > 13) { "Stored Adzuna credentials are corrupt." }
        val ivSize = data[0].toInt() and 0xff
        require(ivSize in 12..16 && data.size > 1 + ivSize) { "Stored Adzuna credentials are corrupt." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, data.copyOfRange(1, 1 + ivSize)))
        return cipher.doFinal(data.copyOfRange(1 + ivSize, data.size))
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}
