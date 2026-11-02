// v7.158 sandbox compile harness: minimal sherpa-onnx stubs (the real AAR comes from tools/fetch_sherpa.sh in the
// Gradle build; the sandbox has none). The deep voice never speaks in unit tests; this only completes the compile.
package com.k2fsa.sherpa.onnx

class OfflineTtsVitsModelConfig(
    val model: String = "",
    val lexicon: String = "",
    val tokens: String = "",
    val dataDir: String = "",
)

class OfflineTtsModelConfig(
    val vits: OfflineTtsVitsModelConfig = OfflineTtsVitsModelConfig(),
    val numThreads: Int = 1,
    val debug: Boolean = false,
    val provider: String = "cpu",
)

class OfflineTtsConfig(
    val model: OfflineTtsModelConfig = OfflineTtsModelConfig(),
    val ruleFsts: String = "",
    val ruleFars: String = "",
    val maxNumSentences: Int = 1,
)

class OfflineTts(assetManager: Any?, config: OfflineTtsConfig) {
    fun sampleRate(): Int = 0
    fun generateWithCallback(text: String, sid: Int, speed: Float, callback: (FloatArray) -> Int): Int = 0
    fun release() { }
}
