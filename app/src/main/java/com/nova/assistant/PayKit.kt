package com.nova.assistant

import android.app.Notification
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.service.notification.StatusBarNotification
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * v7.170 PAYMENT QR + PAYMENT CHECK storage. Plain SharedPreferences, capped. Only amount + (best effort) name of a
 * credit alert is kept - never the full alert text, never uploaded. The business profile (BizStore) is NOT touched:
 * a QR for any UPI id never changes the UPI id used for advance-payment messages.
 */
object PayStore {
    private const val PREFS = "nova_pay"
    private const val MAX_PENDING = 20
    private const val MAX_RECENT = 20

    private fun p(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized fun watch(ctx: Context): Boolean = p(ctx).getBoolean("watch", true)   // v7.171: default ON (only UPI-app / SMS credit alerts are read)
    @Synchronized fun setWatch(ctx: Context, on: Boolean) { p(ctx).edit().putBoolean("watch", on).commit() }
    @Synchronized fun lastUpi(ctx: Context): String = p(ctx).getString("last_upi", "").orEmpty()
    @Synchronized fun lastPayee(ctx: Context): String = p(ctx).getString("last_payee", "").orEmpty()
    @Synchronized fun lastLink(ctx: Context): String = p(ctx).getString("last_link", "").orEmpty()
    @Synchronized fun setLast(ctx: Context, upi: String, payee: String, link: String) {
        p(ctx).edit().putString("last_upi", upi).putString("last_payee", payee).putString("last_link", link).commit()
    }

    @Synchronized fun pendings(ctx: Context): List<PayRules.Pending> = try {
        val a = JSONArray(p(ctx).getString("pending", "[]"))
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            PayRules.Pending(o.optString("id"), o.optLong("paise"), o.optLong("c"), o.optLong("e"), o.optString("s"), o.optLong("r"), o.optString("f"), o.optString("n"))
        }
    } catch (e: Exception) { emptyList() }

    private fun savePendings(ctx: Context, l: List<PayRules.Pending>) {
        val a = JSONArray()
        for (x in l.takeLast(MAX_PENDING)) a.put(JSONObject().put("id", x.id).put("paise", x.paise).put("c", x.createdAt).put("e", x.expiresAt)
            .put("s", x.state).put("r", x.receivedAt).put("f", x.from).put("n", x.note))
        p(ctx).edit().putString("pending", a.toString()).commit()
    }

    @Synchronized fun addPending(ctx: Context, x: PayRules.Pending) { savePendings(ctx, pendings(ctx) + x) }

    @Synchronized fun setState(ctx: Context, id: String, state: String, now: Long, from: String = "") {
        savePendings(ctx, pendings(ctx).map { if (it.id == id) it.copy(state = state, receivedAt = if (state == PayRules.RECEIVED) now else it.receivedAt, from = if (from.isNotBlank()) from else it.from) else it })
    }

    /** Waiting QRs whose time is over become EXPIRED; returns the ones that just changed. */
    @Synchronized fun expire(ctx: Context, now: Long): List<PayRules.Pending> {
        val l = pendings(ctx)
        val gone = l.filter { it.state == PayRules.WAITING && now > it.expiresAt }
        if (gone.isEmpty()) return emptyList()
        val ids = gone.map { it.id }.toSet()
        savePendings(ctx, l.map { if (it.id in ids) it.copy(state = PayRules.EXPIRED) else it })
        return gone
    }

    class Seen(val at: Long, val paise: Long, val from: String, val src: String, val matched: Boolean)

    @Synchronized fun recent(ctx: Context): List<Seen> = try {
        val a = JSONArray(p(ctx).getString("recent", "[]"))
        (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { Seen(it.optLong("t"), it.optLong("a"), it.optString("f"), it.optString("s"), it.optBoolean("m")) } }
    } catch (e: Exception) { emptyList() }

