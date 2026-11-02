package com.nova.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.EncodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * v7.153 WIFI KIT: Android side of NOVA's LEGAL Wi-Fi tools (rules in WifiKitRules, store in WifiKitStore).
 * What it does: scan nearby networks (information), switch to the strongest of the OWNER'S OWN saved networks,
 * join a genuinely OPEN network on request, make/read the owner's own Wi-Fi QR, run the connectivity doctor,
 * and (optional, default OFF) WIFI GUARD that switches among the owner's own saved networks only.
 *
 * What it NEVER does: bypass, guess or force its way into any network. A password-protected network is joined
 * only with a password the owner gave (typed/spoken order or their own QR photo). On Android 10+ the system may
 * show its own confirmation for a suggested network - that is Android's rule and it is respected.
 */
object WifiKit {

    // ---------------------------------------------------------------- state read

    data class Now(
        val wifiOn: Boolean, val ssid: String, val level: Int, val freqMhz: Int, val caps: String,
        val hasNetwork: Boolean, val validated: Boolean
    )

    private fun wm(ctx: Context): WifiManager? =
        ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private fun cm(ctx: Context): ConnectivityManager? =
        ctx.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    fun permitted(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Live snapshot of the Wi-Fi side. Never throws. */
    fun now(ctx: Context): Now {
        var wifiOn = false; var ssid = ""; var level = WifiKitRules.DISCONNECTED; var freq = 0; var caps = ""
        try {
            val w = wm(ctx)
            if (w != null) {
                wifiOn = w.isWifiEnabled
                if (wifiOn && permitted(ctx)) {
                    @Suppress("DEPRECATION") val info = w.connectionInfo
                    if (info != null) {
                        val raw = info.ssid ?: ""
                        val clean = raw.removeSurrounding("\"").trim()
                        if (clean.isNotEmpty() && clean != "<unknown ssid>" && !clean.startsWith("0x")) ssid = clean
                        level = info.rssi
                        freq = info.frequency
                    }
                    if (ssid.isNotEmpty()) {
                        caps = w.scanResults.firstOrNull { it.SSID == ssid }?.capabilities ?: ""
                        if (freq == 0) w.scanResults.firstOrNull { it.SSID == ssid }?.let { freq = it.frequency }
                    }
                }
            }
        } catch (e: Throwable) { ErrLog.addOnce("quiet:WifiKit", e.toString()) }
        var hasNetwork = false; var validated = false
        try {
            val c = cm(ctx)
            if (c != null) {
                for (n in c.allNetworks) {
                    val nc = c.getNetworkCapabilities(n) ?: continue
                    if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        hasNetwork = true
                        validated = nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                        break
                    }
                }
            }
        } catch (e: Throwable) { ErrLog.addOnce("quiet:WifiKit", e.toString()) }
        return Now(wifiOn, ssid, level, freq, caps, hasNetwork, validated)
    }

    /** Nearby networks (dedup by SSID, strongest first). Needs Wi-Fi ON + Location permission; otherwise empty. */
    fun scan(ctx: Context): List<WifiKitRules.Net> {
        val out = ArrayList<WifiKitRules.Net>()
        try {
            val w = wm(ctx) ?: return out
            if (!w.isWifiEnabled || !permitted(ctx)) return out
            try { w.startScan() } catch (e: Throwable) { ErrLog.addOnce("quiet:WifiKit", e.toString()) }
            for (r in w.scanResults) {
                val s = (r.SSID ?: "").trim()
                if (s.isNotEmpty()) out.add(WifiKitRules.Net(s, r.level, r.frequency, r.capabilities ?: ""))
            }
        } catch (e: Throwable) { ErrLog.addOnce("quiet:WifiKit", e.toString()) }
        return out
    }

