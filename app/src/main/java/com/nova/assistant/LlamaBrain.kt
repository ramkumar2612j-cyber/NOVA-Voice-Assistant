package com.nova.assistant

import android.content.Context
import org.nehuatl.llamacpp.LlamaHelper
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Real on-device GGUF inference adapter for the bundled LocalBrain slot.
 * Written against io.github.ljcamargo:llamacpp-kotlin 0.1.0 (LlamaHelper(scope), suspend load(path, ctx),
 * setCollector(): Flow<String>, blocking predict(prompt, partial)). NOT compiled on a phone yet.
 */
class LlamaBrain(
    @Suppress("unused") private val context: Context,
    private val modelPath: String
) : LocalBrain {
    private val loadLatch = CountDownLatch(1)
    // A failure inside the library's own coroutines must never crash the app.
    private val scope = CoroutineScope(
        Dispatchers.IO + SupervisorJob() + CoroutineExceptionHandler { _, _ -> loadLatch.countDown() }
    )
    private val helper = LlamaHelper(scope)
    @Volatile private var loaded = false
    @Volatile private var closed = false
    private var loadJob: Job? = null

    init {
        val file = File(modelPath)
        if (file.isFile && file.length() > 100L * 1024 * 1024) {
            loadJob = scope.launch {
                try {
                    helper.load(file.absolutePath, 2048)
                    loaded = true
                } catch (ignored: Throwable) {
                } finally {
                    loadLatch.countDown()
                }
            }
            loadLatch.await(45, TimeUnit.SECONDS)
        }
    }

    override val ready: Boolean
        get() = loaded && !closed

    override fun complete(prompt: String): String? {
        if (!ready) return null
        val out = StringBuilder()
        val done = CountDownLatch(1)
        val job = scope.launch {
            var collector: Job? = null
            try {
                val tokens = helper.setCollector()
                collector = launch { tokens.collect { t -> synchronized(out) { out.append(t) } } }
                delay(150)                      // let the collector subscribe before the first token is produced
                helper.predict(prompt, true)    // blocks until generation ends
                delay(200)                      // flush the last tokens
            } catch (ignored: Throwable) {
            } finally {
                collector?.cancel()
                try { helper.unsetCollector() } catch (ignored: Throwable) { ErrLog.add("LlamaBrain", ignored.toString()) }
                done.countDown()
            }
        }
        done.await(LocalChat.TIMEOUT_MS + 1000L, TimeUnit.MILLISECONDS)
        job.cancel()
        val text = synchronized(out) { out.toString() }
        return LocalChat.clean(text)
    }

    override fun close() {
        if (closed) return
        closed = true
        try { helper.abort() } catch (ignored: Throwable) { ErrLog.add("LlamaBrain", ignored.toString()) }
        try { helper.release() } catch (ignored: Throwable) { }
        scope.cancel()
    }
}
