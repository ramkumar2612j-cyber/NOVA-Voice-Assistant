package com.nova.assistant

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/**
 * Pure drawing maths for the NOVA card (no Android window code). The card is a dark glass panel with a glowing
 * orb; it fades and scales in. (The plate helpers of the old "transformer" card are kept only because they are tested.)
 */
internal object HudMath {
    const val COLS = 4
    const val ROWS = 3
    const val PANELS = COLS * ROWS

    /** Per-plate progress 0..1 for global time t (0..1). Plates start staggered, each takes 55% of the timeline. */
    fun plateProgress(index: Int, t: Float): Float {
        val delay = index * 0.035f
        val p = (t - delay) / 0.55f
        return if (p < 0f) 0f else if (p > 1f) 1f else p
    }

    /** easeOutBack: slight overshoot, which reads as the plate "clicking" into place. */
    fun easeOutBack(x: Float): Float {
        val c1 = 1.70158f
        val c3 = c1 + 1f
        val u = x - 1f
        return 1f + c3 * u * u * u + c1 * u * u
    }

    /** Pop-in curve for the glass card: t 0..1 -> 0..1, fast start, soft landing. */
    fun popEase(t: Float): Float {
        val k = t.coerceIn(0f, 1f)
        val u = 1f - k
        return 1f - u * u * u
    }

    /** Spring open: 0 -> about 1.04 -> 1 (a damped overshoot), so the card "snaps" in with weight. */
    fun springEase(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return 1f - exp(-6f * x) * cos(10f * x)
    }

    /** Light burst while the card opens: 1 at the start, 0 when fully open (and always 0 outside 0..1). */
    fun entryFlash(t: Float): Float {
        if (t <= 0f || t >= 1f) return 0f
        val u = 1f - t
        return u * u
    }

    /** Brightness 0..1 of the glowing border: a slow breathing, a little stronger while listening. */
    fun glowLevel(nowMs: Long, state: String): Float {
        val base = if (state == "listen") 0.65f else 0.5f
        return (base + 0.25f * sin(nowMs / 450f)).coerceIn(0f, 1f)
    }

    const val BARS = 28

    /**
     * Height 0..1 of waveform bar [i] of [n]. listen = moves with the mic [level] (0..1), think = a scanner sweeps
     * across, anything else = a calm idle wave. [nowMs] is a monotonic clock.
     */
    fun barHeight(i: Int, n: Int, level: Float, nowMs: Long, state: String): Float {
        val x = i.toFloat() / n
        val tt = nowMs / 1000f
        val v = when (state) {
            "think" -> {
                val p = (tt * 0.9f) % 1f
                max(0f, 1f - abs(x - p) * 6f) * 0.9f + 0.08f
            }
            "listen" -> {
                val wave = 0.5f + 0.5f * sin(tt * 7f + i * 0.6f)
                0.12f + level.coerceIn(0f, 1f) * 0.88f * wave
            }
            else -> 0.1f + 0.12f * (0.5f + 0.5f * sin(tt * 2.5f + i * 0.4f))
        }
        return v.coerceIn(0f, 1f)
    }

    /** One expanding ring: [p] 0..1 -> alpha 1..0 (fades out as it grows). */
    fun rippleAlpha(p: Float): Float {
        val k = 1f - p.coerceIn(0f, 1f)
        return k * k
    }

    /** Linear colour mix (ARGB ints), t 0..1. */
    fun mix(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        fun ch(s: Int) = (((a shr s) and 0xff) + ((((b shr s) and 0xff) - ((a shr s) and 0xff)) * k)).toInt() and 0xff
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** 0..1..0 flash of the centre seam just as the plates finish joining (t 0.7 to 1.0). */
    fun seamFlare(t: Float): Float {
        val x = (t - 0.7f) / 0.3f
        return if (x <= 0f || x >= 1f) 0f else sin(Math.PI.toFloat() * x)
    }

    /** How long the card stays after a reply: long enough to READ it (replies are silent by default). */
    fun holdMs(replyLen: Int): Long = (1500L + replyLen * 55L).coerceIn(1500L, 9000L)

    /** Text becomes visible only after the plates joined (t from 0.75 to 1.0). */
    fun textAlpha(t: Float): Float {
        val a = (t - 0.75f) / 0.25f
        return if (a < 0f) 0f else if (a > 1f) 1f else a
    }
}

/**
 * Floating NOVA card shown over every app (needs the "Display over other apps" permission).
 * All methods must be called on the main thread. If the permission is missing everything is a silent no-op,
 * so voice commands keep working exactly as before.
 */
class NovaHud(private val ctx: Context) {

    private val wm: WindowManager = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    @Volatile private var view: HudView? = null
    /** v7.1: tap on HAAN (true) / NAHI (false) while NOVA waits for a yes/no. Set by the service. */
    @Volatile var onAnswer: ((Boolean) -> Unit)? = null
    /** Explicit STOP control; unlike dismiss, this cancels active work and listening. */
    @Volatile var onStop: (() -> Unit)? = null
    @Volatile var onSilentToggle: (() -> Boolean)? = null
    /** Text typed in the popup's chat box; returns "sent", "busy" or "off" (wired to NovaService.typed). */
    @Volatile var onTyped: ((String) -> String)? = null
    @Volatile var silentOn: Boolean = false
    @Volatile private var askOn = false
    private var chat: android.widget.LinearLayout? = null
    private var shownAt = 0L
    private var lastReply = ""

