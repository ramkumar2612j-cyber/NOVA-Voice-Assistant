package com.nova.assistant

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * v7.05 I level 1: on-device professional photo edit. NO network, NO key, nothing leaves the phone.
 * The maths works on ARGB int arrays (pure Kotlin, JVM unit tested); only decode / encode touch android.graphics.
 * Presets: ENHANCE (auto levels, contrast, skin-safe saturation, light noise reduction, unsharp mask), BRIGHT (dark photo fix),
 * BW, SEPIA, CINEMATIC, WARM, COOL, VIVID, SOFT. Crop to a ratio. Never touches the original: callers keep their own copy.
 */
object PhotoPro {
    enum class Preset { ENHANCE, BRIGHT, BW, SEPIA, CINEMATIC, WARM, COOL, VIVID, SOFT }

    private fun clamp(v: Float): Int = if (v < 0f) 0 else if (v > 255f) 255 else (v + 0.5f).toInt()
    private fun r(p: Int) = (p shr 16) and 0xFF
    private fun g(p: Int) = (p shr 8) and 0xFF
    private fun b(p: Int) = p and 0xFF
    private fun rgb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b
    private fun lum(p: Int): Float = 0.299f * r(p) + 0.587f * g(p) + 0.114f * b(p)

    /** Crop window (x, y, w, h) of the biggest centred area with the ratio rw:rh. */
    fun cropRect(w: Int, h: Int, rw: Int, rh: Int): IntArray {
        if (w <= 0 || h <= 0 || rw <= 0 || rh <= 0) return intArrayOf(0, 0, w, h)
        var cw = w
        var ch = (w.toLong() * rh / rw).toInt()
        if (ch > h) { ch = h; cw = (h.toLong() * rw / rh).toInt() }
        cw = cw.coerceIn(1, w); ch = ch.coerceIn(1, h)
        return intArrayOf((w - cw) / 2, (h - ch) / 2, cw, ch)
    }

    /** Stretch each channel's 1%..99% range to 0..255 (keeps colour balance by using ONE common range from luminance). */
    fun autoLevels(px: IntArray) {
        if (px.isEmpty()) return
        val hist = IntArray(256)
        for (p in px) hist[lum(p).toInt().coerceIn(0, 255)]++
        val cut = (px.size * 0.01f).toInt()
        var lo = 0; var acc = 0
        while (lo < 255 && acc + hist[lo] <= cut) { acc += hist[lo]; lo++ }
        var hi = 255; acc = 0
        while (hi > 0 && acc + hist[hi] <= cut) { acc += hist[hi]; hi-- }
        if (hi - lo < 16) return
        val sc = 255f / (hi - lo)
        for (i in px.indices) {
            val p = px[i]
            px[i] = rgb(p ushr 24, clamp((r(p) - lo) * sc), clamp((g(p) - lo) * sc), clamp((b(p) - lo) * sc))
        }
    }

    /** Gentle S-curve: amount 0..1 (0 = none). */
    fun contrast(px: IntArray, amount: Float) {
        val lut = IntArray(256) { v ->
            val x = v / 255f
            val s = x * x * (3f - 2f * x)
            clamp(255f * (x + (s - x) * amount))
        }
        for (i in px.indices) { val p = px[i]; px[i] = rgb(p ushr 24, lut[r(p)], lut[g(p)], lut[b(p)]) }
    }

    /** factor 1 = same. Pixels that look like skin (r > g > b, moderate saturation) get only half of the change, so faces do not turn orange. */
    fun saturation(px: IntArray, factor: Float) {
        for (i in px.indices) {
            val p = px[i]
            val l = lum(p); val rr = r(p); val gg = g(p); val bb = b(p)
            val mx = maxOf(rr, maxOf(gg, bb)); val mn = minOf(rr, minOf(gg, bb))
            val sat = if (mx == 0) 0f else (mx - mn) / mx.toFloat()
            val skin = rr > gg && gg > bb && sat in 0.15f..0.6f && rr > 90
            val f = if (skin) 1f + (factor - 1f) * 0.5f else factor
            px[i] = rgb(p ushr 24, clamp(l + (rr - l) * f), clamp(l + (gg - l) * f), clamp(l + (bb - l) * f))
        }
    }

    fun brightness(px: IntArray, gamma: Float) {   // gamma < 1 brightens
        val lut = IntArray(256) { v -> clamp(255f * Math.pow((v / 255f).toDouble(), gamma.toDouble()).toFloat()) }
        for (i in px.indices) { val p = px[i]; px[i] = rgb(p ushr 24, lut[r(p)], lut[g(p)], lut[b(p)]) }
    }

    fun tint(px: IntArray, dr: Int, dg: Int, db: Int) {
        for (i in px.indices) { val p = px[i]; px[i] = rgb(p ushr 24, clamp((r(p) + dr).toFloat()), clamp((g(p) + dg).toFloat()), clamp((b(p) + db).toFloat())) }
    }

    fun grayscale(px: IntArray) {
        for (i in px.indices) { val p = px[i]; val l = clamp(lum(p)); px[i] = rgb(p ushr 24, l, l, l) }
    }

    fun sepia(px: IntArray) {
        for (i in px.indices) {
            val p = px[i]; val rr = r(p).toFloat(); val gg = g(p).toFloat(); val bb = b(p).toFloat()
            px[i] = rgb(p ushr 24, clamp(0.393f * rr + 0.769f * gg + 0.189f * bb), clamp(0.349f * rr + 0.686f * gg + 0.168f * bb), clamp(0.272f * rr + 0.534f * gg + 0.131f * bb))
        }
    }

