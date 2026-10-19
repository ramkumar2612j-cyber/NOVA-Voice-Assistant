package com.nova.assistant

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * v7.66 PART 14B-4 - pure maths + texts of the full-screen laser overlay of the movie scan (no Android classes, so JUnit can drive it).
 * The stages are the REAL ones: CAPTURE (frames), READ TEXT 10, VISION 25, SEARCH 55..90, RESULT 100 (NovaService.movieStage).
 */
object LaserMath {
    const val ST_CAPTURE = "CAPTURE"
    val STAGES = listOf(ST_CAPTURE, MovieGuessPipeline.ST_READ, MovieGuessPipeline.ST_VISION, MovieGuessPipeline.ST_SEARCH, MovieGuessPipeline.ST_RESULT)
    const val SWEEP_MS = 2600L
    private const val GLYPHS = "01ABCDEF<>/\\|=+*#"

    class Stage(val name: String, val percent: Int)

    /** "SEARCH 55" -> (SEARCH, 55); "READ TEXT 10" -> (READ TEXT, 10); blank / unknown -> CAPTURE 0; percent clamps to 0..100. */
    fun parse(raw: String): Stage {
        val s = raw.trim()
        if (s.isEmpty()) return Stage(ST_CAPTURE, 0)
        val i = s.lastIndexOf(' ')
        val num = if (i > 0) s.substring(i + 1).toIntOrNull() else null
        val name = (if (num != null) s.substring(0, i) else s).trim().uppercase()
        if (name !in STAGES) return Stage(ST_CAPTURE, 0)
        return Stage(name, (num ?: 0).coerceIn(0, 100))
    }

    fun stageIndex(name: String): Int = STAGES.indexOf(name).let { if (it < 0) 0 else it }

    /** Laser position 0..1, top -> bottom, then it starts again at the top (a printer head). */
    fun sweep(nowMs: Long): Float = ((nowMs % SWEEP_MS).toFloat() / SWEEP_MS).coerceIn(0f, 1f)

    /** Simple mode: the laser does not move, it sits at the real percent of the work. */
    fun staticLine(percent: Int): Float = (percent.coerceIn(0, 100) / 100f)

    /** Reduced motion / low-end phone / animations off in Android -> a plain static overlay. */
    fun simple(lowTier: Boolean, animatorScale: Float): Boolean = lowTier || animatorScale <= 0f

    /** A deterministic matrix glyph for column [col], row [row] at time step [step]. */
    fun glyph(col: Int, row: Int, step: Long): Char {
        val k = (col * 131L + row * 17L + step * 7L)
        return GLYPHS[(((k % GLYPHS.length) + GLYPHS.length) % GLYPHS.length).toInt()]
    }

    /** Small horizontal jitter (px) near the laser line; 0 most of the time, so it reads as a short glitch. */
    fun glitchDx(nowMs: Long, amp: Float): Float {
        val slot = nowMs / 90L
        return if (slot % 11L == 0L) (if (slot % 2L == 0L) amp else -amp) else 0f
    }

    fun label(stage: String, en: Boolean): String = when (stage) {
        ST_CAPTURE -> if (en) "CAPTURING SCREEN" else "SCREEN PAKAD RAHA HOON"
        MovieGuessPipeline.ST_READ -> if (en) "READING TEXT" else "TEXT PADH RAHA HOON"
        MovieGuessPipeline.ST_VISION -> if (en) "LOOKING AT FRAMES" else "FRAMES DEKH RAHA HOON"
        MovieGuessPipeline.ST_SEARCH -> if (en) "SEARCHING THE WEB" else "WEB PAR DHOONDH RAHA HOON"
        MovieGuessPipeline.ST_RESULT -> if (en) "RESULT READY" else "RESULT TAIYAAR"
        else -> ""
    }

    fun stopText(en: Boolean): String = if (en) "STOP" else "ROKO"

    /** Bottom bar fill 0..1 follows the real percent. */
    fun barFill(percent: Int): Float = percent.coerceIn(0, 100) / 100f
}

/**
 * Full-screen hacker-style scan: dark scrim, grid, a green laser line sweeping top to bottom, falling matrix text, the real stage + percent and a STOP button.
 * It is NOT touch-through (the whole screen is covered, taps do nothing except STOP). It is hidden while a frame is captured, so the laser never appears in the frames.
 * Main thread for show / setStage / destroy; hideForShot / showAfterShot may be called from the worker thread. Without the overlay permission everything is a silent no-op.
 */
class LaserOverlay(private val ctx: Context) {
    private val wm: WindowManager = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var view: LaserView? = null
    /** STOP tapped on the overlay. */
    @Volatile var onStop: (() -> Unit)? = null

    companion object {
        fun canShow(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

        fun isSimple(ctx: Context): Boolean {
            val scale = try { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) } catch (e: Throwable) { 1f }
            val am = try { ctx.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager } catch (e: Throwable) { null }
            val low = try { am?.isLowRamDevice == true } catch (e: Throwable) { false }
            return LaserMath.simple(low, scale)
        }
    }

    val isShown: Boolean get() = view != null

