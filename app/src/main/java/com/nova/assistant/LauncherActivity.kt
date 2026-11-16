package com.nova.assistant

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.provider.MediaStore
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar

/**
 * v7.189 Part L2: JARVIS HOME screen, dark neon design (clock + weather, live battery / RAM / storage bars, search or command box,
 * pinned apps dock, quick actions, laptop-link card, ALL APPS page, JARVIS orb). Built in code, no layout files.
 *
 * - A normal sentence in the box goes to JARVIS (JarvisService.typed); an app name opens that app.
 * - The orb starts JARVIS, or (already running) pops the JARVIS card over whatever app is open (needs "Display over other apps", asked ONCE).
 * - Weather: phone's last known place if the location permission is already granted, else a city the owner types once (tap the weather card).
 * - Dark mode / WiFi only open the system screen: Android does not let an app flip them silently.
 */
class LauncherActivity : Activity() {
    private var all: List<LauncherRules.AppItem> = emptyList()
    private var shown: List<LauncherRules.AppItem> = emptyList()
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var home: ScrollView
    private lateinit var appsPage: LinearLayout
    private lateinit var grid: GridView
    private lateinit var search: EditText
    private lateinit var status: TextView
    private lateinit var lastTask: TextView
    private lateinit var bizOrders: TextView   // v7.192 L4: "Aaj ka business" card
    private lateinit var bizLeads: TextView
    private lateinit var bizAuto: TextView
    private lateinit var bizReport: TextView
    private lateinit var bizInfo: TextView
    @Volatile private var bizBusy = false
    private lateinit var weatherTemp: TextView
    private lateinit var weatherText: TextView
    private lateinit var batBar: ProgressBar
    private lateinit var ramBar: ProgressBar
    private lateinit var stoBar: ProgressBar
    private lateinit var batTxt: TextView
    private lateinit var ramTxt: TextView
    private lateinit var stoTxt: TextView
    private lateinit var laptopTxt: TextView
    private lateinit var dock: LinearLayout
    private lateinit var orb: TextView
    private var torchOn = false
    private var askedHome = false   // v7.198: ask "make me Home" once per opening
    private var web: android.webkit.WebView? = null   // v7.194: animated home (launcher.html); native views stay underneath as fallback
    private val dockPins = listOf("com.google.android.youtube" to "YouTube", "com.google.android.googlequicksearchbox" to "Google", "com.android.vending" to "Play Store",
        "com.instagram.android" to "Instagram", "com.whatsapp" to "WhatsApp")
    private val adapter = AppAdapter()
    private val prefs by lazy { getSharedPreferences("launcher", Context.MODE_PRIVATE) }

