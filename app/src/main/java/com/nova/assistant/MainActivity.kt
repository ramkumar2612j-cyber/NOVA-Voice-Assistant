package com.nova.assistant

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream

class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var cfg: Cfg
    private var testTts: TextToSpeech? = null
    private var listenerOwned = false

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        cfg = Cfg(this)
        ApiUsage.init(this)
        LocalBrains.installEngine(this)
        web = WebView(this)
        hardenWebView(web)
        web.setBackgroundColor(0)
        web.addJavascriptInterface(Bridge(), "Android")
        web.loadUrl(ASSET_URL)
        setContentView(web)
        web.post { firstRunPermissions() }   // v6.38 owner request: every permission asked together on the first open
        boostRefresh()
        // v6.32: Android Keystore work used to run here BEFORE the first frame (visible freeze on open). Same migration, now in the background.
        Thread {
            try {
                SecureStore.migrateOld(this)
                SecureStore.migrateSingleToPool(this)
            } catch (ignored: Exception) { ErrLog.add("MainActivity", ignored.toString()) }
        }.start()
        NovaService.listener = { role, text ->
            runOnUiThread {
                if (!isDestroyed) {
                    web.evaluateJavascript(
                        "onNova(" + JSONObject.quote(role) + "," + JSONObject.quote(text) + ")", null
                    )
                }
            }
        }
        NovaService.learnListener = { runJs("onLearn()") }
        NovaService.publishListener = { ok, msg, url ->
            runJs("onPublish(" + ok + "," + JSONObject.quote(msg) + "," + JSONObject.quote(url) + ")")
        }
        NovaService.askListener = { q -> runJs("onAsk(" + JSONObject.quote(q) + ")") }
        NovaService.siteListener = { ok, msg, html, saved ->
            runJs("onSite(" + ok + "," + JSONObject.quote(msg) + "," + JSONObject.quote(html) + "," + JSONObject.quote(saved) + ")")
        }
        NovaService.imageListener = { ok, msg, thumb, saved ->
            runJs("onImage(" + ok + "," + JSONObject.quote(msg) + "," + JSONObject.quote(thumb) + "," + JSONObject.quote(saved) + ")")
        }
        NovaService.contactChoiceListener = { candidates, complete ->
            runOnUiThread {
                if (isDestroyed || isFinishing) { complete(null) }
                else {
                    val labels = candidates.map { c ->
                        val digits = c.number.filter { it.isDigit() }
                        val masked = if (digits.length > 4) "•••• ${digits.takeLast(4)}" else "••••"
                        "${c.displayName}  ·  $masked"
                    }.toTypedArray()
                    var answered = false
                    val dialog = AlertDialog.Builder(this)
                        .setTitle("NOVA — Kaun sa contact?")
                        .setItems(labels) { _, which ->
                            if (!answered) { answered = true; complete(candidates.getOrNull(which)) }
                        }
                        .setNegativeButton("Cancel") { _, _ ->
                            if (!answered) { answered = true; complete(null) }
                        }
                        .create()
                    dialog.setOnCancelListener { if (!answered) { answered = true; complete(null) } }
                    dialog.setOnDismissListener { if (!answered) { answered = true; complete(null) } }
                    dialog.show()
                    dialog.window?.setLayout(
                        (resources.displayMetrics.widthPixels * 0.92f).toInt(),
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
            }
        }
        NovaService.songChoiceListener = { labels, complete ->
            runOnUiThread {
                if (isDestroyed || isFinishing) { complete(null) }
                else {
                    var answered = false
                    val dialog = AlertDialog.Builder(this)
                        .setTitle("NOVA — Kaun sa gaana?")
                        .setItems(labels.toTypedArray()) { _, which ->
                            if (!answered) { answered = true; complete(which) }
                        }
                        .setNegativeButton("Cancel") { _, _ ->
                            if (!answered) { answered = true; complete(null) }
                        }
                        .create()
                    dialog.setOnCancelListener { if (!answered) { answered = true; complete(null) } }
                    dialog.show()
                    dialog.window?.setLayout(
                        (resources.displayMetrics.widthPixels * 0.92f).toInt(),
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
            }
        }
        NovaService.permRequest = { what ->
            if (what == "contacts") runOnUiThread { if (!isDestroyed) requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), REQ_CONTACTS) }
            if (what == "call") runOnUiThread { if (!isDestroyed) requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), REQ_CALL) }
        }
        listenerOwned = true
        // Prepare verified offline models silently as soon as the app is opened.
        web.postDelayed({ if (!isDestroyed) AutoSetup.start(this) }, 1500L)   // v6.49: the first frames and the intro run before any model / disk work
        // NOVA never starts by itself: opening the app does not start the microphone. Only the owner's ACTIVATE tap does.
        handleIntent(intent)
    }

    /** The page is a local asset only: no file/content access, no navigation away, no network sub-requests. */
    private fun hardenWebView(w: WebView) {
        val s = w.settings
        s.javaScriptEnabled = true
        s.mediaPlaybackRequiresUserGesture = false   // lets the opening sound play without a tap
        s.domStorageEnabled = true                  // remembers the sound on/off choice
        s.allowFileAccess = false              // file:///android_asset/ is always readable regardless
        s.allowContentAccess = false
        s.allowFileAccessFromFileURLs = false
        s.allowUniversalAccessFromFileURLs = false
        s.setGeolocationEnabled(false)
        s.javaScriptCanOpenWindowsAutomatically = false
        s.setSupportMultipleWindows(false)
        s.textZoom = s.textZoom.coerceAtMost(115)   // v6.38: big system font sizes no longer push buttons off small screens
        w.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                // Never navigate anywhere except our own page.
                return request?.url?.toString() != ASSET_URL
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val u = request?.url?.toString() ?: return blocked()
                return if (u.startsWith(ASSET_PREFIX) || u.startsWith("data:") || u.startsWith("blob:")) null else blocked()
            }

            /** v6.81: Android kills the WebView process on low-RAM phones. Without this the whole app crashes; now the page is rebuilt (at most once per 5 s). */
            override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                ErrLog.add("WebView", "render process gone, crashed=" + (detail?.didCrash() ?: false))
                try { (view?.parent as? ViewGroup)?.removeView(view); view?.destroy() } catch (ignored: Exception) { }
                val now = System.currentTimeMillis()
                if (now - lastRenderRecover > 5_000L) {
                    lastRenderRecover = now
                    runOnUiThread { if (!isFinishing && !isDestroyed) recreate() }
                } else runOnUiThread { if (!isFinishing) finish() }
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                if (url != null && !url.startsWith(ASSET_PREFIX)) view?.stopLoading()
            }
        }
    }

    /** Smoothness: use the phone's highest refresh rate (90/120 Hz) while NOVA is on screen. */
    @Suppress("DEPRECATION")
    private fun boostRefresh() {
        try {
            val modes = windowManager.defaultDisplay.supportedModes
            var best: android.view.Display.Mode? = null
            for (m in modes) { if (best == null || m.refreshRate > best.refreshRate) best = m }
            if (best != null) {
                val lp = window.attributes
                lp.preferredDisplayModeId = best.modeId
                window.attributes = lp
            }
        } catch (e: Throwable) {
        }
    }

    private fun blocked() = WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))

    /** Back: the page decides (close sheet / settings, play the closing animation, or leave). */
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        web.evaluateJavascript("(window.novaBack?novaBack():'exit')") { r ->
            if (r == null || r.contains("exit")) moveTaskToBack(true)
        }
    }

    @Suppress("DEPRECATION")
    private fun vibe(ms: Long) {
        try {
            val v: android.os.Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
                getSystemService(android.os.VibratorManager::class.java)?.defaultVibrator
            } else {
                getSystemService(VIBRATOR_SERVICE) as? android.os.Vibrator
            }
            if (v == null) return
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(android.os.VibrationEffect.createOneShot(ms, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                v.vibrate(ms)
            }
        } catch (e: Throwable) {
        }
    }

    override fun onResume() {
        super.onResume()
        NovaService.uiVisible = true
        pushFlow("resumed")
        nextSpecialPermission()   // v6.38: continues the one-time permission chain after the user comes back from a Settings page
        autoActivate()            // v6.94: NOVA starts by itself once the microphone is allowed and the Settings chain is finished
        // v6.21: health dialogs are never shown automatically on resume. The automatic AutoTest report is in Settings > TEST REPORT.
        // v6.33: quiet automatic self test (no popup/sound/torch/screen jump) once keys + mic permission exist, and again when they change.
        AutoTest.kick(this) { sum, rep -> runJs("onAutoTest(" + JSONObject.quote(sum) + "," + JSONObject.quote(rep) + ")") }
    }

    @Volatile private var lastAutoActivateAt = 0L

    /**
     * v6.94: no ACTIVATE tap needed after install. When the app is on screen, the microphone is allowed, the Settings chain has finished and the owner
     * did not press STOP on purpose, NOVA starts by itself. (Android only allows a microphone service to start while the app is visible.)
     */
    private fun autoActivate() {
        try {
            val now = System.currentTimeMillis()
            if (NovaService.running || now - lastAutoActivateAt < 15000L) return
            val prefs = getSharedPreferences("nova_perm", Context.MODE_PRIVATE)
            if (prefs.getBoolean("user_stopped", false) || prefs.getBoolean("special_chain", false)) return
            if (!granted(Manifest.permission.RECORD_AUDIO)) return
            lastAutoActivateAt = now
            val r = startFlow(true)   // afterResult = true: this path never opens a permission dialog
            if (r == "started") pushFlow(r)
        } catch (e: Exception) { ErrLog.add("MainActivity", "autoActivate " + e.javaClass.simpleName) }
    }

    @Volatile private var lastKeyClip = ""
    @Volatile private var lastClipStamp = 0L

    /** v6.94: a Groq / OpenRouter / Gemini key just copied is saved by itself (encrypted, never shown). Only unmistakable key shapes are taken. */
    private fun autoPickKeys() {
        try {
            val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val desc = cm.primaryClipDescription ?: return
            if (!desc.hasMimeType(android.content.ClipDescription.MIMETYPE_TEXT_PLAIN)) return
            val stamp = desc.timestamp
            if (stamp == lastClipStamp) return   // the clipboard did not change: its content is not read again (no "pasted from clipboard" notice)
            lastClipStamp = stamp
            val t = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()?.trim() ?: return
            if (t == lastKeyClip) return
            val kind = KeyClipRules.kind(t) ?: return
            val saved = when (kind) {
                KeyClipRules.GROQ -> if (SecureStore.getGroq(this) == t) true else SecureStore.saveGroq(this, t)
                KeyClipRules.OPENROUTER -> if (SecureStore.getSlot(this, "openrouter") == t) true else SecureStore.saveSlot(this, "openrouter", t)
                else -> {
                    val cur = SecureStore.getKeys(this)
                    if (cur.contains(t)) true else if (cur.size >= 10) false else SecureStore.saveKeys(this, cur + t)
                }
            }
            lastKeyClip = t
            if (saved) android.widget.Toast.makeText(this, KeyClipRules.label(kind) + " key save ho gayi", android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: Exception) { ErrLog.add("MainActivity", "autoPickKeys " + e.javaClass.simpleName) }
    }

    override fun onPause() {
        NovaService.uiVisible = false
        AutoTest.stop()   // Part 10: the 5-second feature watcher sleeps while the screen is closed
        super.onPause()
    }

    override fun onNewIntent(i: Intent?) {
        super.onNewIntent(i)
        setIntent(i)
        handleIntent(i)
    }

    private var clickTts: TextToSpeech? = null
    private var clickTtsReady = false

    /** Speaker icon while NOVA is not ACTIVATED: the phone's own TTS, one message, no microphone involved. Main thread. */
    private fun speakLocal(text: String) {
        val existing = clickTts
        if (existing != null && clickTtsReady) { existing.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nova_click"); return }
        if (existing != null) return
        clickTts = TextToSpeech(this) { st ->
            clickTtsReady = st == TextToSpeech.SUCCESS
            if (clickTtsReady) clickTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nova_click")
        }
    }

    override fun onDestroy() {
        try { clickTts?.shutdown() } catch (e: Exception) { }
        clickTts = null
        NovaService.uiVisible = false
        if (listenerOwned) {
            NovaService.listener = null
            NovaService.learnListener = null
            NovaService.publishListener = null
            NovaService.siteListener = null
            NovaService.askListener = null
            NovaService.imageListener = null
            NovaService.permRequest = null
            NovaService.contactChoiceListener = null
            NovaService.songChoiceListener = null
        }
        testTts?.shutdown()
        testTts = null
        (web.parent as? ViewGroup)?.removeView(web)
        web.removeJavascriptInterface("Android")
        web.destroy()
        super.onDestroy()
    }

    // No auto-start of any kind: intents never start the microphone service.
    private fun handleIntent(i: Intent?) { i?.removeExtra("autostart") }

    private fun isDefaultAssistant(): Boolean = try {
        (Settings.Secure.getString(contentResolver, "assistant") ?: "").startsWith("$packageName/")
    } catch (e: Exception) { false }

    /** Part 9D: Do Not Disturb access (needed only for "ringer silent"). Below Android 6 there is no such gate, so it counts as granted. */
    private fun dndGranted(): Boolean = try {
        if (Build.VERSION.SDK_INT < 23) true
        else getSystemService(android.app.NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true
    } catch (e: Exception) { false }

    private fun granted(p: String) =
        checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    /**
     * Requests missing permissions (only reached from a user tap or a user-tapped notification),
     * then starts NOVA. Returns a short status word for the UI: started / perm / nomic.
     * afterResult=true means Android just answered a permission dialog, so we never ask again (no loop).
     */
    private fun startFlow(afterResult: Boolean): String {
        val prefs = getSharedPreferences("nova_perm", Context.MODE_PRIVATE)
        if (!granted(Manifest.permission.RECORD_AUDIO)) {
            if (afterResult) return "nomic"
            if (prefs.getBoolean("start_asked", false)) return "nomic"
            val ask = mutableListOf(Manifest.permission.RECORD_AUDIO)
            if (!granted(Manifest.permission.READ_CONTACTS)) ask.add(Manifest.permission.READ_CONTACTS)
            if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS)) ask.add(Manifest.permission.POST_NOTIFICATIONS)
            prefs.edit().putBoolean("start_asked", true).apply()
            runOnUiThread { requestPermissions(ask.toTypedArray(), REQ_START) }
            return "perm"
        }
        if (!afterResult && Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS) && !prefs.getBoolean("notif_asked", false)) {
            prefs.edit().putBoolean("notif_asked", true).apply()
            runOnUiThread { requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_START) }
            return "perm"
        }
        cfg.active = true
        val svc = Intent(this, NovaService::class.java)
        runOnUiThread {
            try {
                startForegroundService(svc)
            } catch (e: Exception) {
                cfg.active = false
                pushFlow("start_failed")
            }
        }
        return "started"
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQ_ALL -> {
                // onResume (which follows this callback) continues with the special-access Settings pages
                if (granted(Manifest.permission.READ_CALENDAR)) { try { CalendarRead.scheduleUpcoming(this) } catch (e: Exception) { ErrLog.add("MainActivity", e.toString()) } }
                pushFlow("basics_done")
            }
            REQ_START -> pushFlow(startFlow(true))
            REQ_BASIC -> pushFlow("basics_done")
            REQ_CONTACTS -> {
                val ok = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
                pushFlow(if (ok) "contacts_ok" else "contacts_denied")
            }
            REQ_CALENDAR -> {
                val ok = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
                val count = if (ok) CalendarRead.scheduleUpcoming(this) else 0
                pushFlow(if (ok) "calendar_ok:$count" else "calendar_denied")
            }
            REQ_VIDEO -> {
                val cameraOk = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                val micOk = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                pushFlow(if (cameraOk && micOk) "video_permission_ok" else "video_permission_denied")
            }
        }
    }

    /**
     * v6.38 owner request: on the very first open ask for all normal permissions in ONE Android dialog
     * (microphone, camera, contacts, calendar, notifications). Android does not allow the special-access items
     * (overlay, write settings, battery, exact alarm, notification access, accessibility) in that dialog, so they follow
     * one after another as Settings pages, each opened at most once. Nothing is granted by NOVA itself.
     */
    private fun firstRunPermissions() {
        val prefs = getSharedPreferences("nova_perm", Context.MODE_PRIVATE)
        // v6.84: the chain is no longer one-shot. A new SetupChainRules.VERSION (a release that adds a permission) gives every phone one more
        // round; inside a version it re-runs on later app opens only while something is still missing, at most MAX_RUNS times.
        if (prefs.getInt("setup_ver", 0) != SetupChainRules.VERSION) {
            val e = prefs.edit().putInt("setup_ver", SetupChainRules.VERSION).putInt("setup_runs", 0)
            for (id in SetupChainRules.ORDER) e.remove("sp_open_" + id)
            e.apply()
        }
        val ask = ArrayList<String>()
        for (p in listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA, Manifest.permission.READ_CONTACTS, Manifest.permission.READ_CALENDAR, Manifest.permission.CALL_PHONE)) {
            if (!granted(p)) ask.add(p)
        }
        if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS)) ask.add(Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT >= 31 && !granted(Manifest.permission.BLUETOOTH_CONNECT)) ask.add(Manifest.permission.BLUETOOTH_CONNECT)   // v6.97-wip9: watch mic over Bluetooth
        val runs = prefs.getInt("setup_runs", 0)
        val opens = specialOpenCounts(prefs)
        val specialMissing = missingSpecial().keys.filter { (opens[it] ?: 0) < SetupChainRules.maxOpens(it) }
        if (!SetupChainRules.shouldRun(ask.size + specialMissing.size, runs)) return
        prefs.edit().putInt("setup_runs", runs + 1).putBoolean("special_chain", true).putStringSet("special_done", emptySet()).apply()
        if (ask.isEmpty()) { nextSpecialPermission(); return }
        try { requestPermissions(ask.toTypedArray(), REQ_ALL) } catch (e: Exception) { nextSpecialPermission() }
    }

    private fun specialOpenCounts(prefs: android.content.SharedPreferences): Map<String, Int> =
        SetupChainRules.ORDER.associateWith { prefs.getInt("sp_open_" + it, 0) }

    /** id -> Settings page, for every special access that is still missing right now (nothing is granted by NOVA; the page is only opened). */
    private fun missingSpecial(): LinkedHashMap<String, Intent> {
        val pkgUri = Uri.parse("package:" + packageName)
        fun enabledIn(key: String): Boolean = try {
            (Settings.Secure.getString(contentResolver, key) ?: "").contains(packageName)
        } catch (e: Exception) { true }
        val steps = LinkedHashMap<String, Intent>()
        if (!Settings.canDrawOverlays(this)) steps["overlay"] = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri)
        if (!Settings.System.canWrite(this)) steps["write"] = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, pkgUri)
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) steps["battery"] = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri)
        } catch (e: Exception) { ErrLog.add("MainActivity", e.toString()) }
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                val am = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                if (!am.canScheduleExactAlarms()) steps["alarm"] = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkgUri)
            } catch (e: Exception) { ErrLog.add("MainActivity", e.toString()) }
        }
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                if (!nm.canUseFullScreenIntent()) steps["fullscreen"] = Intent("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT", pkgUri)
            } catch (e: Exception) { ErrLog.add("MainActivity", e.toString()) }
        }
        try {
            if (!packageManager.canRequestPackageInstalls()) steps["install"] = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, pkgUri)   // v6.62: needed for the in-app NOVA update
        } catch (e: Exception) { ErrLog.add("MainActivity", e.toString()) }
        if (!enabledIn("enabled_notification_listeners")) steps["listener"] = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        if (!enabledIn("enabled_accessibility_services")) steps["access"] = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        // v6.84: the phone brand's own "autostart / background" screen (Xiaomi, Oppo, Vivo, Huawei ...). Android has no way to read whether it is ON,
        // so it is offered at most MAX_OPENS times in total, and only on brands that PowerGuideRules knows.
        try {
            val own = PowerGuideRules.targets(Build.MANUFACTURER ?: "").map { Intent().setComponent(android.content.ComponentName(it.pkg, it.cls)) }
                .firstOrNull { it.resolveActivity(packageManager) != null }
            if (own != null) steps["autostart"] = own
        } catch (e: Exception) { ErrLog.add("MainActivity", e.toString()) }
        return steps
    }

    /** Opens the next missing special-access Settings page (once per run, MAX_OPENS in total). Called from onResume while a chain run is active. */
    private fun nextSpecialPermission() {
        val prefs = getSharedPreferences("nova_perm", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("special_chain", false)) return
        val done = HashSet(prefs.getStringSet("special_done", emptySet()) ?: emptySet())
        val steps = missingSpecial()
        while (true) {
            val id = SetupChainRules.nextStep(steps.keys, done, specialOpenCounts(prefs)) ?: break
            val intent = steps[id]
            done.add(id)
            prefs.edit().putStringSet("special_done", done).putInt("sp_open_" + id, prefs.getInt("sp_open_" + id, 0) + 1).apply()
            if (intent == null) continue
            var target: Intent = intent
            if ((id == "access" || id == "listener") && Build.VERSION.SDK_INT >= 33) {
                // v6.94: Android 13+ greys these switches for apps installed from a file. Round 1: tell what to expect. Round 2: open App info,
                // where the 3-dot menu has "Allow restricted settings". Round 3: the switch page again.
                val before = prefs.getInt("sp_open_" + id, 0) - 1
                if (before == 1) {
                    target = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + packageName))
                    android.widget.Toast.makeText(this, "NOVA ki info khuli hai: upar teen dot menu me 'Allow restricted settings' dabao, phir wapas aao", android.widget.Toast.LENGTH_LONG).show()
                } else if (before == 0) {
                    android.widget.Toast.makeText(this, "Agar NOVA ka switch grey ho ya 'restricted' likha aaye, to wapas aao: agli baar NOVA khud sahi jagah khol dega", android.widget.Toast.LENGTH_LONG).show()
                }
            }
            try { startActivity(target); return } catch (e: Exception) { /* page missing on this phone: try the next one */ }
        }
        prefs.edit().putBoolean("special_chain", false).apply()
        pushFlow("perm_all_done")
    }

    /** Tells the page the result of an asynchronous action (the page defines onFlow). */
    private fun pushFlow(status: String) {
        runOnUiThread {
            if (!isDestroyed) {
                web.evaluateJavascript(
                    "if(window.onFlow)onFlow(" + JSONObject.quote(status) + ")", null
                )
            }
        }
    }

    // ---- FIXIMG1: picked photo + AI image (explicit user taps only)
    private var pickedPhoto: ByteArray? = null
    @Volatile private var imgBusy = false

    @Volatile private var pendingExportChat: String? = null
    @Volatile private var lastRenderRecover = 0L

    private fun runJs(js: String) {
        runOnUiThread { if (!isDestroyed) web.evaluateJavascript(js, null) }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_SPEECH) {
            val t = if (resultCode == RESULT_OK) data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty() else ""
            runJs("onSystemVoice(" + JSONObject.quote(t) + ")")
            return
        }
        if (resultCode != RESULT_OK) return
        if (requestCode == REQ_SITE_MEDIA) { handleSiteMedia(data); return }
        val uri = data?.data ?: return
        if (requestCode == REQ_PPN) {
            Thread {
                val ok = try {
                    val raw = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (raw == null || raw.size < 100 || raw.size > 2_000_000) false else {
                        val f = PorcupineWake.ppnFile(this)
                        f.parentFile?.mkdirs()
                        f.writeBytes(raw)
                        true
                    }
                } catch (ignored: Exception) { false }
                runJs("onPorcupineFile(" + JSONObject.quote(if (ok) "ok" else "fail") + ")")
            }.start()
            return
        }
        if (requestCode == REQ_EXPORT_TEACH) {
            Thread {
                val ok = try {
                    val out = contentResolver.openOutputStream(uri) ?: throw java.io.IOException("No output stream")
                    out.use { it.write(TeachStore.exportJson(this).toByteArray(Charsets.UTF_8)) }
                    true
                } catch (ignored: Exception) { false }
                runJs("onTeachBackup(" + JSONObject.quote(if (ok) "export_ok" else "export_fail") + ")")
            }.start()
            return
        }
        if (requestCode == REQ_EXPORT_CHAT) {
            val cid = pendingExportChat
            pendingExportChat = null
            Thread {
                val ok = try {
                    val text = if (cid == null) null else ChatSessions.exportText(this, cid)
                    if (text == null) false else {
                        val out = contentResolver.openOutputStream(uri) ?: throw java.io.IOException("No output stream")
                        out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                        true
                    }
                } catch (ignored: Exception) { false }
                runJs("onChatExport(" + JSONObject.quote(if (ok) "export_ok" else "export_fail") + ")")
            }.start()
            return
        }
        if (requestCode == REQ_EXPORT_MEM) {
            Thread {
                val ok = try {
                    val out = contentResolver.openOutputStream(uri) ?: throw java.io.IOException("No output stream")
                    out.use { it.write(PersonalMemory.exportText(PersonalMemoryStore.load(this)).toByteArray(Charsets.UTF_8)) }
                    true
                } catch (ignored: Exception) { false }
                runJs("onMemBackup(" + JSONObject.quote(if (ok) "export_ok" else "export_fail") + ")")
            }.start()
            return
        }
        if (requestCode == REQ_IMPORT_MEM) {
            Thread {
                val code = try {
                    val raw = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (raw == null || raw.size > 80_000) "import_invalid" else {
                        val r = PersonalMemoryStore.importFrom(this, String(raw, Charsets.UTF_8))
                        if (r == null) "import_invalid" else "import_ok:" + PersonalMemory.importReply(r)
                    }
                } catch (ignored: Exception) { "import_invalid" }
                runJs("onMemBackup(" + JSONObject.quote(code) + ")")
            }.start()
            return
        }
        if (requestCode == REQ_IMPORT_TEACH) {
            Thread {
                val result = try {
                    val raw = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (raw == null || raw.size > 20_000) "invalid" else TeachStore.importJson(this, String(raw, Charsets.UTF_8))
                } catch (ignored: Exception) { "invalid" }
                runJs("onTeachBackup(" + JSONObject.quote(if (result == "ok") "import_ok" else "import_" + result) + ")")
            }.start()
            return
        }
        if (requestCode != REQ_PICK) return
        Thread {
            try {
                val raw = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (raw == null || raw.isEmpty()) { runJs("onPhotoErr('Photo padh nahi paya')"); return@Thread }
                if (raw.size > 30000000) { runJs("onPhotoErr('Photo bahut badi hai')"); return@Thread }
                val small = ImageGen.shrink(raw, 1280)
                if (small == null) { runJs("onPhotoErr('Ye photo format nahi padh paya')"); return@Thread }
                pickedPhoto = small
                val thumb = ImageGen.shrink(small, 160) ?: small
                runJs("onPhoto(" + JSONObject.quote(ImageGen.b64(thumb)) + ")")
            } catch (e: Throwable) { runJs("onPhotoErr('Photo padhne mein dikkat')") }
        }.start()
    }

    // v7.0 website media: photos and short videos for the next website / website edit (kept in the app folder, embedded after the page is written)
    private fun handleSiteMedia(data: Intent?) {
        val uris = ArrayList<Uri>()
        data?.data?.let { uris.add(it) }
        val cd = data?.clipData
        if (cd != null) for (i in 0 until cd.itemCount) cd.getItemAt(i).uri?.let { uris.add(it) }
        if (uris.isEmpty()) return
        Thread {
            var added = 0
            var err = ""
            for (u in uris.distinct().take(SiteMedia.MAX_ITEMS)) {
                try {
                    val mime = contentResolver.getType(u).orEmpty()
                    val raw = contentResolver.openInputStream(u)?.use { it.readBytes() }
                    if (raw == null || raw.isEmpty()) { err = "Ek file padh nahi paya"; continue }
                    if (mime.startsWith("video/")) {
                        val r = SiteMedia.add(this, "vid", if (mime.contains("webm")) "webm" else "mp4", raw, 0, 0)
                        if (r == null) added++ else err = r
                    } else {
                        if (raw.size > 30_000_000) { err = "Photo bahut badi hai"; continue }
                        var side = 1600
                        var jpg = ImageGen.shrink(raw, side)
                        while (jpg != null && jpg.size > SiteMedia.MAX_IMG_BYTES && side > 640) { side -= 320; jpg = ImageGen.shrink(raw, side) }
                        if (jpg == null) { err = "Ek photo ka format nahi padh paya"; continue }
                        val o = android.graphics.BitmapFactory.Options()
                        o.inJustDecodeBounds = true
                        android.graphics.BitmapFactory.decodeByteArray(jpg, 0, jpg.size, o)
                        val r = SiteMedia.add(this, "img", "jpg", jpg, o.outWidth, o.outHeight)
                        if (r == null) added++ else err = r
                    }
                } catch (e: Throwable) { err = "Media padhne mein dikkat" }
            }
            runJs("onSiteMedia(" + SiteMedia.count(this) + "," + added + "," + JSONObject.quote(err) + ")")
        }.start()
    }

    @Volatile private var pubBusy = false

    private fun startPublish(subject: String, html: String) {
        pubBusy = true
        Thread {
            var js: String
            try {
                val r = GitHubPublish.run(this, subject, html)
                js = "onPublish(" + r.ok + "," + JSONObject.quote(r.msg) + "," + JSONObject.quote(r.url) + ")"
            } catch (e: Throwable) {
                js = "onPublish(false," + JSONObject.quote("Publish karte waqt dikkat aayi") + ",\"\")"
            }
            pubBusy = false
            runJs(js)
        }.start()
    }

    private fun startImage(prompt: String, photo: ByteArray?) {
        imgBusy = true
        Thread {
            var js = ""
            try {
                val full = if (photo != null)
                    "Edit the attached photo exactly as requested and keep everything else, especially faces and identity, unchanged. Request: " + prompt
                else prompt
                val r = ImageGen.generate(this, full, photo)
                var thumb = ""
                var saved = ""
                val img = r.img
                if (r.ok && img != null) {
                    saved = ImageGen.saveToGallery(this, img) ?: ""
                    thumb = ImageGen.b64(ImageGen.shrink(img, 1024) ?: img)
                }
                val good = r.ok && thumb.isNotEmpty()
                js = "onImage(" + good + "," + JSONObject.quote(r.msg) + "," + JSONObject.quote(thumb) + "," + JSONObject.quote(saved) + ")"
            } catch (e: Throwable) {
                js = "onImage(false," + JSONObject.quote("Image banate waqt dikkat aayi") + ",\"\",\"\")"
            }
            imgBusy = false
            runJs(js)
        }.start()
    }

    // ---- FIXTEST: image API test (asks first, may cost a little)
    private fun runImageTest() {
        imgBusy = true
        Thread {
            var line = ""
            try {
                val r = ImageGen.generateDirect(this, "A plain blue circle on a white background, minimal", null)
                val img = r.img
                if (r.ok && img != null) {
                    val saved = ImageGen.saveToGallery(this, img)
                    line = "PASS  Image banana: image mili (" + (img.size / 1024) + " KB). Gallery save: " + (saved ?: "FAIL")
                } else {
                    line = "FAIL  Image banana: " + r.msg
                }
            } catch (e: Throwable) {
                line = "FAIL  Image test beech mein ruk gaya"
            }
            imgBusy = false
            runJs("onSelfTest(" + JSONObject.quote(line) + ")")
        }.start()
    }

    // FIXFREE2: tests each free image provider one by one (text-only prompt, no photo)
    private fun runFreeImageTest() {
        imgBusy = true
        Thread {
            try {
                FreeImage.testAll(this) { l -> runJs("onSelfTest(" + JSONObject.quote(l) + ")") }
            } catch (e: Throwable) {
                runJs("onSelfTest(" + JSONObject.quote("FAIL  Free image test beech mein ruk gaya") + ")")
            }
            imgBusy = false
        }.start()
    }

    // ------------------------------------------------------------------ v6.86/6.87 connector helpers (activity level: also used by the voice/chat connect flow)
    private fun clipToken(id: String): String? = try {
        val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val t = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this@MainActivity)?.toString()?.trim()
        if (t != null && ConnectorCatalog.looksLikeToken(id, t)) t else null
    } catch (e: Exception) { null }

    private fun pushConn(id: String, status: String, msg: String) {
        runJs("if(window.onConn)onConn(" + JSONObject.quote(id) + "," + JSONObject.quote(status) + "," + JSONObject.quote(msg) + ")")
    }

    @Volatile private var flowBusy = false

    /** Verifies [token] with the provider, stores it encrypted, tells the page. [announce] = also tell the owner by voice/chat (connect started by voice or chat). */
    private fun connectorVerifyAsync(id: String, token: String, fromClip: Boolean, announce: Boolean = false) {
        pushConn(id, "checking", "")
        Thread({
            try {
                val r = ConnectorApi.verify(id, token)
                val name = ConnectorCatalog.byId(id)?.name ?: id
                if (r.first && ConnectorStore.save(this@MainActivity, id, token, r.second)) {
                    if (fromClip) runOnUiThread { try { (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).clearPrimaryClip() } catch (e: Exception) { } }
                    if (ConnectorFlow.active() == id) ConnectorFlow.clear()
                    if (announce) NovaService.instance?.announce("$name connect ho gaya (${r.second}). Abhi sirf padhna allowed hai. " +
                        "Likhne ke liye bolo: ${name.lowercase()} write on karo")
                    pushConn(id, "ok", r.second)
                } else {
                    val why = if (r.first) "Token save nahi ho paya" else r.second
                    if (announce) { ConnectorFlow.clear(); NovaService.instance?.announce("$name connect nahi hua. $why. Dobara connect karo bolo") }
                    pushConn(id, "fail", why)
                }
            } catch (e: Throwable) {
                if (announce) ConnectorFlow.clear()
                pushConn(id, "fail", "Connect nahi ho paya")
            } finally {
                flowBusy = false
            }
        }, "nova-conn-verify").start()
    }

    /** NOVA came back to the front while a voice/chat connect is waiting for the owner's token: take it from the clipboard. */
    private fun connectFromFlow() {
        val id = ConnectorFlow.active()
        if (id.isEmpty() || flowBusy) return
        val t = clipToken(id) ?: return
        flowBusy = true
        connectorVerifyAsync(id, t, true, true)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) { connectFromFlow(); autoPickKeys() }   // Android lets only the focused app read the clipboard
    }

    inner class Bridge {
        @JavascriptInterface
        fun vibrate(ms: Int) { vibe(ms.toLong().coerceIn(5L, 400L)) }

        /** Opens the phone's own voice typing (the same engine the keyboard mic uses). The text comes back through onSystemVoice(). */
        @JavascriptInterface
        fun systemVoice(): String = try {
            runOnUiThread {
                try {
                    val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_PROMPT, "Boliye...")
                        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    @Suppress("DEPRECATION")
                    this@MainActivity.startActivityForResult(i, REQ_SPEECH)
                } catch (e: Exception) { runJs("onSystemVoice('')") }
            }
            "ok"
        } catch (e: Exception) { "fail" }

        @JavascriptInterface
        fun running() = NovaService.running

        @JavascriptInterface
        fun hasKey() = SecureStore.hasKeys(this@MainActivity)

        /** Keys are only ever written here, never sent back to the page. */
        @JavascriptInterface
        fun saveKeys(raw0: String): String = try {
            val raw = if (raw0.length > 4000) "" else raw0
            val keys = Logic.parseKeys(raw)
            if (keys.isEmpty()) "invalid"
            else if (SecureStore.saveKeys(this@MainActivity, keys)) "ok:" + keys.size
            else "fail"
        } catch (e: Exception) { "fail" }

        @JavascriptInterface
        fun keyCount(): Int = SecureStore.getKeys(this@MainActivity).size

        @JavascriptInterface
        fun clearKey() { SecureStore.clearKeys(this@MainActivity) }


        // ---- v6.40 schedule editor (days + time + how important, own words)
        @JavascriptInterface
        fun getSchedules(): String {
            val a = JSONArray()
            for (e in ScheduledTaskStore.list(this@MainActivity)) {
                a.put(JSONObject().put("id", e.id).put("label", e.spec.label).put("kind", e.spec.kind.name)
                    .put("time", String.format(java.util.Locale.ROOT, "%02d:%02d", e.spec.hour, e.spec.minute))
                    .put("days", ScheduledTaskRules.dayText(e.spec.days)).put("repeat", e.spec.repeatDaily)
                    .put("prio", e.spec.prio).put("note", e.spec.note))
            }
            return a.toString()
        }

        @JavascriptInterface
        fun setSchedulePrio(id0: String, prio: Int, note: String): String {
            val id = id0.take(40)
            return if (ScheduledTaskStore.update(this@MainActivity, id, prio.coerceIn(0, 2), note.trim().take(120)) != null) "ok" else "notfound"
        }

        @JavascriptInterface
        fun cancelSchedule(id0: String): String {
            val id = id0.take(40)
            return if (ScheduledTaskStore.cancel(this@MainActivity, id)) "ok" else "notfound"
        }

        /** time = "HH:MM" (24 h). daysMask: Monday = 1 ... Sunday = 64, 0 = no day filter. Returns the store status or badtime / nolabel. */
        @JavascriptInterface
        fun addSchedule(label: String, time0: String, daysMask: Int, repeat: Boolean, prio: Int, note: String): String {
            val time = time0.take(16)
            val t = Regex("^\\s*(\\d{1,2}):(\\d{2})\\s*$").find(time) ?: return "badtime"
            val hh = t.groupValues[1].toInt()
            val mm = t.groupValues[2].toInt()
            if (hh !in 0..23 || mm !in 0..59) return "badtime"
            if (label.isBlank()) return "nolabel"
            val spec = ScheduledTaskRules.Spec(hh, mm, repeat, ScheduledTaskRules.Kind.REMINDER, label.trim().take(100),
                days = daysMask and 127, prio = prio.coerceIn(0, 2), note = note.trim().take(120))
            return ScheduledTaskStore.add(this@MainActivity, spec)
        }

        @JavascriptInterface
        fun stopSpeak() { NovaService.instance?.stopSpeaking() }

        @JavascriptInterface
        fun setSilentMode(on: Boolean): String {
            cfg.silentMode = on
            return if (on) "on" else "off"
        }

        @JavascriptInterface
        fun getSilentMode(): Boolean = cfg.silentMode

        @JavascriptInterface
        fun sendText(t: String): String {
            // v7.0: a long website brief is kept whole (was cut at 500); every other typed sentence keeps the old 500 limit.
            val limit = if (t.length > 500 && SiteIntent.parse(t.take(SiteIntent.MAX_RAW)) != null) SiteIntent.MAX_RAW else 500
            return NovaService.instance?.typed(t.take(limit)) ?: "off"
        }

        // ---- v6.69 chat sessions (New Chat, history list, resume). The page shows the result; nothing here speaks or acts on the phone.
        @JavascriptInterface
        fun newChat(): String {
            val svc = NovaService.instance
            if (svc != null && svc.isBusyNow()) return JSONObject().put("error", "busy").toString()
            svc?.resetChatContext()
            val id = ChatSessions.newChat(this@MainActivity)
            return JSONObject().put("id", id).toString()
        }

        @JavascriptInterface
        fun listChats(): String {
            val cur = ChatSessions.currentId(this@MainActivity)
            val a = JSONArray()
            for (c in ChatSessions.list(this@MainActivity)) a.put(chatJson(c, cur, ""))
            return a.toString()
        }

        private fun chatJson(c: ChatSessionRules.Chat, cur: String?, snippet: String): JSONObject =
            JSONObject().put("id", c.id).put("title", c.title).put("updated", c.updated).put("count", c.count)
                .put("label", ChatSessionRules.countLabel(c.count)).put("current", c.id == cur).put("pinned", c.pinned).put("snippet", snippet)

        /** v6.80: history list with search. Empty query = every chat. Pinned first, then newest. {"ok":true,"items":[...]} or {"error":...}. */
        @JavascriptInterface
        fun searchChats(q: String): String = try {
            val cur = ChatSessions.currentId(this@MainActivity)
            val a = JSONArray()
            for (h in ChatSessions.search(this@MainActivity, q.take(ChatSessionRules.QUERY_MAX))) a.put(chatJson(h.chat, cur, h.snippet))
            JSONObject().put("ok", true).put("items", a).toString()
        } catch (e: Exception) { ErrLog.add("MainActivity", e.toString()); JSONObject().put("error", "read").toString() }

        @JavascriptInterface
        fun renameChat(id: String, title: String): String =
            if (!ChatSessionRules.validId(id)) "missing" else ChatSessions.rename(this@MainActivity, id, title.take(200))

        @JavascriptInterface
        fun pinChat(id: String, on: Boolean): String =
            if (!ChatSessionRules.validId(id)) "missing" else ChatSessions.setPinned(this@MainActivity, id, on)

        /** Every chat is deleted (the page asks twice first). Not while NOVA is answering. "ok" / "busy" / "fail". */
        @JavascriptInterface
        fun deleteAllChats(): String {
            val svc = NovaService.instance
            if (svc != null && svc.isBusyNow()) return "busy"
            val ok = ChatSessions.deleteAll(this@MainActivity)
            svc?.resetChatContext()
            return if (ok) "ok" else "fail"
        }

        /** Opens the system "save file" box for one chat. The text is written in onActivityResult. */
        @JavascriptInterface
        fun exportChat(id: String): String {
            if (!ChatSessionRules.validId(id)) return "missing"
            if (ChatSessions.exportText(this@MainActivity, id) == null) return "missing"
            pendingExportChat = id
            val name = ChatSessions.exportName(this@MainActivity, id)
            runOnUiThread { try { startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/plain").putExtra(Intent.EXTRA_TITLE, name), REQ_EXPORT_CHAT) } catch (ignored: Exception) { runJs("onChatExport('export_fail')") } }
            return "started"
        }

        /** How much of the current chat the AI gets: {total, gemini:{sent,cut}, free:{sent,cut}}. Gemini has a big window, free providers a small one. */
        @JavascriptInterface
        fun contextInfo(): String = try {
            val id = ChatSessions.currentId(this@MainActivity)
            val msgs = if (id == null) emptyList() else ChatSessions.messages(this@MainActivity, id)
            val g = ChatSessionRules.contextCounts(msgs, FallbackContext.FULL_GEMINI_CHARS)
            val f = ChatSessionRules.contextCounts(msgs, FallbackContext.FULL_FREE_CHARS)
            JSONObject().put("total", msgs.size)
                .put("gemini", JSONObject().put("sent", g.sent).put("cut", g.cut))
                .put("free", JSONObject().put("sent", f.sent).put("cut", f.cut)).toString()
        } catch (e: Exception) { ErrLog.add("MainActivity", e.toString()); JSONObject().put("total", 0).toString() }

        @JavascriptInterface
        fun openChat(id: String): String {
            val svc = NovaService.instance
            if (svc != null && svc.isBusyNow()) return JSONObject().put("error", "busy").toString()
            if (!ChatSessionRules.validId(id) || !ChatSessions.open(this@MainActivity, id)) return JSONObject().put("error", "missing").toString()
            val msgs = ChatSessions.messages(this@MainActivity, id)
            svc?.loadChatContext(ChatSessionRules.contextTurns(msgs))
            val a = JSONArray()
            msgs.forEach { a.put(JSONObject().put("r", it.role).put("t", it.text)) }
            return JSONObject().put("id", id).put("messages", a).toString()
        }

        @JavascriptInterface
        fun deleteChat(id: String): String {
            if (!ChatSessionRules.validId(id)) return "missing"
            val wasCurrent = ChatSessions.currentId(this@MainActivity) == id
            val ok = ChatSessions.delete(this@MainActivity, id)
            if (ok && wasCurrent) NovaService.instance?.resetChatContext()
            return if (ok) "ok" else "missing"
        }

        @JavascriptInterface
        fun currentChat(): String = ChatSessions.currentId(this@MainActivity) ?: ""

        @JavascriptInterface
        fun wakeNow() { NovaService.instance?.wakeNow() }

        @JavascriptInterface
        fun selfTest(): String {
            if (SelfTest.running) return "busy"
            SelfTest.start(
                this@MainActivity,
                { l -> runJs("onSelfTest(" + JSONObject.quote(l) + ")") },
                { rep -> runJs("onSelfTestDone(" + JSONObject.quote(rep) + ")") }
            )
            return "started"
        }

        @JavascriptInterface
        fun getFreeImg(): Boolean = FreeImage.enabled(this@MainActivity)

        @JavascriptInterface
        fun setFreeImg(on: Boolean): String {
            FreeImage.setEnabled(this@MainActivity, on)
            return "ok"
        }

        @JavascriptInterface
        fun selfTestFree(): String {
            if (imgBusy) return "busy"
            runOnUiThread {
                try {
                    android.app.AlertDialog.Builder(this@MainActivity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle("Free image test karein?")
                        .setMessage("Har free provider ko ek chhota text prompt jaayega (koi photo nahi). Together aur Hugging Face tabhi jab unki key saved ho. Hugging Face par paise lag sakte hain. 1-2 minute lag sakte hain.")
                        .setPositiveButton("HAAN") { _, _ -> runFreeImageTest() }
                        .setNegativeButton("NAHI", null)
                        .show()
                } catch (e: Exception) {
                }
            }
            return "asking"
        }

        @JavascriptInterface
        fun selfTestImage(): String {
            if (!SecureStore.hasKeys(this@MainActivity)) return "nokey"
            if (imgBusy) return "busy"
            runOnUiThread {
                try {
                    android.app.AlertDialog.Builder(this@MainActivity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle("Image test karein?")
                        .setMessage("Gemini se 1 chhoti image banegi. Billing wali key par thoda paisa lag sakta hai.")
                        .setPositiveButton("HAAN") { _, _ -> runImageTest() }
                        .setNegativeButton("NAHI", null)
                        .show()
                } catch (e: Exception) {
                }
            }
            return "asking"
        }

        @JavascriptInterface
        fun copyText(t: String): String = try {
            val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("NOVA report", t.take(20000)))
            "ok"
        } catch (e: Exception) { "fail" }

        @JavascriptInterface
        fun getErrors(): String = ErrLog.dump()

        @JavascriptInterface
        fun clearErrors(): String { ErrLog.clear(); return "ok" }

        @JavascriptInterface
        fun openPowerGuide(): String = try { PowerGuide.open(this@MainActivity) } catch (e: Exception) { "fail" }

        @JavascriptInterface
        fun shareText(t: String): String {
            runOnUiThread {
                try {
                    val i = Intent(Intent.ACTION_SEND)
                    i.type = "text/plain"
                    i.putExtra(Intent.EXTRA_TEXT, t.take(20000))
                    startActivity(Intent.createChooser(i, "Report bhejo"))
                } catch (e: Exception) {
                }
            }
            return "ok"
        }

        @JavascriptInterface
        fun pickPhoto(): String {
            runOnUiThread {
                try {
                    val i = Intent(Intent.ACTION_GET_CONTENT)
                    i.type = "image/*"
                    i.addCategory(Intent.CATEGORY_OPENABLE)
                    startActivityForResult(Intent.createChooser(i, "Photo chuno"), REQ_PICK)
                } catch (e: Exception) {
                    runJs("onPhotoErr('Gallery nahi khuli')")
                }
            }
            return "ok"
        }

        @JavascriptInterface
        fun clearPhoto() { pickedPhoto = null }

        /** v7.1: yes/no question NOVA is waiting for ("" = none) and the HAAN / NAHI tap. */
        @JavascriptInterface
        fun pendingAsk(): String = NovaService.instance?.pendingPromptNow() ?: ""

        @JavascriptInterface
        fun answerAsk(yes: Boolean): String = NovaService.instance?.answerPending(yes) ?: "off"

        @JavascriptInterface
        fun pickSiteMedia(): String {
            runOnUiThread {
                try {
                    val i = Intent(Intent.ACTION_GET_CONTENT)
                    i.type = "*/*"
                    i.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/mp4", "video/webm"))
                    i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    i.addCategory(Intent.CATEGORY_OPENABLE)
                    startActivityForResult(Intent.createChooser(i, "Website ke liye photo/video chuno"), REQ_SITE_MEDIA)
                } catch (e: Exception) {
                    runJs("onSiteMedia(" + SiteMedia.count(this@MainActivity) + ",0,'Gallery nahi khuli')")
                }
            }
            return "ok"
        }

        @JavascriptInterface
        fun clearSiteMedia(): String { SiteMedia.clear(this@MainActivity); return "ok" }

        @JavascriptInterface
        fun siteMediaCount(): Int = SiteMedia.count(this@MainActivity)

        /** started / asking (waits for the HAAN tap) / busy / nokey / empty. A photo is only uploaded after that tap. */
        @JavascriptInterface
        fun makeImage(prompt: String): String {
            val p = prompt.trim().take(800)
            if (p.isEmpty()) return "empty"
            if (!SecureStore.hasKeys(this@MainActivity)) return "nokey"
            if (imgBusy) return "busy"
            val photo = pickedPhoto
            if (photo == null) { startImage(p, null); return "started" }
            runOnUiThread {
                try {
                    android.app.AlertDialog.Builder(this@MainActivity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle("Photo Google ko bhejein?")
                        .setMessage("Ye photo edit ke liye internet par Google Gemini ko bheji jayegi.")
                        .setPositiveButton("HAAN, BHEJO") { _, _ -> startImage(p, photo) }
                        .setNegativeButton("NAHI") { _, _ -> runJs("onImgCancel()") }
                        .setOnCancelListener { runJs("onImgCancel()") }
                        .show()
                } catch (e: Exception) {
                    runJs("onImgCancel()")
                }
            }
            return "asking"
        }

        /** The optional Groq key is only ever written here, never sent back to the page. ok / invalid / fail. */
        @JavascriptInterface
        fun saveGroqKey(raw0: String): String = try {
            val raw = if (raw0.length > 400) "" else raw0
            val k = raw.trim()
            if (k.length < 20 || k.length > 200 || k.any { it.isWhitespace() }) "invalid"
            else if (SecureStore.saveGroq(this@MainActivity, k)) "ok"
            else "fail"
        } catch (e: Exception) { "fail" }

        @JavascriptInterface
        fun hasGroqKey(): Boolean = SecureStore.hasGroq(this@MainActivity)

        /** v6.56 optional Porcupine wake word. The AccessKey is only written here, never sent back to the page. ok / invalid / fail. */
        @JavascriptInterface
        fun savePorcupineKey(raw0: String): String = try {
            val raw = if (raw0.length > 400) "" else raw0
            val k = raw.trim()
            if (k.length < 20 || k.length > 200 || k.any { it.isWhitespace() }) "invalid"
            else if (SecureStore.saveSlot(this@MainActivity, PorcupineWake.SLOT, k)) "ok"
            else "fail"
        } catch (e: Exception) { "fail" }

        @JavascriptInterface
        fun porcupineStatus(): String = try { PorcupineWake.status(this@MainActivity) } catch (e: Exception) { "pata nahi" }

        @JavascriptInterface
        fun pickPorcupineModel() {
            runOnUiThread { try { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), REQ_PPN) } catch (ignored: Exception) { ErrLog.add("MainActivity", ignored.toString()) } }
        }

        @JavascriptInterface
        fun clearPorcupine() {
            SecureStore.clearSlot(this@MainActivity, PorcupineWake.SLOT)
            try { PorcupineWake.ppnFile(this@MainActivity).delete() } catch (ignored: Exception) { }
        }

        /** Seconds NOVA waits after you stop speaking (2 to 10). */
        @JavascriptInterface
        fun getWaitSec(): Int = ExtCfg.waitSec(this@MainActivity)

        @JavascriptInterface
        fun setWaitSec(sec: Int): String { ExtCfg.setWaitSec(this@MainActivity, sec); return "ok" }

        @JavascriptInterface
        fun clearGroqKey() { SecureStore.clearGroq(this@MainActivity) }

        /** Optional OpenRouter key for the free AI chain. Written only here, never sent back to the page. ok / invalid / fail. */
        @JavascriptInterface
        fun saveOpenRouterKey(raw0: String): String = try {
            val raw = if (raw0.length > 400) "" else raw0
            val k = raw.trim()
            if (k.length < 20 || k.length > 200 || k.any { it.isWhitespace() }) "invalid"
            else if (SecureStore.saveSlot(this@MainActivity, "openrouter", k)) "ok"
            else "fail"
        } catch (e: Exception) { "fail" }

        @JavascriptInterface
        fun hasOpenRouterKey(): Boolean = SecureStore.hasSlot(this@MainActivity, "openrouter")

        @JavascriptInterface
        fun clearOpenRouterKey() { SecureStore.clearSlot(this@MainActivity, "openrouter") }

        /** Optional Z.ai (Zhipu GLM, free Flash models) key for the free AI chain. Written only here, never sent back. ok / invalid / fail. */
        @JavascriptInterface
        fun saveZaiKey(raw0: String): String = try {
            val raw = if (raw0.length > 400) "" else raw0
            val k = raw.trim()
            if (k.length < 20 || k.length > 200 || k.any { it.isWhitespace() }) "invalid"
            else if (SecureStore.saveSlot(this@MainActivity, "zai", k)) "ok"
            else "fail"
        } catch (e: Exception) { "fail" }

        @JavascriptInterface
        fun hasZaiKey(): Boolean = SecureStore.hasSlot(this@MainActivity, "zai")

        @JavascriptInterface
        fun clearZaiKey() { SecureStore.clearSlot(this@MainActivity, "zai") }

        /** v6.30: extra GLM keys (n = 2 or 3). When key 1's limit is used up, the chain tries key 2, then key 3. ok / invalid / fail. */
        @JavascriptInterface
        fun saveZaiKeyN(n: Int, raw0: String): String = try {
            val raw = if (raw0.length > 400) "" else raw0
            val k = raw.trim()
            if (n != 2 && n != 3) "fail"
            else if (k.length < 20 || k.length > 200 || k.any { it.isWhitespace() }) "invalid"
            else if (SecureStore.saveSlot(this@MainActivity, "zai$n", k)) "ok"
            else "fail"
        } catch (e: Exception) { "fail" }

        @JavascriptInterface
        fun hasZaiKeyN(n: Int): Boolean = (n == 2 || n == 3) && SecureStore.hasSlot(this@MainActivity, "zai$n")

        @JavascriptInterface
        fun clearZaiKeyN(n: Int) { if (n == 2 || n == 3) SecureStore.clearSlot(this@MainActivity, "zai$n") }

        @JavascriptInterface
        fun getChatAi(): String = ExtCfg.chatAiJson(this@MainActivity)

        @JavascriptInterface
        fun setChatAi(choice: String, fallback: Boolean): String { ExtCfg.setChatAi(this@MainActivity, choice.take(40), fallback); return ExtCfg.chatAiJson(this@MainActivity) }

        @JavascriptInterface
        fun getFreeChain(): Boolean = ExtCfg.freeChain(this@MainActivity)

        @JavascriptInterface
        fun setFreeChain(on: Boolean): String { ExtCfg.setFreeChain(this@MainActivity, on); return "ok" }

        // PART 2 personal memory: the page only gets a COUNT, and the text only after the owner taps SHOW.
        @JavascriptInterface
        fun getMemoryCount(): Int = PersonalMemoryStore.count(this@MainActivity)

        @JavascriptInterface
        fun getMemoryText(): String = PersonalMemory.encode(PersonalMemoryStore.load(this@MainActivity))

        @JavascriptInterface
        fun clearMemory(): String = if (PersonalMemoryStore.clear(this@MainActivity)) "ok" else "fail"

        // PART 7 memory screen. Shown only after the owner taps DIKHAO. Edit / delete carry the old text, so a voice command that changed the list meanwhile cannot hit the wrong fact.
        @JavascriptInterface
        fun getMemoryJson(): String = org.json.JSONArray(PersonalMemoryStore.load(this@MainActivity)).toString()

        private fun memResult(u: PersonalMemoryStore.Updated): String = when {
            u.result.status == "saved" && u.persisted -> "ok"
            u.result.status == "saved" -> "err:Phone ne save nahi kiya"
            else -> "err:" + PersonalMemory.screenReply(u.result)
        }

        @JavascriptInterface
        fun memoryAdd(raw: String): String =
            memResult(PersonalMemoryStore.update(this@MainActivity) { PersonalMemory.add(it, raw.take(1000)) })

        @JavascriptInterface
        fun memoryEdit(index: Int, expected0: String, raw: String): String {
            val expected = expected0.take(1000)
            return memResult(PersonalMemoryStore.update(this@MainActivity) { PersonalMemory.replaceAt(it, index, expected, raw.take(1000)) })
        }

        @JavascriptInterface
        fun memoryDelete(index: Int, expected0: String): String {
            val expected = expected0.take(1000)
            return memResult(PersonalMemoryStore.update(this@MainActivity) { PersonalMemory.removeAt(it, index, expected) })
        }

        @JavascriptInterface
        fun memoryExport(): String {
            runOnUiThread { try { startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/plain").putExtra(Intent.EXTRA_TITLE, "nova-memory.txt"), REQ_EXPORT_MEM) } catch (ignored: Exception) { runJs("onMemBackup('export_fail')") } }
            return "started"
        }

        @JavascriptInterface
        fun memoryImport(): String {
            runOnUiThread { try { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/plain"), REQ_IMPORT_MEM) } catch (ignored: Exception) { runJs("onMemBackup('import_fail')") } }
            return "started"
        }

        @JavascriptInterface
        fun getMemoryInChain(): Boolean = ExtCfg.memoryInChain(this@MainActivity)

        @JavascriptInterface
        fun setMemoryInChain(on: Boolean): String { ExtCfg.setMemoryInChain(this@MainActivity, on); return "ok" }

        /** GitHub token for publishing a website. Written only here, never sent back to the page. ok / invalid / fail. */
        @JavascriptInterface
        fun saveGithubToken(raw0: String): String = try {
            val raw = if (raw0.length > 400) "" else raw0
            val k = raw.trim()
            if (k.length < 20 || k.length > 255 || k.any { it.isWhitespace() }) "invalid"
            else if (SecureStore.saveSlot(this@MainActivity, GitHubPublish.SLOT, k)) "ok"
            else "fail"
        } catch (e: Exception) { "fail" }

        @JavascriptInterface
        fun hasGithubToken(): Boolean = SecureStore.hasSlot(this@MainActivity, GitHubPublish.SLOT)

        @JavascriptInterface
        fun clearGithubToken() { SecureStore.clearSlot(this@MainActivity, GitHubPublish.SLOT) }

        /** PUBLISH button in the chat. A native box asks first; nothing leaves the phone before HAAN. nosite / notoken / busy / asking. */
        @JavascriptInterface
        fun publishLastSite(): String {
            val html = NovaService.lastSiteHtml
            val subject = NovaService.lastSiteSubject
            if (html.isEmpty()) return "nosite"
            if (!SecureStore.hasSlot(this@MainActivity, GitHubPublish.SLOT)) return "notoken"
            if (pubBusy) return "busy"
            runOnUiThread {
                try {
                    android.app.AlertDialog.Builder(this@MainActivity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle("Website publish karein?")
                        .setMessage("Tumhare GitHub account mein ek nayi PUBLIC repo banegi aur website internet par sabko dikhegi. Baad mein repo GitHub se hata sakte ho.")
                        .setPositiveButton("HAAN") { _, _ -> startPublish(subject, html) }
                        .setNegativeButton("NAHI", null)
                        .show()
                } catch (e: Exception) {
                }
            }
            return "asking"
        }

        /** Opens only an HTTPS GitHub Pages hostname (not a URL that merely contains "github.io"). */
        @JavascriptInterface
        fun openPublished(url0: String) {
            if (url0.length > 300) return
            val url = url0
            val uri = try { android.net.Uri.parse(url) } catch (e: Exception) { return }
            val host = uri.host?.lowercase() ?: return
            if (uri.scheme != "https" || uri.userInfo != null || (uri.port != -1 && uri.port != 443) ||
                host == "github.io" || !host.endsWith(".github.io")) return
            runOnUiThread {
                try {
                    startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri))
                } catch (e: Exception) {
                }
            }
        }

        @JavascriptInterface
        fun getLearned(): String = LearnStore.listJson(this@MainActivity)

        @JavascriptInterface
        fun keepLearned(id0: String): String {
            val id = id0.take(40)
            return if (LearnStore.keep(this@MainActivity, id)) "ok" else "fail"
        }

        @JavascriptInterface
        fun deleteLearned(id0: String): String {
            val id = id0.take(40)
            return if (LearnStore.delete(this@MainActivity, id)) "ok" else "fail"
        }

        @JavascriptInterface
        fun clearLearned(): String { LearnStore.clear(this@MainActivity); return "ok" }

        // v6.95 ROZ KA SUDHAAR: proposals from a non-Gemini AI. Nothing changes until HAAN; WAPAS undoes it.
        @JavascriptInterface
        fun getSelfImprove(): String = SelfImproveStore.listJson(this@MainActivity)

        @JavascriptInterface
        fun getSelfImproveCfg(): String = "{\"on\":" + ExtCfg.selfImprove(this@MainActivity) + ",\"hour\":" + ExtCfg.selfImproveHour(this@MainActivity) + "}"

        @JavascriptInterface
        fun setSelfImproveCfg(on: Boolean, hour: Int): String { ExtCfg.setSelfImprove(this@MainActivity, on, hour); return "ok" }

        @JavascriptInterface
        fun applySelfImprove(id0: String): String = SelfImproveStore.apply(this@MainActivity, id0.take(40))

        @JavascriptInterface
        fun rejectSelfImprove(id0: String): String = SelfImproveStore.reject(this@MainActivity, id0.take(40))

        @JavascriptInterface
        fun revertSelfImprove(id0: String): String = SelfImproveStore.revert(this@MainActivity, id0.take(40))

        @JavascriptInterface
        fun runSelfImprove(): String {
            Thread({
                val en = Cfg(this@MainActivity).lang == "en"
                val o = try { SelfImprove.run(this@MainActivity, en, true) } catch (t: Throwable) { ErrLog.add("MainActivity", t.toString()); SelfImprove.Outcome("fail", 0) }
                runJs("onSelfImprove(" + JSONObject.quote(SelfImprove.message(o, en)) + ")")
            }, "nova-selfimprove-now").start()
            return "started"
        }

        @JavascriptInterface
        fun getAlerts(): Boolean = ExtCfg.alerts(this@MainActivity)

        // v6.53: auto reply settings + history of what NOVA did
        @JavascriptInterface
        fun getAutoReply(): String = JSONObject().put("on", ActionLog.autoReplyOn(this@MainActivity)).put("text", ActionLog.autoReplyText(this@MainActivity)).toString()

        @JavascriptInterface
        fun getSmartReply(): Boolean = ActionLog.smartReplyOn(this@MainActivity)

        @JavascriptInterface
        fun setSmartReply(on: Boolean): String { ActionLog.setSmartReply(this@MainActivity, on); return "ok" }

        @JavascriptInterface
        fun getOwnerName(): String = ActionLog.ownerName(this@MainActivity)

        @JavascriptInterface
        fun setOwnerName(name: String): String { ActionLog.setOwnerName(this@MainActivity, name.take(60)); return ActionLog.ownerName(this@MainActivity) }

        @JavascriptInterface
        fun setAutoReply(on: Boolean, text0: String): String { val text = text0.take(500); ActionLog.setAutoReply(this@MainActivity, on, text); return "ok" }

        @JavascriptInterface
        fun getActionLog(): String = ActionLog.listJson(this@MainActivity)

        @JavascriptInterface
        fun actionUnseen(): Int = ActionLog.unseen(this@MainActivity)

        @JavascriptInterface
        fun markActionsSeen(): String { ActionLog.markSeen(this@MainActivity); return "ok" }

        @JavascriptInterface
        fun clearActionLog(): String { ActionLog.clear(this@MainActivity); return "ok" }

        /** Starts "delete the message NOVA sent" for the history entry [ts]; the result appears in the history. */
        @JavascriptInterface
        fun deleteSent(ts0: String): String {
            val ts = ts0.take(20)
            val t = ts.toLongOrNull() ?: return "bad"
            val app = applicationContext
            Thread { try { MessageDelete.runForEntry(app, t) } catch (e: Exception) { ErrLog.add("MainActivity", e.toString()) } }.start()
            return "started"
        }

        @JavascriptInterface
        fun setAlerts(on: Boolean): String { ExtCfg.setAlerts(this@MainActivity, on); return "ok" }

        @JavascriptInterface
        fun getConvo(): Boolean = ExtCfg.convo(this@MainActivity)

        @JavascriptInterface
        fun setConvo(on: Boolean): String { ExtCfg.setConvo(this@MainActivity, on); return "ok" }

        @JavascriptInterface
        fun getAutoModels(): Boolean = ExtCfg.autoModels(this@MainActivity)

        @JavascriptInterface
        fun setAutoModels(on: Boolean): String { ExtCfg.setAutoModels(this@MainActivity, on); return "ok" }

        @JavascriptInterface
        fun getAutoLearn(): Boolean = ExtCfg.autoLearn(this@MainActivity)

        @JavascriptInterface
        fun setAutoLearn(on: Boolean): String { ExtCfg.setAutoLearn(this@MainActivity, on); return "ok" }

        /** v6.37: the free AI order NOVA uses right now (best first). */
        @JavascriptInterface
        fun getAiOrder(): String = NovaService.instance?.aiOrderText() ?: ""

        @JavascriptInterface
        fun getNoiseCancel(): Boolean = ExtCfg.noiseCancel(this@MainActivity)

        @JavascriptInterface
        fun setNoiseCancel(on: Boolean): String {
            ExtCfg.setNoiseCancel(this@MainActivity, on)
            MicEffects.setEnabled(on)   // takes effect at once on the running mic
            return "ok"
        }

        @JavascriptInterface
        fun noiseCancelStatus(): String = try { MicEffects.status(ExtCfg.noiseCancel(this@MainActivity)) } catch (e: Throwable) { "?" }

        @JavascriptInterface
        fun brainEngineReady(): Boolean = LocalBrains.factory != null

        // ---- Part 6A: background audio recording (Settings buttons). "notrunning" = NOVA is not ACTIVE.
        @JavascriptInterface
        fun recStart(): String = NovaService.instance?.recStartUi() ?: "notrunning"

        @JavascriptInterface
        fun recStop(): String = NovaService.instance?.recStopUi() ?: "notrunning"

        @JavascriptInterface
        fun recStatus(): String = NovaService.instance?.recStatusUi() ?: "off"

        // Part 6B: explicit visible UI start is required by Android's while-in-use camera permission policy.
        @JavascriptInterface
        fun videoStart(facing0: String): String {
            val facing = facing0.take(10)
            if (facing !in setOf("front", "back")) return "invalid"
            if (RecordSession.active) return "audio_recording_active"
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                runOnUiThread { requestPermissions(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), REQ_VIDEO) }
                return "permission_requested"
            }
            if (Build.VERSION.SDK_INT >= 34 && !NovaService.uiVisible) return "not_visible"
            return try {
                val i = Intent(this@MainActivity, VideoRecordService::class.java).setAction(VideoRecordService.ACTION_START).putExtra(VideoRecordService.EXTRA_FACING, facing)
                startForegroundService(i); "starting"
            } catch (e: Exception) { "start_failed:" + (e.message ?: e.javaClass.simpleName).take(60) }
        }

        @JavascriptInterface
        fun videoStop(): String = try {
            startService(Intent(this@MainActivity, VideoRecordService::class.java).setAction(VideoRecordService.ACTION_STOP)); "stopping"
        } catch (stopError: Exception) { "fail:" + stopError.javaClass.simpleName }

        @JavascriptInterface
        fun videoStatus(): String = JSONObject().put("busy", VideoRecordService.isBusy()).put("active", VideoRecordService.active)
            .put("status", VideoRecordService.lastStatus).put("file", VideoRecordService.lastFileName).toString()

        @JavascriptInterface
        fun getRecordings(): String = RecordingManager.listJson(this@MainActivity)

        @JavascriptInterface
        fun playRecording(id0: String): String {
            val id = id0.take(300)
            return RecordingManager.play(this@MainActivity, id)
        }

        @JavascriptInterface
        fun shareRecording(id0: String): String {
            val id = id0.take(300)
            return RecordingManager.share(this@MainActivity, id)
        }

        @JavascriptInterface
        fun deleteRecording(id0: String): String {
            val id = id0.take(300)
            if (RecordingManager.list(this@MainActivity).none { it.id == id }) return "not_found"
            runOnUiThread {
                try {
                    android.app.AlertDialog.Builder(this@MainActivity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle("Recording delete karein?")
                        .setMessage("${RecordingManager.list(this@MainActivity).firstOrNull { it.id == id }?.name ?: "Recording"} delete ho jayegi. Ye wapas nahi aayegi.")
                        .setPositiveButton("DELETE") { _, _ ->
                            val ok = RecordingManager.delete(this@MainActivity, id)
                            runJs("onRecordingsChanged(" + JSONObject.quote(if (ok) "deleted" else "delete_failed") + ")")
                        }
                        .setNegativeButton("CANCEL", null).show()
                } catch (ignored: Exception) { ErrLog.add("MainActivity", ignored.toString()) }
            }
            return "confirming"
        }

        @JavascriptInterface
        fun getAutoDeleteDays(): Int = RecordingManager.autoDeleteDays(this@MainActivity)

        @JavascriptInterface
        fun setAutoDeleteDays(days: Int): String {
            val r = RecordingManager.setAutoDeleteDays(this@MainActivity, days)
            runJs("onRecordingsChanged(" + JSONObject.quote(if (r == "ok") "updated" else r) + ")")
            return r
        }

        @JavascriptInterface
        fun recoverRecordings(): String {
            Thread { val r = RecordingManager.recover(this@MainActivity); runJs("onRecordingsChanged(" + JSONObject.quote(r) + ")") }.start()
            return "started"
        }

        @JavascriptInterface
        fun getProviderHealth(): String = NovaService.instance?.providerHealthJson() ?: "{\"active\":false,\"providers\":[]}"

        /** LIMITS screen: today's calls, limit and remaining per online service. No key is ever included. */
        @JavascriptInterface
        fun getApiUsage(): String = try { ApiUsage.snapshotJson(this@MainActivity) } catch (e: Exception) { "{\"rows\":[],\"totalToday\":0}" }

        /** Owner-chosen daily limit for one service (0 = back to default). Returns "ok" or "invalid". */
        @JavascriptInterface
        fun setApiLimit(id0: String, n: Int): String {
            val id = id0.take(40)
            return if (ApiUsage.setLimit(id, n)) "ok" else "invalid"
        }

        /** Zeroes today's local counters only. */
        @JavascriptInterface
        fun resetApiUsage(): String { ApiUsage.resetToday(); return "ok" }

        @JavascriptInterface
        fun teachAdd(alias0: String, value0: String): String {
            val alias = alias0.take(100)
            val value = value0.take(400)
            return TeachStore.add(this@MainActivity, alias, value)
        }

        @JavascriptInterface
        fun teachDelete(alias0: String): String {
            val alias = alias0.take(100)
            return if (TeachStore.delete(this@MainActivity, alias)) "ok" else "not_found"
        }

        @JavascriptInterface
        fun teachList(): String = TeachStore.listJson(this@MainActivity)

        @JavascriptInterface
        fun teachExport(): String {
            runOnUiThread { try { startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE, "nova-learned-aliases.json"), REQ_EXPORT_TEACH) } catch (ignored: Exception) { runJs("onTeachBackup('export_fail')") } }
            return "started"
        }

        @JavascriptInterface
        fun teachImport(): String {
            runOnUiThread { try { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json"), REQ_IMPORT_TEACH) } catch (ignored: Exception) { runJs("onTeachBackup('import_fail')") } }
            return "started"
        }

        @JavascriptInterface
        fun getRoutines(): String = BrainStore.get(this@MainActivity).skillsJson()

        @JavascriptInterface
        fun saveRoutine(name0: String, trigger0: String, steps0: String): String {
            val trigger = trigger0.take(200)
            val steps = steps0.take(2000)
            val name = name0.take(100)
            val parsed = RoutineRules.parse(name, trigger, steps) ?: return "invalid"
            val sk = Skill("routine:" + parsed.name.take(32), listOf(parsed.trigger), parsed.steps, "routine")
            val e = BrainStore.get(this@MainActivity).addSkill(sk)
            return if (e == null) "ok" else "invalid:" + e
        }

        @JavascriptInterface
        fun deleteRoutine(name0: String): String {
            val name = name0.take(100)
            return if (BrainStore.get(this@MainActivity).removeSkill(name)) "ok" else "not_found"
        }

        // ---- v6.97-wip9 LOCK GUARD ----
        @JavascriptInterface
        fun getLockGuard(): Boolean = ExtCfg.lockGuard(this@MainActivity)

        @JavascriptInterface
        fun setLockGuard(on: Boolean): String { ExtCfg.setLockGuard(this@MainActivity, on); return "ok" }

        /** One line: what the lock guard needs and whether it has it (notification access for calls / messages, accessibility for the volume-key stop). */
        @JavascriptInterface
        fun lockGuardStatus(): String = try {
            val n = DrivingBridge.listenerEnabled(this@MainActivity)
            val a = (NovaAccessibilityService.instance != null)
            (if (n) "Notification access ON" else "Notification access OFF (call/message alert nahi aayega)") + " | " +
                (if (a) "Volume-key stop ON" else "Accessibility OFF (volume key se band nahi hoga, unlock se band hoga)")
        } catch (e: Throwable) { "?" }

        // ---- v6.97: five key-less browsers + local-first on-device AI ----
        @JavascriptInterface
        fun getWebBrowsers(): Boolean = ExtCfg.webBrowsers(this@MainActivity)

        @JavascriptInterface
        fun setWebBrowsers(on: Boolean) { ExtCfg.setWebBrowsers(this@MainActivity, on) }

        @JavascriptInterface
        fun getLocalFirst(): Boolean = ExtCfg.localFirst(this@MainActivity)

        @JavascriptInterface
        fun setLocalFirst(on: Boolean) { ExtCfg.setLocalFirst(this@MainActivity, on) }

        @JavascriptInterface
        fun getHomeCity(): String = ExtCfg.homeCity(this@MainActivity)

        /** Returns the cleaned city that was saved. */
        @JavascriptInterface
        fun setHomeCity(city: String): String = ExtCfg.setHomeCity(this@MainActivity, city.take(80))

        /** One JSON object: switches, the 5 browsers with REAL call counts / state, savings counters, whether the on-device AI is installed. */
        @JavascriptInterface
        fun getBrowserStatus(): String = try {
            "{\"web\":${ExtCfg.webBrowsers(this@MainActivity)},\"localFirst\":${ExtCfg.localFirst(this@MainActivity)},\"localReady\":${LocalBrains.available(this@MainActivity)}," +
                "\"city\":\"${ExtCfg.homeCity(this@MainActivity).replace("\"", "")}\",\"saved\":${SavedStats.json(this@MainActivity)},\"browsers\":${WebBrowsers.statusJson()}}"
        } catch (e: Exception) { "{}" }

        /** Real test of ONE browser with a harmless sample question. Answers later through window.onBrowserTest(id, ok, text). */
        @JavascriptInterface
        fun testBrowser(id: String) {
            val b = WebSources.Browser.values().firstOrNull { it.id == id } ?: return
            Thread({
                val online = OfflineJobQueue.hasInternet(this@MainActivity)
                val r = try { WebBrowsers.probe(b, true, online, ExtCfg.homeCity(this@MainActivity)) } catch (t: Throwable) { null }
                val ok = r != null && r.ok
                val text = (r?.text ?: "No answer from this browser right now.").take(300)
                runJs("window.onBrowserTest&&onBrowserTest(" + org.json.JSONObject.quote(id.take(20)) + "," + ok + "," + org.json.JSONObject.quote(text) + ")")
            }, "nova-browser-test").start()
        }

        /** v6.65: LOW / MID / HIGH from RAM, CPU cores and Android version (DeviceTier). The page picks its effects and 3D orb quality from it. */
        @JavascriptInterface
        fun getTier(): String = try {
            val am = this@MainActivity.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val mi = android.app.ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            DeviceTier.of(mi.totalMem / (1024L * 1024L), Runtime.getRuntime().availableProcessors(), Build.VERSION.SDK_INT).name.lowercase()
        } catch (e: Exception) { "mid" }

        @JavascriptInterface
        fun getChatSpeak(): Boolean = ExtCfg.chatSpeak(this@MainActivity)

        @JavascriptInterface
        fun setChatSpeak(on: Boolean): String { ExtCfg.setChatSpeak(this@MainActivity, on); return "ok" }

        /** Speaker icon under a chat message: NOVA's own voice when it is running, else the phone's TTS directly. */
        @JavascriptInterface
        fun speakMsg(t0: String): String {
            val t = t0.take(4000)
            val text = FreeProviders.speakable(ChatText.clean(t), 1500)
            if (text.isBlank()) return "empty"
            val svc = NovaService.instance
            if (svc != null) { svc.speakClick(text); return "ok" }
            runOnUiThread { speakLocal(text) }
            return "ok"
        }

        @JavascriptInterface
        fun getFreeKeyless(): Boolean = ExtCfg.freeKeyless(this@MainActivity)

        @JavascriptInterface
        fun setFreeKeyless(on: Boolean): String { ExtCfg.setFreeKeyless(this@MainActivity, on); return "ok" }

        // FIXFREE1: optional free image provider keys (together / hf). Written only here, never sent back to the page.
        @JavascriptInterface
        fun saveImgKey(slot0: String, raw0: String): String = try {
            val slot = slot0.take(16)
            val raw = if (raw0.length > 400) "" else raw0
            val k = raw.trim()
            if (slot != "together" && slot != "hf") "invalid"
            else if (k.length < 10 || k.length > 300 || k.any { it.isWhitespace() }) "invalid"
            else if (SecureStore.saveSlot(this@MainActivity, slot, k)) "ok"
            else "fail"
        } catch (e: Exception) { "fail" }

        @JavascriptInterface
        fun hasImgKey(slot0: String): Boolean {
            val slot = slot0.take(16)
            return (slot == "together" || slot == "hf") && SecureStore.hasSlot(this@MainActivity, slot)
        }

        @JavascriptInterface
        fun clearImgKey(slot0: String) {
            val slot = slot0.take(16)
            if (slot == "together" || slot == "hf") SecureStore.clearSlot(this@MainActivity, slot)
        }

        @JavascriptInterface
        fun toggle(): String {
            if (NovaService.running) {
                cfg.active = false
                this@MainActivity.getSharedPreferences("nova_perm", Context.MODE_PRIVATE).edit().putBoolean("user_stopped", true).apply()   // v6.94: a deliberate STOP is never auto-started again
                runOnUiThread { stopService(Intent(this@MainActivity, NovaService::class.java)) }
                return "stopped"
            }
            this@MainActivity.getSharedPreferences("nova_perm", Context.MODE_PRIVATE).edit().putBoolean("user_stopped", false).apply()
            return startFlow(false)
        }

        /** READ_CONTACTS is requested here, on a user tap, and nowhere else. */
        @JavascriptInterface
        fun requestContacts(): String {
            if (granted(Manifest.permission.READ_CONTACTS)) return "granted"
            runOnUiThread { requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), REQ_CONTACTS) }
            return "asked"
        }

        /** Calendar permission is requested only from this explicit in-app button tap, never from voice. */
        @JavascriptInterface
        fun requestCalendarRead(): String {
            if (granted(Manifest.permission.READ_CALENDAR)) { CalendarRead.scheduleUpcoming(this@MainActivity); return "granted" }
            runOnUiThread { requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), REQ_CALENDAR) }
            return "asked"
        }

        /** Brightness control needs the special "Modify system settings" permission; the user opens it here. */
        @JavascriptInterface
        fun openWriteSettings() {
            runOnUiThread {
                try {
                    startActivity(
                        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))
                    )
                } catch (e: Exception) {
                    pushFlow("write_settings_failed")
                }
            }
        }

        /** Part 9D: Do Not Disturb access page for NOVA. Needed only for "ringer silent". Android lists NOVA there because the manifest declares ACCESS_NOTIFICATION_POLICY. */
        @JavascriptInterface
        fun openDndAccess() {
            runOnUiThread {
                try {
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                    ErrLog.add("MainActivity", "openDndAccess " + e.toString())
                    try { startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e2: Exception) { pushFlow("dnd_settings_failed") }
                }
            }
        }

        /** The floating NOVA card needs "Display over other apps"; the user opens it here. */
        @JavascriptInterface
        fun openOverlaySettings() {
            runOnUiThread {
                try {
                    startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    )
                } catch (e: Exception) {
                    pushFlow("overlay_settings_failed")
                }
            }
        }

        // ---------------------------------------------------------------- Update Center (what is new / what is missing)

        /** One snapshot for the Update Center page: version + which permissions are on. Nothing is changed here. */
        @JavascriptInterface
        fun getStatus(): String {
            val vName = try { packageManager.getPackageInfo(packageName, 0).versionName ?: "" } catch (e: Exception) { "" }
            return JSONObject()
                .put("versionName", vName)
                .put("versionCode", UpdateManager.appVersion(this@MainActivity))
                .put("mic", granted(Manifest.permission.RECORD_AUDIO))
                .put("notif", Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS))
                .put("overlay", Settings.canDrawOverlays(this@MainActivity))
                .put("acc", NovaAccessibilityService.isEnabled(this@MainActivity))
                .put("notifAccess", DrivingBridge.listenerEnabled(this@MainActivity))
                .put("installOk", packageManager.canRequestPackageInstalls())
                .put("assistant", isDefaultAssistant())
                .put("contacts", granted(Manifest.permission.READ_CONTACTS))
                .put("calendar", granted(Manifest.permission.READ_CALENDAR))
                .put("writeSettings", Settings.System.canWrite(this@MainActivity))
                .put("dnd", dndGranted())
                .put("battery", (getSystemService(POWER_SERVICE) as? android.os.PowerManager)?.isIgnoringBatteryOptimizations(packageName) == true)
                .put("feedUrl", cfg.feedUrl)
                .put("running", NovaService.running)
                .put("engine", LocalBrains.factory != null)
                .put("ramMb", totalRamMb())
                .put("ramOk", ModelCatalog.ramOk(totalRamMb()))
                .put("brainInstalled", UpdateManager.activeModelPath(this@MainActivity).isNotEmpty())
                .toString()
        }

        /** v6.30: real state of every feature for the home-page box (permissions, keys, service, last errors). Nothing is changed. */
        /** v6.84: "SETUP DOBARA CHALAO": the owner asks for another round of the automatic setup chain (permission dialog + Settings pages for what is still missing). Grants nothing itself. */
        @JavascriptInterface
        fun rerunSetup(): String {
            getSharedPreferences("nova_perm", Context.MODE_PRIVATE).edit().putInt("setup_runs", 0)
                .apply { for (id in SetupChainRules.ORDER) remove("sp_open_" + id) }.apply()
            runOnUiThread { if (!isDestroyed) firstRunPermissions() }
            return "started"
        }

        @JavascriptInterface
        fun getFeatureStatus(): String = try { FeatureStatus.json(this@MainActivity) } catch (e: Throwable) { "{\"items\":[]}" }

        private fun totalRamMb(): Long {
            val am = getSystemService(ACTIVITY_SERVICE) as? android.app.ActivityManager ?: return 0L
            val mi = android.app.ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            return mi.totalMem / (1024L * 1024L)
        }

        /** Puts the built-in offline model on the offer list (nothing is downloaded). "ok" or a short reason. */
        @JavascriptInterface
        fun offerOfflineBrain(): String = UpdateManager.offerOfflineBrain(this@MainActivity) ?: "ok"

        /** Asks Android to stop limiting NOVA's battery use (so the mic service is not killed). The user taps Allow. */
        @JavascriptInterface
        fun openBatterySettings() {
            runOnUiThread {
                val tries = listOf(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")),
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                )
                for (i in tries) {
                    try { startActivity(i); return@runOnUiThread } catch (e: Exception) { ErrLog.add("MainActivity", e.toString()) }
                }
                pushFlow("update_msg:Open Settings > Apps > NOVA > Battery > No restrictions")
            }
        }

        /** v6.74: opens Android's Text-to-speech page (choose an engine, install a Hindi / English voice). The user changes it there. */
        @JavascriptInterface
        fun openTtsSettings() {
            runOnUiThread {
                val tries = listOf(Intent("com.android.settings.TTS_SETTINGS"), Intent(Settings.ACTION_SETTINGS))
                for (i in tries) {
                    try { startActivity(i); return@runOnUiThread } catch (e: Exception) { ErrLog.add("MainActivity", e.toString()) }
                }
                pushFlow("update_msg:Open Settings > System > Languages > Text-to-speech output")
            }
        }

        /** Opens Android's own "Default digital assistant app" choice. The user picks NOVA there; NOVA cannot set it by itself. */
        @JavascriptInterface
        fun openAssistantSettings() {
            runOnUiThread {
                val tries = listOf(
                    Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
                    Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
                    Intent(Settings.ACTION_SETTINGS)
                )
                for (i in tries) {
                    try { startActivity(i); return@runOnUiThread } catch (e: Exception) { ErrLog.add("MainActivity", e.toString()) }
                }
                pushFlow("update_msg:Settings could not be opened. Open Settings > Apps > Default apps > Digital assistant app")
            }
        }

        /** Asks Android for the microphone and notification permission (the user sees the normal Android dialog). */
        @JavascriptInterface
        fun requestBasics(): String {
            val ask = ArrayList<String>()
            if (!granted(Manifest.permission.RECORD_AUDIO)) ask.add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS)) {
                ask.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (ask.isEmpty()) return "ok"
            // Android stops showing the popup after two denials; then the button looked dead and the Update Center
            // seemed stuck. In that case open this app's settings page instead.
            val prefs = getSharedPreferences("nova_perm", android.content.Context.MODE_PRIVATE)
            val askedBefore = prefs.getBoolean("basics_asked", false)
            val blocked = askedBefore && ask.all { !shouldShowRequestPermissionRationale(it) }
            if (blocked) {
                runOnUiThread {
                    try {
                        val i = Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.parse("package:" + packageName)
                        )
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(i)
                    } catch (e: Exception) {
                        pushFlow("basics_done")
                    }
                }
                return "settings"
            }
            prefs.edit().putBoolean("basics_asked", true).apply()
            runOnUiThread { requestPermissions(ask.toTypedArray(), REQ_BASIC) }
            return "asked"
        }

        /** Driving mode reads messages only after the user switches "Notification access" on for NOVA. */
        @JavascriptInterface
        fun openNotificationAccess() {
            runOnUiThread { DrivingBridge.openListenerSettings(this@MainActivity) }
        }

        // ---------------------------------------------------------------- updates (Discover -> Ask -> Add)

        /** "ok" or "bad" (the feed URL must be https). An empty URL turns update checks off. */
        @JavascriptInterface
        fun setFeedUrl(url0: String): String {
            if (url0.length > 300) return "bad"
            val url = url0
            val u = url.trim()
            if (u.isEmpty()) { cfg.feedUrl = ""; return "ok" }
            if (!Updater.feedUrlOk(u)) return "bad"
            cfg.feedUrl = u
            return "ok"
        }

        @JavascriptInterface
        fun checkUpdates() {
            val url = cfg.feedUrl
            Thread {
                val e = if (url.isBlank()) "set the feed URL first" else UpdateManager.refresh(this@MainActivity, url)
                pushFlow(if (e == null) "updates_ok" else "updates_err:$e")
            }.start()
        }

        /** {"offers":[{id,type,title,sizeText,status}],"installed":{id:version}} */
        @JavascriptInterface
        fun getUpdates(): String {
            val arr = JSONArray()
            for (o in UpdateManager.offers(this@MainActivity)) {
                arr.put(
                    JSONObject().put("id", o.item.id).put("type", o.item.type).put("title", o.item.title)
                        .put("sizeText", Updater.sizeText(o.item.size)).put("status", o.status)
                )
            }
            return JSONObject().put("offers", arr)
                .put("installed", JSONObject(UpdateManager.installedJson(this@MainActivity))).toString()
        }

        /** Only the user's tap on INSTALL calls this. The download is verified (https, host list, SHA-256, size). */
        @JavascriptInterface
        fun installUpdate(id0: String): String {
            val id = id0.take(100)
            UpdateManager.install(this@MainActivity, id) { ok, msg ->
                if (!ok || msg.isNotEmpty()) pushFlow("update_msg:$msg")
            }
            return "started"
        }

        @JavascriptInterface
        fun skipUpdate(id0: String): String { val id = id0.take(100); UpdateManager.skip(this@MainActivity, id); return "ok" }

        @JavascriptInterface
        fun rollbackUpdate(id0: String): String {
            val id = id0.take(100)
            return UpdateManager.rollback(this@MainActivity, id)
        }

        /** "Install unknown apps" for NOVA - needed once, only for installing a new NOVA version. */
        @JavascriptInterface
        fun openInstallSettings() {
            runOnUiThread {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                } catch (e: Exception) {
                    pushFlow("update_msg:Settings could not be opened. Use APP INFO > Install unknown apps")
                }
            }
        }

        /** {"skills":n,"events":n,"learned":n} - how much NOVA has learned on this phone. */
        @JavascriptInterface
        fun brainStats(): String = BrainStore.get(this@MainActivity).statsJson()

        /** Wipes all learned shortcuts and the local event log. */
        @JavascriptInterface
        fun brainClear(): String {
            BrainStore.get(this@MainActivity).clear()
            return "ok"
        }

        @JavascriptInterface
        fun getSettings(): String {
            val keyCount = SecureStore.getKeys(this@MainActivity).size
            val hasKey = SecureStore.hasKeys(this@MainActivity)
            return JSONObject()
                .put("wake", cfg.wake)
                .put("lang", cfg.lang)
                .put("rate", cfg.rate.toDouble())
                .put("pitch", cfg.pitch.toDouble())
                .put("model", cfg.model)
                .put("models", JSONArray(Logic.MODELS))
                .put("autostart", cfg.autostart)
                .put("hasKey", hasKey)
                .put("keyCount", keyCount)
                .put("hasGroq", SecureStore.hasGroq(this@MainActivity))
                .put("localMode", !hasKey)
                .put("acc", NovaAccessibilityService.isEnabled(this@MainActivity))
                .put("screenMonitor", cfg.screenMonitor)
                .put("contacts", granted(Manifest.permission.READ_CONTACTS))
                .put("calendar", granted(Manifest.permission.READ_CALENDAR))
                .put("writeSettings", Settings.System.canWrite(this@MainActivity))
                .put("dnd", dndGranted())
                .put("overlay", Settings.canDrawOverlays(this@MainActivity))
                .put("feedUrl", cfg.feedUrl)
                .put("running", NovaService.running)
                .toString()
        }

        /** Returns ok, need_acc (everything saved but screen monitoring stayed OFF), bad_wake or error. */
        @JavascriptInterface
        fun saveSettings(json0: String): String {
            if (json0.length > 20000) return "bad"
            val json = json0
            return try {
                val o = JSONObject(json)
                val w = Logic.cleanWake(o.optString("wake"))
                if (w == null) {
                    "bad_wake"
                } else {
                    cfg.wake = w
                    cfg.lang = if (o.optString("lang") == "en") "en" else "hi"
                    cfg.rate = Logic.clampRate(o.optDouble("rate", 1.0).toFloat())
                    cfg.pitch = Logic.clampPitch(o.optDouble("pitch", 1.0).toFloat())
                    cfg.model = Logic.cleanModel(o.optString("model"))
                    cfg.autostart = o.optBoolean("autostart", true)
                    val wantMonitor = o.optBoolean("screenMonitor", false)
                    val accOn = NovaAccessibilityService.isEnabled(this@MainActivity)
                    val before = cfg.screenMonitor
                    cfg.screenMonitor = wantMonitor && accOn
                    if (before != cfg.screenMonitor) NovaAccessibilityService.monitorChanged()
                    NovaService.instance?.reloadVoice()
                    if (wantMonitor && !accOn) "need_acc" else "ok"
                }
            } catch (e: Exception) {
                "error"
            }
        }

        @JavascriptInterface
        fun testVoice() {
            runOnUiThread {
                val text = if (cfg.lang == "en") "Hello, I am Nova" else "नमस्ते, मैं नोवा हूँ"
                val svc = NovaService.instance
                if (svc != null) {
                    svc.speakTest(text)
                } else {
                    testTts?.shutdown()
                    var created: TextToSpeech? = null
                    created = TextToSpeech(this@MainActivity) { st ->
                        if (st == TextToSpeech.SUCCESS) {
                            cfg.applyVoice(created)
                            created?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "test")
                        }
                    }
                    testTts = created
                }
            }
        }

        // ------------------------------------------------------------------ v6.86 CONNECTORS (explicit taps only)
        // The page never receives a token: it only learns "found" / "checking" / the account name. Tokens go to SecureStore.

        @JavascriptInterface
        fun getConnectors(): String = try { ConnectorStore.summaryJson(this@MainActivity) } catch (e: Exception) { "[]" }

        /** ALLOW button: opens the provider's own token page (scopes pre-filled where the provider supports it). */
        @JavascriptInterface
        fun connectorBegin(id: String): String {
            if (id.length > 40) return "bad"
            val d = ConnectorCatalog.byId(id) ?: return "bad"
            if (id == ConnectorCatalog.GMAIL) {
                if (!GmailStore.gmailInstalled(this@MainActivity)) return "nogmail"
                if (DrivingBridge.listenerEnabled(this@MainActivity)) return "connected"
                runOnUiThread { DrivingBridge.openListenerSettings(this@MainActivity) }
                return "opened"
            }
            val url = if (id == ConnectorCatalog.LOVABLE) "https://lovable.dev" else d.tokenUrl
            runOnUiThread { try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: Exception) { ErrLog.add("Connector", "open page failed") } }
            return "opened"
        }

        /** After the owner comes back from the provider page: is a token for this connector on the clipboard? Never returns the token. */
        @JavascriptInterface
        fun connectorClip(id: String): String = if (id.length > 40 || ConnectorCatalog.byId(id) == null || id == ConnectorCatalog.LOVABLE || id == ConnectorCatalog.GMAIL) "none" else if (clipToken(id) != null) "found" else "none"

        @JavascriptInterface
        fun connectorUseClip(id: String): String {
            if (id.length > 40 || ConnectorCatalog.byId(id) == null || id == ConnectorCatalog.LOVABLE || id == ConnectorCatalog.GMAIL) return "bad"
            val t = clipToken(id) ?: return "none"
            connectorVerifyAsync(id, t, true)
            return "checking"
        }

        /** Manual fallback: the owner pastes the token into the box. */
        @JavascriptInterface
        fun connectorPaste(id: String, raw0: String): String {
            if (id.length > 40 || ConnectorCatalog.byId(id) == null || id == ConnectorCatalog.LOVABLE || id == ConnectorCatalog.GMAIL) return "bad"
            val t = (if (raw0.length > 400) "" else raw0).trim()
            if (t.length < 20 || t.length > 255 || t.any { it.isWhitespace() }) return "invalid"
            connectorVerifyAsync(id, t, false)
            return "checking"
        }

        @JavascriptInterface
        fun connectorSetPerm(id: String, level: String): String =
            if (id.length > 40 || level.length > 10) "fail" else if (ConnectorStore.setPerm(this@MainActivity, id, level)) "ok" else "fail"

        @JavascriptInterface
        fun connectorDisconnect(id: String): String {
            if (id.length > 40 || ConnectorCatalog.byId(id) == null && !ConnectorCatalog.isDynId(id)) return "bad"
            ConnectorStore.disconnect(this@MainActivity, id)
            return "ok"
        }

        /** TEST button: asks the provider again who the token belongs to. */
        @JavascriptInterface
        fun connectorTest(id: String): String {
            if (id == ConnectorCatalog.GMAIL) {
                val ok = ConnectorStore.connected(this@MainActivity, id)
                pushConn(id, if (ok) "ok" else "fail", if (ok) "Gmail app + notification access theek hai" else "Notification access ON karo aur Gmail app rakho")
                return "checking"
            }
            if (id.length > 40 || !ConnectorStore.connected(this@MainActivity, id) || id == ConnectorCatalog.LOVABLE) return "none"
            pushConn(id, "checking", "")
            Thread({
                try {
                    if (ConnectorCatalog.isMcpId(id)) {
                        val m = ConnectorStore.mcpOf(this@MainActivity, id)
                        val r = if (m == null) Pair(false, "Connector nahi mila") else ConnectorApi.mcpOpen(m.url, ConnectorStore.token(this@MainActivity, id))
                        pushConn(id, if (r.first) "ok" else "fail", if (r.first) "server jawab de raha hai" else r.second)
                    } else if (ConnectorCatalog.isHookId(id)) {
                        pushConn(id, "ok", "webhook saved hai (TEST se automation chalti nahi; chalane ke liye CHALAO dabao)")
                    } else if (ConnectorCatalog.isN8nId(id)) {
                        val d = ConnectorStore.dynOf(this@MainActivity, id)
                        val r = if (d == null) Pair(false, "Connector nahi mila") else ConnectorApi.n8nVerify(ConnectorCatalog.n8nBase(d.url), ConnectorStore.token(this@MainActivity, id))
                        pushConn(id, if (r.first) "ok" else "fail", r.second)
                    } else {
                        val r = ConnectorApi.verify(id, ConnectorStore.token(this@MainActivity, id))
                        pushConn(id, if (r.first) "ok" else "fail", r.second)
                    }
                } catch (e: Throwable) { pushConn(id, "fail", "Test nahi ho paya") }
            }, "nova-conn-test").start()
            return "checking"
        }

        /** Custom MCP connector: https address (+ optional token). Verified with a real initialize call before it is kept. */
        @JavascriptInterface
        fun connectorAddMcp(name0: String, url0: String, token0: String): String {
            if (url0.length > 300 || name0.length > 200) return "invalid"
            val name = name0.trim().take(40)
            val url = url0.trim()
            val tok = (if (token0.length > 400) "" else token0).trim()
            if (name.isEmpty() || !ConnectorCatalog.mcpUrlOk(url)) return "invalid"
            if (tok.isNotEmpty() && (tok.length < 8 || tok.any { it.isWhitespace() })) return "invalid"
            Thread({
                try {
                    val use = if (tok.isEmpty()) ConnectorApi.NO_TOKEN else tok
                    val r = ConnectorApi.mcpOpen(url, use)
                    if (!r.first) { pushConn("mcp_new", "fail", r.second); return@Thread }
                    val id = ConnectorStore.addMcp(this@MainActivity, name, url)
                    if (id.isEmpty()) { pushConn("mcp_new", "fail", "Custom connector save nahi hua (8 se zyada ya address galat)"); return@Thread }
                    if (!ConnectorStore.save(this@MainActivity, id, use, name)) { pushConn("mcp_new", "fail", "Token save nahi ho paya"); return@Thread }
                    pushConn("mcp_new", "ok", id)
                } catch (e: Throwable) { pushConn("mcp_new", "fail", "Connect nahi ho paya") }
            }, "nova-conn-mcp").start()
            return "checking"
        }

        /** Automation webhook (n8n webhook, Zapier, Make, IFTTT, Pipedream ...). Cannot be verified without firing it, so it is only saved; WRITE level is on, every run still asks yes/no. */
        @JavascriptInterface
        fun connectorAddHook(name0: String, url0: String, token0: String): String {
            if (url0.length > 300 || name0.length > 200) return "invalid"
            val name = name0.trim().take(40)
            val url = url0.trim()
            val tok = (if (token0.length > 400) "" else token0).trim()
            if (name.isEmpty() || !ConnectorCatalog.mcpUrlOk(url)) return "invalid"
            if (tok.isNotEmpty() && (tok.length < 8 || tok.any { it.isWhitespace() })) return "invalid"
            Thread({
                try {
                    val id = ConnectorStore.addDyn(this@MainActivity, ConnectorCatalog.HOOK_PREFIX, name, url)
                    if (id.isEmpty()) { pushConn("hook_new", "fail", "Save nahi hua (10 se zyada ya address galat)"); return@Thread }
                    if (!ConnectorStore.save(this@MainActivity, id, if (tok.isEmpty()) ConnectorApi.NO_TOKEN else tok, name)) { pushConn("hook_new", "fail", "Save nahi ho paya"); ConnectorStore.removeDyn(this@MainActivity, id); return@Thread }
                    ConnectorStore.setPerm(this@MainActivity, id, ConnectorCatalog.PERM_WRITE)
                    pushConn("hook_new", "ok", id)
                } catch (e: Throwable) { pushConn("hook_new", "fail", "Jud nahi paya") }
            }, "nova-conn-hook").start()
            return "checking"
        }

        /** n8n REST API: base address + API key, verified with a real read-only call before it is kept. Starts at READ. */
        @JavascriptInterface
        fun connectorAddN8n(name0: String, url0: String, key0: String): String {
            if (url0.length > 300 || name0.length > 200 || key0.length > 400) return "invalid"
            val name = name0.trim().take(40)
            val base = ConnectorCatalog.n8nBase(url0)
            val key = key0.trim()
            if (name.isEmpty() || base.isEmpty() || key.length < 8 || key.any { it.isWhitespace() }) return "invalid"
            Thread({
                try {
                    val r = ConnectorApi.n8nVerify(base, key)
                    if (!r.first) { pushConn("n8n_new", "fail", r.second); return@Thread }
                    val id = ConnectorStore.addDyn(this@MainActivity, ConnectorCatalog.N8N_PREFIX, name, base)
                    if (id.isEmpty()) { pushConn("n8n_new", "fail", "Save nahi hua (10 se zyada)"); return@Thread }
                    if (!ConnectorStore.save(this@MainActivity, id, key, name)) { pushConn("n8n_new", "fail", "Key save nahi ho paya"); ConnectorStore.removeDyn(this@MainActivity, id); return@Thread }
                    pushConn("n8n_new", "ok", id)
                } catch (e: Throwable) { pushConn("n8n_new", "fail", "Connect nahi ho paya") }
            }, "nova-conn-n8n").start()
            return "checking"
        }

        @JavascriptInterface
        fun connectorSetClientId(v: String): String = if (v.length > 60) "invalid" else if (ConnectorStore.setClientId(this@MainActivity, v)) "ok" else "invalid"

        @JavascriptInterface
        fun connectorClientId(): String = ConnectorStore.clientId(this@MainActivity)

        @Volatile private var deviceGen = 0

        /** GitHub OAuth device login (needs the owner's OAuth app client id in the box). Shows a code, polls, then verifies + saves. */
        @JavascriptInterface
        fun connectorDeviceLogin(): String {
            val cid = ConnectorStore.clientId(this@MainActivity)
            if (cid.isEmpty()) return "noclient"
            val gen = ++deviceGen
            pushConn(ConnectorCatalog.GITHUB, "checking", "")
            Thread({
                try {
                    val dv = ConnectorApi.deviceStart(cid)
                    if (dv == null) { pushConn(ConnectorCatalog.GITHUB, "fail", "GitHub login shuru nahi hua. Client ID aur 'Device Flow' setting check karo"); return@Thread }
                    pushConn(ConnectorCatalog.GITHUB, "device", dv.userCode + "|" + dv.url)
                    runOnUiThread { try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(dv.url))) } catch (e: Exception) { } }
                    var wait = dv.intervalSec
                    val end = System.currentTimeMillis() + dv.expiresSec * 1000L
                    while (System.currentTimeMillis() < end && gen == deviceGen) {
                        Thread.sleep(wait * 1000L)
                        if (gen != deviceGen) return@Thread
                        val p = ConnectorApi.devicePoll(cid, dv.deviceCode)
                        if (p.startsWith("token:")) {
                            val tk = p.removePrefix("token:")
                            val r = ConnectorApi.verify(ConnectorCatalog.GITHUB, tk)
                            if (r.first && ConnectorStore.save(this@MainActivity, ConnectorCatalog.GITHUB, tk, r.second)) pushConn(ConnectorCatalog.GITHUB, "ok", r.second)
                            else pushConn(ConnectorCatalog.GITHUB, "fail", if (r.first) "Token save nahi ho paya" else r.second)
                            return@Thread
                        }
                        if (p == "slow") wait += 5
                        if (p == "denied") { pushConn(ConnectorCatalog.GITHUB, "fail", "GitHub par allow nahi kiya gaya"); return@Thread }
                        if (p == "expired") break
                    }
                    if (gen == deviceGen) pushConn(ConnectorCatalog.GITHUB, "fail", "Code expire ho gaya. Dobara try karo")
                } catch (e: Throwable) { pushConn(ConnectorCatalog.GITHUB, "fail", "GitHub login poora nahi hua") }
            }, "nova-conn-device").start()
            return "started"
        }

        @JavascriptInterface
        fun connectorDeviceCancel() { deviceGen++ }

        /**
         * Runs one connector action. READ actions run at once. A WRITE action runs only when [confirmed] is true (the page shows its own
         * "Haan, karo" step first) and the connector is on the WRITE level; ConnectorApi checks the level again. Result comes back via onConnRun.
         */
        @JavascriptInterface
        fun connectorRun(id: String, action: String, arg0: String, confirmed: Boolean): String {
            if (id.length > 40 || action.length > 30) return "bad"
            val arg = if (arg0.length > 120_000) "" else arg0
            if (ConnectorCatalog.byId(id) == null && !ConnectorCatalog.isDynId(id)) return "bad"
            val write = ConnectorApi.isWrite(id, action)
            if (write && !confirmed) return "confirm"
            Thread({
                val out = try { ConnectorApi.run(this@MainActivity, id, action, arg) } catch (e: Throwable) { ConnectorApi.Out(false, err = "Kaam poora nahi hua") }
                val arr = JSONArray(); out.lines.forEach { arr.put(it) }
                runJs("if(window.onConnRun)onConnRun(" + JSONObject.quote(id) + "," + JSONObject.quote(action) + "," + out.ok + "," +
                    JSONObject.quote(out.err) + "," + arr.toString() + ")")
            }, "nova-conn-run").start()
            return "running"
        }

        @JavascriptInterface
        fun openAccessibility() {
            runOnUiThread { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }

        @JavascriptInterface
        fun openAppInfo() {
            runOnUiThread {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                )
            }
        }
    }

    private companion object {
        const val ASSET_URL = "file:///android_asset/ui.html"
        const val ASSET_PREFIX = "file:///android_asset/"
        const val REQ_SPEECH = 40
        const val REQ_START = 1
        const val REQ_CONTACTS = 2
        const val REQ_CALL = 42
        const val REQ_CALENDAR = 8
        const val REQ_BASIC = 3
        const val REQ_PICK = 4
        const val REQ_SITE_MEDIA = 47
        const val REQ_VIDEO = 5
        const val REQ_ALL = 9
        const val REQ_EXPORT_TEACH = 6
        const val REQ_IMPORT_TEACH = 7
        const val REQ_EXPORT_MEM = 44
        const val REQ_IMPORT_MEM = 45
        const val REQ_EXPORT_CHAT = 46
        const val REQ_PPN = 43
    }
}
