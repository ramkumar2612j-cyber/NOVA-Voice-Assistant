package com.nova.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.service.notification.StatusBarNotification

/**
 * v7.114 REEL RUNNER: does a [ReelRules.Plan] on the phone with the Accessibility service: open a friend's profile, go through their reels / posts one by one,
 * like / save / comment / share, scroll to the next one, and stop on STOP.
 *
 * Honesty rules (the same as the rest of NOVA):
 *  - every action is checked afterwards where the app lets us read the screen; a missing button is reported ("save ka button nahi mila"), never counted as done;
 *  - it only works inside Instagram / Facebook / YouTube / Snapchat (never a bank, payment or settings screen: Control.blocked is checked on every tap);
 *  - comments and shares are capped ([ReelRules.COMMENT_CAP], [ReelRules.SHARE_CAP]), spaced out a few seconds apart, and the run stops the moment the app says "Try again later" / "Action blocked";
 *  - the screen text is read only to check our own action (is my comment there?), it is not stored or sent anywhere.
 * Worker thread only (it sleeps).
 */
object ReelRunner {
    val VIDEO_APPS = setOf("com.instagram.android", "com.facebook.katana", "com.google.android.youtube", "com.snapchat.android", "com.zhiliaoapp.musically")
    private const val INSTAGRAM = "com.instagram.android"

    fun isVideoApp(pkg: String?): Boolean = pkg != null && pkg in VIDEO_APPS

    interface Host {
        fun en(): Boolean
        fun cancelled(): Boolean
        fun locked(): Boolean
        fun openProfile(url: String): Boolean
        fun progress(text: String)
    }

    class Result(val items: Int, val likes: Int, val saves: Int, val comments: Int, val shares: Int, val stopped: Boolean, val reason: String)

    private val LIKE = listOf("like")
    private val LIKED = listOf("unlike", "liked", "remove like")
    private val SAVE = listOf("save", "add to saved", "save to collection", "save post", "save reel")
    private val SAVED = listOf("remove from saved", "saved", "unsave", "remove from collection")
    private val MORE = listOf("more", "more options", "options", "menu", "more actions")
    private val COMMENT = listOf("comment", "comments", "add a comment", "add comment", "view comments")
    private val POST_BTN = listOf("post", "post comment", "send comment")
    private val SHARE = listOf("share", "send", "send post", "send reel", "share post", "share reel", "send video")
    private val SEND = listOf("send", "send separately", "send chat")
    private val BLOCKED = listOf("try again later", "action blocked", "we restrict certain activity", "temporarily blocked", "dobara try karo")
    private val GONE = listOf("sorry, this page isn't available", "this page isn't available", "user not found", "couldn't find", "page not found", "no users found", "profile isn't available")

    private fun sleepC(h: Host, ms: Long): Boolean {
        var left = ms
        while (left > 0) {
            if (h.cancelled()) return false
            val step = minOf(left, 200L)
            try { Thread.sleep(step) } catch (e: InterruptedException) { return false }
            left -= step
        }
        return !h.cancelled()
    }

