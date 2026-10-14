package com.nova.assistant

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.time.ZoneId
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Part 6A: the audio recorder. It does NOT open a second microphone. NovaService's one mic loop (the same AudioRecord that
 * listens for the wake word) hands every 100 ms chunk to [feed]; a worker thread turns the chunks into AAC and writes .m4a files,
 * a new file every 30 minutes (RecordRules.SEGMENT_MS). Because there is only one microphone user, nothing fights over the mic,
 * the wake word keeps working ("nova recording band karo"), and it keeps running with the screen off.
 *
 * Honest limits (also told to the owner): speech quality (16 kHz mono, 32 kbps), not music quality; NOVA's own spoken replies and
 * beeps are recorded too (they come out of the speaker); if the app or phone dies in the middle, only the current 30-minute part can
 * be damaged; the recording needs NOVA to be ACTIVE; on some phones the maker's battery manager can still kill the app.
 * Nothing here ever uploads audio. The foreground notification (with STOP) is shown by NovaService, never hidden.
 */
object RecordSession {
    class Saved(val files: Int, val inMedia: Int, val path: String, val ms: Long, val dropped: Int)

    private const val RATE = RecordRules.RATE
    private const val QUEUE_CHUNKS = 1200   // 120 s of audio buffered if the encoder is slow; beyond that chunks are counted as dropped

    @Volatile var active = false
        private set
    @Volatile private var startedAt = 0L
    @Volatile private var queue: LinkedBlockingQueue<ByteArray>? = null
    @Volatile private var writer: Writer? = null
    @Volatile private var dropped = 0
    private val gate = Any()

    fun elapsedMs(): Long = if (active) System.currentTimeMillis() - startedAt else 0L

    /** Folder where the parts are written while recording (the app's own Music folder, falling back to internal files). */
    fun dir(ctx: Context): File {
        val base = try { ctx.getExternalFilesDir(Environment.DIRECTORY_MUSIC) } catch (e: Exception) { null }
        return File(base ?: ctx.filesDir, "rec")
    }

    fun freeBytes(ctx: Context): Long = try {
        val d = dir(ctx)
        d.mkdirs()
        d.usableSpace
    } catch (e: Exception) { 0L }

    /** null = started, otherwise a short reason ("already" / "folder"). It only starts a thread, so any thread may call it. */
    fun start(ctx: Context): String? {
        return synchronized(gate) {
            if (active) return@synchronized "already"
            val d = dir(ctx)
            var folderOk = false
            try {
                d.mkdirs()
                folderOk = d.isDirectory && d.canWrite()
            } catch (e: Exception) { folderOk = false }
            if (!folderOk) return@synchronized "folder"
            val q = LinkedBlockingQueue<ByteArray>(QUEUE_CHUNKS)
            val t0 = System.currentTimeMillis()
            val w = Writer(q, d, t0)
            queue = q
            writer = w
            dropped = 0
            startedAt = t0
            active = true
            w.start()
            null
        }
    }

    /** Called from the mic loop thread for every chunk. Cheap: copies the samples and queues them. Never blocks. */
    fun feed(buf: ShortArray, n: Int) {
        if (!active) return
        val q = queue ?: return
        val b = ByteArray(n * 2)
        var k = 0
        for (i in 0 until n) {
            val v = buf[i].toInt()
            b[k++] = (v and 0xff).toByte()
            b[k++] = ((v shr 8) and 0xff).toByte()
        }
        if (!q.offer(b)) dropped++
    }

    /** "error" when the writer thread died or failed, null when all is well. */
    fun problem(): String? {
        val w = writer ?: return null
        if (!active) return null
        if (w.err != null || !w.isAlive) return "error"
        return null
    }

    fun lastError(): String = writer?.err ?: ""

    /**
     * Ends the recording, waits for the files to be finished and (Android 10+) copies them to Music/NOVA.
     * Blocking (a few seconds at most): call it from a worker thread, never from the main thread.
     * null = nothing was recording.
     */
    fun stop(ctx: Context): Saved? {
        var w: Writer? = null
        var q: LinkedBlockingQueue<ByteArray>? = null
        val wasActive = synchronized(gate) {
            val was = active
            if (was) {
                active = false
                w = writer
                q = queue
            }
            was
        }
        if (!wasActive) return null
        val wr = w ?: return null
        val ms = System.currentTimeMillis() - startedAt
        wr.stopFlag = true
        try { wr.join(15000) } catch (e: InterruptedException) { ErrLog.add("RecordSession", e.toString()) }
        writer = null
        queue = null
        try { q?.clear() } catch (e: Exception) { ErrLog.add("RecordSession", e.toString()) }
        val files = ArrayList<File>(wr.finished)
        val drop = dropped
        val path = dir(ctx).absolutePath
        if (files.isEmpty()) return Saved(0, 0, path, ms, drop)
        var inMedia = 0
        val app = ctx.applicationContext
        if (Build.VERSION.SDK_INT >= 29) {
            for (f in files) {
                if (exportToMusic(app, f)) {
                    inMedia++
                    try { f.delete() } catch (e: Exception) { }
                }
            }
        }
        return Saved(files.size, inMedia, path, ms, drop)
    }

