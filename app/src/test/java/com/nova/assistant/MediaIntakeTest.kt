package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v7.51 Part 11-A. Expected values worked out by hand (Kotlin was NOT compiled when this was written unless the status file says otherwise). */
class MediaIntakeTest {
    private val MB = 1024L * 1024L

    @Test fun kindFromMimeThenExtension() {
        assertEquals(MediaIntake.Kind.IMAGE, MediaIntake.classify("image/jpeg", null))
        assertEquals(MediaIntake.Kind.VIDEO, MediaIntake.classify("video/mp4; codecs=avc1", "a"))
        assertEquals(MediaIntake.Kind.VIDEO, MediaIntake.classify(null, "clip.MOV"))
        assertEquals(MediaIntake.Kind.IMAGE, MediaIntake.classify("application/octet-stream", "pic.heic"))
        assertEquals(MediaIntake.Kind.UNKNOWN, MediaIntake.classify("text/plain", "note.txt"))
        assertEquals(MediaIntake.Kind.UNKNOWN, MediaIntake.classify(null, null))
    }

    @Test fun onlyContentUrisAreSafe() {
        assertTrue(MediaIntake.isSafeUri("content://media/external/images/1"))
        assertFalse(MediaIntake.isSafeUri("file:///data/data/com.nova.assistant/x"))
        assertFalse(MediaIntake.isSafeUri("content://a/../b"))
        assertFalse(MediaIntake.isSafeUri(null))
        assertFalse(MediaIntake.isSafeUri("content:/"))
    }

    @Test fun sizeLimits() {
        assertTrue(MediaIntake.check(MediaIntake.Kind.IMAGE, 25 * MB).ok)
        val big = MediaIntake.check(MediaIntake.Kind.IMAGE, 25 * MB + 1)
        assertFalse(big.ok)
        assertTrue(big.reason.startsWith("Photo bahut badi hai"))
        assertTrue(MediaIntake.check(MediaIntake.Kind.VIDEO, 300 * MB).ok)
        assertFalse(MediaIntake.check(MediaIntake.Kind.VIDEO, 300 * MB + 1).ok)
        assertTrue(MediaIntake.check(MediaIntake.Kind.VIDEO, -1).ok)
        assertFalse(MediaIntake.check(MediaIntake.Kind.VIDEO, 0).ok)
        assertTrue(MediaIntake.check(MediaIntake.Kind.VIDEO, 0).reason.contains("khaali"))
        assertFalse(MediaIntake.check(MediaIntake.Kind.UNKNOWN, 10).ok)
        assertFalse(MediaIntake.check(MediaIntake.Kind.LINK, 10).ok)
        assertTrue(MediaIntake.streamOk(MediaIntake.Kind.IMAGE, 25 * MB))
        assertFalse(MediaIntake.streamOk(MediaIntake.Kind.IMAGE, 25 * MB + 1))
        assertFalse(MediaIntake.streamOk(MediaIntake.Kind.VIDEO, 300 * MB + 1))
    }

    @Test fun humanSizeText() {
        assertEquals("0 B", MediaIntake.humanSize(0))
        assertEquals("1023 B", MediaIntake.humanSize(1023))
        assertEquals("1.0 KB", MediaIntake.humanSize(1024))
        assertEquals("1.5 KB", MediaIntake.humanSize(1536))
        assertEquals("25.0 MB", MediaIntake.humanSize(25 * MB))
        assertEquals("300.0 MB", MediaIntake.humanSize(300 * MB))
        assertEquals("1.0 GB", MediaIntake.humanSize(1024 * MB))
        assertEquals("?", MediaIntake.humanSize(-1))
    }

    @Test fun namesAreCleanAndNeverEmpty() {
        assertEquals("IMG_001.jpg", MediaIntake.cleanName("/storage/emulated/0/DCIM/IMG_001.jpg"))
        assertEquals("c.mp4", MediaIntake.cleanName("a\\b\\c.mp4"))
        assertEquals("hidden.jpg", MediaIntake.cleanName("..hidden.jpg"))
        assertEquals("my video.mp4", MediaIntake.cleanName("my   video.mp4"))
        assertEquals("badname.png", MediaIntake.cleanName("bad<>name?.png"))
        assertEquals("photo_2.jpg", MediaIntake.cleanName(null, MediaIntake.Kind.IMAGE, 2))
        assertEquals("video_1.mp4", MediaIntake.cleanName("", MediaIntake.Kind.VIDEO, 1))
        assertEquals("link_3", MediaIntake.cleanName("  ", MediaIntake.Kind.LINK, 3))
        assertEquals("media_1", MediaIntake.cleanName("...", MediaIntake.Kind.UNKNOWN, 1))
        val long = MediaIntake.cleanName("a".repeat(70) + ".mp4")
        assertEquals(60, long.length)
        assertTrue(long.endsWith(".mp4"))
    }