    private fun back() { try { NovaAccessibilityService.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK) } catch (e: Throwable) { ErrLog.addOnce("quiet:ReelRunner", e.toString()) } }

    private fun blockedNow(): Boolean = try { NovaAccessibilityService.reelLines().any { l -> BLOCKED.any { l.lowercase().contains(it) } } } catch (e: Throwable) { false }

    /** Open [friend]'s profile (Instagram link), check it really is that profile, go to the Reels tab (unless [posts]) and open the first item. Returns "" = ready, else the honest reason. */
    private fun openFriend(friend: String, posts: Boolean, h: Host): String {
        val en = h.en()
        if (!h.openProfile("https://www.instagram.com/$friend/")) return if (en) "Instagram did not open." else "Instagram khul nahi paya."
        var shown = false
        for (i in 0 until 20) {
            if (!sleepC(h, 500)) return "STOP"
            val lines = NovaAccessibilityService.reelLines()
            if (NovaAccessibilityService.reelPackage() == INSTAGRAM && lines.isNotEmpty()) {
                if (lines.any { l -> GONE.any { l.lowercase().contains(it) } }) return if (en) "Instagram says there is no account named $friend. Say the username again." else "Instagram par $friend naam ka account nahi mila. Username dobara bolo."
                if (lines.any { it.lowercase().contains(friend.lowercase()) }) { shown = true; break }
            }
        }
        if (!shown) return if (en) "I could not confirm $friend's profile on the screen." else "$friend ka profile screen par confirm nahi ho paya."
        if (!posts) { NovaAccessibilityService.reelTap(listOf("reels", "reels tab")); if (!sleepC(h, 1200)) return "STOP" }
        var opened = NovaAccessibilityService.reelTapContains(listOf("row 1, column 1")).ok
        if (!opened) opened = NovaAccessibilityService.reelTapContains(if (posts) listOf("photo by", "post by", "video by", "reel by") else listOf("reel by", "video by", "photo by")).ok
        if (!opened) return if (en) "I could not open the first item on $friend's profile (private account, or no posts)." else "$friend ke profile par pehli reel / post khul nahi payi (account private ho sakta hai, ya post nahi hai)."
        if (!sleepC(h, 1800)) return "STOP"
        return ""
    }

    fun run(p: ReelRules.Plan, h: Host): Result {
        val en = h.en()
        var likes = 0; var saves = 0; var comments = 0; var shares = 0; var items = 0
        fun done(stopped: Boolean, reason: String) = Result(items, likes, saves, comments, shares, stopped, reason)
        if (NovaAccessibilityService.instance == null) return done(false, ReelRules.noAccText(en))
        if (h.locked()) return done(false, ReelRules.lockedText(en))
        val notes = LinkedHashSet<String>()

        if (p.friend != null) {
            val why = openFriend(p.friend, p.posts, h)
            if (why == "STOP") return done(true, "")
            if (why.isNotEmpty()) return done(false, why)
        } else if (!isVideoApp(NovaAccessibilityService.reelPackage())) {
            return done(false, ReelRules.notVideoAppText(en))
        }
        val appPkg = NovaAccessibilityService.reelPackage()

        var lastComment = 0L
        for (i in 0 until p.count) {
            if (h.cancelled()) return done(true, notes.joinToString("; "))
            if (NovaAccessibilityService.reelPackage() != appPkg) return done(false, if (en) "You left the app, so I stopped." else "Aap app se bahar nikal gaye, isliye ruk gaya.")
            // watch the reel for a moment (a little random, so it does not look like a machine)
            val wait = (if (i == 0) 900L else p.dwellSec * 1000L) + (Math.random() * 700).toLong()
            if (!sleepC(h, if (p.acts || i > 0) wait else 400L)) return done(true, notes.joinToString("; "))
            items++

            if (p.like) {
                if (NovaAccessibilityService.reelHas(LIKED)) likes++      // already liked: pressing again would UNlike it
                else {
                    val r = NovaAccessibilityService.reelTap(LIKE)
                    if (!r.ok) NovaAccessibilityService.reelDoubleTap()
                    sleepC(h, 700)
                    if (NovaAccessibilityService.reelHas(LIKED) || r.ok) likes++ else notes.add(if (en) "like button not found" else "like ka button nahi mila")
                }
            }
            if (p.save && !h.cancelled()) {
                if (NovaAccessibilityService.reelHas(SAVED)) saves++
                else {
                    var r = NovaAccessibilityService.reelTap(SAVE)
                    if (!r.ok) {   // reels keep Save inside the "..." menu
                        if (NovaAccessibilityService.reelTap(MORE).ok) { sleepC(h, 900); r = NovaAccessibilityService.reelTap(SAVE); if (!r.ok) back() }
                    }
                    sleepC(h, 700)
                    if (r.ok) saves++ else notes.add(if (en) "save button not found" else "save ka button nahi mila")
                }
            }
            val c = ReelRules.commentFor(p, en, i)
            if (c != null && !h.cancelled()) {
                val gap = System.currentTimeMillis() - lastComment
                if (lastComment != 0L && gap < 4000) sleepC(h, 4000 - gap)
                if (commentOnce(c, h)) { comments++; lastComment = System.currentTimeMillis() } else notes.add(if (en) "a comment did not post" else "ek comment post nahi hua")
            }
            val to = p.shareTo
            if (to != null && i < ReelRules.SHARE_CAP && !h.cancelled()) {
                if (shareOnce(to, h)) shares++ else notes.add(if (en) "share to $to did not work" else "$to ko share nahi ho paya")
            }
            if (blockedNow()) return done(false, if (en) "Instagram says \"try again later\", so I stopped. Wait a while before the next run." else "Instagram ne \"baad me try karo\" bol diya, isliye ruk gaya. Thodi der baad dobara karna.")
            if (notes.size >= 3) return done(false, (if (en) "Stopped: too many problems. " else "Ruk gaya: bahut dikkat aayi. ") + notes.joinToString("; "))

            if (i < p.count - 1 && p.scroll) {
                val r = NovaAccessibilityService.reelSwipe(!p.back)
                if (!r.ok) return done(false, if (en) "Scroll did not work (${r.code})." else "Scroll nahi ho paya (${r.code}).")
                if (!sleepC(h, 900)) return done(true, notes.joinToString("; "))
            }
            if (i > 0 && i % 10 == 0) h.progress(if (en) "$i done" else "$i ho gayi")
        }
        return done(h.cancelled(), notes.joinToString("; "))
    }

    private fun commentOnce(text: String, h: Host): Boolean {
        if (!NovaAccessibilityService.reelTap(COMMENT).ok) return false
        if (!sleepC(h, 1300)) return false
        var ok = false
        if (NovaAccessibilityService.reelType(text, listOf("comment")).ok) {
            sleepC(h, 500)
            if (NovaAccessibilityService.reelTap(POST_BTN).ok) {
                sleepC(h, 1400)
                // the comment now shows in the list: our own text is on the screen
                ok = NovaAccessibilityService.reelLines().any { it.contains(text.take(20), ignoreCase = true) && !it.equals("", true) }
                if (!ok) ok = !NovaAccessibilityService.reelHas(POST_BTN)   // the Post button went away = it was sent (some apps hide the new comment)
            }
        }
        // close the comment sheet: press Back only while a text box is still open (never more than 3 times, so the app is never left)
        var tries = 0
        while (tries < 3 && NovaAccessibilityService.reelHasEditable()) { back(); sleepC(h, 500); tries++ }
        if (tries == 0) { back(); sleepC(h, 500) }   // sheet without a text box (reader view): one Back closes it
        return ok
    }

    private fun shareOnce(to: String, h: Host): Boolean {
        if (!NovaAccessibilityService.reelTap(SHARE).ok) return false
        if (!sleepC(h, 1500)) return false
        var ok = false
        if (NovaAccessibilityService.reelType(to, listOf("search", "to")).ok) {
            sleepC(h, 1400)
            if (NovaAccessibilityService.reelTapContains(listOf(to)).ok) {
                sleepC(h, 600)
                ok = NovaAccessibilityService.reelTap(SEND).ok
                sleepC(h, 900)
            }
        }
        var tries = 0
        while (tries < 2 && (NovaAccessibilityService.reelHasEditable() || NovaAccessibilityService.reelHas(SEND))) { back(); sleepC(h, 500); tries++ }
        return ok
    }

    // ------------------------------------------------------------------ "jaise hi post kare": a saved watch + the Instagram notification
    private const val PREF = "nova_reel_watch"
    private const val MAX_FIRES = 20
    private const val EXPIRE_MS = 7L * 24 * 60 * 60 * 1000
    private const val COOLDOWN_MS = 90_000L

    class Watch(val friend: String, val like: Boolean, val save: Boolean, val comment: String?, val shareTo: String?, val posts: Boolean, val since: Long, val fires: Int, val lastFire: Long)

    fun saveWatch(ctx: Context, p: ReelRules.Plan) {
        val f = p.friend ?: return
        try {
            val o = org.json.JSONObject().put("friend", f).put("like", p.like).put("save", p.save).put("posts", p.posts)
                .put("since", System.currentTimeMillis()).put("fires", 0).put("lastFire", 0L)
            if (p.comment != null) o.put("comment", p.comment)
            if (p.shareTo != null) o.put("shareTo", p.shareTo)
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("w", o.toString()).apply()
        } catch (e: Throwable) { ErrLog.add("ReelRunner", e.toString()) }
    }

    fun clearWatch(ctx: Context): Boolean {
        val had = loadWatch(ctx) != null
        try { ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove("w").apply() } catch (e: Throwable) { ErrLog.add("ReelRunner", e.toString()) }
        return had
    }

    fun loadWatch(ctx: Context): Watch? = try {
        val s = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("w", null)
        if (s == null) null else {
            val o = org.json.JSONObject(s)
            Watch(o.getString("friend"), o.optBoolean("like"), o.optBoolean("save"), if (o.has("comment")) o.optString("comment") else null,
                if (o.has("shareTo")) o.optString("shareTo") else null, o.optBoolean("posts"), o.optLong("since"), o.optInt("fires"), o.optLong("lastFire"))
        }
    } catch (e: Throwable) { null }

    /** The plan a matching notification starts: ONE post, no scrolling, no profile opening (the notification opens the post itself). */
    fun planOf(w: Watch): ReelRules.Plan = ReelRules.Plan(
        friend = null, friendAsked = false, count = 1, scroll = false, auto = false, dwellSec = 2, like = w.like, save = w.save, comment = w.comment,
        shareTo = w.shareTo, shareAsked = false, back = false, watch = false, posts = w.posts
    )

    /**
     * Called by the notification listener for every Instagram notification. true = it was a post of the watched friend and the run was started.
     * Opens the post through the notification's own link, waits for it, then [fire] runs the plan. At most one run per [COOLDOWN_MS], [MAX_FIRES] runs, 7 days.
     */
    fun onNotification(ctx: Context, sbn: StatusBarNotification, fire: (ReelRules.Plan) -> Boolean): Boolean {
        if (sbn.packageName != INSTAGRAM) return false
        val w = loadWatch(ctx) ?: return false
        val now = System.currentTimeMillis()
        if (now - w.since > EXPIRE_MS || w.fires >= MAX_FIRES) { clearWatch(ctx); return false }
        if (now - w.lastFire < COOLDOWN_MS) return false
        val ex = sbn.notification?.extras
        val title = ex?.getCharSequence("android.title")?.toString()
        val body = ex?.getCharSequence("android.text")?.toString()
        if (!ReelRules.isFriendPost(w.friend, title, body)) return false
        try {
            val pi = sbn.notification?.contentIntent ?: return false
            pi.send()
        } catch (e: Throwable) { ErrLog.add("ReelRunner", "watch open: " + e.javaClass.simpleName); return false }
        try {
            val o = org.json.JSONObject(ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("w", "{}") ?: "{}")
            o.put("fires", w.fires + 1).put("lastFire", now)
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("w", o.toString()).apply()
        } catch (e: Throwable) { ErrLog.addOnce("quiet:ReelRunner", e.toString()) }
        return fire(planOf(w))
    }

    fun profileIntent(url: String): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(INSTAGRAM).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
