package com.nova.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * v7.179 LEAD FINDER (Android layer, integrated). The owner speaks a search:
 *   "jarvis delhi me restaurant ke genuine leads dhoondo"
 * JARVIS parses it ([LeadFinderRules]), runs a REAL public-directory search, filters + scores the
 * results, saves them to [LeadStore] (so the existing "leads dikhao" / draft / send flow works),
 * pushes a live card to the UI, and reports back honestly.
 *
 * Sources (in order, first one that returns usable leads wins):
 *  1. Google Places API (Text Search) — used WHEN the owner saved a Places key (jarvis biz placeskey <k>).
 *     Structured phone + website + address + reviews -> honest scoring.
 *  2. OpenStreetMap Overpass (anonymous, public, no key) — used when no Places key is set.
 *     Phone + website + address from OSM tags -> scoring with no reviews (so capped lower).
 *
 * Reality (told to the owner, never hidden):
 *  - Every lead is a PUBLIC business listing. NO login walls, NO owner contacts read, NO broker data.
 *  - "Genuine" is a SCORE (0..100), not a promise a lead will buy. The score reflects only what is
 *    verifiable from the public listing (phone, website, reviews, recent activity, address).
 *  - Rate-limited: at most [LeadFinderRules.MAX_LEADS_PER_SEARCH] kept, at most
 *    [LeadFinderRules.MAX_SEARCHES_PER_DAY] searches/day, at most [BizStore] per-day goal added.
 *
 * One live search at a time. [StopRules] kills it. Must run off the main thread (network).
 */
object LeadFinder {

    @Volatile var active = false
        private set

    @Volatile private var state = LeadFinderRules.State.IDLE
    @Volatile private var niche = ""
    @Volatile private var city = ""
    @Volatile private var genuine = false
    @Volatile private var leads: List<LeadFinderRules.Lead> = emptyList()
    @Volatile private var startedAt = 0L
    private val lock = Any()
    private var searchCountToday = 0
    private var dayStamp = 0L

    /** UI push: (role, text). role "leadcard" lines are JSON. Set by JarvisService. */
    @Volatile var emitter: ((role: String, text: String) -> Unit)? = null

    /** The report of the last finished search (bridge shows it on the card when the app opens). */
    @Volatile var lastReport = ""
        private set

    // ------------------------------------------------------------------ state push

    private fun poke(stateNew: LeadFinderRules.State, text: String) {
        state = stateNew
        val json = JSONObject()
            .put("s", stateNew.name)
            .put("niche", niche)
            .put("city", city)
            .put("txt", text)
            .put("n", leads.size)
            .put("secs", if (startedAt > 0) (System.currentTimeMillis() - startedAt) / 1000L else 0L)
        try { emitter?.invoke("leadcard", json.toString()) } catch (ignored: Exception) { }
    }

    /** The whole live card as JSON (bridge pulls this when the app opens). */
    fun snapshotJson(): String = synchronized(lock) {
        val arr = JSONArray()
        for (l in leads) arr.put(LeadFinderRules.leadToJson(l))
        JSONObject()
            .put("active", active)
            .put("s", state.name)
            .put("niche", niche)
            .put("city", city)
            .put("genuine", genuine)
            .put("leads", arr)
            .put("report", lastReport)
            .put("secs", if (startedAt > 0) (System.currentTimeMillis() - startedAt) / 1000L else 0L)
            .toString()
    }

    // ------------------------------------------------------------------ start (voice / chat)

    /**
     * Voice / chat entry. Returns the one sentence JARVIS says right now; the search runs on its own thread.
     * [onLeads] is called (on the same worker thread) with the final leads list so JarvisService can
     * speak the count + push the card once.
     */
    fun start(ctx: Context, text: String, en: Boolean, onDone: (spoken: String) -> Unit): String {
        if (active) return if (en) "One lead search is already running. Say stop to end it first." else "Ek lead search pehle se chal raha hai. Pehle ruko bolo."
        if (LeadFinderRules.denied(text)) return if (en) "I do not search for that kind of lead." else "Is tarah ke leads main nahi dhoondhta."
        val p = LeadFinderRules.parse(text)
        if (p.niche.isBlank()) return if (en) "Which kind of business? Say: restaurant / salon / gym / doctor..." else "Kaunse business ke leads? Bolo: restaurant / salon / gym / doctor..."
        if (p.city.isBlank()) return if (en) "Which city? Say: delhi / mumbai / jaipur..." else "Kaunse shahar? Bolo: delhi / mumbai / jaipur..."
        if (!canSearchToday()) return if (en) "Daily search limit reached (${LeadFinderRules.MAX_SEARCHES_PER_DAY}). Try tomorrow." else "Aaj ki search limit puri (${LeadFinderRules.MAX_SEARCHES_PER_DAY}). Kal try karo."
        synchronized(lock) {
            active = true
            state = LeadFinderRules.State.SEARCHING
            this.niche = p.niche
            this.city = p.city
            this.genuine = p.genuineOnly
            this.leads = emptyList()
            startedAt = System.currentTimeMillis()
            bumpDayCounter()
        }
        Thread { run(ctx, p.niche, p.city, p.genuineOnly, en, onDone) }.start()
        return if (en) "Searching ${p.niche} leads in ${p.city}..." else "${p.city} me ${p.niche} ke leads dhoondh raha hoon..."
    }