    companion object {
        fun canShow(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)
        const val MIN_VISIBLE_MS = 2500L
    }

    val isShown: Boolean get() = view != null
    fun visibleForMs(): Long = if (view == null) 0L else System.currentTimeMillis() - shownAt

    /** state: "listen", "think", "reply". */
    @Volatile private var leaving: HudView? = null   // v7.86: a card that is flying apart; it must never stay as a second assistant

    private fun removeLeaving() {
        val l = leaving ?: return
        leaving = null
        try { l.cancelAnim() } catch (e: Throwable) { }
        try { wm.removeView(l) } catch (e: Exception) { }
    }

    fun show(state: String) {
        if (!canShow(ctx)) return
        removeLeaving()
        val v = view
        if (v != null) { v.changeState(state); return }
        val nv = HudView(ctx)
        nv.changeState(state)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        lp.y = (44 * ctx.resources.displayMetrics.density).toInt()
        nv.onDismissTap = { hide(true) }
        nv.onChatTap = { openChat() }
        nv.onAnswerTap = { yes -> onAnswer?.invoke(yes) }
        nv.onStop = { onStop?.invoke() }
        nv.onSilentToggle = { onSilentToggle?.invoke() ?: nv.silentOn }
        nv.silentOn = silentOn
        nv.setAsk(askOn)
        nv.onMinimizeTap = {
            if (view === nv) {
                nv.toggleMinimized()
                try { wm.updateViewLayout(nv, lp) } catch (e: Exception) { /* keep the current card if resize is temporarily rejected */ }
            }
        }
        try {
            wm.addView(nv, lp)
            view = nv
            shownAt = System.currentTimeMillis()
            lastReply = ""
            nv.assemble()
        } catch (e: Exception) {
            view = null     // no overlay allowed right now: keep running without the card
        }
    }

    /** Mic loudness 0..1, may be called from the audio thread (it only stores a number; the card redraws itself). */
    fun setLevel(l: Float) { view?.level = l }

    /** v7.1: show (or hide) the HAAN / NAHI buttons on the card. */
    fun setAsk(on: Boolean) { askOn = on; view?.setAsk(on) }
    fun holdMs(): Long = HudMath.holdMs(view?.reply?.length ?: 0)

    fun setHeard(text: String) { view?.heard = text; view?.invalidate() }
    fun setReply(text: String) {
        view?.reply = text
        view?.invalidate()
        // one short tick when a reply appears (never while the mic is capturing a command, so it cannot be heard as speech)
        if (view != null && text.isNotBlank() && text != lastReply) tick()
        lastReply = text
    }

    @Suppress("DEPRECATION")
    private fun tick() {
        try {
            val v: android.os.Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
                ctx.getSystemService(android.os.VibratorManager::class.java)?.defaultVibrator
            } else {
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            }
            if (v == null) return
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(android.os.VibrationEffect.createOneShot(22L, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                v.vibrate(22L)
            }
        } catch (e: Throwable) {
        }
    }
    fun setState(state: String) { view?.changeState(state) }

    /** The chat box of the three-dots menu: a small native typing window under the popup (it needs keyboard focus, so it is its own window). */
    private fun closeChat() {
        val v = chat ?: return
        chat = null
        try {
            val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            imm?.hideSoftInputFromWindow(v.windowToken, 0)
        } catch (e: Throwable) { }
        try { wm.removeView(v) } catch (e: Exception) { }
    }

