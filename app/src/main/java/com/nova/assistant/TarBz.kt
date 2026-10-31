package com.nova.assistant

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.IOException

/**
 * v7.154 JARVIS UPGRADE: safe .tar.bz2 extraction for the deep-voice model archive (the Piper voice comes as
 * a tar.bz2 from the sherpa-onnx release page). Pure JVM (no Android classes) so it is unit tested directly.
 *
 * Safety rules (a bad archive can never escape or fill the disk):
 *  - every entry name must stay INSIDE [into] (no "..", no absolute path, no drive letters, no symlinks),
 *  - at most [MAX_ENTRIES] entries and [MAX_TOTAL_BYTES] bytes written in total,
 *  - the whole stream is closed on any error and the caller deletes the partial directory.
 */
object TarBz {
    const val MAX_ENTRIES = 4000
    const val MAX_TOTAL_BYTES = 400L * 1024 * 1024   // 400 MB: the voice model extracts to ~120 MB

    sealed class Outcome {
        class Ok(val files: Int, val bytes: Long) : Outcome()
        class Fail(val reason: String) : Outcome()
    }

    /** true when the entry is a regular file or a directory we can create. Symlinks/devices are refused. */
    private fun allowedType(e: TarArchiveEntry): Boolean =
        e.isDirectory || e.isFile || e.isLink   // hard links inside the archive are treated as files below

    /** Resolved target must sit inside [into]. */
    private fun safeTarget(into: File, name: String): File? {
        if (name.isBlank()) return null
        if (name.contains("..")) return null
        if (name.startsWith("/")) return null
        if (Regex("^[A-Za-z]:").containsMatchIn(name)) return null
        val f = File(into, name)
        val canonInto = into.canonicalPath
        val canonF = f.canonicalPath
        return if (canonF == canonInto || canonF.startsWith(canonInto + File.separator)) f else null
    }

    fun extract(archive: File, into: File, maxEntries: Int = MAX_ENTRIES, maxTotalBytes: Long = MAX_TOTAL_BYTES): Outcome {
        if (!archive.exists() || archive.length() < 64) return Outcome.Fail("archive missing")
        try {
            into.mkdirs()
            var files = 0
            var total = 0L
            TarArchiveInputStream(BZip2CompressorInputStream(archive.inputStream().buffered(1 shl 16))).use { tin ->
                while (true) {
                    val e: TarArchiveEntry = tin.nextTarEntry ?: break
                    if (!allowedType(e)) return Outcome.Fail("bad entry type: " + e.name)
                    if (files >= maxEntries) return Outcome.Fail("too many entries")
                    val target = safeTarget(into, e.name) ?: return Outcome.Fail("unsafe entry name: " + e.name)
                    if (e.isDirectory) {
                        target.mkdirs()
                        continue
                    }
                    val parent = target.parentFile
                    if (parent != null) parent.mkdirs()
                    val size = e.size
                    if (size < 0) return Outcome.Fail("bad entry size: " + e.name)
                    if (total + size > maxTotalBytes) return Outcome.Fail("archive too big")
                    target.outputStream().use { out ->
                        val buf = ByteArray(1 shl 16)
                        var left = size
                        while (left > 0) {
                            val n = tin.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                            if (n < 0) return Outcome.Fail("truncated entry: " + e.name)
                            out.write(buf, 0, n)
                            left -= n
                            total += n
                        }
                    }
                    files++
                }
            }
            if (files == 0) return Outcome.Fail("empty archive")
            return Outcome.Ok(files, total)
        } catch (e: IOException) {
            return Outcome.Fail(e.message ?: "io error")
        } catch (e: Throwable) {
            return Outcome.Fail(e.javaClass.simpleName + ": " + (e.message ?: ""))
        }
    }
}
