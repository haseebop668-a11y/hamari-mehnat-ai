package com.hamari.mehenat.jobhunter.data

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class LocalModelManager(context: Context) {
    private val modelDirectory = File(context.filesDir, "models").apply { mkdirs() }
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .callTimeout(45, TimeUnit.MINUTES)
        .followRedirects(true)
        .build()

    val modelFile: File
        get() = File(modelDirectory, MODEL_FILENAME)

    fun isDownloaded(): Boolean = modelFile.isFile && modelFile.length() > 0L

    fun verifyInstalled(): Boolean {
        if (!isDownloaded()) return false
        return sha256(modelFile) == EXPECTED_SHA256
    }

    fun downloadModel(onProgress: (Int) -> Unit = {}): File {
        require(modelDirectory.usableSpace >= MIN_FREE_SPACE_BYTES) {
            "At least 900 MB free internal storage is required for the model download."
        }
        val temporary = File(modelDirectory, "$MODEL_FILENAME.part")
        temporary.delete()
        val request = Request.Builder()
            .url(DOWNLOAD_URL)
            .header("User-Agent", "HamariMehnatPersonalAndroid/1.0")
            .header("Accept", "application/octet-stream")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Model download failed: HTTP ${response.code}" }
            val body = response.body ?: error("Model download returned an empty response.")
            val total = body.contentLength()
            val digest = MessageDigest.getInstance("SHA-256")
            var downloaded = 0L
            body.byteStream().use { input ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        downloaded += count
                        if (total > 0L) onProgress(((downloaded * 100L) / total).toInt().coerceIn(0, 100))
                    }
                    output.fd.sync()
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            check(actual.equals(EXPECTED_SHA256, ignoreCase = true)) {
                "Model checksum verification failed. The partial download has been rejected."
            }
        }

        if (modelFile.exists()) check(modelFile.delete()) { "Could not replace the previous model file." }
        check(temporary.renameTo(modelFile)) { "Could not finalize the verified model file." }
        onProgress(100)
        return modelFile
    }

    fun deleteModel(): Boolean = !modelFile.exists() || modelFile.delete()

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val MODEL_FILENAME = "LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf"
        const val EXPECTED_SHA256 = "bb741ebb106d543e9de114b843a3d3d73d51c74b5801e69da2abde821a0cb3e1"
        const val DOWNLOAD_URL = "https://huggingface.co/LiquidAI/LFM2.5-1.2B-Instruct-GGUF/resolve/430bea1/LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf?download=true"
        private const val MIN_FREE_SPACE_BYTES = 900L * 1024L * 1024L
    }
}