    /** Android 10+: copy the finished file into Music/NOVA so the owner finds it in any Files / Music app. */
    private fun exportToMusic(ctx: Context, f: File): Boolean {
        return try {
            val cv = ContentValues()
            cv.put(MediaStore.Audio.Media.DISPLAY_NAME, f.name)
            cv.put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            cv.put(MediaStore.Audio.Media.RELATIVE_PATH, RecordRules.REL_PATH)
            cv.put(MediaStore.Audio.Media.IS_PENDING, 1)
            val res = ctx.contentResolver
            val uri = res.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cv) ?: return false
            var ok = false
            try {
                res.openOutputStream(uri)?.use { out ->
                    f.inputStream().use { inp -> inp.copyTo(out) }
                    ok = true
                }
            } catch (e: Exception) { ok = false }
            if (ok) {
                val done = ContentValues()
                done.put(MediaStore.Audio.Media.IS_PENDING, 0)
                res.update(uri, done, null, null)
            } else {
                try { res.delete(uri, null, null) } catch (e: Exception) { }
            }
            ok
        } catch (e: Exception) { false }
    }

    // ------------------------------------------------------------------ writer thread

    private class Writer(val q: LinkedBlockingQueue<ByteArray>, val dir: File, val t0: Long) : Thread("nova-rec") {
        @Volatile var stopFlag = false
        @Volatile var err: String? = null
        val finished: MutableList<File> = java.util.Collections.synchronizedList(ArrayList<File>())

        override fun run() {
            var seg: Segment? = null
            var part = 0
            try {
                while (true) {
                    val chunk = q.poll(200, TimeUnit.MILLISECONDS)
                    if (chunk == null) {
                        if (stopFlag) break
                        continue
                    }
                    if (seg == null) {
                        part++
                        seg = Segment(File(dir, RecordRules.segName(t0, ZoneId.systemDefault(), part)))
                    }
                    seg.add(chunk)
                    if (seg.ms() >= RecordRules.SEGMENT_MS) {
                        if (seg.close()) finished.add(seg.file)
                        seg = null
                    }
                }
            } catch (t: Throwable) {
                err = t.javaClass.simpleName + (t.message?.let { ": " + it.take(80) } ?: "")
            } finally {
                try {
                    if (seg != null && seg.close()) finished.add(seg.file)
                } catch (t: Throwable) { ErrLog.add("RecordSession", t.toString()) }
            }
        }
    }

    /** One AAC encoder + one MP4 muxer = one .m4a file. */
    private class Segment(val file: File) {
        private val codec: MediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        private val muxer: MediaMuxer
        private val info = MediaCodec.BufferInfo()
        private var track = -1
        private var muxStarted = false
        private var samples = 0L
        private var wrote = 0

        init {
            val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, RATE, 1)
            fmt.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            fmt.setInteger(MediaFormat.KEY_BIT_RATE, RecordRules.BITRATE)
            fmt.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            try {
                codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                codec.start()
                muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            } catch (t: Throwable) {
                try { codec.release() } catch (e: Throwable) { }
                throw t
            }
        }

        fun ms(): Long = samples * 1000L / RATE

        fun add(pcm: ByteArray) {
            var off = 0
            var stuck = 0
            while (off < pcm.size) {
                val idx = codec.dequeueInputBuffer(20_000)
                if (idx < 0) {
                    drain(false)
                    if (++stuck > 100) throw IllegalStateException("encoder stuck")
                    continue
                }
                stuck = 0
                val ib = codec.getInputBuffer(idx) ?: throw IllegalStateException("no input buffer")
                ib.clear()
                var len = minOf(ib.remaining(), pcm.size - off)
                len -= len % 2
                if (len <= 0) throw IllegalStateException("input buffer too small")
                ib.put(pcm, off, len)
                codec.queueInputBuffer(idx, 0, len, RecordRules.ptsUs(samples), 0)
                samples += (len / 2).toLong()
                off += len
                drain(false)
            }
        }

        private fun drain(eos: Boolean) {
            var tries = 0
            while (true) {
                val oi = codec.dequeueOutputBuffer(info, if (eos) 10_000L else 0L)
                if (oi == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (!eos || ++tries > 200) return
                    continue
                }
                if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (!muxStarted) {
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxStarted = true
                    }
                    continue
                }
                if (oi < 0) continue
                val ob = codec.getOutputBuffer(oi)
                if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) info.size = 0
                if (info.size > 0 && muxStarted && ob != null) {
                    ob.position(info.offset)
                    ob.limit(info.offset + info.size)
                    muxer.writeSampleData(track, ob, info)
                    wrote++
                }
                codec.releaseOutputBuffer(oi, false)
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return
            }
        }

        /** true = the file has audio in it and was finished properly. An empty or failed file is deleted. */
        fun close(): Boolean {
            var ok = false
            try {
                val idx = codec.dequeueInputBuffer(100_000)
                if (idx >= 0) codec.queueInputBuffer(idx, 0, 0, RecordRules.ptsUs(samples), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                drain(true)
            } catch (t: Throwable) { ErrLog.add("RecordSession", t.toString()) }
            try { codec.stop() } catch (t: Throwable) { }
            try { codec.release() } catch (t: Throwable) { }
            try {
                if (muxStarted) {
                    muxer.stop()
                    ok = wrote > 0
                }
            } catch (t: Throwable) { ok = false }
            try { muxer.release() } catch (t: Throwable) { }
            if (!ok) { try { file.delete() } catch (t: Throwable) { } }
            return ok
        }
    }
}
