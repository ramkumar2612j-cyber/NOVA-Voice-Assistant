package com.nova.assistant

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v7.05 I: PhotoPro pixel maths (pure, no android classes touched). */
class PhotoProTest {
    private fun px(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    private fun rr(p: Int) = (p shr 16) and 0xFF
    private fun gg(p: Int) = (p shr 8) and 0xFF
    private fun bb(p: Int) = p and 0xFF

    @Test fun cropSquareFromWide() { assertArrayEquals(intArrayOf(50, 0, 300, 300), PhotoPro.cropRect(400, 300, 1, 1)) }
    @Test fun cropWideFromTall() { val c = PhotoPro.cropRect(300, 600, 16, 9); assertEquals(300, c[2]); assertEquals(168, c[3]); assertEquals(216, c[1]) }
    @Test fun cropKeepsBadInput() { assertArrayEquals(intArrayOf(0, 0, 10, 10), PhotoPro.cropRect(10, 10, 0, 0)) }

    @Test fun autoLevelsStretchesFlatPhoto() {
        val a = IntArray(1000) { i -> val v = 100 + i * 40 / 1000; px(v, v, v) }
        PhotoPro.autoLevels(a)
        assertTrue(rr(a.min()) < 20 || rr(a.minOf { rr(it) }) < 20)
        assertTrue(a.maxOf { rr(it) } > 235)
    }

    @Test fun autoLevelsLeavesAlreadyWideRange() {
        val a = IntArray(1000) { i -> val v = i * 255 / 999; px(v, v, v) }
        val b = a.copyOf(); PhotoPro.autoLevels(b)
        assertTrue(Math.abs(rr(b[500]) - rr(a[500])) < 8)
    }

    @Test fun grayscaleMakesChannelsEqualAndKeepsAlpha() {
        val a = intArrayOf(px(200, 50, 10), px(10, 20, 240)); PhotoPro.grayscale(a)
        for (p in a) { assertEquals(rr(p), gg(p)); assertEquals(gg(p), bb(p)); assertEquals(0xFF, p ushr 24) }
    }

    @Test fun sepiaIsWarm() { val a = intArrayOf(px(120, 120, 120)); PhotoPro.sepia(a); assertTrue(rr(a[0]) > bb(a[0])) }

    @Test fun brightnessGammaBrightensDark() { val a = intArrayOf(px(40, 40, 40)); PhotoPro.brightness(a, 0.6f); assertTrue(rr(a[0]) > 60) }

    @Test fun saturationOneIsIdentity() { val a = intArrayOf(px(30, 90, 200)); PhotoPro.saturation(a, 1f); assertEquals(px(30, 90, 200), a[0]) }

    @Test fun skinToneChangesLessThanOtherColours() {
        val skin = intArrayOf(px(210, 150, 120)); val blue = intArrayOf(px(60, 90, 200))
        val s0 = rr(skin[0]) - bb(skin[0]); val b0 = bb(blue[0]) - rr(blue[0])
        PhotoPro.saturation(skin, 1.4f); PhotoPro.saturation(blue, 1.4f)
        val dSkin = (rr(skin[0]) - bb(skin[0])) - s0; val dBlue = (bb(blue[0]) - rr(blue[0])) - b0
        assertTrue(dSkin < dBlue)
    }

    @Test fun sharpenKeepsFlatImageFlat() {
        val a = IntArray(25) { px(90, 90, 90) }; PhotoPro.denoiseSharpen(a, 5, 5, 0.2f, 0.6f)
        for (p in a) assertEquals(90, rr(p))
    }

    @Test fun sharpenIncreasesEdgeContrast() {
        val w = 8; val h = 8
        val a = IntArray(w * h) { i -> if (i % w < 4) px(80, 80, 80) else px(170, 170, 170) }
        PhotoPro.denoiseSharpen(a, w, h, 0f, 1f)
        assertTrue(rr(a[3]) < 80 || rr(a[4]) > 170)
    }

    @Test fun everyPresetKeepsSizeAndAlpha() {
        for (pr in PhotoPro.Preset.values()) {
            val w = 6; val h = 5
            val a = IntArray(w * h) { i -> px((i * 9) % 256, (i * 17) % 256, (i * 31) % 256) }
            PhotoPro.apply(a, w, h, pr)
            assertEquals(w * h, a.size)
            for (p in a) assertEquals(0xFF, p ushr 24)
        }
    }

    @Test fun vignetteDarkensCornersNotCentre() {
        val w = 21; val h = 21; val a = IntArray(w * h) { px(200, 200, 200) }
        PhotoPro.vignette(a, w, h, 0.6f)
        assertEquals(200, rr(a[10 * w + 10])); assertTrue(rr(a[0]) < 150)
    }
}