    fun show(en: Boolean): Boolean {
        if (!canShow(ctx)) return false
        if (view != null) return true
        val v = LaserView(ctx, en, isSimple(ctx))
        v.onStopTap = { onStop?.invoke() }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,   // no FLAG_NOT_TOUCHABLE: the overlay takes the touches (14B-4 order)
            PixelFormat.TRANSLUCENT
        )
        return try { wm.addView(v, lp); view = v; true } catch (e: Exception) { view = null; false }
    }

    fun setStage(raw: String) { view?.setStage(LaserMath.parse(raw)) }

    /** Worker thread: make the overlay invisible and wait until Android has drawn that, so the next screenshot has no laser in it. */
    fun hideForShot() {
        val v = view ?: return
        val latch = CountDownLatch(1)
        main.post { try { v.visibility = View.INVISIBLE } catch (e: Throwable) { }; latch.countDown() }
        try { latch.await(400, TimeUnit.MILLISECONDS) } catch (e: InterruptedException) { }
        try { Thread.sleep(140) } catch (e: InterruptedException) { }   // one or two display frames for the compositor to drop the layer
    }

    fun showAfterShot() {
        val v = view ?: return
        main.post { try { v.visibility = View.VISIBLE } catch (e: Throwable) { } }
    }

    fun destroy() {
        val v = view ?: return
        view = null
        try { wm.removeView(v) } catch (e: Exception) { }
    }
}

private class LaserView(ctx: Context, private val en: Boolean, private val simple: Boolean) : View(ctx) {
    var onStopTap: (() -> Unit)? = null
    @Volatile private var stage: LaserMath.Stage = LaserMath.Stage(LaserMath.ST_CAPTURE, 0)
    private val d = resources.displayMetrics.density
    private val green = Color.rgb(57, 255, 120)
    private val scrim = Paint().apply { color = Color.argb(205, 0, 8, 4) }
    private val grid = Paint().apply { color = Color.argb(46, 57, 255, 120); strokeWidth = d }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = green; strokeWidth = 2f * d }
    private val glow = Paint()
    private val trail = Paint()
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = green; typeface = Typeface.MONOSPACE; textSize = 13f * d }
    private val rain = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 57, 255, 120); typeface = Typeface.MONOSPACE; textSize = 12f * d }
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = green; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); textSize = 18f * d; textAlign = Paint.Align.CENTER }
    private val barBg = Paint().apply { color = Color.argb(70, 57, 255, 120) }
    private val barFg = Paint().apply { color = green }
    private val btnBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(230, 190, 30, 40) }
    private val btnTx = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); textSize = 15f * d; textAlign = Paint.Align.CENTER }
    private val btn = RectF()
    private var t0 = SystemClock.uptimeMillis()

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    fun setStage(s: LaserMath.Stage) { stage = s; invalidate() }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_UP && btn.contains(e.x, e.y)) onStopTap?.invoke()
        return true   // not touch-through: every touch is taken
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val now = SystemClock.uptimeMillis()
        val st = stage
        c.drawRect(0f, 0f, w, h, scrim)
        val cell = 28f * d
        var x = 0f
        while (x < w) { c.drawLine(x, 0f, x, h, grid); x += cell }
        var y = 0f
        while (y < h) { c.drawLine(0f, y, w, y, grid); y += cell }

        val pos = if (simple) LaserMath.staticLine(st.percent) else LaserMath.sweep(now - t0)
        val ly = (pos * h).coerceIn(0f, h)
        // matrix text only above the laser (as if the printer has already written it); none in simple mode
        if (!simple) {
            val colW = 16f * d; val rowH = 15f * d
            val step = (now - t0) / 140L
            var col = 0
            var cx = colW / 2
            while (cx < w) {
                val rows = (ly / rowH).toInt()
                val from = (rows - 6).coerceAtLeast(0)
                for (r in from..rows) c.drawText(LaserMath.glyph(col, r, step).toString(), cx, r * rowH, rain)
                cx += colW; col++
            }
            trail.shader = LinearGradient(0f, (ly - 140f * d).coerceAtLeast(0f), 0f, ly, Color.argb(0, 57, 255, 120), Color.argb(70, 57, 255, 120), Shader.TileMode.CLAMP)
            c.drawRect(0f, (ly - 140f * d).coerceAtLeast(0f), w, ly, trail)
        }
        val dx = if (simple) 0f else LaserMath.glitchDx(now, 6f * d)
        glow.shader = LinearGradient(0f, ly - 18f * d, 0f, ly + 18f * d,
            intArrayOf(Color.argb(0, 57, 255, 120), Color.argb(120, 57, 255, 120), Color.argb(0, 57, 255, 120)), null, Shader.TileMode.CLAMP)
        c.drawRect(dx, ly - 18f * d, w + dx, ly + 18f * d, glow)
        c.drawLine(dx, ly, w + dx, ly, line)

        // real stage + percent
        val top = 64f * d
        c.drawText("NOVA // SCREEN SCAN", 18f * d, top, txt)
        var yy = top + 22f * d
        for ((i, name) in LaserMath.STAGES.withIndex()) {
            val idx = LaserMath.stageIndex(st.name)
            val mark = if (i < idx) "[x]" else if (i == idx) "[>]" else "[ ]"
            c.drawText("$mark $name", 18f * d, yy, txt)
            yy += 18f * d
        }
        c.drawText(LaserMath.label(st.name, en) + "  " + st.percent + "%", w / 2, h * 0.5f, big)

        // progress bar + STOP
        val bw = w - 36f * d; val by = h - 120f * d
        c.drawRect(18f * d, by, 18f * d + bw, by + 6f * d, barBg)
        c.drawRect(18f * d, by, 18f * d + bw * LaserMath.barFill(st.percent), by + 6f * d, barFg)
        val bwid = 150f * d; val bh = 48f * d
        btn.set((w - bwid) / 2, h - 90f * d, (w + bwid) / 2, h - 90f * d + bh)
        c.drawRoundRect(btn, 24f * d, 24f * d, btnBg)
        c.drawText(LaserMath.stopText(en), btn.centerX(), btn.centerY() + 5f * d, btnTx)

        if (!simple) postInvalidateOnAnimation()
    }
}