    /** Captive-portal probe: true = login page (30x/200), false = 204 OK, null = unknown. Runs on its own thread. */
    fun portalProbe(): Boolean? = try {
        var res: Boolean? = null
        val th = Thread {
            var conn: HttpURLConnection? = null
            try {
                conn = URL("http://connectivitycheck.gstatic.com/generate_204").openConnection() as HttpURLConnection
                conn.connectTimeout = 4000; conn.readTimeout = 4000
                conn.instanceFollowRedirects = false
                conn.setRequestProperty("User-Agent", "NovaWiFiDoctor/1")
                val code = conn.responseCode
                res = when (code) {
                    204 -> false
                    in 200..399 -> true
                    else -> null
                }
            } catch (e: Throwable) { res = null }
            finally { try { conn?.disconnect() } catch (e: Throwable) { } }
        }
        th.isDaemon = true; th.start(); th.join(6000)
        res
    } catch (e: Throwable) { null }

    // ---------------------------------------------------------------- joining (owner's own networks / open networks)

    private enum class JoinHow { DIRECT, SUGGESTED, FAIL }

    /**
     * Joins the given network with a password the OWNER supplied (or no password for an open network).
     * API 28-: WifiConfiguration (works directly). API 29+: WifiNetworkSuggestion - Android shows its own
     * consent and joins when it decides; the reply stays honest about that.
     */
    @Suppress("DEPRECATION")
    private fun join(ctx: Context, ssid: String, pass: String): JoinHow {
        val w = wm(ctx) ?: return JoinHow.FAIL
        return try {
            if (!w.isWifiEnabled) return JoinHow.FAIL
            if (android.os.Build.VERSION.SDK_INT <= 28) {
                val wc = WifiConfiguration()
                wc.SSID = "\"" + ssid + "\""
                if (pass.isNotBlank()) wc.preSharedKey = "\"" + pass + "\""
                wc.hiddenSSID = false
                val existing = w.configuredNetworks?.firstOrNull { it.SSID == "\"" + ssid + "\"" }
                val id = if (existing != null) existing.networkId else w.addNetwork(wc)
                if (id == -1) return JoinHow.FAIL
                w.disconnect()
                if (!w.enableNetwork(id, true)) return JoinHow.FAIL
                w.reconnect()
                JoinHow.DIRECT
            } else {
                val b = WifiNetworkSuggestion.Builder().setSsid(ssid)
                if (pass.isNotBlank()) b.setWpa2Passphrase(pass)
                val how = w.addNetworkSuggestions(listOf(b.build()))
                if (how == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) JoinHow.SUGGESTED else JoinHow.FAIL
            }
        } catch (e: Throwable) {
            ErrLog.add("WifiKit", e.toString()); JoinHow.FAIL
        }
    }

    /** Opens Android's own Wi-Fi panel (API 29+) or the Wi-Fi settings page - the owner taps, NOVA never forces. */
    fun openWifiPanel(ctx: Context) {
        try {
            val i = if (android.os.Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_WIFI)
            else Intent(Settings.ACTION_WIFI_SETTINGS)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        } catch (e: Throwable) { ErrLog.add("WifiKit", e.toString()) }
    }

    // ---------------------------------------------------------------- QR make / read (owner's own Wi-Fi)