    private fun openChat() {
        if (!canShow(ctx)) return
        if (chat != null) { closeChat(); return }
        val d = ctx.resources.displayMetrics.density
        val boxW = minOf(ctx.resources.displayMetrics.widthPixels - 24 * d, 296 * d).toInt()
        val row = android.widget.LinearLayout(ctx)
        row.orientation = android.widget.LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding((14 * d).toInt(), (6 * d).toInt(), (8 * d).toInt(), (6 * d).toInt())
        val bg = android.graphics.drawable.GradientDrawable()
        bg.setColor(0xF20A1236.toInt())
        bg.colors = intArrayOf(0xF2101C44.toInt(), 0xF20A1030.toInt(), 0xF2160C38.toInt())   // v7.44: same glass gradient as the pill
        bg.cornerRadius = 24 * d
        bg.setStroke((1.5f * d).toInt(), 0xFF27E1FF.toInt())
        row.background = bg
        val et = android.widget.EditText(ctx)
        et.hint = "NOVA se likho\u2026"
        et.setHintTextColor(0xFF7F95BC.toInt())
        et.setTextColor(0xFFEAF2FF.toInt())
        et.textSize = 15f
        et.background = null
        et.setSingleLine(true)
        et.inputType = android.text.InputType.TYPE_CLASS_TEXT
        et.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEND
        row.addView(et, android.widget.LinearLayout.LayoutParams(0, (44 * d).toInt(), 1f))
        fun pill(label: String, color: Int, w: Int): android.widget.TextView {
            val tv = android.widget.TextView(ctx)
            tv.text = label
            tv.setTextColor(0xFFFFFFFF.toInt())
            tv.textSize = 12f
            tv.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            tv.gravity = Gravity.CENTER
            val b = android.graphics.drawable.GradientDrawable()
            b.setColor(color)
            b.cornerRadius = 18 * d
            tv.background = b
            val lp = android.widget.LinearLayout.LayoutParams(w, (40 * d).toInt())
            lp.leftMargin = (6 * d).toInt()
            tv.layoutParams = lp
            return tv
        }
        fun doSend() {
            val text = et.text.toString().trim()
            if (text.isEmpty()) return
            et.setText("")
            val r = try { onTyped?.invoke(text) } catch (e: Throwable) { null }
            if (r == "busy") { view?.let { it.reply = "NOVA abhi kaam kar raha hai, thoda ruko"; it.invalidate() } }
        }
        val send = pill("SEND", 0xFF2F5FD0.toInt(), (62 * d).toInt())
        send.setOnClickListener { doSend() }
        val close = pill("\u2715", 0xFF3A4B7A.toInt(), (40 * d).toInt())
        close.setOnClickListener { closeChat() }
        et.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) { doSend(); true } else false
        }
        row.addView(send)
        row.addView(close)
        val lp = WindowManager.LayoutParams(
            boxW,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        lp.y = (176 * d).toInt()
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        try {
            wm.addView(row, lp)
            chat = row
            row.post {
                try {
                    et.requestFocus()
                    val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                    imm?.showSoftInput(et, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                } catch (e: Throwable) { }
            }
        } catch (e: Exception) {
            chat = null    // no overlay allowed right now: the popup keeps working without the box
        }
    }

    /** Plates fly apart, then the window is removed. While the chat box is open an automatic hide is ignored; a tap on BAND KARO / X forces it. */
    fun hide(force: Boolean = false) {
        if (!force && chat != null) return
        closeChat()
        val v = view ?: return
        view = null
        removeLeaving()
        leaving = v
        v.disassemble { if (leaving === v) removeLeaving() else try { wm.removeView(v) } catch (e: Exception) { } }
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ if (leaving === v) removeLeaving() }, 700L)   // v7.86: if the animation was cancelled, remove anyway
    }

    /** Immediate removal (service stopping). */
    fun destroy() {
        closeChat()
        removeLeaving()
        val v = view ?: return
        view = null
        v.cancelAnim()
        try { wm.removeView(v) } catch (e: Exception) { }
    }
}

private class HudView(ctx: Context) : View(ctx) {
    private val d = ctx.resources.displayMetrics.density
    private val margin = 10f * d                      // room around the card for the glow
    private val cardW = (minOf(ctx.resources.displayMetrics.widthPixels - 24 * d, 296 * d)).toInt()
    private val cardH = (148f * d).toInt()
    private val fullViewW = cardW + (2 * margin).toInt()
    private val fullViewH = cardH + (2 * margin).toInt()
    private val miniViewW = minOf(fullViewW, (minOf(ctx.resources.displayMetrics.widthPixels - 8 * d, 194 * d)).toInt())
    private val miniViewH = (72f * d).toInt()

