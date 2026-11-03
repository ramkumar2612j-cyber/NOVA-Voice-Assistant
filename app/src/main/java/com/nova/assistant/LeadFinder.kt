package com.nova.assistant

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL

/**
 * v7.169 LEAD FINDER (Android layer): Google Places search with the owner's own key -> filtered picks -> LeadStore with the owner's pitch as the draft.
 * Never sends anything. The owner approves and the existing "lead bhejo" flow (daily cap) does the sending.
 * Must run off the main thread (network).
 */
object LeadFinder {
    class Result(val added: Int, val message: String)

    fun hasKey(ctx: Context): Boolean = SecureStore.hasSlot(ctx, BizStore.SLOT_PLACES)

    /** One search + add. [force] = owner said "leads abhi" (ignores the ON switch, still honours the daily goal). */
    fun run(ctx: Context, en: Boolean, force: Boolean): Result {
        val app = ctx.applicationContext
        try {
            val prof = BizStore.profile(app)
            if (!force && !prof.leadsOn) return Result(0, "")
            val miss = BizRules.missingForLeads(prof)
            if (miss.isNotEmpty()) return Result(0, (if (en) "Lead search needs: " else "Lead search ke liye ye bharo: ") + miss.joinToString(", ") + (if (en) " (say: nova biz <field> <value>)." else " (bolo: nova biz <field> <value>)."))
            val key = SecureStore.getSlot(app, BizStore.SLOT_PLACES)
            if (key.isEmpty()) return Result(0, if (en) "Google Places key is not saved (say: nova biz placeskey <key>)." else "Google Places key save nahi hai (bolo: nova biz placeskey <key>).")
            val room = LeadFinderRules.remainingToday(prof.perDay, BizStore.addedToday(app))
            if (room <= 0) return Result(0, if (en) "Today's lead goal (${prof.perDay}) is already done." else "Aaj ka lead goal (${prof.perDay}) pehle hi pura hai.")

            val q = BizRules.leadQuery(prof)
            val c = URL(LeadFinderRules.ENDPOINT).openConnection() as HttpURLConnection
            c.requestMethod = "POST"; c.connectTimeout = 8000; c.readTimeout = 15000; c.doOutput = true; c.instanceFollowRedirects = false
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("X-Goog-Api-Key", key)
            c.setRequestProperty("X-Goog-FieldMask", LeadFinderRules.FIELD_MASK)
            val code: Int; val body: String
            try {
                c.outputStream.use { it.write(LeadFinderRules.requestBody(q).toByteArray(Charsets.UTF_8)) }
                code = c.responseCode
                body = (if (code in 200..299) c.inputStream else c.errorStream)?.use { it.readBytes().take(400_000).toByteArray().toString(Charsets.UTF_8) }.orEmpty()
            } catch (e: java.io.IOException) { return Result(0, LeadFinderRules.humanError(598, en)) } finally { c.disconnect() }
            if (code !in 200..299) return Result(0, LeadFinderRules.humanError(code, en))

            val known = LeadStore.list(app).map { l -> l.phone.filter { it.isDigit() } }.toSet()
            val picks = LeadFinderRules.pick(LeadFinderRules.parse(body), known, emptySet(), room)
            var added = 0
            for ((pl, phone) in picks) {
                val r = LeadStore.add(app, LeadRules.Candidate(pl.name, phone, pl.address, pl.website, "com.google.android.apps.maps"))
                val lead = r.second
                if (r.first && lead != null && lead.draft.isBlank()) {
                    LeadStore.createDraft(app, if (en) "en" else "hi", lead.id, BizRules.pitch(prof, pl.name, en))
                    added++
                }
            }
            if (added > 0) BizStore.noteAdded(app, added)
            val msg = if (added > 0) (if (en) "$added new leads found (\"$q\") - drafts are ready, review them with: nova leads dikhao." else "$added nayi leads mili (\"$q\") - draft taiyar hain, dekhne ke liye bolo: nova leads dikhao.")
            else (if (en) "No new genuine leads found for \"$q\" this time." else "\"$q\" ke liye is baar koi nayi genuine lead nahi mili.")
            return Result(added, msg)
        } catch (e: Throwable) {
            ErrLog.add("LeadFinder", "run: $e")
            return Result(0, if (en) "Lead search failed." else "Lead search fail hua.")
        }
    }

    /** Called from the Autopilot pass (every ~15 min): at most ONE automatic search per day, only when the owner switched leads ON. */
    fun dailyTick(ctx: Context) {
        val app = ctx.applicationContext
        try {
            val prof = BizStore.profile(app)
            if (!prof.leadsOn) return
            val sp = app.getSharedPreferences("nova_biz", Context.MODE_PRIVATE)
            val today = java.time.LocalDate.now().toString()
            if (sp.getString("finder_day", "") == today) return
            sp.edit().putString("finder_day", today).commit()
            val r = LeadFinder.run(app, false, false)
            if (r.message.isNotBlank()) NovaService.instance?.onBizNote(r.message)
        } catch (e: Throwable) { ErrLog.add("LeadFinder", "tick: $e") }
    }
}
