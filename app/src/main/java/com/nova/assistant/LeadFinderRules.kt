package com.nova.assistant

import org.json.JSONArray
import org.json.JSONObject

/**
 * v7.169 LEAD FINDER rules - pure (JSON in, picks out). Source: Google Places API (New) "Text Search" with the OWNER's own API key.
 * Only real fields Google returns are used: name, phone, website, address, rating, review count, business status.
 * Filter = genuine-looking business: OPERATIONAL, a callable mobile number, rating >= 3.5 with >= 3 reviews, not already a lead, not on the block list.
 * NOTHING is sent from here. Picks go to LeadStore as drafts; the existing approve -> send flow (daily cap, owner approval) stays in charge.
 */
object LeadFinderRules {
    const val ENDPOINT = "https://places.googleapis.com/v1/places:searchText"
    const val FIELD_MASK = "places.displayName,places.nationalPhoneNumber,places.internationalPhoneNumber,places.websiteUri,places.formattedAddress,places.rating,places.userRatingCount,places.businessStatus"
    const val MIN_RATING = 3.5
    const val MIN_REVIEWS = 3

    data class Place(val name: String, val phone: String, val website: String, val address: String, val rating: Double, val reviews: Int, val status: String)

    fun requestBody(query: String, pageSize: Int = 20): String =
        JSONObject().put("textQuery", query.take(120)).put("pageSize", pageSize.coerceIn(1, 20)).put("regionCode", "IN").toString()

    fun parse(json: String): List<Place> {
        val out = ArrayList<Place>()
        try {
            val arr: JSONArray = JSONObject(json).optJSONArray("places") ?: return out
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.optJSONObject("displayName")?.optString("text").orEmpty().trim()
                if (name.isEmpty()) continue
                val phone = o.optString("internationalPhoneNumber").ifBlank { o.optString("nationalPhoneNumber") }
                out.add(Place(name.take(100), phone.take(24), o.optString("websiteUri").take(220), o.optString("formattedAddress").take(140),
                    o.optDouble("rating", 0.0), o.optInt("userRatingCount", 0), o.optString("businessStatus")))
            }
        } catch (e: Exception) { /* bad body -> nothing */ }
        return out
    }

    private fun digits(s: String) = s.filter { it.isDigit() }

    /** Indian mobile (starts 6-9, 10 digits after an optional 91 / 0). Landlines are not WhatsApp-able. */
    fun mobile(raw: String): String {
        var d = digits(raw)
        if (d.length == 12 && d.startsWith("91")) d = d.substring(2)
        if (d.length == 11 && d.startsWith("0")) d = d.substring(1)
        return if (d.length == 10 && d[0] in '6'..'9') "+91$d" else ""
    }

    /** Genuine-looking and new. [known] = digits of phones already in LeadStore; [blocked] = digits the owner never wants. */
    fun pick(places: List<Place>, known: Set<String>, blocked: Set<String>, max: Int): List<Pair<Place, String>> {
        val seen = HashSet<String>()
        return places.asSequence()
            .filter { it.status.isEmpty() || it.status == "OPERATIONAL" }
            .filter { it.rating >= MIN_RATING && it.reviews >= MIN_REVIEWS }
            .map { it to mobile(it.phone) }
            .filter { it.second.isNotEmpty() }
            .filter { digits(it.second) !in known && digits(it.second) !in blocked }
            .filter { seen.add(digits(it.second)) }
            .sortedByDescending { it.first.reviews * it.first.rating }
            .take(max.coerceIn(0, BizRules.MAX_LEADS))
            .toList()
    }

    /** How many more leads may be added today (daily goal minus already added today), never above 10. */
    fun remainingToday(perDay: Int, addedToday: Int): Int = (perDay.coerceIn(BizRules.MIN_LEADS, BizRules.MAX_LEADS) - addedToday).coerceAtLeast(0)

    fun humanError(code: Int, en: Boolean): String = when (code) {
        400 -> if (en) "Google rejected the search (check city/category)." else "Google ne search mana kiya (sheher/category check karo)."
        401, 403 -> if (en) "The Google Places key was refused (key wrong, or Places API (New) not enabled)." else "Google Places key mana ho gayi (key galat ya Places API (New) chalu nahi)."
        429 -> if (en) "Google Places limit reached for now." else "Google Places ki limit abhi poori ho gayi."
        598 -> if (en) "No internet." else "Internet nahi hai."
        else -> if (en) "Google Places failed (code $code)." else "Google Places fail hua (code $code)."
    }
}