    @Test fun chatLabelHasNameAndSizeOnly() {
        assertEquals("x.jpg (1.5 KB)", MediaIntake.chatLabel("/sdcard/Download/x.jpg", 1536))
    }

    @Test fun linksAreHttpsOnlyAndKeptExactly() {
        assertEquals(listOf("https://www.instagram.com/reel/ABC/?igsh=xyz123"),
            MediaIntake.extractUrls("dekho https://www.instagram.com/reel/ABC/?igsh=xyz123 kya hai"))
        assertEquals(listOf("https://youtu.be/abc123"), MediaIntake.extractUrls("(https://youtu.be/abc123)."))
        assertEquals(listOf("https://example.com/a_(b)"), MediaIntake.extractUrls("https://example.com/a_(b)"))
        assertEquals(listOf("https://example.com/watch?v=1", "https://example.org/2"),
            MediaIntake.extractUrls("https://example.com/watch?v=1, https://example.org/2"))
        assertEquals(listOf("https://example.com:8443/path"), MediaIntake.extractUrls("https://example.com:8443/path"))
        assertTrue(MediaIntake.extractUrls("http://example.com/x").isEmpty())
        assertTrue(MediaIntake.extractUrls("https://user:pw@example.com/x").isEmpty())
        assertTrue(MediaIntake.extractUrls("https://localhost/x").isEmpty())
        assertTrue(MediaIntake.extractUrls("https://192.168.1.1/x").isEmpty())
        assertTrue(MediaIntake.extractUrls("https://intranet.local/x").isEmpty())
        assertTrue(MediaIntake.extractUrls("https://singleword/x").isEmpty())
        assertTrue(MediaIntake.extractUrls(null).isEmpty())
        assertTrue(MediaIntake.extractUrls("koi link nahi").isEmpty())
    }

    @Test fun duplicateLinksAndLinkCap() {
        assertEquals(1, MediaIntake.extractUrls("https://a-b.com/x https://A-B.com/x").size)
        assertEquals(3, MediaIntake.extractUrls("https://a1.com https://a2.com https://a3.com https://a4.com").size)
    }

    @Test fun videoLinkWording() {
        assertTrue(MediaIntake.isVideoLink("https://www.instagram.com/reel/x"))
        assertTrue(MediaIntake.isVideoLink("https://youtu.be/x"))
        assertFalse(MediaIntake.isVideoLink("https://example.com/video"))
        assertFalse(MediaIntake.isVideoLink("https://notinstagram.com/x"))
        assertFalse(MediaIntake.isVideoLink("http://instagram.com/x"))
        assertTrue(MediaIntake.linkNote("https://www.instagram.com/reel/x").contains("Video file nahi mili"))
        assertTrue(MediaIntake.linkNote("https://example.com/x").contains("sirf link hai"))
    }

    @Test fun planDropsDuplicatesAndRejectsBadOnes() {
        val p = MediaIntake.plan(listOf(
            MediaIntake.Candidate("content://m/1", "IMG_1.jpg", "image/jpeg", 1000),
            MediaIntake.Candidate("content://m/1", "IMG_1.jpg", "image/jpeg", 1000),
            MediaIntake.Candidate("content://m/2", "IMG_1.jpg", "image/jpeg", 1000),
            MediaIntake.Candidate("file:///data/x", "evil.jpg", "image/jpeg", 10),
            MediaIntake.Candidate("content://m/3", "big.mp4", "video/mp4", 400 * MB),
            MediaIntake.Candidate("content://m/4", "notes.pdf", "application/pdf", 500)
        ))
        assertEquals(1, p.accepted.size)
        assertEquals("IMG_1.jpg", p.accepted[0].name)
        assertEquals(MediaIntake.Kind.IMAGE, p.accepted[0].kind)
        assertEquals(3, p.rejected.size)
        assertEquals("Video bahut badi hai (400.0 MB). Limit 300.0 MB hai.", p.rejected[1].reason)
        assertTrue(p.rejected[2].reason.contains("nahi lagti"))
    }

