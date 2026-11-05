package com.hamari.mehenat.jobhunter.ai

/**
 * Thin JNI boundary for the locally stored GGUF model.
 * Call loadModel/generate from Dispatchers.IO; never run inference on the UI thread.
 */
object NativeLlama {
    init {
        System.loadLibrary("hamari_llama")
    }

    external fun loadModel(modelPath: String, contextSize: Int = 2048, threads: Int = 4): Boolean
    external fun generate(prompt: String, maxTokens: Int = 256): String
    external fun unload()
}