    /** STOP ("ruko", "bas karo") and the bridge both come here. Safe to call any time. */
    fun stop(ctx: Context): Boolean {
        if (!active) return false
        poke(LeadFinderRules.State.DONE, "stopped")
        active = false
        return true
    }

    // ------------------------------------------------------------------ rate limit (per day)

    private fun canSearchToday(): Boolean {
        val today = System.currentTimeMillis() / 86_400_000L
        if (today != dayStamp) { dayStamp = today; searchCountToday = 0 }
        return searchCountToday < LeadFinderRules.MAX_SEARCHES_PER_DAY
    }
    private fun bumpDayCounter() {
        val today = System.currentTimeMillis() / 86_400_000L
        if (today != dayStamp) { dayStamp = today; searchCountToday = 0 }
        searchCountToday += 1
    }

    // ------------------------------------------------------------------ the search loop

    private fun run(ctx: Context, niche: String, city: String, genuine: Boolean, en: Boolean, onDone: (String) -> Unit) {
        val app = ctx.applicationContext
        try {
            poke(LeadFinderRules.State.SEARCHING, if (en) "Searching $niche in $city..." else "$city me $niche dhoondh raha hoon...")
            // 1) try Google Places if the owner saved a key (best data: phone + web + reviews)
            var raw: List<LeadFinderRules.Lead> = emptyList()
            val placesKey = try { SecureStore.getSlot(app, BizStore.SLOT_PLACES) } catch (e: Exception) { "" }
            if (placesKey.isNotEmpty()) {
                raw = placesSearch(app, niche, city, placesKey)
            }
            if (!active) { onDone(stoppedReport(en)); return }

            // 2) fallback: Overpass (anonymous, public) when Places returned too few or no key
            if (raw.size < 5) {
                poke(LeadFinderRules.State.FETCHING, if (en) "Open public directory..." else "Public directory khol raha hoon...")
                val osm = overpassSearch(niche, city)
                if (osm.isNotEmpty()) raw = (raw + osm).distinctBy { it.name.lowercase() }
            }
            if (!active) { onDone(stoppedReport(en)); return }

            poke(LeadFinderRules.State.FILTERING, if (en) "Filtering + scoring ${raw.size} leads..." else "${raw.size} leads filter + score ho rahe hain...")
            // 3) filter + score + dedupe
            val filtered = LeadFinderRules.filter(raw, genuine)
            leads = filtered
            val count = filtered.size

            // 4) save to LeadStore so the existing "leads dikhao" / draft / send flow still works
            val prof = try { BizStore.profile(app) } catch (e: Exception) { null }
            val room = if (prof != null) (prof.perDay - BizStore.addedToday(app)).coerceAtLeast(0) else 0
            var added = 0
            for (l in filtered) {
                if (room > 0 && added >= room) break
                val r = try { LeadStore.add(app, LeadRules.Candidate(l.name, l.phone, l.address, l.website, "com.google.android.apps.maps")) } catch (e: Exception) { null }
                if (r != null && r.first && r.second != null && r.second!!.draft.isBlank()) {
                    try {
                        if (prof != null) LeadStore.createDraft(app, if (en) "en" else "hi", r.second!!.id, BizRules.pitch(prof, l.name, en))
                        else LeadStore.createDraft(app, if (en) "en" else "hi", r.second!!.id, "")
                    } catch (e: Exception) { }
                    added++
                }
            }
            if (added > 0 && prof != null) try { BizStore.noteAdded(app, added) } catch (e: Exception) { }

            val rep = LeadFinderRules.report(LeadFinderRules.State.DONE, niche, city, count, en)
            poke(LeadFinderRules.State.DONE, rep)
            active = false
            lastReport = rep
            onDone(rep)
        } catch (t: Throwable) {
            ErrLog.add("LeadFinder", "run: $t")
            val rep = LeadFinderRules.report(LeadFinderRules.State.FAILED, niche, city, 0, en)
            poke(LeadFinderRules.State.FAILED, rep)
            active = false
            lastReport = rep
            onDone(rep)
        }
    }

    private fun stoppedReport(en: Boolean): String {
        active = false
        lastReport = if (en) "Lead search stopped." else "Lead search ruk gaya."
        return lastReport
    }

    // ------------------------------------------------------------------ Google Places (Text Search)

