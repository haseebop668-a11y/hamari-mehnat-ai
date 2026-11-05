package com.hamari.mehenat.jobhunter.data

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class VaultDocument(val id: String, val originalName: String, val mimeType: String, val sizeBytes: Long)

class EncryptedDocumentVault(context: Context) {
    private val directory = File(context.filesDir, "private_vault").apply { mkdirs() }
    private val resolver = context.contentResolver
    private val alias = "hamari_mehnat_vault_key"

    fun import(uri: Uri, displayName: String, mimeType: String): VaultDocument {
        require(displayName.isNotBlank()) { "Document name is required" }
        val raw = resolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_DOCUMENT_BYTES) { "Document exceeds 25 MB limit" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: error("Cannot open selected document")
        require(raw.isNotEmpty()) { "Selected document is empty" }
        val id = UUID.randomUUID().toString()
        val metadata = "$displayName\n$mimeType\n".toByteArray(Charsets.UTF_8)
        val payload = metadata + raw
        val encrypted = encrypt(payload)
        File(directory, "$id.vault").writeBytes(encrypted)
        return VaultDocument(id, displayName, mimeType, raw.size.toLong())
    }

    fun read(documentId: String): ByteArray {
        require(documentId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid document identifier" }
        val file = File(directory, "$documentId.vault")
        require(file.isFile && file.length() <= MAX_DOCUMENT_BYTES + 4096) { "Document not found or invalid size" }
        val decrypted = decrypt(file.readBytes())
        val separator = decrypted.indexOf('\n'.code.toByte())
        require(separator >= 0) { "Vault metadata is corrupt" }
        val second = decrypted.indexOf('\n'.code.toByte(), separator + 1)
        require(second >= 0) { "Vault metadata is corrupt" }
        return decrypted.copyOfRange(second + 1, decrypted.size)
    }

    fun listIds(): List<String> = directory.listFiles()
        ?.filter { it.isFile && it.extension == "vault" }
        ?.map { it.nameWithoutExtension } ?: emptyList()

    fun delete(documentId: String): Boolean {
        require(documentId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid document identifier" }
        return File(directory, "$documentId.vault").delete()
    }

    private fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        return byteArrayOf(iv.size.toByte()) + iv + cipher.doFinal(plain)
    }

    private fun decrypt(encrypted: ByteArray): ByteArray {
        require(encrypted.size > 13) { "Encrypted document is truncated" }
        val ivSize = encrypted[0].toInt() and 0xff
        require(ivSize in 12..16 && encrypted.size > 1 + ivSize) { "Invalid vault header" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, encrypted.copyOfRange(1, 1 + ivSize)))
        return cipher.doFinal(encrypted.copyOfRange(1 + ivSize, encrypted.size))
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

    companion object { private const val MAX_DOCUMENT_BYTES = 25 * 1024 * 1024 }
}