    private val tick = object : Runnable {
        override fun run() { refreshStatus(); ui.postDelayed(this, 5000L) }
    }
    private val batRx = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { setBar(batBar, batTxt, "Battery", batteryPct(), true) }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    /** v7.193 modern look: dark glass fill + neon blue-to-purple border (stroke colour arg kept for old call sites). */
    private fun glass(fill: String, stroke: String, r: Int = 18): android.graphics.drawable.Drawable {
        val rr = dp(r).toFloat()
        val outer = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.parseColor("#1FB6FF"), Color.parseColor("#5A4BFF"), Color.parseColor("#B23DFF"))).apply { cornerRadius = rr }
        val inner = GradientDrawable().apply {
            setColor((Color.parseColor(fill) and 0x00FFFFFF) or (0xF2 shl 24))
            cornerRadius = if (rr > dp(1)) rr - dp(1) else 0f
        }
        return LayerDrawable(arrayOf(outer, inner)).apply { setLayerInset(1, dp(1), dp(1), dp(1), dp(1)) }
    }
    private fun tv(text: String, size: Float, color: String, bold: Boolean = false) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(Color.parseColor(color)); if (bold) typeface = Typeface.DEFAULT_BOLD
    }
    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; background = glass("#0A1236", "#2E4BFF", 20); setPadding(dp(15), dp(13), dp(15), dp(13))
    }
    private fun lpm(top: Int = 10) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val frame = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.parseColor("#02030F"), Color.parseColor("#071035"), Color.parseColor("#17062F")))
            setPadding(dp(14), dp(30), dp(14), dp(10))
        }
        // ---- HOME page
        val voicePill = tv("\uD83C\uDF99  Say: \"Open YouTube\" or \"Play GTA 5\"", 13f, "#CFE6FF").apply {
            gravity = Gravity.CENTER; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(14), dp(10), dp(14), dp(10)); background = glass("#08123A", "#2E4BFF", 24)
            setOnClickListener { orbTap() }
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        home = ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(col) }

        col.addView(voicePill, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        left.addView(TextClock(this).apply { format12Hour = "hh:mm"; format24Hour = "HH:mm"; textSize = 54f; setTextColor(Color.WHITE); typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL) })
        left.addView(TextClock(this).apply { format12Hour = "EEEE, d MMM"; format24Hour = "EEEE, d MMM"; textSize = 15f; setTextColor(Color.parseColor("#8FB8FF")) })
        left.addView(tv(LauncherRules.greeting(Calendar.getInstance().get(Calendar.HOUR_OF_DAY), false), 14f, "#4FD8FF"))
        top.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
        val wcard = card().apply { gravity = Gravity.CENTER; setOnClickListener { askCity() } }
        weatherTemp = tv("--°", 26f, "#FFFFFF", true); weatherText = tv("Mausam: tap karo", 12f, "#9AA7C7").apply { gravity = Gravity.CENTER }
        wcard.addView(weatherTemp); wcard.addView(weatherText)
        top.addView(wcard, LinearLayout.LayoutParams(dp(130), -2))
        col.addView(top)

        status = tv("", 13f, "#9AA7C7"); col.addView(status, lpm(6))
        lastTask = tv("", 13f, "#4FD8FF"); col.addView(lastTask, lpm(2))

        // ---- v7.193 hero: JARVIS robot picture + tagline (picture = launcher_hero.jpg in assets)
        val hero = FrameLayout(this).apply {
            background = glass("#050A26", "#2E4BFF", 24)
            setPadding(dp(1), dp(1), dp(1), dp(1))
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: android.graphics.Outline) { o.setRoundRect(0, 0, v.width, v.height, dp(24).toFloat()) }
            }
            clipToOutline = true
        }
        val heroImg = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            try { assets.open("launcher_hero.jpg").use { setImageBitmap(BitmapFactory.decodeStream(it)) } } catch (e: Throwable) { }
        }
        hero.addView(heroImg, FrameLayout.LayoutParams(-1, -1))
        col.addView(hero, LinearLayout.LayoutParams(-1, dp(250)).apply { topMargin = dp(10) })
        col.addView(tv("JARVIS  \u00B7  Your Personal AI Assistant  \u00B7  Always With You...", 12f, "#8FB8FF").apply { gravity = Gravity.CENTER; maxLines = 1; ellipsize = TextUtils.TruncateAt.END }, lpm(8))

        search = EditText(this).apply {
            hint = "\uD83D\uDD0D  App ka naam ya Jarvis ko kaam likho..."
            setHintTextColor(Color.parseColor("#6F7FA8")); setTextColor(Color.WHITE); textSize = 16f
            background = glass("#0C1847", "#3A66FF", 28); setPadding(dp(20), dp(13), dp(20), dp(13))
            setSingleLine(true); imeOptions = EditorInfo.IME_ACTION_GO
            setOnEditorActionListener { _, _, _ -> submit(); true }
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { onSearchChanged(s?.toString() ?: "") }
                override fun beforeTextChanged(s: CharSequence?, a: Int, c: Int, d: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, c: Int, d: Int) {}
            })
        }
        col.addView(search, lpm(8))

        dock = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 6f }
        col.addView(dock, lpm(10))

        // ---- v7.192 L4: AAJ KA BUSINESS (real numbers from the stores; no data = "abhi koi nahi")
        val bz = card()
        bz.addView(tv("\uD83D\uDCBC  Aaj ka business  (tap = sunao)", 15f, "#4FD8FF", true).apply { setOnClickListener { bizTap(BusinessHomeRules.Tap.REPORT) } })
        fun bl(t: BusinessHomeRules.Tap) = tv("", 13f, "#FFFFFF").apply { setPadding(0, dp(7), 0, dp(3)); setOnClickListener { bizTap(t) } }
        bizOrders = bl(BusinessHomeRules.Tap.ORDERS); bizLeads = bl(BusinessHomeRules.Tap.LEADS)
        bizAuto = bl(BusinessHomeRules.Tap.AUTOPILOT); bizReport = bl(BusinessHomeRules.Tap.REPORT)
        for (v in listOf(bizOrders, bizLeads, bizAuto, bizReport)) bz.addView(v)
        bizInfo = tv("", 11f, "#9AA7C7").apply { setPadding(0, dp(4), 0, 0) }
        bz.addView(bizInfo)
        val bzRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 2f }
        fun bb(t: String, go: () -> Unit) = Button(this).apply {
            text = t; textSize = 11f; setTextColor(Color.WHITE); isAllCaps = false; background = glass("#101C55", "#2E5BFF", 16)
            setOnClickListener { try { go() } catch (e: Throwable) { Toast.makeText(this@LauncherActivity, "Ye abhi nahi chala", Toast.LENGTH_SHORT).show() } }
        }
        val bzLp = { LinearLayout.LayoutParams(0, dp(42), 1f).apply { setMargins(dp(3), dp(8), dp(3), 0) } }
        bzRow.addView(bb("Leads dhoondo") { bizTap(BusinessHomeRules.Tap.FIND) }, bzLp())
        bzRow.addView(bb("Report sunao") { bizTap(BusinessHomeRules.Tap.REPORT) }, bzLp())
        bz.addView(bzRow)
        col.addView(bz, lpm(10))

        val qa = card(); qa.addView(tv("\u26A1  Quick Actions", 15f, "#4FD8FF", true))
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 4f }
        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 4f }
        fun qb(t: String, go: () -> Unit) = Button(this).apply {
            text = t; textSize = 11f; setTextColor(Color.WHITE); isAllCaps = false; background = glass("#101C55", "#2E5BFF", 16)
            setOnClickListener { try { go() } catch (e: Throwable) { Toast.makeText(this@LauncherActivity, "Ye abhi nahi khula", Toast.LENGTH_SHORT).show() } }
        }
        val lp = { LinearLayout.LayoutParams(0, dp(60), 1f).apply { setMargins(dp(3), dp(6), dp(3), 0) } }
        fun go(i: Intent) = startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        row1.addView(qb("\uD83D\uDCF7\nCamera") { go(Intent(MediaStore.ACTION_IMAGE_CAPTURE)) }, lp())
        row1.addView(qb("\uD83D\uDCDE\nCall") { go(Intent(Intent.ACTION_DIAL)) }, lp())
        row1.addView(qb("\uD83D\uDCAC\nMessage") { go(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))) }, lp())
        row1.addView(qb("\uD83D\uDCF6\nWiFi") { go(Intent(Settings.ACTION_WIFI_SETTINGS)) }, lp())
        row2.addView(qb("\uD83C\uDF19\nDark mode") { go(Intent(Settings.ACTION_DISPLAY_SETTINGS)) }, lp())
        row2.addView(qb("\uD83D\uDD26\nTorch") { toggleTorch() }, lp())
        row2.addView(qb("\uD83E\uDD16\nJarvis chat") { go(Intent(this, MainActivity::class.java)) }, lp())
        row2.addView(qb("\u25A6\nSab apps") { showApps(true) }, lp())
        qa.addView(row1); qa.addView(row2)
        col.addView(qa, lpm(10))

        val st = card(); st.addView(tv("\uD83D\uDCCA  System Status", 15f, "#4FD8FF", true))
        batTxt = tv("", 12f, "#FFFFFF"); ramTxt = tv("", 12f, "#FFFFFF"); stoTxt = tv("", 12f, "#FFFFFF")
        batBar = bar(); ramBar = bar(); stoBar = bar()
        for ((t, bb) in listOf(batTxt to batBar, ramTxt to ramBar, stoTxt to stoBar)) { st.addView(t, lpm(6)); st.addView(bb) }
        col.addView(st, lpm(10))

        val lc = card(); lc.addView(tv("\uD83D\uDCBB  Laptop Control", 15f, "#4FD8FF", true))
        laptopTxt = tv("", 13f, "#FFFFFF"); lc.addView(laptopTxt, lpm(6))
        lc.addView(qb("Laptop par kaam bolo") { search.setText("laptop par "); search.requestFocus(); search.setSelection(search.text.length) }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(8) })
        col.addView(lc, lpm(10))

        // ---- ALL APPS page
        appsPage = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        appsPage.addView(qb("< Home") { search.setText(""); showApps(false) }, LinearLayout.LayoutParams(-2, dp(40)))
        grid = GridView(this).apply {
            numColumns = 4; verticalSpacing = dp(10); horizontalSpacing = dp(4); selector = ColorDrawable(Color.TRANSPARENT)
            adapter = this@LauncherActivity.adapter; setPadding(0, dp(10), 0, dp(4)); clipToPadding = false
            setOnItemClickListener { _, _, pos, _ -> if (pos < shown.size) open(shown[pos].pkg) }
        }
        appsPage.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))

        frame.addView(home, LinearLayout.LayoutParams(-1, 0, 1f))
        frame.addView(appsPage, LinearLayout.LayoutParams(-1, 0, 1f))
        orb = TextView(this).apply {
            textSize = 15f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            background = glass("#0E2A8A", "#4FD8FF", 30); elevation = dp(8).toFloat(); setOnClickListener { orbTap() }
        }
        frame.addView(orb, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(6) })
        val root = FrameLayout(this)
        root.addView(frame, FrameLayout.LayoutParams(-1, -1))
        setupWeb(root)
        setContentView(root)
    }

    private fun bar(): ProgressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 100; layoutParams = LinearLayout.LayoutParams(-1, dp(8))
    }

    /** v7.198: is JARVIS the phone's current default home app? */
    private fun isDefaultHome(): Boolean = try {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == packageName
    } catch (e: Throwable) { false }

    /** v7.198: ask ONCE per opening (not forced): "JARVIS ko Home banao?" -> system chooser (Android 10+ RoleManager, else Home settings). */
    private fun askDefaultHome() {
        if (askedHome || isDefaultHome()) return
        askedHome = true
        try {
            AlertDialog.Builder(this)
                .setTitle("JARVIS ko Home screen banao?")
                .setMessage("Abhi aapka purana launcher Home hai. HAAN dabao, phir phone jo list dikhaye usme JARVIS chuno aur \"Hamesha\" / \"Always\" dabao. Wapas purana launcher kabhi bhi Settings me se chun sakte ho.")
                .setPositiveButton("HAAN") { _, _ -> openHomeChooser() }
                .setNegativeButton("ABHI NAHI", null)
                .show()
        } catch (e: Throwable) { }
    }

    private fun openHomeChooser() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                val rm = getSystemService(android.app.role.RoleManager::class.java)
                if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME) && !rm.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)) {
                    startActivityForResult(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME), 7198)
                    return
                }
            }
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (e: Throwable) {
            try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (e2: Throwable) { }
        }
    }

    override fun onResume() {
        super.onResume()
        loadApps(); buildDock(); refreshStatus(); loadWeather()
        askDefaultHome()
        try { registerReceiver(batRx, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) } catch (e: Throwable) { }
        ui.postDelayed(tick, 5000L)
    }

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(tick)
        try { unregisterReceiver(batRx) } catch (e: Throwable) { }
    }

    /** Home button pressed while already on the launcher: back to the home page. */
    override fun onNewIntent(i: Intent?) {
        super.onNewIntent(i)
        search.setText(""); showApps(false); home.scrollTo(0, 0)
        try { web?.evaluateJavascript("window.goHome&&window.goHome()", null) } catch (e: Throwable) { }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web?.visibility == View.VISIBLE) { try { web?.evaluateJavascript("window.onBack&&window.onBack()", null) } catch (e: Throwable) { }; return }
        if (appsPage.visibility == View.VISIBLE) { search.setText(""); showApps(false) }
    }

    // ------------------------------------------------------------------ status
    private fun batteryPct(): Int {
        val i = try { registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) } catch (e: Throwable) { null }
        val l = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val s = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        return if (l >= 0 && s > 0) l * 100 / s else 0
    }

    private fun setBar(b: ProgressBar, t: TextView, name: String, pct: Int, higherIsBetter: Boolean) {
        b.progress = pct
        b.progressTintList = ColorStateList.valueOf(LauncherRules.barColor(pct, higherIsBetter))
        t.text = "$name  $pct%"
    }

    private fun refreshStatus() {
        try {
            setBar(batBar, batTxt, "Battery", batteryPct(), true)
            val mi = ActivityManager.MemoryInfo()
            (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
            setBar(ramBar, ramTxt, "RAM", LauncherRules.percent(mi.totalMem - mi.availMem, mi.totalMem), false)
            val sf = StatFs(Environment.getDataDirectory().path)
            setBar(stoBar, stoTxt, "Storage", LauncherRules.percent(sf.totalBytes - sf.availableBytes, sf.totalBytes), false)
            val linked = LaptopLinkRules.parseCode("JARVIS-LINK:" + SecureStore.getSlot(applicationContext, LaptopLink.SLOT)) != null
            laptopTxt.text = if (linked) "Laptop jura hua hai (online ho to kaam chalega)" else "Laptop abhi nahi jura. Laptop par \"phone jodo\" bolo."
            status.text = if (JarvisService.running) "Jarvis chalu hai - 'Jarvis' bolo" else "Jarvis band hai - neeche JARVIS dabao"
            val lt = TaskManagerStore.last(applicationContext)
            lastTask.text = if (lt.isBlank()) "" else "Aakhri kaam: $lt"
            refreshBusiness()
            orb.text = if (JarvisService.running) "JARVIS  -  bolo / tap karo" else "JARVIS  -  chalu karo"
            pushState()
        } catch (e: Throwable) { }
    }

    // ------------------------------------------------------------------ weather
    private fun loadWeather() {
        val cached = prefs.getString("w_text", null)
        if (cached != null) { weatherTemp.text = prefs.getString("w_temp", "--°"); weatherText.text = cached; pushState() }
        if (System.currentTimeMillis() - prefs.getLong("w_at", 0L) < 20 * 60_000L) return
        Thread {
            try {
                var ll = lastKnown()
                var place = "Aapki jagah"
                if (ll == null) {
                    val city = prefs.getString("city", "") ?: ""
                    if (city.isBlank()) { ui.post { weatherText.text = "Shehar set karo (tap)" }; return@Thread }
                    ll = LauncherRules.parseGeo(http(WebSources.geoUrl(city)) ?: return@Thread) ?: run { ui.post { weatherText.text = "Shehar nahi mila (tap)" }; return@Thread }
                    place = city
                }
                val w = LauncherRules.parseWeather(http(WebSources.forecastUrl(ll.first, ll.second)) ?: return@Thread) ?: return@Thread
                val temp = w.tempC.toString() + "°C"; val txt = LauncherRules.weatherLabel(w.code) + " - " + place
                prefs.edit().putString("w_temp", temp).putString("w_text", txt).putLong("w_at", System.currentTimeMillis()).apply()
                ui.post { weatherTemp.text = temp; weatherText.text = txt; pushState() }
            } catch (e: Throwable) { }
        }.start()
    }

    private fun http(u: String): String? = try {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        c.inputStream.bufferedReader().use { it.readText() }
    } catch (e: Throwable) { null }

    private fun lastKnown(): Pair<Double, Double>? {
        val ok = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!ok) return null
        return try {
            val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val best = lm.getProviders(true).mapNotNull { try { lm.getLastKnownLocation(it) } catch (e: SecurityException) { null } }.maxByOrNull { it.time }
            if (best == null) null else Pair(best.latitude, best.longitude)
        } catch (e: Throwable) { null }
    }

    private fun askCity() {
        val et = EditText(this).apply { setSingleLine(true); setText(prefs.getString("city", "")) }
        AlertDialog.Builder(this).setTitle("Aapka shehar?").setView(et)
            .setPositiveButton("Save") { _, _ -> prefs.edit().putString("city", et.text.toString().trim()).putLong("w_at", 0L).apply(); loadWeather() }
            .setNegativeButton("Cancel", null).show()
    }

    // ------------------------------------------------------------------ v7.192 L4: Aaj ka business
    /** Numbers come from the real stores on a worker thread (website store pull itself is throttled to 15 min). */
    private fun refreshBusiness() {
        if (bizBusy) return
        bizBusy = true
        Thread {
            try {
                val snap = BusinessHomeStore.snapshot(applicationContext)
                val info = BusinessHomeStore.websiteInfo(applicationContext)
                val lines = BusinessHomeRules.cardLines(snap)
                val hint = if (JarvisHud.canShow(this)) "" else "\nNaya order / lead kisi bhi app ke upar dikhane ke liye 'Display over other apps' ON karo (orb dabao)."
                ui.post {
                    try { bizOrders.text = lines[0]; bizLeads.text = lines[1]; bizAuto.text = lines[2]; bizReport.text = lines[3]; bizInfo.text = info + hint; pushState() } catch (e: Throwable) { }
                }
            } catch (e: Throwable) { } finally { bizBusy = false }
        }.start()
    }

    private fun bizTap(t: BusinessHomeRules.Tap) {
        if (!JarvisService.running) { wakeJarvis(); Toast.makeText(this, "Jarvis shuru ho raha hai, phir se dabao", Toast.LENGTH_SHORT).show(); return }
        if (t == BusinessHomeRules.Tap.REPORT) {
            val ok = try { JarvisService.instance?.speakBusinessReport() ?: false } catch (e: Throwable) { false }
            if (!ok) Toast.makeText(this, "Jarvis abhi doosra kaam kar raha hai", Toast.LENGTH_SHORT).show()
            return
        }
        val cc = BusinessHomeStore.profileCityCategory(this)
        val cmd = BusinessHomeRules.commandFor(t, cc.first, cc.second)
        if (cmd == null) {
            Toast.makeText(this, "Pehle bolo: jarvis biz city <shehar> aur jarvis biz category <dhandha>", Toast.LENGTH_LONG).show()
            return
        }
        val r = try { JarvisService.instance?.typed(cmd) ?: "off" } catch (e: Throwable) { "off" }
        if (r == "busy") Toast.makeText(this, "Jarvis abhi doosra kaam kar raha hai", Toast.LENGTH_SHORT).show()
    }

    // ------------------------------------------------------------------ dock / apps / search
    private fun buildDock() {
        dock.removeAllViews()
        val pins = listOf("com.google.android.youtube" to "YouTube", "com.google.android.googlequicksearchbox" to "Google", "com.android.vending" to "Play Store",
            "com.instagram.android" to "Instagram", "com.whatsapp" to "WhatsApp")
        for ((pkg, name) in pins) {
            if (packageManager.getLaunchIntentForPackage(pkg) == null) continue
            val c = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setOnClickListener { open(pkg) }
                background = glass("#0A1236", "#2E4BFF", 16); setPadding(dp(2), dp(6), dp(2), dp(6))
            }
            c.addView(ImageView(this).apply { try { setImageDrawable(packageManager.getApplicationIcon(pkg)) } catch (e: Throwable) { } }, LinearLayout.LayoutParams(dp(40), dp(40)))
            c.addView(tv(name, 10f, "#FFFFFF").apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER })
            dock.addView(c, LinearLayout.LayoutParams(0, dp(70), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        }
        val more = tv("\u25A6\nMore", 12f, "#4FD8FF", true).apply {
            gravity = Gravity.CENTER; setOnClickListener { showApps(true) }
            background = glass("#0A1236", "#2E4BFF", 16)
        }
        dock.addView(more, LinearLayout.LayoutParams(0, dp(70), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
    }

    private fun showApps(on: Boolean) {
        appsPage.visibility = if (on) View.VISIBLE else View.GONE
        home.visibility = if (on) View.GONE else View.VISIBLE
    }

    private fun onSearchChanged(q: String) {
        shown = LauncherRules.filter(all, q); adapter.notifyDataSetChanged()
        if (q.isNotBlank() && appsPage.visibility != View.VISIBLE && !LauncherRules.looksLikeCommand(q, all)) showApps(true)
    }

    private fun loadApps() {
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = try { packageManager.queryIntentActivities(q, 0) } catch (e: Throwable) { emptyList() }
        all = LauncherRules.sorted(list.filter { it.activityInfo.name != "com.nova.assistant.LauncherEntry" }.map { LauncherRules.AppItem(it.loadLabel(packageManager).toString(), it.activityInfo.packageName) })
        shown = LauncherRules.filter(all, search.text?.toString() ?: ""); adapter.notifyDataSetChanged()
    }

    private fun open(pkg: String) {
        val i = packageManager.getLaunchIntentForPackage(pkg)
        if (i != null) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); try { startActivity(i) } catch (e: Throwable) { } }
    }

    /** Enter pressed: a normal sentence goes to JARVIS, otherwise the best app match opens. */
    private fun submit() {
        val t = search.text.toString().trim()
        if (t.isEmpty()) return
        if (!LauncherRules.looksLikeCommand(t, all) && shown.isNotEmpty()) { open(shown[0].pkg); search.setText(""); showApps(false); return }
        if (!JarvisService.running) { wakeJarvis(); Toast.makeText(this, "Jarvis shuru ho raha hai, phir se bhejo", Toast.LENGTH_SHORT).show(); return }
        val r = try { JarvisService.instance?.typed(t) ?: "off" } catch (e: Throwable) { "off" }
        if (r == "sent") search.setText("") else if (r == "busy") Toast.makeText(this, "Jarvis abhi doosra kaam kar raha hai", Toast.LENGTH_SHORT).show()
    }

    // ------------------------------------------------------------------ orb / torch
    private fun orbTap() {
        if (!JarvisService.running) { wakeJarvis(); return }
        if (!JarvisHud.canShow(this)) {
            // One-time Android permission so JARVIS can show its card over any open app.
            Toast.makeText(this, "Ek baar 'Display over other apps' ON karo", Toast.LENGTH_LONG).show()
            try { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Throwable) { }
            return
        }
        try { startService(Intent(this, JarvisService::class.java).setAction(JarvisService.ACTION_SHOW_POPUP)) } catch (e: Throwable) { }
    }

    private fun wakeJarvis() {
        // The service start needs the one-time permissions the main screen already asks for: open it if anything is missing.
        try { startForegroundService(Intent(this, JarvisService::class.java)) } catch (e: Throwable) {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        status.text = "Jarvis shuru ho raha hai..."
        ui.postDelayed({ refreshStatus() }, 2500L)
    }

    private fun toggleTorch() {
        val cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true } ?: return
        torchOn = !torchOn
        cm.setTorchMode(id, torchOn)
    }

    // ------------------------------------------------------------------ v7.194: animated home (launcher.html)
    private fun setupWeb(root: FrameLayout) {
        try {
            val html = assets.open("launcher.html").bufferedReader().use { it.readText() }
            val w = android.webkit.WebView(this)
            w.setBackgroundColor(Color.parseColor("#02030F"))
            w.settings.javaScriptEnabled = true
            w.settings.domStorageEnabled = true
            w.overScrollMode = View.OVER_SCROLL_NEVER
            w.addJavascriptInterface(Bridge(), "LB")
            w.webViewClient = object : android.webkit.WebViewClient() {
                override fun shouldInterceptRequest(view: android.webkit.WebView?, request: android.webkit.WebResourceRequest?): android.webkit.WebResourceResponse? {
                    val u = request?.url?.toString() ?: return null
                    try {
                        if (u.endsWith("/hero.jpg")) return android.webkit.WebResourceResponse("image/jpeg", null, assets.open("launcher_hero.jpg"))
                        val k = "/icon/"
                        val i = u.indexOf(k)
                        if (i >= 0) return android.webkit.WebResourceResponse("image/png", null, java.io.ByteArrayInputStream(iconPng(Uri.decode(u.substring(i + k.length)))))
                    } catch (e: Throwable) { }
                    return null
                }
                override fun onPageFinished(view: android.webkit.WebView?, url: String?) { pushState() }
            }
            w.loadDataWithBaseURL("https://jarvis.local/", html, "text/html", "utf-8", null)
            root.addView(w, FrameLayout.LayoutParams(-1, -1))
            web = w
        } catch (e: Throwable) { web = null }
    }

    private fun iconPng(pkg: String): ByteArray {
        val d = packageManager.getApplicationIcon(pkg)
        val bmp = Bitmap.createBitmap(144, 144, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, 144, 144); d.draw(Canvas(bmp))
        val out = java.io.ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    private fun stateJson(): String {
        val o = org.json.JSONObject()
        try {
            o.put("run", JarvisService.running)
            o.put("greet", LauncherRules.greeting(Calendar.getInstance().get(Calendar.HOUR_OF_DAY), false))
            o.put("status", status.text.toString()); o.put("last", lastTask.text.toString())
            o.put("orders", bizOrders.text.toString()); o.put("leads", bizLeads.text.toString())
            o.put("auto", bizAuto.text.toString()); o.put("report", bizReport.text.toString()); o.put("info", bizInfo.text.toString())
            o.put("temp", weatherTemp.text.toString()); o.put("wtext", weatherText.text.toString())
            o.put("bat", batBar.progress); o.put("ram", ramBar.progress); o.put("sto", stoBar.progress)
            o.put("laptop", laptopTxt.text.toString())
            val d = org.json.JSONArray()
            for ((pkg, name) in dockPins) if (packageManager.getLaunchIntentForPackage(pkg) != null) d.put(org.json.JSONObject().put("p", pkg).put("n", name))
            o.put("dock", d)
        } catch (e: Throwable) { }
        return o.toString()
    }

    private fun pushState() {
        val w = web ?: return
        try { w.evaluateJavascript("window.setState&&window.setState(" + stateJson() + ")", null) } catch (e: Throwable) { }
    }

    private fun quick(k: String) {
        fun go(i: Intent) { try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Throwable) { Toast.makeText(this, "Ye abhi nahi khula", Toast.LENGTH_SHORT).show() } }
        when (k) {
            "camera" -> go(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
            "call" -> go(Intent(Intent.ACTION_DIAL))
            "message" -> go(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")))
            "wifi" -> go(Intent(Settings.ACTION_WIFI_SETTINGS))
            "dark" -> go(Intent(Settings.ACTION_DISPLAY_SETTINGS))
            "torch" -> { try { toggleTorch() } catch (e: Throwable) { Toast.makeText(this, "Torch abhi nahi chala", Toast.LENGTH_SHORT).show() } }
            "chat" -> go(Intent(this, MainActivity::class.java))
        }
    }

    private fun handleAct(name: String, arg: String) {
        when (name) {
            "orb" -> orbTap()
            "biz" -> bizTap(when (arg) {
                "ORDERS" -> BusinessHomeRules.Tap.ORDERS
                "LEADS" -> BusinessHomeRules.Tap.LEADS
                "AUTOPILOT" -> BusinessHomeRules.Tap.AUTOPILOT
                "FIND" -> BusinessHomeRules.Tap.FIND
                else -> BusinessHomeRules.Tap.REPORT
            })
            "open" -> open(arg)
            "submit" -> { search.setText(arg); submit() }
            "quick" -> quick(arg)
            "city" -> askCity()
            "classic" -> { web?.visibility = View.GONE }
        }
    }

    /** JavaScript side of launcher.html. Every call is handed to the main thread; nothing here decides anything by itself. */
    private inner class Bridge {
        @android.webkit.JavascriptInterface
        fun apps(q: String): String {
            val arr = org.json.JSONArray()
            try { for (a in LauncherRules.filter(all, q)) arr.put(org.json.JSONObject().put("n", a.label).put("p", a.pkg)) } catch (e: Throwable) { }
            return arr.toString()
        }
        @android.webkit.JavascriptInterface
        fun cmd(q: String): Boolean = try { LauncherRules.looksLikeCommand(q, all) } catch (e: Throwable) { false }
        @android.webkit.JavascriptInterface
        fun act(name: String, arg: String) { ui.post { try { handleAct(name, arg) } catch (e: Throwable) { } } }
    }

    private inner class AppAdapter : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(p: Int) = shown[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getView(p: Int, v: View?, parent: ViewGroup?): View {
            val cell = (v as? LinearLayout) ?: LinearLayout(this@LauncherActivity).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                addView(ImageView(context), LinearLayout.LayoutParams(dp(46), dp(46)))
                addView(TextView(context).apply { textSize = 11f; setTextColor(Color.WHITE); maxLines = 1; gravity = Gravity.CENTER; ellipsize = TextUtils.TruncateAt.END })
            }
            val it = shown[p]
            (cell.getChildAt(1) as TextView).text = it.label
            val iv = cell.getChildAt(0) as ImageView
            try { iv.setImageDrawable(packageManager.getApplicationIcon(it.pkg)) } catch (e: Throwable) { iv.setImageDrawable(null) }
            return cell
        }
    }
}
