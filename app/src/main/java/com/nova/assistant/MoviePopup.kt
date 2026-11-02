package com.nova.assistant

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView

/**
 * v7.76 - small popup browser (a floating overlay window over the video app) that shows the web page of the movie / series NOVA found.
 * Main thread only. Needs "display over other apps" (same permission as the laser overlay); without it the caller opens the normal browser instead.
 * Hardening: only https pages (ScreenVideoRules.isSafeUrl), no file / content access, no JS bridge, one window, third-party redirects to other schemes are blocked.
 */
class MoviePopup(private val ctx: Context) {
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: LinearLayout? = null
    private var web: WebView? = null

    companion object {
        fun canShow(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)
    }

    val isShown: Boolean get() = root != null

    /** false = nothing shown (no permission, unsafe url, Android refused): the caller falls back to the normal browser. */
    fun show(url: String, title: String, closeLabel: String, browserLabel: String, note: String, chips: List<ScreenVideoRules.Chip>, onOpenBrowser: (String) -> Unit): Boolean {
        if (!canShow(ctx) || !ScreenVideoRules.isSafeUrl(url)) return false
        destroy()
        try {
            val d = ctx.resources.displayMetrics.density
            val box = LinearLayout(ctx)
            box.orientation = LinearLayout.VERTICAL
            val bg = GradientDrawable()
            bg.setColor(Color.rgb(12, 16, 20)); bg.cornerRadius = 16f * d
            bg.setStroke((1.5f * d).toInt(), Color.rgb(57, 255, 120))
            box.background = bg
            box.setPadding((6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt())

            val bar = LinearLayout(ctx)
            bar.orientation = LinearLayout.HORIZONTAL
            bar.gravity = Gravity.CENTER_VERTICAL
            val tv = TextView(ctx)
            tv.text = "🎬 " + title.take(60)
            tv.setTextColor(Color.rgb(57, 255, 120)); tv.textSize = 14f; tv.maxLines = 1
            tv.ellipsize = android.text.TextUtils.TruncateAt.END
            bar.addView(tv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            fun btn(label: String, fill: Int, tap: () -> Unit): TextView {
                val b = TextView(ctx)
                b.text = label; b.setTextColor(Color.WHITE); b.textSize = 13f
                b.setPadding((12 * d).toInt(), (7 * d).toInt(), (12 * d).toInt(), (7 * d).toInt())
                val g = GradientDrawable(); g.setColor(fill); g.cornerRadius = 10f * d
                b.background = g
                b.setOnClickListener { tap() }
                return b
            }
            val lpB = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lpB.leftMargin = (6 * d).toInt()
            bar.addView(btn(browserLabel, Color.rgb(40, 90, 170)) { try { onOpenBrowser(web?.url ?: url) } catch (e: Throwable) { ErrLog.addOnce("quiet:MoviePopup", e.toString()) } ; destroy() }, lpB)
            bar.addView(btn(closeLabel, Color.rgb(190, 30, 40)) { destroy() }, lpB)
            box.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

            if (chips.isNotEmpty()) {   // suggestions: each chip loads another page for the same title inside this popup
                val hs = android.widget.HorizontalScrollView(ctx)
                hs.isHorizontalScrollBarEnabled = false
                val row = LinearLayout(ctx)
                row.orientation = LinearLayout.HORIZONTAL
                for (c in chips) {
                    val ch = TextView(ctx)
                    ch.text = c.label; ch.setTextColor(Color.rgb(57, 255, 120)); ch.textSize = 12.5f
                    ch.setPadding((12 * d).toInt(), (6 * d).toInt(), (12 * d).toInt(), (6 * d).toInt())
                    val g = GradientDrawable(); g.setColor(Color.argb(40, 57, 255, 120)); g.cornerRadius = 16f * d
                    g.setStroke((1 * d).toInt(), Color.argb(140, 57, 255, 120))
                    ch.background = g
                    ch.setOnClickListener { if (ScreenVideoRules.isSafeUrl(c.url)) web?.loadUrl(c.url) }
                    val lc = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    lc.rightMargin = (6 * d).toInt()
                    row.addView(ch, lc)
                }
                hs.addView(row)
                val lh = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lh.topMargin = (6 * d).toInt()
                box.addView(hs, lh)
            }

            if (note.isNotBlank()) {   // what NOVA saw in the video, 3 lines at most (tap to read more)
                val nt = TextView(ctx)
                nt.text = note.take(400); nt.setTextColor(Color.rgb(205, 215, 225)); nt.textSize = 12f; nt.maxLines = 3
                nt.ellipsize = android.text.TextUtils.TruncateAt.END
                nt.setPadding((4 * d).toInt(), (6 * d).toInt(), (4 * d).toInt(), 0)
                var open = false
                nt.setOnClickListener { open = !open; nt.maxLines = if (open) 12 else 3 }
                box.addView(nt, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            val w = WebView(ctx)
            val s = w.settings
            s.javaScriptEnabled = true
            s.allowFileAccess = false
            s.allowContentAccess = false
            s.setSupportMultipleWindows(false)
            s.javaScriptCanOpenWindowsAutomatically = false
            s.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            s.domStorageEnabled = true
            s.setGeolocationEnabled(false)
            w.setBackgroundColor(Color.WHITE)
            w.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val u: Uri = request?.url ?: return true
                    return !ScreenVideoRules.isSafeUrl(u.toString())   // true = blocked (intent:, market:, http:, file: ...)
                }
            }
            val lpW = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            lpW.topMargin = (6 * d).toInt()
            box.addView(w, lpW)

            val dm = ctx.resources.displayMetrics
            val lp = WindowManager.LayoutParams(
                (dm.widthPixels * 0.94f).toInt(),
                (dm.heightPixels * 0.60f).toInt(),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            lp.y = (16 * d).toInt()
            wm.addView(box, lp)
            root = box; web = w
            w.loadUrl(url)
            return true
        } catch (e: Throwable) {
            ErrLog.add("moviePopup", "show")
            destroy()
            return false
        }
    }

    fun destroy() {
        val r = root
        val w = web
        root = null; web = null
        try { w?.stopLoading() } catch (e: Throwable) { ErrLog.addOnce("quiet:MoviePopup", e.toString()) }
        try { if (r != null) wm.removeView(r) } catch (e: Throwable) { }
        try { w?.destroy() } catch (e: Throwable) { }
    }
}