    @Synchronized fun addRecent(ctx: Context, s: Seen) {
        val a = JSONArray()
        for (x in (recent(ctx) + s).takeLast(MAX_RECENT)) a.put(JSONObject().put("t", x.at).put("a", x.paise).put("f", x.from).put("s", x.src).put("m", x.matched))
        p(ctx).edit().putString("recent", a.toString()).commit()
    }
}

object PayKit {
    /** MainActivity sets this: runs one JS line on the page (the PAYMENT QR screen). */
    @Volatile var ui: ((String) -> Unit)? = null

    private val seen = HashMap<String, Long>()
    private const val DEDUPE_MS = 10 * 60_000L

    // ---------------------------------------------------------------- QR image

    fun qrPng(link: String, size: Int = 640): ByteArray? = try {
        val hints = mapOf(EncodeHintType.MARGIN to 2, EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M)
        val m = QRCodeWriter().encode(link, BarcodeFormat.QR_CODE, size, size, hints)
        val px = IntArray(m.width * m.height)
        for (y in 0 until m.height) { val o = y * m.width; for (x in 0 until m.width) px[o + x] = if (m.get(x, y)) Color.BLACK else Color.WHITE }
        val bmp = Bitmap.createBitmap(m.width, m.height, Bitmap.Config.ARGB_8888)
        bmp.setPixels(px, 0, m.width, 0, 0, m.width, m.height)
        val bos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
        bmp.recycle()
        bos.toByteArray()
    } catch (e: Throwable) { ErrLog.add("PayKit", "qr: $e"); null }

    class Made(val ok: Boolean, val err: String, val link: String, val png: ByteArray?, val paise: Long, val upi: String, val id: String)

    /** Builds the QR for ANY valid UPI id (blank = the one used last time), remembers it as a waiting payment and starts watching. */
    fun make(ctx: Context, upi0: String, payee0: String, amount0: String, note0: String, en: Boolean = false): Made {
        fun bad(hi: String, e: String) = Made(false, if (en) e else hi, "", null, 0, "", "")
        val app = ctx.applicationContext
        val upi = upi0.trim().ifBlank { PayStore.lastUpi(app) }.ifBlank { BizStore.profile(app).upi }
        if (upi.isBlank()) return bad("UPI id batao (jaise naam@bank).", "Give a UPI id (like name@bank).")
        if (!PayRules.validUpi(upi)) return bad("UPI id sahi nahi lagi (jaise naam@bank).", "That UPI id looks wrong (like name@bank).")
        val paise = if (amount0.isBlank() || amount0.trim() == "0") 0L else (PayRules.toPaise(amount0) ?: return bad("Rakam number me likho (jaise 500 ya 499.50).", "Write the amount as a number (like 500 or 499.50)."))
        val payee = payee0.trim().ifBlank { PayStore.lastPayee(app) }.ifBlank { BizStore.profile(app).payee }
        val link = PayRules.upiLink(upi, payee, paise, note0)
        val png = qrPng(link) ?: return bad("QR image nahi ban payi.", "Could not draw the QR.")
        val now = System.currentTimeMillis()
        val id = "p" + now
        PayStore.setLast(app, upi, payee, link)
        PayStore.addPending(app, PayRules.Pending(id, paise, now, now + PayRules.WAIT_MS, PayRules.WAITING, 0L, "", note0.trim().take(40)))
        PayStore.setWatch(app, true)
        Handler(Looper.getMainLooper()).postDelayed({ try { checkExpiry(app) } catch (e: Throwable) { ErrLog.add("PayKit", "expiry: $e") } }, PayRules.WAIT_MS + 2000L)
        return Made(true, "", link, png, paise, upi, id)
    }

    fun cancel(ctx: Context, id: String) { PayStore.setState(ctx.applicationContext, id, PayRules.CANCELLED, System.currentTimeMillis()) }

