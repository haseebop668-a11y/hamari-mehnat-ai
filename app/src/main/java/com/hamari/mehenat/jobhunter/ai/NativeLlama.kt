package com.hamari.mehenat.jobhunter.ai

import java.io.File

/**
 * Thin synchronized JNI boundary for the locally stored GGUF model.
 * The model stays loaded for the process lifetime and is reloaded only when
 * its private file identity or inference configuration changes.
 */
object NativeLlama {
    init {
        System.loadLibrary("hamari_llama")
    }

    private val runtimeLock = Any()
    @Volatile private var loadedFingerprint: String? = null

    fun loadModel(modelPath: String, contextSize: Int = 2048, threads: Int = 4): Boolean =
        synchronized(runtimeLock) {
            val file = File(modelPath)
            if (!file.isFile || file.length() <= 0L) {
                nativeUnload()
                loadedFingerprint = null
                return@synchronized false
            }

            val fingerprint = file.canonicalPath + ":" + file.length() + ":" +
                file.lastModified() + ":" + contextSize + ":" + threads
            if (loadedFingerprint == fingerprint) return@synchronized true

            nativeUnload()
            loadedFingerprint = null
            val loaded = nativeLoadModel(modelPath, contextSize, threads)
            if (loaded) {
                loadedFingerprint = fingerprint
            } else {
                nativeUnload()
            }
            loaded
        }

    fun generate(prompt: String, maxTokens: Int = 256): String =
        synchronized(runtimeLock) {
            nativeGenerate(prompt, maxTokens)
        }

    fun unload() {
        synchronized(runtimeLock) {
            nativeUnload()
            loadedFingerprint = null
        }
    }

    private external fun nativeLoadModel(modelPath: String, contextSize: Int, threads: Int): Boolean
    private external fun nativeGenerate(prompt: String, maxTokens: Int): String
    private external fun nativeUnload()
}
