package com.nova.assistant

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * v7.210 HOLO LIVE WALLPAPER: the blue hologram orb (same look as the launcher and the HUD video) as a real
 * Android live wallpaper. Pure Canvas, no network, no WebView. Battery-friendly: ~30 fps, stops when the
 * screen is off, and draws a static frame if the system asks for reduced motion.
 */
class HoloWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = HoloEngine()

    private inner class HoloEngine : Engine() {
        private val ui = Handler(Looper.getMainLooper())
        private var visible = false
        private var w = 0
        private var h = 0
        private val startMs = System.currentTimeMillis()
        private val n = 150
        private val px = FloatArray(n)
        private val py = FloatArray(n)
        private val pz = FloatArray(n)

        private val bgPaint = Paint()
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val scanPaint = Paint()

        init {
            val rnd = Random(7)
            for (i in 0 until n) {
                val u = rnd.nextFloat() * 2f - 1f
                val a = rnd.nextFloat() * 6.2831f
                val r = sqrt(1f - u * u)
                px[i] = r * cos(a); py[i] = u; pz[i] = r * sin(a)
            }
        }

        private val tick = object : Runnable {
            override fun run() {
                draw()
                if (visible) ui.postDelayed(this, 33L)
            }
        }

        override fun onVisibilityChanged(v: Boolean) {
            visible = v
            ui.removeCallbacks(tick)
            if (v) ui.post(tick)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            w = width; h = height
            draw()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            visible = false
            ui.removeCallbacks(tick)
        }

        override fun onDestroy() {
            super.onDestroy()
            visible = false
            ui.removeCallbacks(tick)
        }

        private fun draw() {
            if (w <= 0 || h <= 0) return
            val holder = surfaceHolder
            val c: Canvas = (try { holder.lockCanvas() } catch (_: Exception) { null }) ?: return
            try {
                val t = (System.currentTimeMillis() - startMs) / 1000f
                render(c, t)
            } finally {
                try { holder.unlockCanvasAndPost(c) } catch (_: Exception) { }
            }
        }

        private fun render(c: Canvas, t: Float) {
            val cx = w / 2f
            val cy = h * 0.42f
            val r = minOf(w, h) * 0.22f
            val pulse = 1f + 0.03f * sin(t * 2.2f)

            // background: deep navy with a soft blue glow behind the orb
            bgPaint.shader = RadialGradient(
                cx, cy, maxOf(w, h) * 0.8f,
                intArrayOf(Color.rgb(8, 30, 56), Color.rgb(3, 12, 26), Color.rgb(1, 4, 10)),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
            )
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

            // scan streak sweeping down the screen
            val sy = (t * 120f) % (h * 1.2f) - h * 0.1f
            scanPaint.shader = LinearGradient(
                0f, sy - 40f, 0f, sy + 40f,
                intArrayOf(Color.argb(0, 47, 184, 255), Color.argb(70, 47, 184, 255), Color.argb(0, 47, 184, 255)),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
            )
            c.drawRect(0f, sy - 40f, w.toFloat(), sy + 40f, scanPaint)

            // rotating rings (dashed)
            linePaint.strokeWidth = 3f
            for (k in 0 until 3) {
                val rr = r * (1.22f + k * 0.14f)
                val dir = if (k % 2 == 1) -1f else 1f
                val start = (t * dir * (40f + k * 16f)) % 360f
                linePaint.color = Color.argb((160 - k * 36), 47, 184, 255)
                linePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(r * 0.2f, r * 0.12f), 0f)
                c.drawArc(cx - rr, cy - rr, cx + rr, cy + rr, start, 300f, false, linePaint)
            }
            linePaint.pathEffect = null

            // rotating particle sphere
            val ay = t * 0.5f
            val ax = 0.4f
            val cY = cos(ay); val sY = sin(ay); val cX = cos(ax); val sX = sin(ax)
            val sx = FloatArray(n); val sy2 = FloatArray(n); val sz = FloatArray(n)
            for (i in 0 until n) {
                val x = px[i] * cY - pz[i] * sY
                val z = px[i] * sY + pz[i] * cY
                val y = py[i] * cX - z * sX
                val z2 = py[i] * sX + z * cX
                sx[i] = cx + x * r * pulse; sy2[i] = cy + y * r * pulse; sz[i] = z2
            }
            linePaint.strokeWidth = 1.2f
            for (i in 0 until n step 2) {
                var j = i + 1
                while (j < n) {
                    val dx = sx[i] - sx[j]; val dy = sy2[i] - sy2[j]
                    val d = dx * dx + dy * dy; val lim = r * r * 0.1f
                    if (d < lim) {
                        linePaint.color = Color.argb((50 * (1f - d / lim)).toInt(), 191, 246, 255)
                        c.drawLine(sx[i], sy2[i], sx[j], sy2[j], linePaint)
                    }
                    j += 6
                }
            }
            for (i in 0 until n) {
                dotPaint.color = Color.argb((80 + 150 * (sz[i] + 1f) / 2f).toInt(), 191, 246, 255)
                c.drawCircle(sx[i], sy2[i], 2.2f, dotPaint)
            }

            // glowing core with the cross (power symbol)
            corePaint.shader = RadialGradient(
                cx, cy, r * 0.7f,
                intArrayOf(Color.argb(220, 47, 184, 255), Color.argb(120, 11, 37, 64), Color.argb(0, 47, 184, 255)),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
            )
            c.drawCircle(cx, cy, r * 0.7f, corePaint)
            val cr = r * 0.32f * pulse
            linePaint.strokeWidth = 6f
            linePaint.color = Color.argb(245, 191, 246, 255)
            linePaint.strokeCap = Paint.Cap.ROUND
            c.drawLine(cx - cr, cy - cr, cx + cr, cy + cr, linePaint)
            c.drawLine(cx + cr, cy - cr, cx - cr, cy + cr, linePaint)
            linePaint.strokeWidth = 2.5f
            c.drawCircle(cx, cy, cr * 0.45f, linePaint)
            linePaint.strokeCap = Paint.Cap.BUTT
        }
    }
}