    /** Google Places API (Text Search) -> raw leads. Owner's own key (no key = returns empty). */
    private fun placesSearch(ctx: Context, niche: String, city: String, key: String): List<LeadFinderRules.Lead> {
        val query = "$niche in $city"
        val body = JSONObject().put("textQuery", query).put("languageCode", "en").put("maxResultCount", 20).toString()
        var c: HttpURLConnection? = null
        try {
            c = (URL("https://places.googleapis.com/v1/places:searchText").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 10000; readTimeout = 20000; doOutput = true; instanceFollowRedirects = false
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("X-Goog-Api-Key", key)
                setRequestProperty("X-Goog-FieldMask", "places.displayName,places.formattedAddress,places.internationalPhoneNumber,places.websiteUri,places.rating,places.userRatingCount")
            }
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val resp = (if (code in 200..299) c.inputStream else c.errorStream)?.use { it.readBytes().take(300_000).toByteArray().toString(Charsets.UTF_8) }.orEmpty()
            if (code !in 200..299 || resp.isBlank()) {
                ErrLog.addOnce("LeadFinder", "places code=$code")
                return emptyList()
            }
            val out = ArrayList<LeadFinderRules.Lead>()
            val places = JSONObject(resp).optJSONArray("places") ?: return emptyList()
            for (i in 0 until places.length()) {
                val p = places.getJSONObject(i)
                val name = p.optJSONObject("displayName")?.optString("text", "") ?: ""
                if (name.isBlank()) continue
                val phone = p.optString("internationalPhoneNumber", p.optString("nationalPhoneNumber", ""))
                val web = p.optString("websiteUri", "")
                val addr = p.optString("formattedAddress", "")
                val rate = p.optDouble("rating", 0.0)
                val rev = p.optInt("userRatingCount", 0)
                out.add(LeadFinderRules.Lead(name, phone, web, addr, niche, rev, rate, 30, "Google Places", "https://www.google.com/maps/search/?api=1&query=" + URLEncoder.encode(name + " " + city, "UTF-8")))
                if (out.size >= 30) break
            }
            return out
        } catch (e: Exception) { ErrLog.add("LeadFinder", "places: $e"); return emptyList() }
        finally { c?.disconnect() }
    }

    // ------------------------------------------------------------------ OpenStreetMap Overpass (no key)

    private data class Bbox(val s: Double, val w: Double, val n: Double, val e: Double)

    /** Nominatim (anonymous, public) -> city bounding box. null = could not resolve. */
    private fun nominatimBbox(city: String): Bbox? {
        val url = "https://nominatim.openstreetmap.org/search?format=json&q=" + URLEncoder.encode(city, "UTF-8") + "&limit=1&countrycodes=in"
        val res = ConnectorApi.http("GET", url, mapOf("Accept" to "application/json"), null)
        if (res.code !in 200..299 || res.body.isBlank()) return null
        return try {
            val arr = JSONArray(res.body)
            if (arr.length() == 0) return null
            val bb = arr.getJSONObject(0).optJSONArray("boundingbox") ?: return null
            Bbox(bb.getString(0).toDouble(), bb.getString(2).toDouble(), bb.getString(1).toDouble(), bb.getString(3).toDouble())
        } catch (e: Exception) { ErrLog.add("LeadFinder", "nominatim: $e"); null }
    }

    private fun nicheToOsm(niche: String): String = when (niche) {
        "restaurant", "hotel", "cafe", "dhaba" -> "amenity"
        "salon", "parlour", "parlor", "gym" -> "leisure"
        "doctor", "clinic", "hospital", "dentist", "pharmacy", "chemist" -> "amenity"
        "bakery", "garage", "shop", "store", "kirana", "wholesale", "fmcg", "mobile", "electronics", "hardware", "furniture", "jewellery", "jewelry", "saree", "garments", "tailor" -> "shop"
        "school", "coaching", "institute" -> "amenity"
        else -> "shop"
    }

    private fun nicheToTagValue(niche: String): String = when (niche) {
        "restaurant" -> "restaurant"; "hotel" -> "hotel"; "cafe" -> "cafe"; "dhaba" -> "fast_food"
        "salon", "parlour", "parlor" -> "hairdresser"; "gym" -> "fitness_centre"
        "doctor" -> "doctors"; "clinic" -> "clinic"; "hospital" -> "hospital"; "dentist" -> "dentist"
        "pharmacy", "chemist" -> "pharmacy"; "bakery" -> "bakery"; "garage" -> "car_repair"
        "kirana", "wholesale", "fmcg" -> "supermarket"; "mobile", "electronics" -> "mobile_phone"
        "hardware" -> "hardware"; "furniture" -> "furniture"; "jewellery", "jewelry" -> "jewelry"
        "saree", "garments", "tailor" -> "clothes"; "school" -> "school"; "coaching", "institute" -> "college"
        else -> niche
    }

