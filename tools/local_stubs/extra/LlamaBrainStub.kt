package com.nova.assistant
import android.content.Context
// Harness-only replacement: the real LlamaBrain.kt needs llamacpp-kotlin + kotlinx-coroutines jars, which are not reachable here.
class LlamaBrain(@Suppress("unused") private val context: Context, @Suppress("unused") private val modelPath: String) : LocalBrain {
    override val ready: Boolean get() = false
    override fun complete(prompt: String): String? = null
}
