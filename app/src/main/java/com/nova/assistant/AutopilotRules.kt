package com.nova.assistant

/**
 * v7.158 AUTOPILOT - PURE RULES (no Android imports, JUnit-able, exactly like JarvisDeepRules).
 *
 * The owner's goal in his own words:
 *  1. Whatever is uploaded in his WhatsApp Business GROUP is posted automatically (he says nothing) to his
 *     WEBSITE, INSTAGRAM (reel / post / story) and YOUTUBE (Shorts), written to have the best chance to go viral.
 *  2. Anyone who contacts for business on Instagram / WhatsApp: NOVA talks to them, takes the order, confirms it.
 *     A discount question gets "website se order karoge to discount milega" + the offer CURRENTLY running on the website.
 *
 * This file decides EVERYTHING that can be decided without the network, the disk or Android:
 * media kind, group-title match, the "arming" state, IST best-time slots, the AI copy pack (prompt / parse /
 * honest fallback), website offer extraction, the order state machine, the discount reply, the site feed JSON,
 * the Instagram host by token prefix, the sent-by-me verdict, safe-ignore kinds, 90-second batching,
 * hash de-dupe, daily caps with spacing and the idempotency key.
 * The Android layer lives in Autopilot.kt; the network in AutopilotNet.kt; the storage in AutopilotStore.kt.
 */
object AutopilotRules {

    // ---------------------------------------------------------------- media kind

    enum class Kind { IMAGE, VIDEO, OTHER }