    fun vignette(px: IntArray, w: Int, h: Int, strength: Float) {
        if (w <= 0 || h <= 0) return
        val cx = w / 2f; val cy = h / 2f; val maxD = Math.sqrt((cx * cx + cy * cy).toDouble()).toFloat()
        for (y in 0 until h) for (x in 0 until w) {
            val d = Math.sqrt(((x - cx) * (x - cx) + (y - cy) * (y - cy)).toDouble()).toFloat() / maxD
            val f = 1f - strength * d * d
            val i = y * w + x; val p = px[i]
            px[i] = rgb(p ushr 24, clamp(r(p) * f), clamp(g(p) * f), clamp(b(p) * f))
        }
    }

    /** 3x3 box blur copy (edges clamped). */
    fun blur3(px: IntArray, w: Int, h: Int): IntArray {
        val out = IntArray(px.size)
        for (y in 0 until h) for (x in 0 until w) {
            var sr = 0; var sg = 0; var sb = 0; var n = 0
            for (dy in -1..1) { val yy = y + dy; if (yy < 0 || yy >= h) continue
                for (dx in -1..1) { val xx = x + dx; if (xx < 0 || xx >= w) continue
                    val p = px[yy * w + xx]; sr += r(p); sg += g(p); sb += b(p); n++ } }
            out[y * w + x] = rgb(px[y * w + x] ushr 24, sr / n, sg / n, sb / n)
        }
        return out
    }

    /** Light noise reduction (mix with the blurred copy) then unsharp mask. amount ~0.6 = professional, not crunchy. */
    fun denoiseSharpen(px: IntArray, w: Int, h: Int, denoise: Float, sharp: Float) {
        if (w < 3 || h < 3) return
        val bl = blur3(px, w, h)
        for (i in px.indices) {
            val p = px[i]; val q = bl[i]
            px[i] = rgb(p ushr 24, clamp(r(p) + (r(q) - r(p)) * denoise), clamp(g(p) + (g(q) - g(p)) * denoise), clamp(b(p) + (b(q) - b(p)) * denoise))
        }
        val bl2 = blur3(px, w, h)
        for (i in px.indices) {
            val p = px[i]; val q = bl2[i]
            px[i] = rgb(p ushr 24, clamp(r(p) + (r(p) - r(q)) * sharp), clamp(g(p) + (g(p) - g(q)) * sharp), clamp(b(p) + (b(p) - b(q)) * sharp))
        }
    }

    /** All presets on an ARGB array, in place. */
    fun apply(px: IntArray, w: Int, h: Int, preset: Preset) {
        when (preset) {
            Preset.ENHANCE -> { autoLevels(px); contrast(px, 0.25f); saturation(px, 1.12f); denoiseSharpen(px, w, h, 0.2f, 0.6f) }
            Preset.BRIGHT -> { autoLevels(px); brightness(px, 0.7f); contrast(px, 0.15f); denoiseSharpen(px, w, h, 0.25f, 0.4f) }
            Preset.BW -> { autoLevels(px); grayscale(px); contrast(px, 0.3f) }
            Preset.SEPIA -> { autoLevels(px); sepia(px) }
            Preset.CINEMATIC -> { autoLevels(px); contrast(px, 0.45f); saturation(px, 0.85f); tint(px, -6, 0, 10); vignette(px, w, h, 0.45f) }
            Preset.WARM -> { tint(px, 14, 4, -12); autoLevels(px) }
            Preset.COOL -> { tint(px, -12, 0, 14); autoLevels(px) }
            Preset.VIVID -> { autoLevels(px); contrast(px, 0.3f); saturation(px, 1.4f); denoiseSharpen(px, w, h, 0.1f, 0.5f) }
            Preset.SOFT -> { val bl = blur3(px, w, h); for (i in px.indices) { val p = px[i]; val q = bl[i]; px[i] = rgb(p ushr 24, (r(p) + r(q)) / 2, (g(p) + g(q)) / 2, (b(p) + b(q)) / 2) }; brightness(px, 0.9f) }
        }
    }

    /**
     * Android part: JPEG bytes in, JPEG bytes out (null on any failure, incl. OutOfMemory). ratio = (rw, rh) crops first; preset may be null for crop only.
     * Works on a copy, off the main thread (the caller starts a Thread).
     */
    fun run(raw: ByteArray, preset: Preset?, ratio: IntArray? = null, maxSide: Int = 2048): ByteArray? {
        return try {
            val sm = ImageGen.shrink(raw, maxSide) ?: return null
            var bmp: Bitmap = BitmapFactory.decodeByteArray(sm, 0, sm.size) ?: return null
            if (ratio != null && ratio.size == 2) {
                val c = cropRect(bmp.width, bmp.height, ratio[0], ratio[1])
                val cropped = Bitmap.createBitmap(bmp, c[0], c[1], c[2], c[3])
                bmp = cropped
            }
            val w = bmp.width; val h = bmp.height
            val px = IntArray(w * h)
            bmp.getPixels(px, 0, w, 0, 0, w, h)
            if (preset != null) apply(px, w, h, preset)
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            out.setPixels(px, 0, w, 0, 0, w, h)
            val bo = java.io.ByteArrayOutputStream()
            out.compress(Bitmap.CompressFormat.JPEG, 93, bo)
            bo.toByteArray()
        } catch (e: OutOfMemoryError) {
            null
        } catch (e: Throwable) {
            ErrLog.add("PhotoPro", e.toString())
            null
        }
    }
}