    /** Android 10+: saves the last QR into Pictures/NOVA (no permission needed). */
    fun saveLastToGallery(ctx: Context): String {
        val link = PayStore.lastLink(ctx)
        if (link.isBlank()) return "Pehle QR banao."
        if (Build.VERSION.SDK_INT < 29) return "Is phone par seedha save nahi hota; QR ka screenshot le lo."
        val png = qrPng(link) ?: return "QR image nahi ban payi."
        return try {
            val v = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "NOVA_QR_" + System.currentTimeMillis() + ".png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/NOVA")
            }
            val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v) ?: return "Gallery me save nahi hua."
            val ok = ctx.contentResolver.openOutputStream(uri)?.use { it.write(png); true } ?: false
            if (ok) "Gallery me save ho gaya (Pictures/NOVA)." else "Gallery me save nahi hua."
        } catch (e: Throwable) { ErrLog.add("PayKit", "save: $e"); "Gallery me save nahi hua." }
    }

    // ---------------------------------------------------------------- watching the credit alerts

    /** Called for every posted notification (NovaNotificationService). Cheap exit unless the watch is ON and the app is a UPI / SMS app. */
    fun onNotification(ctx: Context, sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        if (pkg == ctx.packageName) return
        val src = PayRules.sourceOf(pkg) ?: return
        val app = ctx.applicationContext
        if (!PayStore.watch(app)) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val ex = n.extras ?: return
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        val text = (title + " " + body).trim()
        val c = PayRules.parseCredit(text) ?: return
        val now = System.currentTimeMillis()
        val key = (sbn.key ?: pkg) + "|" + text.hashCode()
        synchronized(seen) {
            seen.entries.removeAll { now - it.value > DEDUPE_MS }
            if (seen.containsKey(key)) return
            seen[key] = now
        }
        val list = PayStore.pendings(app)
        val i = PayRules.matchIndex(list, c, now)
        val matched = i >= 0
        if (matched) PayStore.setState(app, list[i].id, PayRules.RECEIVED, now, c.from)
        PayStore.addRecent(app, PayStore.Seen(now, c.paise, c.from, src.name, matched))
        val en = NovaService.instance?.let { it.isEnglish() } ?: false
        deliver(app, PayRules.receivedText(c, src, matched, en), if (matched) "Payment aa gayi" else "Paise aaye")
    }

    private fun checkExpiry(app: Context) {
        val en = NovaService.instance?.let { it.isEnglish() } ?: false
        for (p in PayStore.expire(app, System.currentTimeMillis())) deliver(app, PayRules.expiredText(p, en), "Payment nahi aaya")
    }

    /** Voice + chat line (NovaService), a phone notification, and a refresh of the PAYMENT QR screen. */
    private fun deliver(app: Context, text: String, title: String) {
        try { NovaService.instance?.onPayAlert(text) } catch (e: Throwable) { ErrLog.add("PayKit", "alert: $e") }
        try { notifyPhone(app, title, text) } catch (e: Throwable) { ErrLog.add("PayKit", "notif: $e") }
        try { ui?.invoke("window.onPayEvent&&window.onPayEvent()") } catch (e: Throwable) { }
    }

    private fun notifyPhone(app: Context, title: String, message: String) {
        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(android.app.NotificationChannel("nova_pay", "NOVA Payment", android.app.NotificationManager.IMPORTANCE_HIGH))
        val intent = android.content.Intent(app, MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) android.app.PendingIntent.FLAG_IMMUTABLE else 0
        val pi = android.app.PendingIntent.getActivity(app, 7421, intent, flags)
        val note = androidx.core.app.NotificationCompat.Builder(app, "nova_pay")
            .setSmallIcon(com.nova.assistant.R.mipmap.ic_launcher).setContentTitle(title.take(80))
            .setContentText(message.take(180)).setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(message.take(500)))
            .setContentIntent(pi).setAutoCancel(true).build()
        nm.notify(7421, note)
    }

    // ---------------------------------------------------------------- status

    fun listenerOn(ctx: Context): Boolean = try {
        androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
    } catch (e: Throwable) { false }

    fun statusText(ctx: Context, en: Boolean): String {
        val app = ctx.applicationContext
        val now = System.currentTimeMillis()
        PayStore.expire(app, now)
        return PayRules.statusText(PayStore.pendings(app), now, PayStore.watch(app), listenerOn(app), en)
    }

    fun cmdQr(ctx: Context, arg: String, en: Boolean): String {
        val i = arg.indexOf('|')
        val upi = if (i >= 0) arg.substring(0, i) else ""
        val paise = (if (i >= 0) arg.substring(i + 1) else "0").toLongOrNull() ?: 0L
        val r = make(ctx, upi, "", if (paise > 0) PayRules.rupeeText(paise) else "", "", en)
        if (!r.ok) return r.err
        openScreen()
        return PayRules.qrDoneText(r.paise, r.upi, en) + (if (listenerOn(ctx)) "" else (if (en) " Notification access is OFF, so I cannot see the payment alert. Turn it on in Settings." else " Notification access band hai, isliye payment ka alert nahi dikh payega. Settings me ON karo."))
    }

    fun cmdWatch(ctx: Context, on: Boolean, en: Boolean): String {
        PayStore.setWatch(ctx.applicationContext, on)
        ui?.invoke("window.onPayEvent&&window.onPayEvent()")
        return if (on) (if (en) "Payment auto check ON. I read only UPI app and SMS credit alerts." else "Payment auto check ON. Main sirf UPI app aur SMS ke credit alert padhta hoon.")
        else (if (en) "Payment auto check OFF." else "Payment auto check OFF.")
    }

    fun openScreen() { try { ui?.invoke("window.__payOpen&&window.__payOpen()") } catch (e: Throwable) { } }

    /** JSON for the PAYMENT QR screen. */
    fun snapshotJson(ctx: Context): String {
        val app = ctx.applicationContext
        val now = System.currentTimeMillis()
        PayStore.expire(app, now)
        val pend = JSONArray()
        for (x in PayStore.pendings(app).takeLast(6).reversed()) pend.put(JSONObject().put("id", x.id).put("paise", x.paise).put("amt", if (x.paise > 0) PayRules.rupeeText(x.paise) else "")
            .put("state", x.state).put("left", ((x.expiresAt - now) / 1000L).coerceAtLeast(0)).put("from", x.from).put("at", x.createdAt).put("rat", x.receivedAt))
        val rec = JSONArray()
        for (x in PayStore.recent(app).takeLast(8).reversed()) rec.put(JSONObject().put("at", x.at).put("amt", PayRules.rupeeText(x.paise)).put("from", x.from).put("src", x.src).put("m", x.matched))
        return JSONObject().put("watch", PayStore.watch(app)).put("listener", listenerOn(app)).put("upi", PayStore.lastUpi(app).ifBlank { BizStore.profile(app).upi })
            .put("payee", PayStore.lastPayee(app).ifBlank { BizStore.profile(app).payee }).put("pending", pend).put("recent", rec).put("now", now).toString()
    }

    /** JSON for one QR make from the screen: {ok, err, png (data URI), link, upi, amt, id, msg}. */
    fun makeJson(ctx: Context, upi: String, payee: String, amount: String, note: String): String {
        val r = make(ctx, upi, payee, amount, note, false)
        val o = JSONObject().put("ok", r.ok).put("err", r.err)
        if (r.ok && r.png != null) o.put("png", "data:image/png;base64," + Base64.encodeToString(r.png, Base64.NO_WRAP))
            .put("link", r.link).put("upi", r.upi).put("amt", if (r.paise > 0) PayRules.rupeeText(r.paise) else "").put("id", r.id)
            .put("msg", PayRules.qrDoneText(r.paise, r.upi, false))
        return o.toString()
    }

    /** The last QR again as an image (when the screen is opened later). */
    fun lastQrJson(ctx: Context): String {
        val link = PayStore.lastLink(ctx.applicationContext)
        val png = if (link.isBlank()) null else qrPng(link)
        return if (png == null) "{\"ok\":false}" else JSONObject().put("ok", true).put("png", "data:image/png;base64," + Base64.encodeToString(png, Base64.NO_WRAP)).put("link", link).toString()
    }
}
