package com.hamari.mehenat.jobhunter.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class LocalProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("private_profile_v1", Context.MODE_PRIVATE)
    private val alias = "hamari_mehnat_profile_key"

    fun save(profileJson: String) {
        require(profileJson.length <= 100_000) { "Profile too large" }
        val parsed = JSONObject(profileJson)
        val encrypted = encrypt(parsed.toString().toByteArray(Charsets.UTF_8))
        prefs.edit().putString("profile", android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP)).apply()
    }

    fun load(): JSONObject? {
        val stored = prefs.getString("profile", null) ?: return null
        return try {
            JSONObject(String(decrypt(android.util.Base64.decode(stored, android.util.Base64.NO_WRAP)), Charsets.UTF_8))
        } catch (_: Exception) { null }
    }

    fun saveTargetRoles(roles: List<String>) {
        val profile = load() ?: JSONObject()
        profile.put("target_roles", JSONArray(roles.map { it.trim() }.filter { it.isNotBlank() }.distinct()))
        save(profile.toString())
    }

    private fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        return byteArrayOf(cipher.iv.size.toByte()) + cipher.iv + cipher.doFinal(plain)
    }

    private fun decrypt(data: ByteArray): ByteArray {
        require(data.size > 13)
        val ivSize = data[0].toInt() and 0xff
        require(ivSize in 12..16 && data.size > 1 + ivSize)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, data.copyOfRange(1, 1 + ivSize)))
        return cipher.doFinal(data.copyOfRange(1 + ivSize, data.size))
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }
}