    @Test fun planKeepsUnknownSizeAndRejectsEmpty() {
        val p = MediaIntake.plan(listOf(
            MediaIntake.Candidate("content://m/9", "a.png", "image/png", -1),
            MediaIntake.Candidate("content://m/10", "a.png", "image/png", -1),
            MediaIntake.Candidate("content://m/11", "e.png", "image/png", 0)
        ))
        assertEquals(2, p.accepted.size)
        assertEquals(1, p.rejected.size)
        assertTrue(p.rejected[0].reason.contains("khaali"))
    }

    @Test fun planCapsFilesPerShare() {
        val c = (1..8).map { MediaIntake.Candidate("content://m/$it", "f$it.jpg", "image/jpeg", 100L + it) }
        val p = MediaIntake.plan(c)
        assertEquals(6, p.accepted.size)
        assertEquals(2, p.rejected.size)
        assertTrue(p.rejected[0].reason.contains("sirf 6 file"))
        val q = MediaIntake.plan(c.take(5), alreadyHave = 2)
        assertEquals(4, q.accepted.size)
        assertEquals(1, q.rejected.size)
    }

    @Test fun planTakesLinksFromSharedText() {
        val p = MediaIntake.plan(emptyList(), "https://youtu.be/x")
        assertEquals(0, p.accepted.size)
        assertEquals(listOf("https://youtu.be/x"), p.links)
    }

    @Test fun twentyFourHourRule() {
        val t = MediaIntake.TTL_MS
        assertEquals(86_400_000L, t)
        assertFalse(MediaIntake.isExpired(1000L, 1000L + t - 1))
        assertTrue(MediaIntake.isExpired(1000L, 1000L + t))
        assertTrue(MediaIntake.isExpired(0L, 5L))
        assertTrue(MediaIntake.isExpired(5000L, 1000L))
    }

    @Test fun statusLines() {
        assertTrue(MediaIntake.statusLine(0, 0).startsWith("Abhi koi media nahi mila"))
        assertEquals("2 file mili hai.", MediaIntake.statusLine(2, 0))
        assertEquals("1 link mila hai.", MediaIntake.statusLine(0, 1))
        assertEquals("2 file aur 1 link mile hain.", MediaIntake.statusLine(2, 1))
    }

    @Test fun storeHelpers() {
        val t = MediaIntake.TTL_MS
        val a = MediaIntake.Entry("m1", MediaIntake.Kind.IMAGE, "a.jpg", 10, 1000L, "m1.jpg", "")
        val b = MediaIntake.Entry("m2", MediaIntake.Kind.VIDEO, "b.mp4", 20, 5000L, "m2.mp4", "")
        val l = MediaIntake.Entry("m3", MediaIntake.Kind.LINK, "link_1", 0, 1000L, "", "https://youtu.be/x")
        assertEquals(listOf("m1", "m3"), MediaIntake.expiredIds(listOf(a, b, l), 1000L + t))
        assertEquals(emptyList<String>(), MediaIntake.expiredIds(listOf(a, b, l), 1000L + t - 1))
        assertEquals("jpg", MediaIntake.safeExt("x.JPG", MediaIntake.Kind.IMAGE))
        assertEquals("mp4", MediaIntake.safeExt("noext", MediaIntake.Kind.VIDEO))
        assertEquals("jpg", MediaIntake.safeExt("evil.j/../p", MediaIntake.Kind.IMAGE))
        assertEquals("bin", MediaIntake.safeExt("a.toolongext", MediaIntake.Kind.UNKNOWN))
        assertEquals("m1.jpg", MediaIntake.cacheFileName("m1", "/etc/passwd.jpg", MediaIntake.Kind.IMAGE))
        assertTrue(MediaIntake.isValidId("m170_1"))
        assertFalse(MediaIntake.isValidId("../x"))
        assertFalse(MediaIntake.isValidId(""))
        assertFalse(MediaIntake.isValidId(null))
        assertEquals(4, MediaIntake.room(listOf(a, b, l)))
        assertEquals(0, MediaIntake.room((1..7).map { MediaIntake.Entry("i$it", MediaIntake.Kind.IMAGE, "x", 1, 1L, "f", "") }))
    }
}
