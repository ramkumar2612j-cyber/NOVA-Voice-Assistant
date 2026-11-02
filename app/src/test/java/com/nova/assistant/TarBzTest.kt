package com.nova.assistant

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * v7.154 JARVIS UPGRADE: tests for the safe .tar.bz2 extractor of the deep-voice model. A hostile or broken
 * archive must NEVER escape the target directory, and the limits must hold.
 */
class TarBzTest {
    @get:Rule val tmp = TemporaryFolder()

    /** Builds a real small tar.bz2 with the given entries. Entries: name -> content (null = directory). */
    private fun build(entries: List<Pair<String, String?>>): File {
        val f = tmp.newFile("t" + System.nanoTime() + ".tar.bz2")
        TarArchiveOutputStream(BZip2CompressorOutputStream(f.outputStream().buffered())).use { out ->
            for ((name, content) in entries) {
                val e = TarArchiveEntry(if (content == null && !name.endsWith("/")) name + "/" else name)
                if (content == null) {
                    out.putArchiveEntry(e)
                    out.closeArchiveEntry()
                } else {
                    val b = content.toByteArray(Charsets.UTF_8)
                    e.size = b.size.toLong()
                    out.putArchiveEntry(e)
                    out.write(b)
                    out.closeArchiveEntry()
                }
            }
            out.finish()
        }
        return f
    }

    @Test fun normalArchiveExtracts() {
        val dir = tmp.newFolder("out1")
        val a = build(listOf("voice/" to null, "voice/model.onnx" to "x".repeat(300), "voice/tokens.txt" to "a\nb\n", "voice/espeak-ng-data/" to null))
        val r = TarBz.extract(a, dir)
        assertTrue(r is TarBz.Outcome.Ok)
        assertEquals(2, (r as TarBz.Outcome.Ok).files)
        assertTrue(File(dir, "voice/model.onnx").length() == 300L)
        assertTrue(File(dir, "voice/tokens.txt").exists())
        assertTrue(File(dir, "voice/espeak-ng-data").isDirectory)
    }

    @Test fun pathTraversalIsRefused() {
        for (evil in listOf("../escape.txt", "voice/../../up.txt")) {
            val dir = tmp.newFolder()
            val a = build(listOf(evil to "bad"))
            assertTrue("must refuse " + evil, TarBz.extract(a, dir) is TarBz.Outcome.Fail)
        }
    }

    @Test fun symlinkEntriesAreRefused() {
        val f = tmp.newFile("s.tar.bz2")
        TarArchiveOutputStream(BZip2CompressorOutputStream(f.outputStream().buffered())).use { out ->
            val e = TarArchiveEntry("link")
            e.linkName = "/etc/passwd"
            e.size = 0
            out.putArchiveEntry(e)
            out.closeArchiveEntry()
            out.finish()
        }
        assertTrue(TarBz.extract(f, tmp.newFolder()) is TarBz.Outcome.Fail)
    }

    @Test fun entryAndSizeLimitsHold() {
        val dir = tmp.newFolder()
        // more entries than allowed
        val many = (0 until 5).map { "f$it" to "x" }
        val a = build(many)
        assertTrue(TarBz.extract(a, dir, maxEntries = 3) is TarBz.Outcome.Fail)
        // bigger than the byte limit
        val b = build(listOf("big" to "x".repeat(1000)))
        assertTrue(TarBz.extract(b, tmp.newFolder(), maxTotalBytes = 500) is TarBz.Outcome.Fail)
    }

    @Test fun missingAndEmptyArchivesFailCleanly() {
        val dir = tmp.newFolder()
        assertTrue(TarBz.extract(File(dir, "nope.tar.bz2"), dir) is TarBz.Outcome.Fail)
        val empty = tmp.newFile("e.tar.bz2")
        assertTrue(TarBz.extract(empty, dir) is TarBz.Outcome.Fail)
    }

    @Test fun truncatedArchiveFailsWithoutPartialEscape() {
        val a = build(listOf("v/x" to "x".repeat(10_000)))
        // keep only the first 200 bytes = a truncated bzip2 stream
        val cut = tmp.newFile("cut.tar.bz2")
        cut.writeBytes(a.readBytes().let { it.copyOfRange(0, it.size / 2) })
        val dir = tmp.newFolder()
        val r = TarBz.extract(cut, dir)
        assertTrue(r is TarBz.Outcome.Fail)
        assertFalse(File(dir, "v/x").exists())   // the caller deletes the directory; nothing complete was written
    }
}