    var heard = ""
    var reply = ""
        set(v) {
            val had = field.isNotBlank()
            field = v
            if (had != v.isNotBlank()) { fillShader = null; requestLayout() }   // the compact pill grows by one text row only while a reply is shown
        }
    var state = "listen"
    var onDismissTap: (() -> Unit)? = null
    var onAnswerTap: ((Boolean) -> Unit)? = null
    var onStop: (() -> Unit)? = null                 // explicit STOP tap (wired from NovaHud)
    var onSilentToggle: (() -> Boolean)? = null      // SILENT tap: returns the new state
    @Volatile var silentOn: Boolean = false
    private var askMode = false
    private val askExtra = (62f * d).toInt()   // v77: room for 44dp HAAN/NAHI buttons
    private val btnYes = RectF()
    private val btnNo = RectF()
    private val btnStop = RectF()
    private val btnSilent = RectF()
    private val btnPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val btnText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textSize = 14f * d; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textAlign = Paint.Align.CENTER }
    var onMinimizeTap: (() -> Unit)? = null
    var onChatTap: (() -> Unit)? = null              // CHAT item of the three-dots menu (NovaHud opens the typing box)
    private var menuOpen = false                      // three-dots menu: SILENT / CHAT / CHHOTA KARO / BAND KARO
    private val btnDots = RectF()
    private val mSilent = RectF()
    private val mChat = RectF()
    private val mMin = RectF()
    private val mClose = RectF()
    @Volatile var level = 0f            // mic loudness 0..1 (written from the audio thread)
    private var smooth = 0f
    private var minimized = false
    private var modeProgress = 0f
    private var modeAnimator: ValueAnimator? = null
    private var downX = 0f
    private var downY = 0f
    private var pressedControl = 0 // 1=minimize/restore, 2=close, 3=STOP, 4=SILENT, 5=three dots, 6=CHAT, 7=HAAN, 8=NAHI

    private var t = 0f                     // 0 = hidden, 1 = fully shown
    private var entering = false           // true only while the card opens (light burst is not played when closing)
    private var anim: ValueAnimator? = null
    private var ticker: ValueAnimator? = null

    private val cyan = 0xFF27E1FF.toInt()
    private val blue = 0xFF2F7BFF.toInt()
    private val violet = 0xFFA35CFF.toInt()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.6f * d }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.4f * d; strokeCap = Paint.Cap.ROUND }
    private val orbFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textSize = 18f * d; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    private val sub = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF9FB4D6.toInt(); textSize = 12.5f * d }
    private val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFEAF2FF.toInt(); textSize = 14.5f * d }
    private val hint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF9FB4D6.toInt(); textSize = 13f * d }
    private val controlFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x88304A8D.toInt() }
    private val controlStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.2f * d; color = 0xAA8CEBFF.toInt() }
    private val controlLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.8f * d; strokeCap = Paint.Cap.ROUND; color = 0xFFEAF7FF.toInt() }
    private val sweep = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shock = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()
    private val oval = RectF()
    private var fillShader: LinearGradient? = null
    private var borderShader: LinearGradient? = null
    private var barShader: LinearGradient? = null

    private fun replyOn(): Boolean = reply.isNotBlank()
    private fun pillH(): Float = (54f + (if (replyOn()) 38f else 0f) + (if (askMode) 54f else 0f)) * d
    private fun menuH(): Float = (2 * 40f + 3 * 8f) * d
    private fun contentH(): Int = (2 * margin + pillH() + (if (menuOpen) 8f * d + menuH() else 0f)).toInt()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(if (minimized) miniViewW else fullViewW, if (minimized) miniViewH else contentH())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fillShader = null; borderShader = null; barShader = null
    }

    fun setAsk(on: Boolean) {
        if (askMode == on) return
        askMode = on
        fillShader = null
        requestLayout()
        invalidate()
    }

    private fun toggleMenu() { menuOpen = !menuOpen; fillShader = null; requestLayout(); invalidate() }
    private fun closeMenu() { if (menuOpen) { menuOpen = false; fillShader = null; requestLayout(); invalidate() } }

    /** One place decides where every control is, so the drawn rectangles and the touch areas can never drift apart. */
    private fun layoutRects(w: Float) {
        val left = margin
        val top = margin
        val right = w - margin
        val rowCy = top + 27f * d
        btnDots.set(right - 8f * d - 32f * d, rowCy - 16f * d, right - 8f * d, rowCy + 16f * d)
        btnStop.set(btnDots.left - 6f * d - 58f * d, rowCy - 15f * d, btnDots.left - 6f * d, rowCy + 15f * d)
        val pillBottom = top + pillH()
        val bh = 44f * d   // 44dp touch target
        val gap = 10f * d
        val pad = 14f * d
        val bw2 = (right - left - 2f * pad - gap) / 2f
        btnYes.set(left + pad, pillBottom - 8f * d - bh, left + pad + bw2, pillBottom - 8f * d)
        btnNo.set(btnYes.right + gap, btnYes.top, right - pad, btnYes.bottom)
        val my0 = pillBottom + 8f * d
        val cw = (right - left - 24f * d) / 2f
        mSilent.set(left + 8f * d, my0 + 8f * d, left + 8f * d + cw, my0 + 8f * d + 40f * d)
        mChat.set(mSilent.right + 8f * d, mSilent.top, mSilent.right + 8f * d + cw, mSilent.bottom)
        mMin.set(mSilent.left, mSilent.bottom + 8f * d, mSilent.right, mSilent.bottom + 8f * d + 40f * d)
        mClose.set(mChat.left, mMin.top, mChat.right, mMin.bottom)
    }

    /** 3 STOP, 4 SILENT, 5 three dots, 6 CHAT, 1 minimise, 2 close, 7 HAAN, 8 NAHI, 0 = nothing. */
    private fun hitControl(x: Float, y: Float): Int {
        layoutRects(width.toFloat())
        fun inside(r: RectF, px: Float, py: Float) = x >= r.left - px && x <= r.right + px && y >= r.top - py && y <= r.bottom + py
        if (askMode) {
            if (inside(btnYes, 0f, 0f)) return 7
            if (inside(btnNo, 0f, 0f)) return 8
        }
        if (menuOpen) {
            if (inside(mSilent, 0f, 0f)) return 4
            if (inside(mChat, 0f, 0f)) return 6
            if (inside(mMin, 0f, 0f)) return 1
            if (inside(mClose, 0f, 0f)) return 2
        }
        if (inside(btnStop, 4f * d, 7f * d)) return 3
        if (inside(btnDots, 6f * d, 6f * d)) return 5
        return 0
    }

    fun changeState(s: String) { state = s; invalidate() }

    /** Collapse to a small animated NOVA chip; tapping the chip restores the full card. */
    fun toggleMinimized() {
        minimized = !minimized
        requestLayout()
        modeAnimator?.cancel()
        val target = if (minimized) 1f else 0f
        val a = ValueAnimator.ofFloat(modeProgress, target)
        a.duration = 360L
        a.interpolator = DecelerateInterpolator(1.5f)
        a.addUpdateListener { modeProgress = it.animatedValue as Float; invalidate() }
        modeAnimator = a
        a.start()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                val x = event.x
                val y = event.y
                pressedControl = if (minimized) {
                    val closeCx = width - margin - 17f * d
                    if (x >= closeCx - 13f * d && y in (height / 2f - 16f * d)..(height / 2f + 16f * d)) 2 else 1
                } else hitControl(x, y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                val moved = kotlin.math.abs(event.x - downX) > 18f * d || kotlin.math.abs(event.y - downY) > 18f * d
                val pressed = pressedControl
                pressedControl = 0
                invalidate()
                if (moved) return true
                val x = event.x
                val y = event.y
                if (minimized) {
                    val right = width - margin
                    val closeCx = right - 17f * d
                    if (x >= closeCx - 13f * d && y in (height / 2f - 16f * d)..(height / 2f + 16f * d)) onDismissTap?.invoke()
                    else onMinimizeTap?.invoke()
                    return true
                }
                val id = hitControl(x, y)
                if (id != 0 && id == pressed) {
                    when (id) {
                        3 -> onStop?.invoke()
                        4 -> { onSilentToggle?.invoke()?.let { silentOn = it; invalidate() } }
                        5 -> toggleMenu()
                        6 -> { closeMenu(); onChatTap?.invoke() }
                        1 -> { closeMenu(); onMinimizeTap?.invoke() }
                        2 -> { closeMenu(); onDismissTap?.invoke() }
                        7 -> onAnswerTap?.invoke(true)
                        8 -> onAnswerTap?.invoke(false)
                    }
                } else if (id == 0) {
                    if (menuOpen) closeMenu()
                    else if (!askMode) onDismissTap?.invoke()   // while NOVA waits for HAAN / NAHI a stray tap must not hide the question
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> { pressedControl = 0; invalidate(); return true }
        }
        return true
    }

    fun assemble() { entering = true; run(0f, 1f, 560L, null) }
    fun disassemble(end: () -> Unit) { entering = false; run(t, 0f, 240L, end) }
    fun cancelAnim() {
        anim?.cancel(); anim = null
        ticker?.cancel(); ticker = null
        modeAnimator?.cancel(); modeAnimator = null
    }

    private fun run(from: Float, to: Float, ms: Long, end: (() -> Unit)?) {
        anim?.cancel()
        val a = ValueAnimator.ofFloat(from, to)
        a.duration = ms
        a.interpolator = LinearInterpolator()
        a.addUpdateListener { t = it.animatedValue as Float; invalidate() }
        a.addListener(object : android.animation.AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(animation: android.animation.Animator) { cancelled = true }
            override fun onAnimationEnd(animation: android.animation.Animator) {
                if (cancelled) return          // a newer animation replaced this one
                if (end != null) end()
                else startTicker()
            }
        })
        anim = a
        a.start()
    }

    /** Keeps the orb, waveform and glow moving while the card is shown. */
    private fun startTicker() {
        ticker?.cancel()
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = 1000L
        a.repeatCount = ValueAnimator.INFINITE
        a.interpolator = LinearInterpolator()
        a.addUpdateListener { invalidate() }
        ticker = a
        a.start()
    }

    override fun onDetachedFromWindow() {
        anim?.cancel(); anim = null
        ticker?.cancel(); ticker = null
        modeAnimator?.cancel(); modeAnimator = null
        super.onDetachedFromWindow()
    }

    private fun stateColor(): Int = when (state) {
        "think" -> violet
        "listen" -> cyan
        else -> blue
    }

    override fun onDraw(c: Canvas) {
        val e = HudMath.popEase(t)
        if (e <= 0f) return
        val now = SystemClock.uptimeMillis()
        smooth += (level - smooth) * 0.3f
        val w = width.toFloat().takeIf { it > 0f } ?: fullViewW.toFloat()
        val h = height.toFloat().takeIf { it > 0f } ?: fullViewH.toFloat()
        if (minimized) {
            drawMinimized(c, w, h, now, e)
            return
        }
        val left = margin
        val top = margin
        val right = w - margin
        layoutRects(w)
        val pillBottom = top + pillH()
        val radius = 22f * d

        val sc = 0.78f + 0.22f * HudMath.springEase(t)
        val expand = 0.92f + 0.08f * (1f - modeProgress.coerceIn(0f, 1f))
        val sv = c.save()
        c.translate(0f, -(1f - e) * 30f * d)       // slides down from above while it springs open
        c.scale(sc * expand, sc * expand, w / 2f, top + (pillBottom - top) / 2f)
        val layer = c.saveLayerAlpha(0f, 0f, w, h, (255 * e).toInt().coerceIn(0, 255))

        // glass pill
        val fs = fillShader ?: LinearGradient(0f, top, 0f, pillBottom,
            intArrayOf(0xF2101C44.toInt(), 0xF20A1030.toInt(), 0xF2160C38.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        ).also { fillShader = it }
        fill.shader = fs
        rect.set(left, top, right, pillBottom)
        c.drawRoundRect(rect, radius, radius, fill)

        // glowing gradient border: three soft outer strokes + one crisp line
        val gl = HudMath.glowLevel(now, state)
        val bs = borderShader ?: LinearGradient(left, top, right, pillBottom,
            intArrayOf(cyan, blue, violet), null, Shader.TileMode.CLAMP
        ).also { borderShader = it }
        border.shader = bs
        for (k in 3 downTo 1) {
            border.strokeWidth = (1.5f + k * 2.6f) * d
            border.alpha = (28 * gl * (4 - k)).toInt().coerceIn(0, 255)
            c.drawRoundRect(rect, radius, radius, border)
        }
        border.strokeWidth = 1.5f * d
        border.alpha = (150 + 100 * gl).toInt().coerceIn(0, 255)
        c.drawRoundRect(rect, radius, radius, border)

        // small orb
        val r = 16f * d
        val ocx = left + 14f * d + r
        val ocy = top + 27f * d
        val sColor = stateColor()
        halo.shader = RadialGradient(ocx, ocy, r * 2.0f, intArrayOf((sColor and 0x00FFFFFF) or 0x66000000, (sColor and 0x00FFFFFF)), null, Shader.TileMode.CLAMP)
        c.drawCircle(ocx, ocy, r * 2.0f, halo)
        if (state == "listen") {
            for (k in 0 until 3) {
                val p = ((now % 1800L) / 1800f + k / 3f) % 1f
                val ra = (190 * HudMath.rippleAlpha(p)).toInt().coerceIn(0, 255)
                ring.color = (sColor and 0x00FFFFFF) or (ra shl 24)
                c.drawCircle(ocx, ocy, r * (0.9f + (0.7f + 0.7f * smooth) * p), ring)
            }
        }
        orbFill.shader = RadialGradient(ocx - r * 0.25f, ocy - r * 0.3f, r,
            intArrayOf(0xFF1B2C6B.toInt(), 0xFF070D27.toInt()), null, Shader.TileMode.CLAMP)
        c.drawCircle(ocx, ocy, r * 0.82f, orbFill)
        val spin = (now % (if (state == "think") 900L else 2600L)) / (if (state == "think") 900f else 2600f) * 360f
        oval.set(ocx - r, ocy - r, ocx + r, ocy + r)
        arc.color = cyan
        c.drawArc(oval, spin, 110f, false, arc)
        arc.color = violet
        c.drawArc(oval, -spin * 0.7f + 180f, 110f, false, arc)
        if (state == "think") {
            for (k in 0 until 3) {
                val a = Math.toRadians((spin * 1.3f + k * 120f).toDouble())
                dot.color = HudMath.mix(cyan, violet, k / 2f)
                c.drawCircle(ocx + (Math.cos(a) * r * 0.42f).toFloat(), ocy + (Math.sin(a) * r * 0.42f).toFloat(), 2.2f * d, dot)
            }
        } else {
            for (k in 0 until 5) {
                val v = HudMath.barHeight(k * 5, HudMath.BARS, smooth, now, state)
                val bh = (3f + v * 11f) * d
                bar.shader = null
                bar.color = HudMath.mix(cyan, violet, k / 4f)
                val bx = ocx + (k - 2) * 4.2f * d
                rect.set(bx - 1.2f * d, ocy - bh / 2f, bx + 1.2f * d, ocy + bh / 2f)
                c.drawRoundRect(rect, 1.2f * d, 1.2f * d, bar)
            }
        }

        // status + live text
        title.textSize = 14.5f * d
        title.color = 0xFFFFFFFF.toInt()
        sub.textSize = 11f * d
        sub.color = 0xFF9FB4D6.toInt()
        val tx = ocx + r + 12f * d
        val textW = btnStop.left - 10f * d - tx
        val head = when (state) {
            "listen" -> "Listening\u2026"
            "think" -> "Thinking\u2026"
            else -> "NOVA"
        }
        c.drawText(TextUtils.ellipsize(head, title, textW, TextUtils.TruncateAt.END).toString(), tx, ocy - 3f * d, title)
        val line = heard.removePrefix("\uD83C\uDFA4").trim().ifBlank {
            when (state) { "listen" -> "Speak now"; "think" -> "Working on your request"; else -> "More than an assistant" }
        }
        c.drawText(TextUtils.ellipsize(line, sub, textW, TextUtils.TruncateAt.END).toString(), tx, ocy + 13f * d, sub)

        // STOP (always visible) and the three dots
        val stopSaved = c.save()
        if (pressedControl == 3) c.scale(0.92f, 0.92f, btnStop.centerX(), btnStop.centerY())
        btnPaint.style = Paint.Style.FILL
        btnPaint.color = 0xFFB52F48.toInt()
        c.drawRoundRect(btnStop, 15f * d, 15f * d, btnPaint)
        btnText.textSize = 11f * d
        c.drawText("STOP", btnStop.centerX(), btnStop.centerY() - (btnText.descent() + btnText.ascent()) / 2f, btnText)
        btnText.textSize = 14f * d
        c.restoreToCount(stopSaved)
        val dotsSaved = c.save()
        if (pressedControl == 5) c.scale(0.9f, 0.9f, btnDots.centerX(), btnDots.centerY())
        controlFill.color = if (menuOpen || pressedControl == 5) 0xCC2873B8.toInt() else 0x88304A8D.toInt()
        controlFill.alpha = 230
        c.drawRoundRect(btnDots, 11f * d, 11f * d, controlFill)
        controlStroke.alpha = 220
        controlStroke.strokeWidth = 1.2f * d
        c.drawRoundRect(btnDots, 11f * d, 11f * d, controlStroke)
        dot.shader = null
        dot.color = 0xFFEAF7FF.toInt()
        for (k in -1..1) c.drawCircle(btnDots.centerX(), btnDots.centerY() + k * 5.5f * d, 1.9f * d, dot)
        c.restoreToCount(dotsSaved)

        // reply (a second row only while there is something to read)
        if (replyOn()) {
            body.textSize = 12.5f * d
            val lay = build(reply, body, (right - left - 28f * d).toInt(), 2)
            c.save(); c.translate(left + 14f * d, top + 52f * d); lay.draw(c); c.restore()
        }

        // HAAN / NAHI (only while NOVA waits for a yes/no)
        if (askMode) {
            btnPaint.style = Paint.Style.FILL
            btnPaint.color = 0xFF16B58A.toInt()
            c.drawRoundRect(btnYes, 12f * d, 12f * d, btnPaint)
            btnPaint.color = 0xFF3A4B7A.toInt()
            c.drawRoundRect(btnNo, 12f * d, 12f * d, btnPaint)
            btnText.textSize = 16f * d
            val ty = btnYes.centerY() - (btnText.descent() + btnText.ascent()) / 2f
            c.drawText("HAAN", btnYes.centerX(), ty, btnText)
            c.drawText("NAHI", btnNo.centerX(), ty, btnText)
            btnText.textSize = 14f * d
        }

        // three-dots menu: every extra feature lives here
        if (menuOpen) {
            val card = RectF(left, pillBottom + 8f * d, right, pillBottom + 8f * d + menuH())
            fill.shader = null
            fill.color = 0xF20A1236.toInt()
            c.drawRoundRect(card, 18f * d, 18f * d, fill)
            border.strokeWidth = 1.2f * d
            border.alpha = 200
            c.drawRoundRect(card, 18f * d, 18f * d, border)
            drawChip(c, mSilent, if (silentOn) "SILENT ON" else "SILENT OFF", if (silentOn) 0xFF187B70.toInt() else 0xFF304A8D.toInt(), 4)
            drawChip(c, mChat, "CHAT", 0xFF2F5FD0.toInt(), 6)
            drawChip(c, mMin, "CHHOTA KARO", 0xFF304A8D.toInt(), 1)
            drawChip(c, mClose, "BAND KARO", 0xFF5A2A56.toInt(), 2)
        }

        // opening light: border flash + a light band sweeping across the glass + one shock ring (only while opening)
        if (entering && t < 1f) {
            val fl = HudMath.entryFlash(t)
            rect.set(left, top, right, pillBottom)
            border.strokeWidth = 3.2f * d
            border.alpha = (255 * fl).toInt().coerceIn(0, 255)
            c.drawRoundRect(rect, radius, radius, border)
            val sx = left + (right - left) * (-0.25f + 1.5f * t)
            sweep.shader = LinearGradient(sx - 70f * d, top, sx + 70f * d, pillBottom,
                intArrayOf(0x00FFFFFF, 0x66CFF4FF, 0x00FFFFFF), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            c.drawRoundRect(rect, radius, radius, sweep)
            sweep.shader = null
            val g = margin * 0.9f * t
            rect.set(left - g, top - g, right + g, pillBottom + g)
            shock.color = (cyan and 0x00FFFFFF) or ((190 * (1f - t)).toInt().coerceIn(0, 255) shl 24)
            shock.strokeWidth = 1.6f * d
            c.drawRoundRect(rect, radius + g, radius + g, shock)
        }
        c.restoreToCount(layer)
        c.restoreToCount(sv)
    }

    private fun drawChip(c: Canvas, r: RectF, label: String, color: Int, id: Int) {
        val saved = c.save()
        if (pressedControl == id) c.scale(0.94f, 0.94f, r.centerX(), r.centerY())
        btnPaint.style = Paint.Style.FILL
        btnPaint.color = color
        c.drawRoundRect(r, 12f * d, 12f * d, btnPaint)
        controlStroke.alpha = 200
        controlStroke.strokeWidth = 1.2f * d
        c.drawRoundRect(r, 12f * d, 12f * d, controlStroke)
        btnText.textSize = 12f * d
        c.drawText(label, r.centerX(), r.centerY() - (btnText.descent() + btnText.ascent()) / 2f, btnText)
        btnText.textSize = 14f * d
        c.restoreToCount(saved)
    }

    private fun padForControls(): Float = 27f * d

    private fun drawControl(c: Canvas, cx: Float, cy: Float, close: Boolean) {
        val pressed = pressedControl == (if (close) 2 else 1)
        val rr = 8.5f * d
        val box = RectF(cx - rr, cy - rr, cx + rr, cy + rr)
        val saved = c.save()
        if (pressed) c.scale(0.86f, 0.86f, cx, cy)
        controlFill.alpha = if (pressed) 255 else 220
        controlFill.color = if (pressed) 0xCC2873B8.toInt() else 0x88304A8D.toInt()
        c.drawRoundRect(box, 5f * d, 5f * d, controlFill)
        controlStroke.alpha = if (pressed) 255 else 220
        controlStroke.strokeWidth = (if (pressed) 1.8f else 1.2f) * d
        c.drawRoundRect(box, 5f * d, 5f * d, controlStroke)
        if (close) {
            c.drawLine(cx - 3f * d, cy - 3f * d, cx + 3f * d, cy + 3f * d, controlLine)
            c.drawLine(cx + 3f * d, cy - 3f * d, cx - 3f * d, cy + 3f * d, controlLine)
        } else {
            c.drawLine(cx - 3.5f * d, cy + 1f * d, cx + 3.5f * d, cy + 1f * d, controlLine)
        }
        c.restoreToCount(saved)
    }

    private fun drawMinimized(c: Canvas, w: Float, h: Float, now: Long, alpha: Float) {
        val left = margin
        val top = margin
        val right = w - margin
        val bottom = h - margin
        val cx = left + 22f * d
        val cy = (top + bottom) / 2f
        val r = 15f * d
        val pulse = 0.72f + 0.28f * (0.5f + 0.5f * sin(now / 260f))
        val sc = 0.88f + 0.12f * modeProgress.coerceIn(0f, 1f)
        val save = c.save()
        val layer = c.saveLayerAlpha(0f, 0f, w, h, (255f * alpha).toInt().coerceIn(0, 255))
        c.scale(sc, sc, w / 2f, h / 2f)
        val box = RectF(left, top, right, bottom)
        fill.shader = null
        fill.color = 0xF20A1236.toInt()
        c.drawRoundRect(box, (bottom - top) / 2f, (bottom - top) / 2f, fill)
        controlStroke.alpha = (150f + 90f * pulse).toInt().coerceIn(0, 255)
        controlStroke.strokeWidth = 1.2f * d
        border.shader = borderShader ?: LinearGradient(left, top, right, bottom,
            intArrayOf(cyan, blue, violet), null, Shader.TileMode.CLAMP).also { borderShader = it }
        for (k in 2 downTo 1) {
            border.strokeWidth = (1.4f + k * 2.0f) * d
            border.alpha = (20f * pulse * (3 - k)).toInt().coerceIn(0, 255)
            c.drawRoundRect(box, (bottom - top) / 2f, (bottom - top) / 2f, border)
        }
        border.strokeWidth = 1.3f * d
        border.alpha = (150f + 85f * pulse).toInt().coerceIn(0, 255)
        c.drawRoundRect(box, (bottom - top) / 2f, (bottom - top) / 2f, border)
        val stateCol = stateColor()
        halo.shader = RadialGradient(cx, cy, r * 1.8f, intArrayOf((stateCol and 0x00FFFFFF) or 0x88000000.toInt(), stateCol), null, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r * 1.65f * pulse, halo)
        ring.color = (stateCol and 0x00FFFFFF) or 0xCC000000.toInt()
        ring.strokeWidth = 1.5f * d
        c.drawCircle(cx, cy, r + 2f * d + (pulse - 0.72f) * 8f * d, ring)
        orbFill.shader = RadialGradient(cx - r * 0.25f, cy - r * 0.25f, r,
            intArrayOf(0xFF355CC8.toInt(), 0xFF070D27.toInt()), null, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, orbFill)
        val spin = (now % 1800L) / 1800f * 360f
        oval.set(cx - r, cy - r, cx + r, cy + r)
        arc.color = cyan
        arc.strokeWidth = 1.8f * d
        c.drawArc(oval, spin, 135f, false, arc)
        arc.color = violet
        c.drawArc(oval, -spin * 0.75f + 180f, 100f, false, arc)
        title.textSize = 12.5f * d
        title.color = 0xFFF4FAFF.toInt()
        val tx = cx + r + 8f * d
        c.drawText("NOVA", tx, cy - 1f * d, title)
        sub.textSize = 8.5f * d
        sub.color = 0xFFA9C4E8.toInt()
        val stateLabel = when (state) { "listen" -> "LISTENING · TAP TO EXPAND"; "think" -> "THINKING · TAP TO EXPAND"; else -> "TAP TO EXPAND" }
        c.drawText(stateLabel, tx, cy + 12f * d, sub)
        drawControl(c, right - 17f * d, cy, true)
        c.restoreToCount(layer)
        c.restoreToCount(save)
    }

    private fun build(text: String, paint: TextPaint, width: Int, maxLines: Int): StaticLayout {
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setMaxLines(maxLines)
            .build()
    }
}