    /** Makes a Wi-Fi QR PNG in the private media cache and returns it, or null. */
    fun makeQrPng(ctx: Context, ssid: String, pass: String): File? = try {
        val payload = WifiKitRules.qrPayload(ssid, pass)
        val hints = mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.CHARACTER_SET to "UTF-8")
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 640, 640, hints)
        val bmp = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
        for (x in 0 until matrix.width) for (y in 0 until matrix.height) {
            bmp.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
        }
        val dir = File(ctx.cacheDir, "media_intake")
        if (!dir.isDirectory) dir.mkdirs()
        val f = File(dir, "wifi_qr_" + System.currentTimeMillis() + ".png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        f
    } catch (e: Throwable) { ErrLog.add("WifiKit", e.toString()); null }

    /** Reads a Wi-Fi QR from one image Uri (the owner's own QR photo). null = no Wi-Fi QR found. */
    fun readQr(ctx: Context, uri: Uri): WifiKitRules.QrWifi? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1200) sample *= 2
        val o2 = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) } ?: return null
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        bmp.recycle()
        val src = RGBLuminanceSource(w, h, px)
        val txt = try {
            QRCodeReader().decode(BinaryBitmap(HybridBinarizer(src))).text
        } catch (e: Exception) {
            try { QRCodeReader().decode(BinaryBitmap(com.google.zxing.common.GlobalHistogramBinarizer(src))).text }
            catch (e2: Exception) { null }
        }
        txt?.let { WifiKitRules.parseQr(it) }
    } catch (e: Throwable) { ErrLog.addOnce("quiet:WifiKit", e.toString()); null }
    }

    /**
     * Called when photo(s) arrive (Share -> NOVA or MEDIA picker): if one of them is a Wi-Fi QR, the password of
     * the OWNER'S OWN network is saved (encrypted) and a Toast tells the next step. Runs on its own thread.
     */
    fun tryQrShare(ctx: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        val app = ctx.applicationContext
        Thread {
            for (u in uris) {
                try {
                    val qr = readQr(app, u) ?: continue
                    val ssid = qr.ssid.trim()
                    if (!WifiKitRules.validSsid(ssid)) continue
                    if (!qr.open && !WifiKitRules.validPass(qr.pass)) continue
                    if (WifiKitStore.save(app, ssid, if (qr.open) "" else qr.pass)) {
                        ErrLog.add("WifiKit", "qr saved: $ssid")
                        Handler(Looper.getMainLooper()).post {
                            try {
                                Toast.makeText(app, if (qr.open) "NOVA: WiFi QR mila ($ssid, free network). Connect karne ke liye bolo: nova wifi switch karo $ssid"
                                else "NOVA: WiFi QR mila ($ssid). Password encrypted save ho gaya. Connect karne ke liye bolo: nova wifi switch karo $ssid",
                                    Toast.LENGTH_LONG).show()
                            } catch (e: Throwable) { ErrLog.addOnce("quiet:WifiKit", e.toString()) }
                        }
                    }
                } catch (e: Throwable) { ErrLog.addOnce("quiet:WifiKit", e.toString()) }
            }
        }.start()
    }

    // ---------------------------------------------------------------- command dispatch (called from NovaService.runCommand)

    fun handle(ctx: Context, c: Logic.Cmd, en: Boolean): String {
        fun t(hi: String, e: String) = if (en) e else hi
        return when (c.kind) {
            WifiKitRules.K_SCAN -> {
                val n = now(ctx)
                if (!n.wifiOn) t("WiFi band hai. Pehle WiFi ON karo, phir bolo: nova wifi scan karo.", "Wi-Fi is off. Turn Wi-Fi on first, then say: nova wifi scan karo.")
                else if (!permitted(ctx)) t("Scan ke liye NOVA ko Location permission chahiye. Phone Settings > Apps > NOVA > Permissions me Location ON karo, phir dobara bolo.",
                    "Scanning needs the Location permission for NOVA. Turn it on in Settings > Apps > NOVA > Permissions, then ask again.")
                else WifiKitRules.scanReply(scan(ctx), en)
            }
            WifiKitRules.K_STATUS -> statusReply(ctx, en)
            WifiKitRules.K_BEST -> switchBest(ctx, en)
            WifiKitRules.K_SWITCH -> if (c.arg.isBlank()) switchBest(ctx, en) else switchNamed(ctx, c.arg, en)
            WifiKitRules.K_OPEN -> connectOpen(ctx, en)
            WifiKitRules.K_QR_MAKE -> makeQr(ctx, c.arg, en)
            WifiKitRules.K_QR_READ -> t("WiFi QR photo ko Gallery se NOVA me SHARE karo (Share > NOVA). Main QR padhkar uska password encrypted save kar dunga, phir bolo: nova wifi switch karo <naam>.",
                "SHARE the Wi-Fi QR photo to NOVA from your gallery (Share > NOVA). I read the QR, save its password encrypted, then say: nova wifi switch karo <name>.")
            WifiKitRules.K_SAVE_PASS -> savePass(ctx, c.arg, en)
            WifiKitRules.K_FORGET -> {
                val ok = WifiKitStore.forget(ctx, c.arg)
                if (ok) t(c.arg + " bhool gaya. Uska encrypted password bhi hata diya.", "Forgot " + c.arg + ". Its encrypted password is removed too.")
                else t(c.arg + " meri saved list me nahi tha.", c.arg + " was not in my saved list.")
            }
            WifiKitRules.K_FIX -> {
                val n = now(ctx)
                val st = if (n.wifiOn && n.ssid.isNotEmpty() && n.hasNetwork && !n.validated) {
                    val p = portalProbe()
                    WifiKitRules.DoctorState(n.wifiOn, n.ssid, n.level, n.hasNetwork, n.validated, p)
                } else WifiKitRules.DoctorState(n.wifiOn, n.ssid, n.level, n.hasNetwork, n.validated, null)
                WifiKitRules.doctorReply(st, en)
            }
            WifiKitRules.K_GUARD_ON -> {
                ExtCfg.setWifiGuard(ctx, true)
                t("WIFI GUARD ON. Har minute check hota hai: aapka WiFi bahut kamzor ho ya kat jaye, to NOVA aapke hi saved networks me se strong wale pe switch karti hai (10 minute me zyada se zyada ek switch). Doosron ka WiFi kabhi nahi khulta.",
                    "WIFI GUARD is ON. Every minute I check: if your Wi-Fi gets very weak or drops, I switch to the strongest of YOUR OWN saved networks (at most one switch per 10 minutes). Other people's Wi-Fi is never opened.")
            }
            WifiKitRules.K_GUARD_OFF -> {
                ExtCfg.setWifiGuard(ctx, false)
                t("WIFI GUARD OFF. Ab NOVA WiFi apne aap switch nahi karegi. Settings mein WIFI GUARD switch se bhi on/off hota hai.",
                    "WIFI GUARD is OFF. NOVA no longer switches Wi-Fi by itself. The WIFI GUARD switch in Settings also works.")
            }
            else -> t("Ye WiFi kaam abhi samajh nahi aaya.", "I did not understand this Wi-Fi request.")
        }
    }

    private fun statusReply(ctx: Context, en: Boolean): String {
        fun t(hi: String, e: String) = if (en) e else hi
        val n = now(ctx)
        if (!n.wifiOn) return t("WiFi band hai.", "Wi-Fi is off.")
        if (n.ssid.isBlank()) return t("Kisi WiFi se juda nahi hai. Bolo: nova wifi scan karo.", "No Wi-Fi is joined. Say: nova wifi scan karo.")
        val sec = WifiKitRules.security(n.caps)
        val bd = WifiKitRules.band(n.freqMhz)
        val parts = ArrayList<String>()
        parts.add("signal " + WifiKitRules.bars(n.level) + "/4")
        if (bd.isNotEmpty()) parts.add(bd)
        if (sec.startsWith("open")) parts.add(if (en) "free network" else "free network") else parts.add(sec)
        parts.add(if (n.validated) (if (en) "internet OK" else "internet chal raha hai") else (if (en) "internet not verified" else "internet verify nahi hua"))
        return t("Abhi " + n.ssid + " se juda hai: ", "Currently on " + n.ssid + ": ") + parts.joinToString(", ") + "."
    }

    private fun storedCandidates(ctx: Context, nets: List<WifiKitRules.Net>): List<WifiKitRules.Net> {
        val stored = WifiKitStore.all(ctx)
        return nets.filter { n -> stored.any { it.equals(n.ssid, true) } }
    }

    private fun switchBest(ctx: Context, en: Boolean): String {
        fun t(hi: String, e: String) = if (en) e else hi
        val n = now(ctx)
        if (!n.wifiOn) return t("WiFi band hai. Pehle WiFi ON karo.", "Wi-Fi is off. Turn Wi-Fi on first.")
        if (!permitted(ctx)) return t("Scan ke liye NOVA ko Location permission chahiye (Settings > Apps > NOVA > Permissions).", "Scanning needs the Location permission (Settings > Apps > NOVA > Permissions).")
        val stored = WifiKitStore.all(ctx)
        if (stored.isEmpty()) return t("Meri list me aapka koi WiFi saved nahi hai. Pehle bolo: wifi password <naam> ka <password> - phir main best network pe switch kar sakta hoon.",
            "None of your Wi-Fi networks are saved yet. First say: wifi password <name> <password> - then I can switch to the best network.")
        val plan = WifiKitRules.bestSwitch(n.ssid, if (n.ssid.isBlank()) WifiKitRules.DISCONNECTED else n.level, storedCandidates(ctx, scan(ctx)))
        if (plan == null) return t("Aapke saved networks me se koi isse behtar paas nahi hai (" + (if (n.ssid.isBlank()) "abhi juda nahi" else n.ssid) + ").",
            "None of your saved networks nearby is better than the current one (" + (if (n.ssid.isBlank()) "not connected" else n.ssid) + ").")
        return doSwitch(ctx, plan.ssid, plan.gainDb, en)
    }

    private fun switchNamed(ctx: Context, ssid: String, en: Boolean): String {
        fun t(hi: String, e: String) = if (en) e else hi
        val n = now(ctx)
        if (!n.wifiOn) return t("WiFi band hai. Pehle WiFi ON karo.", "Wi-Fi is off. Turn Wi-Fi on first.")
        if (ssid.equals(n.ssid, true)) return t(ssid + " pe already juda hai.", "Already connected to " + ssid + ".")
        val known = WifiKitStore.has(ctx, ssid)
        val nets = scan(ctx)
        val here = nets.firstOrNull { it.ssid.equals(ssid, true) }
        val openHere = here?.let { WifiKitRules.isOpen(it.caps) } == true
        if (!known && !openHere) {
            openWifiPanel(ctx)
            return t(ssid + " ka password mere paas nahi hai. WiFi panel khol diya hai - password wahan daal do. Agla baar seedhe switch karne ke liye pehle bolo: wifi password " + ssid + " ka <password>",
                "I do not have the password for " + ssid + ". I opened the Wi-Fi panel - enter it there. To let me switch next time, first say: wifi password " + ssid + " <password>")
        }
        val gain = here?.level?.minus(n.level) ?: 0
        return doSwitch(ctx, ssid, gain, en)
    }

    private fun doSwitch(ctx: Context, ssid: String, gainDb: Int, en: Boolean): String {
        fun t(hi: String, e: String) = if (en) e else hi
        val pass = if (WifiKitStore.has(ctx, ssid)) WifiKitStore.pass(ctx, ssid) else ""
        val gainTxt = if (gainDb > 0) " (+" + gainDb + " dB stronger)" else ""
        val how = join(ctx, ssid, pass)
        if (how == WifiKit.JoinHow.FAIL) {
            openWifiPanel(ctx)
            return t(ssid + " pe switch nahi ho paya. WiFi panel khol diya hai - wahan password daal kar jod lo.",
                "Could not switch to " + ssid + ". I opened the Wi-Fi panel - join from there.")
        }
        return if (how == WifiKit.JoinHow.DIRECT) t(ssid + " pe switch kar diya" + gainTxt + ".", "Switched to " + ssid + gainTxt + ".")
        else t(ssid + gainTxt + " ke liye system ko suggestion de di hai - Android jab manzoor karega jud jayega (kabhi kabhi ek confirm popup aata hai). Na juda ho to bolo: nova wifi settings kholo.",
            "I gave the system a suggestion for " + ssid + gainTxt + " - Android joins when it approves (it may show one confirm popup). If it did not join, say: nova wifi settings kholo.")
    }

    private fun connectOpen(ctx: Context, en: Boolean): String {
        fun t(hi: String, e: String) = if (en) e else hi
        val n = now(ctx)
        if (!n.wifiOn) return t("WiFi band hai. Pehle WiFi ON karo.", "Wi-Fi is off. Turn Wi-Fi on first.")
        if (!permitted(ctx)) return t("Scan ke liye NOVA ko Location permission chahiye (Settings > Apps > NOVA > Permissions).", "Scanning needs the Location permission (Settings > Apps > NOVA > Permissions).")
        val open = WifiKitRules.strongestOpen(scan(ctx))
        if (open == null) return t("Paas koi free/open WiFi nahi mila. Jo networks hain unka password chahiye - doosron ka WiFi bina ijaazat nahi kholta main.",
            "No free/open Wi-Fi nearby. The other networks need their password - I never open other people's Wi-Fi without permission.")
        return when (join(ctx, open.ssid, "")) {
            WifiKit.JoinHow.DIRECT -> t("Free network " + open.ssid + " se jud gaya (" + WifiKitRules.bars(open.level) + "/4 signal). Agar login page maange, koi browser kholo.",
                "Joined the free network " + open.ssid + " (" + WifiKitRules.bars(open.level) + "/4 signal). If it asks for a login, open any browser.")
            WifiKit.JoinHow.SUGGESTED -> t("Free network " + open.ssid + " ka suggestion system ko de diya - Android approve karega to jud jayega.",
                "I suggested the free network " + open.ssid + " to the system - Android joins when it approves.")
            WifiKit.JoinHow.FAIL -> {
                openWifiPanel(ctx)
                t(open.ssid + " se juda nahi ja paya. WiFi panel khol raha hoon.", "Could not join " + open.ssid + ". Opening the Wi-Fi panel.")
            }
        }
    }

    private fun makeQr(ctx: Context, arg: String, en: Boolean): String {
        fun t(hi: String, e: String) = if (en) e else hi
        val ssid = if (arg.isNotBlank()) arg else now(ctx).ssid
        if (ssid.isBlank()) return t("QR kis WiFi ka banau? Naam bolo: nova wifi qr banao <naam>. (Abhi kisi WiFi se juda bhi nahi hai.)",
            "Which Wi-Fi should I make a QR for? Say: nova wifi qr banao <name>. (You are not connected to any Wi-Fi right now.)")
        if (!WifiKitStore.has(ctx, ssid)) {
            return t(ssid + " ka password mere paas saved nahi hai (Android apps saved WiFi ka password nahi padh sakte). Pehle bolo: wifi password " + ssid + " ka <password> - ya uska QR wali photo NOVA se share karo.",
                "I do not have the password for " + ssid + " (Android does not let apps read saved Wi-Fi passwords). First say: wifi password " + ssid + " <password> - or share that network's QR photo to NOVA.")
        }
        val pass = WifiKitStore.pass(ctx, ssid)
        val f = makeQrPng(ctx, ssid, pass) ?: return t("QR bana nahi paya, thodi der baad try karo.", "I could not make the QR, please try again shortly.")
        return try {
            val u = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
            val i = Intent(Intent.ACTION_SEND)
            i.type = "image/png"
            i.putExtra(Intent.EXTRA_STREAM, u)
            i.putExtra(Intent.EXTRA_TEXT, "WiFi QR: " + ssid)
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            val chooser = Intent.createChooser(i, "WiFi QR: " + ssid)
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)   // started from the service context
            ctx.startActivity(chooser)
            t(ssid + " ka QR bana diya aur share sheet khula - jis phone/computer ko dena hai us par bhejo. QR scan karne wala seedha connect ho jayega. QR photo NOVA se share karke main khud bhi padh sakta hoon.",
                "The QR for " + ssid + " is ready and the share sheet is open - send it to whichever phone or PC. Scanning it joins that device. You can also share the QR photo back to NOVA and I will read it.")
        } catch (e: Throwable) {
            ErrLog.add("WifiKit", e.toString())
            t("QR ban gaya par share nahi khula. Bolo: nova media kya mila - wahan QR photo dikhegi.", "The QR is made but the share sheet did not open. Say: nova media kya mila - the QR photo is there.")
        }
    }

    private fun savePass(ctx: Context, arg: String, en: Boolean): String {
        fun t(hi: String, e: String) = if (en) e else hi
        val sep = arg.indexOf(WifiKitRules.SEP)
        if (sep <= 0) return t("Samajh nahi aaya. Aise bolo: wifi password <naam> ka <password>.", "I did not understand. Say: wifi password <name> <password>.")
        val ssid = arg.substring(0, sep).trim()
        val pass = arg.substring(sep + 1).trim()
        if (!WifiKitRules.validSsidPass(ssid, pass)) {
            return t("Ye password theek nahi lagta: WiFi ka password 8 se 63 characters ka hota hai. Dobara bolo ya chat me likho.",
                "That password does not look right: a Wi-Fi password is 8 to 63 characters. Say or type it again.")
        }
        return if (WifiKitStore.save(ctx, ssid, pass)) {
            t(ssid + " ka password encrypted save ho gaya (sirf isi phone me). Ab bolo: nova wifi switch karo " + ssid + " - ya best wifi pe jao.",
                "The password for " + ssid + " is saved encrypted (on this phone only). Now say: nova wifi switch karo " + ssid + " - or best wifi pe jao.")
        } else t("Password save nahi ho paya. Dobara try karo.", "I could not save the password. Please try again.")
    }

    // ---------------------------------------------------------------- WIFI GUARD (optional, default OFF)

    /**
     * WIFI GUARD: every 60 s (only while the service is alive AND the owner turned the switch ON) it checks whether
     * the current Wi-Fi got very weak or dropped, and switches to the strongest of the OWNER'S OWN saved networks.
     * The decision (gain threshold, 10-minute cooldown) comes from the pure, unit-tested WifiKitRules.guardDecide.
     * It never speaks uninvited and never touches a network that is not already saved by the owner.
     */
    object Guard {
        private val h = Handler(Looper.getMainLooper())
        @Volatile private var appCtx: Context? = null
        @Volatile private var lastSwitchAt = 0L

        private val tick = object : Runnable {
            override fun run() {
                val ctx = appCtx
                if (ctx == null) return
                try { if (ExtCfg.wifiGuard(ctx)) guardTick(ctx) } catch (e: Throwable) { ErrLog.add("WifiKit", e.toString()) }
                h.postDelayed(this, 60_000L)
            }
        }

        fun start(ctx: Context) {
            if (appCtx != null) return
            appCtx = ctx.applicationContext
            h.postDelayed(tick, 60_000L)
        }

        fun stop() {
            appCtx = null
            h.removeCallbacks(tick)
        }

        private fun guardTick(ctx: Context) {
            if (!permitted(ctx)) return
            val n = now(ctx)
            if (!n.wifiOn) return
            if (WifiKitStore.all(ctx).isEmpty()) return
            val currentLevel = if (n.ssid.isBlank()) WifiKitRules.DISCONNECTED else n.level
            val plan = WifiKitRules.guardDecide(n.ssid, currentLevel, storedCandidates(ctx, scan(ctx)),
                lastSwitchAt, System.currentTimeMillis())
            if (plan != null) {
                lastSwitchAt = System.currentTimeMillis()
                val pass = if (WifiKitStore.has(ctx, plan.ssid)) WifiKitStore.pass(ctx, plan.ssid) else ""
                val how = join(ctx, plan.ssid, pass)
                ErrLog.add("WifiKit", "guard: " + plan.ssid + " (+" + plan.gainDb + " dB) -> " + how.name)
            }
        }
    }
}