    private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "heic", "bmp")
    private val VIDEO_EXT = setOf("mp4", "mov", "mkv", "avi", "3gp", "webm")

    /** IMAGE / VIDEO / OTHER from the file name + mime. The name wins (WhatsApp names are honest), mime breaks ties. */
    fun kindOf(name: String, mime: String): Kind {
        val ext = name.substringAfterLast('.', "").lowercase().trim()
        if (ext in IMAGE_EXT) return Kind.IMAGE
        if (ext in VIDEO_EXT) return Kind.VIDEO
        val m = mime.lowercase().trim()
        if (m.startsWith("image/")) return Kind.IMAGE
        if (m.startsWith("video/")) return Kind.VIDEO
        return Kind.OTHER
    }

    // ---------------------------------------------------------------- group-title match

    /**
     * WhatsApp group notifications carry the title "Group Name: Sender" (or just the group name when Android
     * collapses it). true only when the title belongs to [group] (case-insensitive, trimmed, tolerant to the
     * sender suffix). A blank group setting never matches anything.
     */
    fun matchesGroup(notifTitle: String, group: String): Boolean {
        val g = group.trim().lowercase()
        if (g.isEmpty() || notifTitle.isBlank()) return false
        val t = notifTitle.trim().lowercase()
        if (t == g) return true
        if (t.startsWith("$g:")) return true
        // "Group Name (3 messages)" style leftovers are tolerated, the sender suffix is not the group
        return false
    }

    // ---------------------------------------------------------------- arming

    /** What the UI / voice asks for. ok=false always carries at least one Hinglish reason. */
    class Armed(val ok: Boolean, val reasons: List<String>) {
        fun line(): String = if (ok) "AUTOPILOT ready hai" else reasons.joinToString(" ")
    }

    /**
     * AUTOPILOT may run only when the master switch is ON, the group name is set and at least one target has
     * what it needs (GitHub token for the site; IG token for feed/reel/story; YT client for Shorts).
     * Every missing thing becomes a Hinglish reason so the owner can act without reading logs.
     */
    fun armed(masterOn: Boolean, group: String, hasGithub: Boolean, hasIg: Boolean, hasYt: Boolean, siteOn: Boolean, igOn: Boolean, ytOn: Boolean): Armed {
        val why = ArrayList<String>()
        if (!masterOn) why.add("AUTOPILOT OFF hai")
        if (group.isBlank()) why.add("group ka naam nahi dala")
        if (!siteOn && !igOn && !ytOn) why.add("koi target ON nahi hai (WEBSITE / INSTAGRAM / YOUTUBE me se koi bhi)")
        if (siteOn && !hasGithub) why.add("GitHub token nahi dala (website ke liye)")
        if (igOn && !hasIg) why.add("Instagram token nahi dala")
        if (ytOn && !hasYt) why.add("YouTube (Google) client nahi dala")
        return Armed(why.isEmpty(), why.ifEmpty { listOf("theek hai") })
    }

    // ---------------------------------------------------------------- IST best-time slots

    /** India posting slots (IST). Copy hooks and reach are best in these windows for a small Indian brand. */
    val SLOT_TIMES_IST = listOf(9 to 0, 12 to 30, 15 to 30, 18 to 30, 20 to 30, 22 to 0)

    const val IST_ZONE = "Asia/Kolkata"

    /**
     * The next slot strictly after [nowMs] (epoch millis). Returns epoch millis of the slot + a short label.
     * When today's slots are over, tomorrow's first slot (9:00 IST) comes back. Pure date math, no clock reads.
     */
    fun nextSlot(nowMs: Long): Pair<Long, String> {
        val zone = java.time.ZoneId.of(IST_ZONE)
        var day = java.time.Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val now = java.time.Instant.ofEpochMilli(nowMs).atZone(zone)
        for (d in 0..1) {
            for ((h, m) in SLOT_TIMES_IST) {
                val t = day.atTime(h, m).atZone(zone)
                if (t.toInstant().toEpochMilli() > nowMs) return Pair(t.toInstant().toEpochMilli(), slotLabel(h, m))
            }
            day = day.plusDays(1)
        }
        return Pair(nowMs, "abhi")   // unreachable, keeps the compiler happy
    }

    private fun slotLabel(h: Int, m: Int): String = String.format("%02d:%02d IST", h, m)

    // ---------------------------------------------------------------- AI copy pack

    /** One platform copy pack. All fields plain text; hashtags without '#'. */
    class Pack(
        val igCaption: String, val hashtags: List<String>, val storyText: String, val pollText: String,
        val ytTitle: String, val ytDesc: String, val ytTags: List<String>, val siteBlurb: String, val lang: String
    ) {
        fun igHashtagLine(): String = (if (hashtags.isEmpty()) "" else " " + hashtags.joinToString(" ") { "#$it" })
        fun ytTagLine(): String = ytTags.joinToString(",")
    }

    /**
     * The AI prompt for one media item. [vision] is what FreeVision really saw in the photo / frame ("" when it
     * could not). [lastCaptions] are the last 3 caption first-lines, so the AI never repeats itself. The prompt
     * asks for STRICT JSON with every field, in the owner's language mix (Hinglish first, Hindi / English allowed).
     */
    fun packPrompt(item: String, vision: String, brand: String, offer: String, lang: String, lastCaptions: List<String>, platformTargets: List<String>): String {
        val b = StringBuilder()
        b.append("Tum ek Indian social-media copywriter ho. Ek ").append(item)
            .append(" ko website, Instagram aur YouTube par viral karne ke liye copy likho.\n")
        if (vision.isNotBlank()) b.append("PHOTO ME ASLI CHEEZ: ").append(vision.take(500)).append('\n')
        if (brand.isNotBlank()) b.append("BRAND: ").append(brand.take(80)).append('\n')
        if (offer.isNotBlank()) b.append("ABHI KA OFFER (website par chal raha hai): ").append(offer.take(200)).append('\n')
        if (lastCaptions.isNotEmpty()) b.append("PEHLE YE HOOKS USE HO CHUKE HAIN (dobara same mat likho): ")
            .append(lastCaptions.take(3).joinToString(" | ") { it.take(80) }).append('\n')
        b.append("TARGETS: ").append(platformTargets.joinToString(", ")).append('\n')
        b.append(
            "Sirf JSON do, koi aur text nahi:\n" +
            "{\"ig_caption\":\"...\",\"hashtags\":[\"...\"],\"story_text\":\"...\",\"poll\":\"...\",\"yt_title\":\"...\",\"yt_desc\":\"...\",\"yt_tags\":[\"...\"],\"site_blurb\":\"...\",\"lang\":\"hinglish|english|hindi\"}\n" +
            "Niyam: ig_caption 60-220 akshar, hook pehle, emoji 2 se kam, jhooth nahi (jo photo me nahi hai wo mat likho), " +
            "price/discount sirf offer line se, hashtags 12-18 (mix: brand, niche, viral, India), story_text 1 line sawaal jaisa, " +
            "poll 1 chhota sawaal, yt_title 70 akshar se kam aur click-worthy bina jhooth ke, yt_desc 2-4 line + offer, " +
            "yt_tags 8-12, site_blurb 1-2 line, lang batana. Price, stock ya delivery date MAT banao."
        )
        return b.toString()
    }

    /** Parses the AI's JSON (a ``` fence around it is tolerated). null when a single required field is unusable. */
    fun parsePack(body: String, langDefault: String = "hinglish"): Pack? {
        var t = body.trim()
        if (t.isEmpty()) return null
        val fence = Regex("```(?:json)?\\s*([\\s\\S]*?)```").find(t)
        if (fence != null) t = fence.groupValues[1].trim()
        val s = t.indexOf('{')
        val e = t.lastIndexOf('}')
        if (s < 0 || e <= s) return null
        val root = MiniJson.parse(t.substring(s, e + 1)) as? Map<*, *> ?: return null
        fun str(k: String): String = (root[k] as? String)?.trim() ?: ""
        fun arr(k: String): List<String> = (root[k] as? List<*>)?.mapNotNull { (it as? String)?.trim()?.take(40) }?.filter { it.isNotEmpty() } ?: emptyList()
        val cap = clean(str("ig_caption"))
        if (cap.length < 10) return null
        return Pack(
            cap.take(2200), arr("hashtags").take(18), clean(str("story_text")).take(120), clean(str("poll")).take(60),
            clean(str("yt_title")).ifEmpty { cap.take(70) }.take(100), clean(str("yt_desc")).take(5000),
            arr("yt_tags").take(12), clean(str("site_blurb")).ifEmpty { cap.take(160) }.take(300),
            str("lang").ifEmpty { langDefault }
        )
    }

    /** Strips hashtag signs and newlines the AI sometimes adds inside fields. */
    private fun clean(s: String): String = s.replace("#", "").replace(Regex("\\s+"), " ").trim()

    /**
     * The HONEST no-AI pack: no claims, no invented hook. Uses what is really known (brand, offer, vision text).
     * This is what goes out when every AI provider failed - a plain true description, never a fake viral promise.
     */
    fun fallbackPack(item: String, vision: String, brand: String, offer: String, lang: String): Pack {
        val what = vision.take(120).ifBlank {
            if (item == "video") "naya video" else "nayi photo"
        }
        val cap = StringBuilder()
        cap.append(brand.trim().take(40).ifBlank { "Hamari" }).append(" taraf se ").append(what)
        if (offer.isNotBlank()) cap.append(". Abhi website par: ").append(offer.take(120))
        cap.append(". Poora dekhiye aur batao kaisa laga!")
        val tags = baseHashtags(brand)
        return Pack(
            cap.toString(), tags,
            (if (lang == "english") "New drop - which one is your favourite?" else "Naya aaya hai - kaisa laga?").take(120),
            if (lang == "english") "Like it?" else "Pasand aaya?",
            (brand.ifBlank { "Update" } + " - " + (if (item == "video") "new video" else "new post")).take(100),
            cap.toString(), tags.map { it.replace(" ", "") }.take(12), cap.toString().take(300), lang
        )
    }

    /** A safe always-true hashtag set built from the brand word + India basics. No niche claims are invented. */
    fun baseHashtags(brand: String): List<String> {
        val out = ArrayList<String>()
        val b = brand.trim().lowercase().split(" ").firstOrNull { it.length in 2..20 && it.all { c -> c.isLetterOrDigit() } }
        if (b != null) out.add(b)
        out.addAll(listOf("madeinindia", "india", "newpost", "trending", "viral", "explore", "reels", "reelsindia", "instagood", "smallbusiness", "handmade", "love"))
        return out.take(14)
    }

    /** The caption first-line used for the never-repeat check. */
    fun hookLine(p: Pack): String = p.igCaption.split('.', '!', '?').firstOrNull()?.trim() ?: p.igCaption.take(80)

    // ---------------------------------------------------------------- website offer

    /**
     * Pulls a running offer out of free text (the homepage or the owner's own sentence).
     * Honest by design: it only keeps a line that really contains % off / flat Rs / code wording. null when none.
     */
    fun extractOffer(text: String): String? {
        if (text.isBlank()) return null
        for (raw in text.split('\n', '.', ';')) {
            val l = raw.trim()
            if (l.length !in 6..140) continue
            val low = l.lowercase()
            val hasOff = low.contains("% off") || low.contains("percent off") || low.contains("discount") ||
                low.contains("flat ") || low.contains("off on") || low.contains("sale") || low.contains("code ") ||
                low.contains("coupon") || low.contains("offer")
            if (hasOff) {
                val clean = Regex("[\\p{Cntrl}]").replace(l, " ").trim()
                if (clean.length in 6..140) return clean
            }
        }
        return null
    }

    /** The site keeps the offer in /nova-offer.json as {"offer":"...","code":"...","updated":ms}. */
    fun offerFromJson(body: String): String? {
        val root = MiniJson.parse(body) as? Map<*, *> ?: return null
        val o = (root["offer"] as? String)?.trim() ?: return null
        if (o.length !in 3..140) return null
        val code = (root["code"] as? String)?.trim() ?: ""
        return if (code.isNotEmpty() && code.length <= 24) "$o (code $code)" else o
    }

    /** Builds the nova-offer.json body from the owner's own sentence ("nova offer 20% off code SK20"). */
    fun offerJson(offer: String, code: String, nowMs: Long): String {
        val o = offer.trim().take(140)
        val c = code.trim().take(24)
        return "{\"offer\":" + MiniJson.quote(o) + ",\"code\":" + MiniJson.quote(c) + ",\"updated\":" + nowMs + "}"
    }

    /** Pulls a coupon code word out of "code XXX" wording. "" when none. */
    fun offerCode(text: String): String {
        val m = Regex("(?:code|coupon|promo)\\s+([A-Za-z0-9]{2,20})", RegexOption.IGNORE_CASE).find(text) ?: return ""
        return m.groupValues[1].uppercase()
    }

    // ---------------------------------------------------------------- discount reply

    /**
     * The owner's exact rule: a discount question gets "website se order karoge to discount milega" plus the
     * offer CURRENTLY running on the website. When no offer is known, the reply stays honest (no invented deal).
     */
    fun discountReply(liveOffer: String, en: Boolean): String {
        if (en) {
            return if (liveOffer.isBlank()) "If you order from our website you get a discount - the current offer will show there."
            else "Order from our website and you'll get a discount. Running right now: $liveOffer"
        }
        return if (liveOffer.isBlank()) "Website se order karoge to discount milega - abhi ka offer website par dikhega."
        else "Website se order karoge to discount milega. Abhi chal raha hai: $liveOffer"
    }

    // ---------------------------------------------------------------- order state machine

    enum class Step { ITEM, SIZE, QTY, NAME, PHONE, ADDRESS, CONFIRM, DONE }

    /** One in-flight order. All fields plain text, capped on write. */
    class Order(
        val id: String, val chat: String, val app: String,
        var item: String = "", var size: String = "", var qty: Int = 0, var name: String = "",
        var phone: String = "", var address: String = "", var step: Step = Step.ITEM, var updated: Long = 0,
        var statusNote: String = ""
    )

    private val YES = setOf("yes", "haan", "han", "ha", "ok", "okay", "confirm", "done", "thik", "theek", "sahi", "haanji", "ji", "y")
    private val NO = setOf("no", "nahi", "nhi", "na", "cancel", "khatam", "rukna", "band")
    private val SKIP = setOf("skip", "nahi hai", "same", "koi nahi", "any", "n/a", "na")

    /** True when the text is only digits/spaces/+/- and 10-13 of them (an Indian mobile number). */
    fun validPhone(text: String): Boolean {
        val digits = text.filter { it.isDigit() }
        val okChars = text.all { it.isDigit() || it == '+' || it == ' ' || it == '-' }
        return okChars && (digits.length == 10 || (digits.length == 12 && text.trim().startsWith("+91")))
    }

    /**
     * One turn of the order machine. Returns the next state + the exact Hinglish reply NOVA sends.
     * The caller saves the order only when [OrderOut.save] is true; [OrderOut.done] means the order is complete.
     */
    class OrderOut(val reply: String, val save: Boolean, val done: Boolean, val cancelled: Boolean)

    fun step(o: Order, text: String, en: Boolean = false): OrderOut {
        val t = text.trim().take(300)
        val low = t.lowercase()
        if (low in NO && o.step != Step.CONFIRM) {
            o.step = Step.ITEM; o.item = ""; o.size = ""; o.qty = 0; o.name = ""; o.phone = ""; o.address = ""
            return OrderOut(if (en) "No problem, we can start again whenever you like." else "Koi baat nahi. Jab chahiye phir bata dena, main yahin hoon.", true, false, true)
        }
        when (o.step) {
            Step.ITEM -> {
                if (t.length < 2) return OrderOut(if (en) "Which item would you like?" else "Kaunsa item chahiye?", false, false, false)
                o.item = t.take(80); o.step = Step.SIZE
                return OrderOut(if (en) "Which size do you need for ${o.item}?" else "${o.item} kaunsa size chahiye?", true, false, false)
            }
            Step.SIZE -> {
                o.size = if (low in SKIP) "" else t.take(40)
                o.step = Step.QTY
                return OrderOut(if (en) "How many pieces?" else "Kitne piece chahiye?", true, false, false)
            }
            Step.QTY -> {
                val n = low.filter { it.isDigit() }.toIntOrNull() ?: 1
                o.qty = n.coerceIn(1, 999)
                o.step = Step.NAME
                return OrderOut(if (en) "Your name, please?" else "Aapka naam?", true, false, false)
            }
            Step.NAME -> {
                if (t.length < 2) return OrderOut(if (en) "Your name, please?" else "Aapka naam?", false, false, false)
                o.name = t.take(60); o.step = Step.PHONE
                return OrderOut(if (en) "Your phone number for the order?" else "Order ke liye aapka phone number?", true, false, false)
            }
            Step.PHONE -> {
                if (!validPhone(t)) return OrderOut(if (en) "That doesn't look like a mobile number - 10 digits, please." else "Ye mobile number nahi lag raha - 10 anko ka number bhejo.", false, false, false)
                o.phone = t.take(16); o.step = Step.ADDRESS
                return OrderOut(if (en) "Delivery address?" else "Delivery ka address?", true, false, false)
            }
            Step.ADDRESS -> {
                if (t.length < 6) return OrderOut(if (en) "Full delivery address, please (house, area, city, pincode)." else "Poora address bhejo (ghar, area, sheher, pincode).", false, false, false)
                o.address = t.take(300); o.step = Step.CONFIRM
                return OrderOut(confirmLine(o, en), true, false, false)
            }
            Step.CONFIRM -> {
                if (low in YES) {
                    o.step = Step.DONE
                    return OrderOut(if (en) "Order confirmed, ${o.name}! We'll message you the delivery details. Thank you!" else "Order confirm ho gaya, ${o.name}! Delivery ki detail aapko message kar denge. Dhanyavaad!", true, true, false)
                }
                if (low in NO) {
                    o.step = Step.ITEM; o.item = ""; o.size = ""; o.qty = 0; o.name = ""; o.phone = ""; o.address = ""
                    return OrderOut(if (en) "Cancelled. Tell me whenever you want to order again." else "Cancel kar diya. Dobara order karna ho to bata dena.", true, false, true)
                }
                return OrderOut(confirmLine(o, en) + (if (en) " (yes / no)" else " (haan / nahi)"), false, false, false)
            }
            Step.DONE -> return OrderOut(if (en) "Your order is already noted - we'll update you here." else "Aapka order note ho gaya hai - update yahin denge.", false, false, false)
        }
    }

    fun confirmLine(o: Order, en: Boolean): String {
        val sz = if (o.size.isBlank()) "" else (if (en) ", size ${o.size}" else ", size ${o.size}")
        return if (en)
            "Please confirm the order: ${o.qty} x ${o.item}$sz for ${o.name}, ${o.phone}, ${o.address}. Reply YES to confirm or NO to cancel."
        else
            "Order confirm karo: ${o.qty} x ${o.item}$sz - ${o.name}, ${o.phone}, ${o.address}. Haan bolo confirm ke liye, nahi bolo cancel."
    }

    // ---------------------------------------------------------------- site feed

    /**
     * Appends one entry to the site's feed.json (newest first, hard cap [FEED_CAP] entries, old file tolerated).
     * Pure text assembly - AutopilotNet only moves bytes.
     */
    const val FEED_CAP = 200

    fun feedWith(existingJson: String, id: String, kind: String, url: String, thumb: String, blurb: String, ts: Long): String {
        val items = ArrayList<String>()
        try {
            val root = MiniJson.parse(existingJson) as? Map<*, *>
            val arr = root?.get("items") as? List<*>
            if (arr != null) for (e in arr) {
                val m = e as? Map<*, *> ?: continue
                items.add(entryJson(
                    (m["id"] as? String) ?: "", (m["kind"] as? String) ?: "", (m["url"] as? String) ?: "",
                    (m["thumb"] as? String) ?: "", (m["blurb"] as? String) ?: "", (m["ts"] as? Long ?: 0L)
                ))
            }
        } catch (e: Exception) { /* corrupt old file: start fresh */ }
        items.add(0, entryJson(id, kind, url, thumb, blurb, ts))
        return "{\"items\":[" + items.take(FEED_CAP).joinToString(",") + "]}"
    }

    private fun entryJson(id: String, kind: String, url: String, thumb: String, blurb: String, ts: Long): String =
        "{\"id\":" + MiniJson.quote(id.take(64)) + ",\"kind\":" + MiniJson.quote(kind.take(10)) +
            ",\"url\":" + MiniJson.quote(url.take(300)) + ",\"thumb\":" + MiniJson.quote(thumb.take(300)) +
            ",\"blurb\":" + MiniJson.quote(blurb.take(300)) + ",\"ts\":" + ts + "}"

    // ---------------------------------------------------------------- Instagram host

    /**
     * Instagram tokens starting with "IG" belong to an Instagram User token -> graph.instagram.com.
     * Everything else (System User / Page token from a Facebook app) goes to graph.facebook.com.
     */
    fun igHost(token: String): String = if (token.trim().startsWith("IG")) "https://graph.instagram.com" else "https://graph.facebook.com"

    // ---------------------------------------------------------------- intake rules (v7.158 task B)

    /** What to do with a file NOVA itself just watched being SENT by the owner (Sent folder / "You" sender). */
    enum class SentMode { OFF, ASK, AUTO }

    /**
     * Sent-by-me policy. AUTO is NOT blind: the file is accepted only when the accessibility service really read
     * the open chat title and it equals the group name; anything else becomes REVIEW (the owner taps in the app).
     */
    fun sentVerdict(mode: SentMode, chatTitle: String, group: String): String {
        when (mode) {
            SentMode.OFF -> return "skip"
            SentMode.ASK -> return "ask"
            SentMode.AUTO -> return if (chatTitle.isBlank()) "review" else if (matchesGroup(chatTitle, group)) "auto" else "review"
        }
    }

    /**
     * Group media that arrives as a document (.pdf, .docx...), a GIF or a voice note can NOT be posted as a
     * reel / post / Short - true means "ignore this file safely, log nothing scary".
     */
    private val IGNORE_EXT = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "apk", "zip", "rar", "7z", "gif", "opus", "ptt", "aac", "amr", "m4a", "mp3", "wav", "vcf")

    fun ignoredKind(name: String, mime: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase().trim()
        if (ext in IGNORE_EXT) return true
        val m = mime.lowercase()
        if (m.startsWith("audio/") || m == "application/pdf" || m.contains("gif")) return true
        return kindOf(name, mime) == Kind.OTHER
    }

    /** Several group items within [BATCH_MS] are ONE batch (one caption, one carousel / one post set). */
    const val BATCH_MS = 90_000L

    fun batchable(prevTs: Long, nowMs: Long): Boolean = prevTs > 0 && nowMs >= prevTs && nowMs - prevTs <= BATCH_MS

    /** SHA-256 hex of the file bytes (first/last 64 KB + size are enough in the caller; this is the full hash). */
    fun sha256Hex(bytes: ByteArray): String {
        val d = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
        val sb = StringBuilder(d.size * 2)
        for (b in d) { val v = b.toInt() and 0xFF; sb.append("0123456789abcdef"[v ushr 4]).append("0123456789abcdef"[v and 0x0F]) }
        return sb.toString()
    }

    // ---------------------------------------------------------------- caps + spacing + idempotency

    /** Daily caps + spacing, all configurable from the AUTOPILOT tab. Instagram's own limits are the ceiling. */
    class Caps(var igFeed: Int = 6, var igStory: Int = 15, var yt: Int = 5, var spacingMin: Int = 45) {
        /** 0 = no cap (the website is the owner's own repo - Instagram / YouTube rate limits do not apply there). */
        fun capFor(target: String): Int = when (target) {
            "ig_feed", "ig_reel" -> igFeed
            "ig_story" -> igStory
            "yt" -> yt
            else -> 0
        }
    }

    const val SPACING_MS_MIN = 45 * 60_000L

    /**
     * null = posting is allowed. Otherwise the Hinglish reason ("aaj ki IG limit 6 ho gayi", "agle post ko 45 minute
     * rukna hai"). Counts come from the store; the clock is passed in (pure).
     */
    fun capReason(target: String, postsToday: Int, lastPostTs: Long, nowMs: Long, caps: Caps): String? {
        if (target == "site") return null   // the owner's own gallery: never capped, no spacing needed
        val cap = caps.capFor(target)
        if (cap > 0 && postsToday >= cap) return "aaj ki ${labelFor(target)} limit $cap ho gayi"
        val spacing = caps.spacingMin * 60_000L
        if (lastPostTs > 0 && nowMs > lastPostTs && nowMs - lastPostTs < spacing) {
            val minLeft = ((spacing - (nowMs - lastPostTs)) + 59_999) / 60_000
            return "agle post ko $minLeft minute rukna hai"
        }
        return null
    }

    fun labelFor(target: String): String = when (target) {
        "site" -> "website"; "ig_feed" -> "Instagram post"; "ig_reel" -> "Instagram reel"; "ig_story" -> "story"; "yt" -> "YouTube"; else -> target
    }

    /** Same key = the same (job, target) pair; a retry with this key can never double-post. */
    fun idemKey(jobId: String, target: String): String = jobId.trim().take(64) + "|" + target.trim().take(16)

    // ---------------------------------------------------------------- media prep rules (v7.158 task C)

    /** What MediaMetadataRetriever really said about a video. All fields honest; mime "" when unknown. */
    class VideoInfo(val durationMs: Long, val width: Int, val height: Int, val rotationDeg: Int, val videoMime: String, val audioMime: String) {
        fun displayW(): Int = if (rotationDeg == 90 || rotationDeg == 270) height else width
        fun displayH(): Int = if (rotationDeg == 90 || rotationDeg == 270) width else height
    }

    /** Shorts only when the video is really short (prompt rule: <= 3 minutes). */
    const val SHORTS_MAX_MS = 3 * 60_000L

    fun shortOk(durationMs: Long): Boolean = durationMs in 1..SHORTS_MAX_MS

    /** Reels need MP4 H.264 + AAC. true when a re-encode would help (unknown mime counts as fine - honest). */
    fun needsReencode(v: VideoInfo?): Boolean {
        if (v == null) return false
        val vid = v.videoMime.lowercase()
        val aud = v.audioMime.lowercase()
        val vidBad = vid.isNotEmpty() && vid !in setOf("video/avc", "video/mp4")
        val audBad = aud.isNotEmpty() && aud !in setOf("audio/mp4a-latm", "audio/aac")
        return vidBad || audBad
    }

    /** A horizontal video needs the 9:16 blurred-background treatment for reels / Shorts. */
    fun isHorizontal(v: VideoInfo?): Boolean = v != null && v.displayW() > v.displayH()

    /** The re-encode / 9:16 pass runs ONLY while charging AND on Wi-Fi (owner: phone hang na ho). */
    fun reencodeAllowed(charging: Boolean, wifi: Boolean): Boolean = charging && wifi

    /** Host size cap (45 MB). Bigger files go to YouTube ONLY and the owner is told - nothing is silently dropped. */
    const val HOST_CAP_BYTES = 45L * 1024 * 1024

    /** "ok" = all targets, "yt-only" = too big for IG / site media, "never" = unusable. */
    fun hostVerdict(sizeBytes: Long): String = when {
        sizeBytes <= 0 -> "never"
        sizeBytes <= HOST_CAP_BYTES -> "ok"
        sizeBytes <= 95_000_000L -> "yt-only"   // YouTube's own limit is far higher; GitHub raw caps at ~100 MB
        else -> "never"
    }

    // ---------------------------------------------------------------- carousel rule (v7.158 task D3)

    const val CAROUSEL_MIN = 2
    const val CAROUSEL_MAX = 10

    /**
     * When 2-10 photos land together (one 90-second batch), Instagram gets ONE carousel instead of
     * separate posts. >10 items = carousels of 10 (honest, Instagram's own hard limit).
     */
    fun carouselGroups(imageJobIds: List<String>): List<List<String>> {
        if (imageJobIds.size < CAROUSEL_MIN) return imageJobIds.map { listOf(it) }
        return imageJobIds.chunked(CAROUSEL_MAX)
    }

    // ---------------------------------------------------------------- never-repeat rule (v7.158 task D5)

    /**
     * null = fresh. Otherwise the reason ("hook same hai", "hashtags same hain"). A pack whose hook line OR
     * whose hashtag set (>= 80% overlap) repeats the last one must be varied before it goes out.
     */
    fun repeatReason(p: Pack, lastHook: String, lastHashtags: List<String>): String? {
        if (lastHook.isBlank() && lastHashtags.isEmpty()) return null
        val hook = hookLine(p)
        if (lastHook.isNotBlank() && hook.equals(lastHook.trim(), true)) return "hook same hai"
        if (lastHashtags.isNotEmpty()) {
            val a = p.hashtags.map { it.lowercase() }.toSet()
            val b = lastHashtags.map { it.lowercase() }.toSet()
            if (a.isNotEmpty() && b.isNotEmpty()) {
                val overlap = a.intersect(b).size.toDouble() / maxOf(a.size, b.size)
                if (overlap >= 0.8) return "hashtags same hain"
            }
        }
        return null
    }

    /** Varies a pack that tripped the repeat rule WITHOUT calling the AI again: new hook opener + rotated tags + new CTA tail. */
    fun varyPack(p: Pack): Pack {
        val pool = baseHashtags("")
        val extra = pool.filter { it.lowercase() !in p.hashtags.map { it.lowercase() } }.shuffled().take(4)
        val tags = (p.hashtags + extra).distinctBy { it.lowercase() }.take(18)
        val openers = listOf("Naya update:", "Suniye zara,", "Fresh drop -", "Is baar,", "Special:")
        val opener = openers.shuffled().first()
        val cta = listOf("Dekho aur batao!", "Kaunsa pasand aaya?", "Comment me batao", "Share karo apne dosto ke saath").shuffled().first()
        val base = p.igCaption.substringBeforeLast('.').take(2000)
        return Pack(
            "$opener $base. $cta", tags, p.storyText, p.pollText,
            p.ytTitle, p.ytDesc, p.ytTags, p.siteBlurb, p.lang
        )
    }

    // ---------------------------------------------------------------- insights learning (v7.158 task D4)

    /** One platform's 24 h numbers. -1 = not available (honest: never shown as 0-success). */
    class Stats(val reach: Long, val saves: Long, val shares: Long, val views: Long) {
        fun anyKnown(): Boolean = reach >= 0 || saves >= 0 || shares >= 0 || views >= 0
    }

    /** The slot (0-5 of SLOT_TIMES_IST) a timestamp falls into, or -1 outside all slots. */
    fun slotIndex(tsMs: Long, zoneId: java.time.ZoneId = java.time.ZoneId.of(IST_ZONE)): Int {
        val t = java.time.Instant.ofEpochMilli(tsMs).atZone(zoneId)
        val mins = t.hour * 60 + t.minute
        SLOT_TIMES_IST.forEachIndexed { i, (h, m) -> if (mins < h * 60 + m + 90) return i }
        return -1
    }

    /**
     * Self-tuning slots: every slot keeps a running average reach. A slot that beats the overall average by
     * >= 20% (and has at least 3 samples) jumps the queue. Falls back to the fixed order honestly.
     */
    fun tunedSlotOrder(slotAvgReach: List<Long>, samples: List<Int>): List<Int> {
        val idx = SLOT_TIMES_IST.indices.toList()
        val known = idx.filter { samples.getOrNull(it) ?: 0 > 0 && (slotAvgReach.getOrNull(it) ?: 0L) > 0 }
        if (known.size < 2) return idx
        val overall = known.map { slotAvgReach[it] }.average()
        val hot = known.filter { slotAvgReach[it] >= overall * 1.2 && (samples[it] ?: 0) >= 3 }
        if (hot.isEmpty()) return idx
        return hot.sortedByDescending { slotAvgReach[it] } + idx.filter { it !in hot }
    }

    /** nextSlot with tuning: the first coming slot from the tuned order. */
    fun nextSlotTuned(nowMs: Long, slotAvgReach: List<Long>, samples: List<Int>): Pair<Long, String> {
        val order = tunedSlotOrder(slotAvgReach, samples)
        val zone = java.time.ZoneId.of(IST_ZONE)
        val now = java.time.Instant.ofEpochMilli(nowMs).atZone(zone)
        for (d in 0..1L) {
            val day = now.toLocalDate().plusDays(d)
            for (i in order) {
                val (h, m) = SLOT_TIMES_IST[i]
                val t = day.atTime(h, m).atZone(zone)
                if (t.toInstant().toEpochMilli() > nowMs) return Pair(t.toInstant().toEpochMilli(), slotLabel(h, m))
            }
        }
        return nextSlot(nowMs)
    }

    /** A viral signal = reach at least 3x the normal average (the owner gets told, nothing is invented). */
    fun viralSignal(reach: Long, avgReach: Long): Boolean = avgReach > 0 && reach >= avgReach * 3

    // ---------------------------------------------------------------- private helpers
}
