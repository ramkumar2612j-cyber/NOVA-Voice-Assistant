package com.nova.assistant

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
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
    /** v7.122: VOICE chip of the idle popup (the service starts listening, same as the wake word). */
    @Volatile var onIdleVoice: (() -> Unit)? = null
    @Volatile private var askOn = false
    private var chat: android.widget.LinearLayout? = null
    private var shownAt = 0L
    private var lastReply = ""

    companion object {
        fun canShow(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)
        const val MIN_VISIBLE_MS = 2500L

        /** v7.121: the round Ultron head (assets/ultron_head.jpg) at [px] pixels, or null if the file cannot be read. */
        fun avatarBitmap(ctx: Context, px: Int): Bitmap? {
            return try {
                val src = ctx.assets.open("ultron_head.jpg").use { BitmapFactory.decodeStream(it) } ?: return null
                val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                p.shader = BitmapShader(Bitmap.createScaledBitmap(src, px, px, true), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                Canvas(out).drawCircle(px / 2f, px / 2f, px / 2f, p)
                out
            } catch (e: Throwable) { null }
        }
    }

    val isShown: Boolean get() = view != null

    /** v7.122: idle popup "How can I help you today?" with VOICE / MESSAGE / MORE. */
    fun showIdle() {
        show("idle")
        view?.heard = ""
        view?.reply = ""
        view?.invalidate()
    }
    fun isIdle(): Boolean = view?.state == "idle"
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
        nv.onIdleVoice = { nv.changeState("listen"); onIdleVoice?.invoke() }
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
        } catch (e: Throwable) { ErrLog.addOnce("quiet:NovaHud", e.toString()) }
        try { wm.removeView(v) } catch (e: Exception) { }
    }

    private fun openChat() {
        if (!canShow(ctx)) return
        if (chat != null) { closeChat(); return }
        val d = ctx.resources.displayMetrics.density
        val boxW = minOf(ctx.resources.displayMetrics.widthPixels - 24 * d, 332 * d).toInt()
        val row = android.widget.LinearLayout(ctx)
        row.orientation = android.widget.LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding((14 * d).toInt(), (6 * d).toInt(), (8 * d).toInt(), (6 * d).toInt())
        val bg = android.graphics.drawable.GradientDrawable()
        bg.setColor(0xF20A1236.toInt())
        bg.colors = intArrayOf(0xF2081A48.toInt(), 0xF2050D2C.toInt(), 0xF2081034.toInt())   // v7.121: same dark neon glass as the Ultron card
        bg.cornerRadius = 18 * d
        bg.setStroke((1.5f * d).toInt(), 0xFF27E1FF.toInt())
        row.background = bg
        val face = avatarBitmap(ctx, (36 * d).toInt())
        if (face != null) {
            val iv = android.widget.ImageView(ctx)
            iv.setImageBitmap(face)
            val ilp = android.widget.LinearLayout.LayoutParams((36 * d).toInt(), (36 * d).toInt())
            ilp.rightMargin = (8 * d).toInt()
            row.addView(iv, ilp)
        }
        val et = android.widget.EditText(ctx)
        et.hint = "Type a message\u2026"
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
        val send = pill("SEND", 0xFF1B6DFF.toInt(), (62 * d).toInt())
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
        lp.y = (270 * d).toInt()
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        try {
            wm.addView(row, lp)
            chat = row
            row.post {
                try {
                    et.requestFocus()
                    val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                    imm?.showSoftInput(et, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                } catch (e: Throwable) { ErrLog.addOnce("quiet:NovaHud", e.toString()) }
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
    private val cardW = (minOf(ctx.resources.displayMetrics.widthPixels - 24 * d, 332 * d)).toInt()
    private val cardH = (214f * d).toInt()
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
    private val btnX = RectF()                      // v7.121: close X in the card corner
    private val btnMain = RectF()                   // v7.121: big SILENT (listen/think) or STOP (speaking) button
    private val btnPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val btnText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textSize = 14f * d; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textAlign = Paint.Align.CENTER }
    var onMinimizeTap: (() -> Unit)? = null
    var onIdleVoice: (() -> Unit)? = null            // v7.122: VOICE chip of the idle popup
    private val iVoice = RectF()
    private val iMsg = RectF()
    private val iMore = RectF()
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
    // v7.121 ULTRON look
    private val cpath = Path()
    private val bpath = Path()
    private val spPath = Path()
    private val iconP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.9f * d; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; color = 0xFFFFFFFF.toInt() }
    private val avatarPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mouthCore = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF000000.toInt() }   // v7.126 Ultron mouth in the popup head
    private val mouthGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF2B3D.toInt() }
    private val avatarMatrix = Matrix()
    private var avatarShader: BitmapShader? = null
    private val avatar: Bitmap? by lazy { try { ctx.assets.open("ultron_head.jpg").use { BitmapFactory.decodeStream(it) } } catch (e: Throwable) { null } }
    private val redC = 0xFFFF2B3D.toInt()
    private var fillShader: LinearGradient? = null
    private var borderShader: LinearGradient? = null
    private var barShader: LinearGradient? = null

    private fun replyOn(): Boolean = reply.isNotBlank()
    private fun pillH(): Float = (204f + (if (replyOn()) 38f else 0f) + (if (askMode) 54f else 0f)) * d
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
        val rowCy = top + 32f * d
        btnX.set(right - 12f * d - 30f * d, rowCy - 15f * d, right - 12f * d, rowCy + 15f * d)
        btnDots.set(btnX.left - 6f * d - 30f * d, rowCy - 15f * d, btnX.left - 6f * d, rowCy + 15f * d)
        val pb = top + pillH()
        val mainBottom = pb - 14f * d - (if (askMode) 54f * d else 0f)
        val mainTop = mainBottom - 44f * d
        val cxm = (left + right) / 2f
        if (state == "idle") {
            val ipad = 14f * d
            val igap = 8f * d
            val iw3 = (right - left - 2f * ipad - 2f * igap) / 3f
            iVoice.set(left + ipad, mainTop, left + ipad + iw3, mainBottom)
            iMsg.set(iVoice.right + igap, mainTop, iVoice.right + igap + iw3, mainBottom)
            iMore.set(iMsg.right + igap, mainTop, iMsg.right + igap + iw3, mainBottom)
            btnMain.setEmpty()
            btnStop.setEmpty()
        } else if (state == "listen" || state == "think") {
            val gw = 176f * d + 10f * d + 44f * d
            val gl = cxm - gw / 2f
            btnMain.set(gl, mainTop, gl + 176f * d, mainBottom)
            btnStop.set(btnMain.right + 10f * d, mainTop, btnMain.right + 10f * d + 44f * d, mainBottom)
        } else {
            btnMain.set(cxm - 100f * d, mainTop, cxm + 100f * d, mainBottom)
            btnStop.setEmpty()
        }
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

    /** 3 STOP, 4 SILENT (menu), 10 SILENT (big button), 9 close X, 5 three dots, 6 CHAT, 1 minimise, 2 close, 7 HAAN, 8 NAHI, 0 = nothing. */
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
        if (inside(btnX, 4f * d, 4f * d)) return 9
        if (inside(btnDots, 4f * d, 4f * d)) return 5
        if (state == "idle") {
            if (inside(iVoice, 0f, 0f)) return 11
            if (inside(iMsg, 0f, 0f)) return 12
            if (inside(iMore, 0f, 0f)) return 13
            return 0
        }
        val quiet = state == "listen" || state == "think"
        if (quiet && inside(btnStop, 2f * d, 2f * d)) return 3
        if (inside(btnMain, 0f, 0f)) return if (quiet) 10 else 3
        return 0
    }

    fun changeState(s: String) { state = s; fillShader = null; invalidate() }

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
                        9 -> onDismissTap?.invoke()
                        11 -> onIdleVoice?.invoke()
                        12 -> { closeMenu(); onChatTap?.invoke() }
                        13 -> toggleMenu()
                        10 -> { onSilentToggle?.invoke()?.let { silentOn = it; invalidate() } }
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
        "think" -> 0xFF35A8FF.toInt()
        "listen", "idle" -> cyan
        else -> redC
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

        val sc = 0.78f + 0.22f * HudMath.springEase(t)
        val expand = 0.92f + 0.08f * (1f - modeProgress.coerceIn(0f, 1f))
        val sv = c.save()
        c.translate(0f, -(1f - e) * 30f * d)       // slides down from above while it springs open
        c.scale(sc * expand, sc * expand, w / 2f, top + (pillBottom - top) / 2f)
        val layer = c.saveLayerAlpha(0f, 0f, w, h, (255 * e).toInt().coerceIn(0, 255))

        // v7.121 ULTRON card: cut-corner glass in the state colour (cyan = listen / think, red = speaking)
        val sColor = stateColor()
        val speaking = state != "listen" && state != "think" && state != "idle"
        val cut = 16f * d
        chamfer(left, top, right, pillBottom, cut)
        val fs = fillShader ?: LinearGradient(0f, top, 0f, pillBottom,
            if (speaking) intArrayOf(0xF2300A16.toInt(), 0xF2180610.toInt(), 0xF2220812.toInt())
            else intArrayOf(0xF2081A48.toInt(), 0xF2050D2C.toInt(), 0xF2081034.toInt()),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        ).also { fillShader = it }
        fill.shader = fs
        c.drawPath(cpath, fill)

        val gl = HudMath.glowLevel(now, state)
        border.shader = null
        border.color = sColor
        for (k in 3 downTo 1) {
            border.strokeWidth = (1.5f + k * 2.6f) * d
            border.alpha = (28 * gl * (4 - k)).toInt().coerceIn(0, 255)
            c.drawPath(cpath, border)
        }
        border.strokeWidth = 1.6f * d
        border.alpha = (170 + 85 * gl).toInt().coerceIn(0, 255)
        c.drawPath(cpath, border)

        // Ultron head (round) with a state-colour ring
        val r = 22f * d
        val ocx = left + 16f * d + r
        val ocy = top + 32f * d
        halo.shader = RadialGradient(ocx, ocy, r * 1.9f, intArrayOf((sColor and 0x00FFFFFF) or 0x66000000, (sColor and 0x00FFFFFF)), null, Shader.TileMode.CLAMP)
        c.drawCircle(ocx, ocy, r * 1.9f, halo)
        val bm = avatar
        if (bm != null && bm.width > 0) {
            val sh = avatarShader ?: BitmapShader(bm, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).also { avatarShader = it }
            val k = (2f * r) / bm.width.toFloat()
            avatarMatrix.reset()
            avatarMatrix.postScale(k, k)
            avatarMatrix.postTranslate(ocx - r, ocy - r)
            sh.setLocalMatrix(avatarMatrix)
            avatarPaint.shader = sh
            c.drawCircle(ocx, ocy, r, avatarPaint)
            if (speaking) {
                // v7.126: the mouth on the round head opens and closes while NOVA talks (mouth slot is at 50% / 74% of ultron_head.jpg)
                val o = MouthMath.open(now)
                val mx = ocx
                val my = ocy + 0.476f * r
                val hw = 0.13f * r
                val hh = (0.03f + 0.11f * o) * r
                oval.set(mx - hw * 1.5f, my - hh * 1.8f - d, mx + hw * 1.5f, my + hh * 1.8f + d)
                mouthGlow.alpha = (90 + 100 * o).toInt().coerceIn(0, 255)
                c.drawOval(oval, mouthGlow)
                oval.set(mx - hw, my - hh, mx + hw, my + hh)
                c.drawOval(oval, mouthCore)
            }
        } else {
            orbFill.shader = RadialGradient(ocx - r * 0.25f, ocy - r * 0.3f, r,
                intArrayOf(0xFF1B2C6B.toInt(), 0xFF070D27.toInt()), null, Shader.TileMode.CLAMP)
            c.drawCircle(ocx, ocy, r * 0.9f, orbFill)
        }
        ring.color = sColor
        ring.strokeWidth = 2f * d
        c.drawCircle(ocx, ocy, r, ring)
        ring.strokeWidth = 1.6f * d

        // texts: NOVA, state title, live line
        val tx = ocx + r + 12f * d
        val textW = btnDots.left - 8f * d - tx
        sub.textSize = 10.5f * d
        sub.color = sColor
        sub.letterSpacing = 0.35f
        c.drawText("NOVA", tx, ocy - 14f * d, sub)
        sub.letterSpacing = 0f
        title.textSize = 17f * d
        title.color = 0xFFFFFFFF.toInt()
        val head = when (state) {
            "listen" -> "I\u2019m listening\u2026"
            "think" -> "Thinking\u2026"
            "idle" -> "Hello"
            else -> "Speaking\u2026"
        }
        c.drawText(TextUtils.ellipsize(head, title, textW, TextUtils.TruncateAt.END).toString(), tx, ocy + 4f * d, title)
        sub.textSize = 12f * d
        sub.color = 0xFFA9BCD9.toInt()
        val line = heard.removePrefix("\uD83C\uDFA4").trim().ifBlank {
            when (state) { "listen" -> "Speak now"; "think" -> "Analyzing your request"; "idle" -> "Ready when you are"; else -> "I\u2019m talking now" }
        }
        c.drawText(TextUtils.ellipsize(line, sub, textW, TextUtils.TruncateAt.END).toString(), tx, ocy + 21f * d, sub)

        // close X and three dots (top right)
        val xSaved = c.save()
        if (pressedControl == 9) c.scale(0.9f, 0.9f, btnX.centerX(), btnX.centerY())
        controlFill.color = 0xCC061230.toInt()
        controlFill.alpha = 230
        c.drawRoundRect(btnX, 9f * d, 9f * d, controlFill)
        controlStroke.color = sColor
        controlStroke.alpha = 230
        controlStroke.strokeWidth = 1.3f * d
        c.drawRoundRect(btnX, 9f * d, 9f * d, controlStroke)
        val xr = 5f * d
        c.drawLine(btnX.centerX() - xr, btnX.centerY() - xr, btnX.centerX() + xr, btnX.centerY() + xr, controlLine)
        c.drawLine(btnX.centerX() + xr, btnX.centerY() - xr, btnX.centerX() - xr, btnX.centerY() + xr, controlLine)
        c.restoreToCount(xSaved)
        val dotsSaved = c.save()
        if (pressedControl == 5) c.scale(0.9f, 0.9f, btnDots.centerX(), btnDots.centerY())
        controlFill.color = if (menuOpen || pressedControl == 5) 0xCC2873B8.toInt() else 0xCC061230.toInt()
        controlFill.alpha = 230
        c.drawRoundRect(btnDots, 9f * d, 9f * d, controlFill)
        c.drawRoundRect(btnDots, 9f * d, 9f * d, controlStroke)
        dot.shader = null
        dot.color = 0xFFEAF7FF.toInt()
        for (k in -1..1) c.drawCircle(btnDots.centerX(), btnDots.centerY() + k * 5.5f * d, 1.9f * d, dot)
        c.restoreToCount(dotsSaved)
        controlStroke.color = 0xAA8CEBFF.toInt()   // back to the colour the minimised chip uses

        // centre visual
        val vcx = (left + right) / 2f
        val vcy = top + 104f * d
        val cr = 30f * d
        if (state == "idle") {
            halo.shader = RadialGradient(vcx, vcy, cr * 2.6f, intArrayOf((sColor and 0x00FFFFFF) or 0x33000000, (sColor and 0x00FFFFFF)), null, Shader.TileMode.CLAMP)
            c.drawCircle(vcx, vcy, cr * 2.6f, halo)
            title.textSize = 19f * d
            title.color = 0xFFFFFFFF.toInt()
            val ilay = build("How can I help you today?", title, (right - left - 48f * d).toInt(), 2)
            val isv = c.save()
            c.translate(left + 24f * d, vcy - ilay.height / 2f)
            ilay.draw(c)
            c.restoreToCount(isv)
        } else if (state == "think") {
            val gr = 22f * d
            iconP.color = 0xFF6EC8FF.toInt()
            iconP.strokeWidth = 1.4f * d
            halo.shader = RadialGradient(vcx, vcy, gr * 2.4f, intArrayOf(0x4435A8FF, 0x0035A8FF), null, Shader.TileMode.CLAMP)
            c.drawCircle(vcx, vcy, gr * 2.4f, halo)
            c.drawCircle(vcx, vcy, gr, iconP)
            oval.set(vcx - gr, vcy - gr * 0.38f, vcx + gr, vcy + gr * 0.38f)
            c.drawOval(oval, iconP)
            val rot = (now % 4000L) / 4000f
            val mwid = gr * abs(cos(rot * 6.2831855f))
            oval.set(vcx - mwid - 1f, vcy - gr, vcx + mwid + 1f, vcy + gr)
            c.drawOval(oval, iconP)
            val os = c.save()
            c.rotate(-14f, vcx, vcy)
            iconP.color = redC
            iconP.strokeWidth = 2f * d
            val orx = gr * 1.9f
            val ory = 11f * d
            oval.set(vcx - orx, vcy - ory, vcx + orx, vcy + ory)
            c.drawOval(oval, iconP)
            val oa = ((now % 1800L) / 1800f) * 6.2831855f
            dot.shader = null
            dot.color = redC
            c.drawCircle(vcx + cos(oa) * orx, vcy + sin(oa) * ory, 3f * d, dot)
            c.restoreToCount(os)
            iconP.color = 0xFFFFFFFF.toInt()
            iconP.strokeWidth = 1.9f * d
        } else {
            halo.shader = RadialGradient(vcx, vcy, cr * 2.2f, intArrayOf((sColor and 0x00FFFFFF) or 0x55000000, (sColor and 0x00FFFFFF)), null, Shader.TileMode.CLAMP)
            c.drawCircle(vcx, vcy, cr * 2.2f, halo)
            for (k in 0 until 2) {
                val p = ((now % 2000L) / 2000f + k * 0.5f) % 1f
                val ra = (170 * HudMath.rippleAlpha(p)).toInt().coerceIn(0, 255)
                ring.color = (sColor and 0x00FFFFFF) or (ra shl 24)
                c.drawCircle(vcx, vcy, cr * (1f + 0.55f * p * (0.6f + smooth)), ring)
            }
            // side waveforms
            for (side in 0..1) for (i in 0 until 11) {
                val v = if (speaking) 0.3f + 0.7f * (0.5f + 0.5f * sin(now / 130f + i * 0.8f + side * 1.7f))
                        else HudMath.barHeight(i * 2, HudMath.BARS, smooth, now, state)
                val bh = (5f + v * 44f * (1f - i * 0.05f)) * d
                val bx = if (side == 0) vcx - cr - 16f * d - i * 6f * d else vcx + cr + 16f * d + i * 6f * d
                bar.shader = null
                bar.color = sColor
                bar.alpha = (255 * (1f - i * 0.045f)).toInt().coerceIn(0, 255)
                rect.set(bx - 1.3f * d, vcy - bh / 2f, bx + 1.3f * d, vcy + bh / 2f)
                c.drawRoundRect(rect, 1.3f * d, 1.3f * d, bar)
            }
            orbFill.shader = RadialGradient(vcx, vcy, cr,
                if (speaking) intArrayOf(0xFF3A0A16.toInt(), 0xFF14040A.toInt()) else intArrayOf(0xFF0B1B45.toInt(), 0xFF050A1E.toInt()),
                null, Shader.TileMode.CLAMP)
            c.drawCircle(vcx, vcy, cr, orbFill)
            ring.color = sColor
            ring.strokeWidth = 2.2f * d
            c.drawCircle(vcx, vcy, cr, ring)
            ring.strokeWidth = 1.2f * d
            ring.alpha = 110
            c.drawCircle(vcx, vcy, cr + 8f * d, ring)
            ring.strokeWidth = 1.6f * d
            iconP.color = 0xFFFFFFFF.toInt()
            if (speaking) {
                for (k in 0 until 5) {
                    val v = 0.35f + 0.65f * (0.5f + 0.5f * sin(now / 110f + k * 1.3f))
                    val hh = (6f + v * 20f) * d * (if (k == 2) 1f else 0.8f)
                    val bx = vcx + (k - 2) * 7f * d
                    bar.color = 0xFFFFFFFF.toInt()
                    bar.alpha = 255
                    rect.set(bx - 1.6f * d, vcy - hh / 2f, bx + 1.6f * d, vcy + hh / 2f)
                    c.drawRoundRect(rect, 1.6f * d, 1.6f * d, bar)
                }
            } else {
                // microphone
                rect.set(vcx - 6f * d, vcy - 15f * d, vcx + 6f * d, vcy + 3f * d)
                c.drawRoundRect(rect, 6f * d, 6f * d, iconP)
                oval.set(vcx - 11f * d, vcy - 10f * d, vcx + 11f * d, vcy + 10f * d)
                c.drawArc(oval, 20f, 140f, false, iconP)
                c.drawLine(vcx, vcy + 10f * d, vcx, vcy + 16f * d, iconP)
                c.drawLine(vcx - 5f * d, vcy + 16f * d, vcx + 5f * d, vcy + 16f * d, iconP)
            }
        }

        // reply text (a row only while there is something to read)
        if (replyOn()) {
            body.textSize = 12.5f * d
            val lay = build(reply, body, (right - left - 32f * d).toInt(), 2)
            c.save(); c.translate(left + 16f * d, top + 148f * d); lay.draw(c); c.restore()
        }

        // big button: SILENT (listen / think) or STOP (speaking); the idle popup has its own three chips instead
        if (state != "idle") {
        val mid = if (speaking) 3 else 10
        val mSaved = c.save()
        if (pressedControl == mid) c.scale(0.95f, 0.95f, btnMain.centerX(), btnMain.centerY())
        hexPath(btnMain, 16f * d)
        val silentGreen = !speaking && silentOn
        btnPaint.style = Paint.Style.FILL
        btnPaint.shader = LinearGradient(0f, btnMain.top, 0f, btnMain.bottom,
            if (speaking) intArrayOf(0xFF5A0A18.toInt(), 0xFF2A0610.toInt()) else intArrayOf(0xFF0F2A66.toInt(), 0xFF081840.toInt()),
            null, Shader.TileMode.CLAMP)
        c.drawPath(bpath, btnPaint)
        btnPaint.shader = null
        border.color = if (silentGreen) 0xFF16B58A.toInt() else sColor
        border.strokeWidth = 1.8f * d
        border.alpha = 255
        c.drawPath(bpath, border)
        val label = if (speaking) "STOP" else if (silentOn) "SILENT ON" else "SILENT"
        btnText.textSize = 14f * d
        btnText.letterSpacing = 0.12f
        val tw = btnText.measureText(label)
        val iw = 18f * d
        val sx = btnMain.centerX() - (iw + 9f * d + tw) / 2f
        val icx = sx + iw / 2f
        val icy = btnMain.centerY()
        if (speaking) {
            btnPaint.color = 0xFFFFFFFF.toInt()
            rect.set(icx - 7f * d, icy - 7f * d, icx + 7f * d, icy + 7f * d)
            c.drawRoundRect(rect, 2f * d, 2f * d, btnPaint)
        } else {
            iconP.color = 0xFFFFFFFF.toInt()
            spPath.reset()
            spPath.moveTo(icx - 8f * d, icy - 3f * d)
            spPath.lineTo(icx - 4f * d, icy - 3f * d)
            spPath.lineTo(icx + 1f * d, icy - 7f * d)
            spPath.lineTo(icx + 1f * d, icy + 7f * d)
            spPath.lineTo(icx - 4f * d, icy + 3f * d)
            spPath.lineTo(icx - 8f * d, icy + 3f * d)
            spPath.close()
            c.drawPath(spPath, iconP)
            c.drawLine(icx - 9f * d, icy - 8f * d, icx + 9f * d, icy + 8f * d, iconP)
        }
        c.drawText(label, sx + iw + 9f * d + tw / 2f, icy - (btnText.descent() + btnText.ascent()) / 2f, btnText)
        btnText.letterSpacing = 0f
        c.restoreToCount(mSaved)

        // small red STOP square (listen / think): work can always be cancelled
        if (!speaking) {
            val stSaved = c.save()
            if (pressedControl == 3) c.scale(0.92f, 0.92f, btnStop.centerX(), btnStop.centerY())
            btnPaint.style = Paint.Style.FILL
            btnPaint.color = 0xFFB52F48.toInt()
            c.drawRoundRect(btnStop, 12f * d, 12f * d, btnPaint)
            btnPaint.color = 0xFFFFFFFF.toInt()
            rect.set(btnStop.centerX() - 6f * d, btnStop.centerY() - 6f * d, btnStop.centerX() + 6f * d, btnStop.centerY() + 6f * d)
            c.drawRoundRect(rect, 1.5f * d, 1.5f * d, btnPaint)
            c.restoreToCount(stSaved)
        }

        }
        if (state == "idle") drawIdleChips(c, sColor)

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
            chamfer(left, top, right, pillBottom, cut)
            border.color = sColor
            border.strokeWidth = 3.2f * d
            border.alpha = (255 * fl).toInt().coerceIn(0, 255)
            c.drawPath(cpath, border)
            val sx = left + (right - left) * (-0.25f + 1.5f * t)
            sweep.shader = LinearGradient(sx - 70f * d, top, sx + 70f * d, pillBottom,
                intArrayOf(0x00FFFFFF, 0x66CFF4FF, 0x00FFFFFF), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            c.drawPath(cpath, sweep)
            sweep.shader = null
            val g = margin * 0.9f * t
            chamfer(left - g, top - g, right + g, pillBottom + g, cut + g)
            shock.color = (sColor and 0x00FFFFFF) or ((190 * (1f - t)).toInt().coerceIn(0, 255) shl 24)
            shock.strokeWidth = 1.6f * d
            c.drawPath(cpath, shock)
        }
        c.restoreToCount(layer)
        c.restoreToCount(sv)
    }

    /** v7.122: VOICE / MESSAGE / MORE chips of the idle popup. */
    private fun drawIdleChips(c: Canvas, sColor: Int) {
        val labels = arrayOf("VOICE", "MESSAGE", "MORE")
        val rects = arrayOf(iVoice, iMsg, iMore)
        val ids = intArrayOf(11, 12, 13)
        for (n in 0 until 3) {
            val r = rects[n]
            val sv = c.save()
            if (pressedControl == ids[n]) c.scale(0.95f, 0.95f, r.centerX(), r.centerY())
            hexPath(r, 10f * d)
            btnPaint.style = Paint.Style.FILL
            btnPaint.shader = LinearGradient(0f, r.top, 0f, r.bottom, intArrayOf(0xFF0F2A66.toInt(), 0xFF081840.toInt()), null, Shader.TileMode.CLAMP)
            c.drawPath(bpath, btnPaint)
            btnPaint.shader = null
            border.color = sColor
            border.strokeWidth = 1.6f * d
            border.alpha = 255
            c.drawPath(bpath, border)
            btnText.textSize = 12f * d
            btnText.letterSpacing = 0.08f
            c.drawText(labels[n], r.centerX(), r.centerY() - (btnText.descent() + btnText.ascent()) / 2f, btnText)
            btnText.letterSpacing = 0f
            btnText.textSize = 14f * d
            c.restoreToCount(sv)
        }
    }

    /** v7.121: cut-corner (octagon) outline of a card into [cpath]. */
    private fun chamfer(l: Float, t0: Float, r: Float, b: Float, cut: Float) {
        cpath.reset()
        cpath.moveTo(l + cut, t0)
        cpath.lineTo(r - cut, t0)
        cpath.lineTo(r, t0 + cut)
        cpath.lineTo(r, b - cut)
        cpath.lineTo(r - cut, b)
        cpath.lineTo(l + cut, b)
        cpath.lineTo(l, b - cut)
        cpath.lineTo(l, t0 + cut)
        cpath.close()
    }

    /** v7.121: pointed-side button outline of [r] into [bpath]. */
    private fun hexPath(r: RectF, tip: Float) {
        bpath.reset()
        bpath.moveTo(r.left + tip, r.top)
        bpath.lineTo(r.right - tip, r.top)
        bpath.lineTo(r.right, r.centerY())
        bpath.lineTo(r.right - tip, r.bottom)
        bpath.lineTo(r.left + tip, r.bottom)
        bpath.lineTo(r.left, r.centerY())
        bpath.close()
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