    private fun overpassSearch(niche: String, city: String): List<LeadFinderRules.Lead> {
        val bb = nominatimBbox(city) ?: return emptyList()
        val key = nicheToOsm(niche)
        val value = nicheToTagValue(niche)
        val q = ("[out:json][timeout:25];(node[\"$key\"=\"$value\"](${bb.s},${bb.w},${bb.n},${bb.e});"
            + "way[\"$key\"=\"$value\"](${bb.s},${bb.w},${bb.n},${bb.e}););out center 80;")
        val res = ConnectorApi.http("POST", "https://overpass-api.de/api/interpreter",
            mapOf("Accept" to "application/json", "User-Agent" to "JARVIS-assistant"), q, "text/plain")
        if (res.code !in 200..299 || res.body.isBlank()) { ErrLog.addOnce("LeadFinder", "overpass code=${res.code}"); return emptyList() }
        return try {
            val els = JSONObject(res.body).optJSONArray("elements") ?: return emptyList()
            val out = ArrayList<LeadFinderRules.Lead>()
            for (i in 0 until els.length()) {
                val el = els.getJSONObject(i)
                val t = el.optJSONObject("tags") ?: continue
                val name = t.optString("name", "")
                if (name.isBlank()) continue
                val phone = t.optString("phone", t.optString("contact:phone", t.optString("mobile", "")))
                val website = t.optString("website", t.optString("contact:website", t.optString("url", "")))
                val addr = listOf(t.optString("addr:housenumber", ""), t.optString("addr:street", ""), t.optString("addr:city", "")).filter { it.isNotBlank() }.joinToString(" ")
                val cat = t.optString("amenity", t.optString("shop", t.optString("leisure", niche)))
                out.add(LeadFinderRules.Lead(name, phone, website, addr, cat, 0, 0.0, 0, "OpenStreetMap",
                    "https://www.openstreetmap.org/" + (if (el.optString("type") == "node") "node" else "way") + "/" + el.optLong("id")))
                if (out.size >= 40) break
            }
            out
        } catch (e: Exception) { ErrLog.add("LeadFinder", "overpass parse: $e"); emptyList() }
    }

    /** The bridge exposes this so the JS card can check a search really went out. */
    fun searchedRecent(ms: Long = 120_000L): Boolean = active || (System.currentTimeMillis() - startedAt) < ms

    // ------------------------------------------------------------------ legacy compat (v7.169 callers)

    /** v7.169 compat: true when a Google Places key is saved (the rich-data source). */
    fun hasKey(ctx: Context): Boolean = try { SecureStore.getSlot(ctx.applicationContext, BizStore.SLOT_PLACES).isNotEmpty() } catch (e: Exception) { false }

    /** v7.169 compat result type, kept so the old leads-ON switch path still compiles. */
    class Result(val added: Int, val message: String)

    /**
     * v7.169 compat: the old "leads ON" switch path called this. We now route it through the new
     * voice-search [start] using the saved biz profile (category + city). Returns a Result so the
     * existing caller in JarvisService compiles and works the same way.
     */
    fun run(ctx: Context, en: Boolean, force: Boolean): Result {
        val app = ctx.applicationContext
        try {
            val prof = BizStore.profile(app)
            if (!force && !prof.leadsOn) return Result(0, "")
            val text = "${prof.category} ${prof.city} ke genuine leads dhoondo"
            start(app, text, en) { msg -> if (msg.isNotBlank()) JarvisService.instance?.onBizNote(msg) }
            return Result(0, "")
        } catch (e: Throwable) {
            ErrLog.add("LeadFinder", "run-compat: $e")
            return Result(0, if (en) "Lead search failed." else "Lead search fail hua.")
        }
    }

    // ------------------------------------------------------------------ legacy Autopilot tick (kept compatible)

    /** Called from the Autopilot pass (every ~15 min): at most ONE automatic search per day, only when leads ON. */
    fun dailyTick(ctx: Context) {
        val app = ctx.applicationContext
        try {
            val prof = BizStore.profile(app)
            if (!prof.leadsOn) return
            val sp = app.getSharedPreferences("nova_biz", Context.MODE_PRIVATE)
            val today = java.time.LocalDate.now().toString()
            if (sp.getString("finder_day", "") == today) return
            sp.edit().putString("finder_day", today).commit()
            // synthesize a voice-shaped search from the saved biz profile
            val text = "${prof.category} ${prof.city} ke genuine leads dhoondo"
            val en = Cfg(app).lang == "en"
            start(app, text, en) { msg -> if (msg.isNotBlank()) JarvisService.instance?.onBizNote(msg) }
        } catch (e: Throwable) { ErrLog.add("LeadFinder", "tick: $e") }
    }
}
