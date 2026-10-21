package com.nova.assistant

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.service.notification.NotificationListenerService.RankingMap
import java.util.concurrent.ConcurrentHashMap

/**
 * Driving mode: sees notifications ONLY from the allow-listed messaging apps (Driving.APPS) and ONLY while the user
 * has switched driving mode on. Text is kept in memory, never logged, never uploaded.
 * The user must switch on "Notification access" for NOVA in Android settings (NOVA cannot do that by itself).
 */
class NovaNotificationService : NotificationListenerService() {
    private val lockGate = LockGuard.Gate()

    override fun onCreate() {
        super.onCreate()
        MessageMode.load(this)
    }

    // v7.83 UNREAD QUERY: lets NOVA answer "koi naya message aaya?" from the notifications still in the shade (read-only).
    override fun onListenerConnected() {
        super.onListenerConnected()
        live = this
    }

    override fun onDestroy() {
        if (live === this) live = null
        super.onDestroy()
    }

    companion object {
        @Volatile private var live: NovaNotificationService? = null

        /** v7.97: Android often leaves the listener unbound after an app update / reinstall although the switch shows ON. Ask it to bind again and wait up to ~2 s (call from a worker thread). */
        fun snapshotMessagesWithRebind(ctx: Context): List<Driving.Msg>? {
            snapshotMessages()?.let { return it }
            try { requestRebind(android.content.ComponentName(ctx, NovaNotificationService::class.java)) } catch (e: Throwable) { ErrLog.add("Listener", "rebind: $e") }
            for (i in 0 until 8) {
                try { Thread.sleep(250) } catch (e: InterruptedException) { break }
                snapshotMessages()?.let { return it }
            }
            return null
        }

        /** Messages from the allow-listed messaging apps that are still showing. null = the listener is not connected. Never logged, never uploaded. */
        fun snapshotMessages(): List<Driving.Msg>? {
            val svc = live ?: return null
            return try {
                val out = ArrayList<Driving.Msg>()
                for (sbn in svc.activeNotifications ?: return null) {
                    val pkg = sbn.packageName ?: continue
                    if (pkg == svc.packageName || pkg !in Driving.APPS) continue
                    val n = sbn.notification ?: continue
                    if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) continue
                    if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) continue
                    val ex = n.extras ?: continue
                    val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
                    val body = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: ""
                    val m = Driving.makeMsg(sbn.key ?: "$pkg:${sbn.id}", pkg, title, body, sbn.postTime, false) ?: continue
                    out.add(m)
                }
                out
            } catch (e: Throwable) {
                ErrLog.add("NotifListener", "snapshot: $e")
                null
            }
        }
    }

    /** v7.17: if Android drops the listener, ask it to bind again so auto-reply / alerts do not silently stop. */
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        try {
            requestRebind(android.content.ComponentName(this, NovaNotificationService::class.java))
        } catch (e: Throwable) {
            ErrLog.add("Listener", "rebind failed: " + e)
        }
    }

    /**
     * v7.99 OWNER FIRST: the owner opened (click), swiped, or the app cancelled the chat notification (read in the app / on another device) = he SAW it.
     * A reply the AI is still waiting to send for this chat is dropped, and the AI stays out of that person's chat for 3 minutes.
     */
    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?, reason: Int) {
        super.onNotificationRemoved(sbn, rankingMap, reason)
        try {
            if (sbn == null) return
            val pkg = sbn.packageName ?: return
            if (pkg == packageName || pkg !in Driving.APPS) return
            if (reason != REASON_CLICK && reason != REASON_CANCEL && reason != REASON_CANCEL_ALL && reason != REASON_APP_CANCEL && reason != REASON_APP_CANCEL_ALL) return
            val key = sbn.key ?: return
            val now = System.currentTimeMillis()
            if (ReplyPresenceRules.sent.recent(key, now)) return   // NOVA's own reply made the app refresh the notification
            ReplyPresenceRules.pending.cancel(key, ReplyPresenceRules.CODE_SEEN)
            val title = sbn.notification?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
            ReplyPresenceRules.takeover.mark(title, now, ReplyPresenceRules.SEEN_TAKEOVER_MS)
        } catch (e: Throwable) {
            ErrLog.add("NotifListener", "removed: $e")
        }
    }

    /** v7.99: the chat lines the notification still carries (MessagingStyle). The writer "You" / the notification's own name = the owner. Blank writers are never the owner. */
    private fun historyOf(ex: Bundle): List<ReplyPresenceRules.Line> {
        val out = ArrayList<ReplyPresenceRules.Line>()
        try {
            val self = ex.getCharSequence("android.selfDisplayName")?.toString()
            @Suppress("DEPRECATION") val arr = ex.getParcelableArray("android.messages") ?: return out
            for (p in arr) {
                val b = p as? Bundle ?: continue
                val text = b.getCharSequence("text")?.toString() ?: continue
                @Suppress("DEPRECATION") val person = try { (b.getParcelable<android.os.Parcelable>("sender_person") as? android.app.Person)?.name?.toString() } catch (e: Throwable) { null }
                val sender = b.getCharSequence("sender")?.toString() ?: person
                out.add(ReplyPresenceRules.Line(ReplyPresenceRules.isOwnSender(sender, self), text))
            }
        } catch (e: Throwable) {
            ErrLog.add("NotifListener", "history: $e")
        }
        return out
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        try {
            if (sbn == null) return
            MessageMode.load(this)
            // v7.17: each helper is isolated, so one failing helper can no longer stop message reading / driving mode below
            try { handleLock(sbn) } catch (e: Throwable) { ErrLog.add("NotifListener", "lock: $e") }
            try { if (WaBiz.active(System.currentTimeMillis())) handleBiz(sbn) } catch (e: Throwable) { ErrLog.add("NotifListener", "biz: $e") }
            try { if (sbn.packageName == GmailStore.GMAIL_PKG) handleGmail(sbn) } catch (e: Throwable) { ErrLog.add("NotifListener", "gmail: $e") }
            if (!Driving.enabled && !MessageMode.enabled) return
            val pkg = sbn.packageName ?: return
            if (pkg == packageName || pkg !in Driving.APPS || (MessageMode.enabled && !MessageMode.isSelected(this, pkg) && !Driving.enabled)) return
            val n = sbn.notification ?: return
            if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
            if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return
            val ex = n.extras ?: return
            val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val body = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: ""
            val action = DrivingBridge.replyAction(n)
            val m = Driving.makeMsg(sbn.key ?: "$pkg:${sbn.id}", pkg, title, body, System.currentTimeMillis(), action != null) ?: return
            if (action != null) DrivingBridge.remember(m.key, action)
            // v7.99 OWNER FIRST: if the owner wrote last in this chat (his own line is the newest in the notification) the AI stays out; NOVA's own reply echoing back does not count.
            val lines = historyOf(ex)
            val nowTs = System.currentTimeMillis()
            val ownLast = ReplyPresenceRules.ownerSpokeLast(lines) && !ReplyPresenceRules.sent.recent(m.key, nowTs)
            if (ownLast) {
                ReplyPresenceRules.pending.cancel(m.key, ReplyPresenceRules.CODE_OWNER)
                ReplyPresenceRules.takeover.mark(m.sender, nowTs, ReplyPresenceRules.OWNER_TAKEOVER_MS)
            }
            if (!ownLast && MessageMode.enabled && MessageMode.isSelected(this, pkg)) autoReply(m, ex.getBoolean("android.isGroupConversation", false), lines)
            // Avoid double-reading when both independent switches are ON. Message Mode wins the announcement;
            // Driving retains its own inbox only when Message Mode is OFF.
            if (MessageMode.enabled && MessageMode.isSelected(this, pkg)) {
                if (MessageMode.inbox.offer(m)) NovaService.instance?.onMessageModeMessage(m)
            } else if (Driving.enabled) {
                if (Driving.inbox.offer(m)) NovaService.instance?.onDrivingMessage(m)
            }
        } catch (e: Exception) {
            // never crash the listener because of one odd notification (v7.17: but write it down)
            ErrLog.add("NotifListener", "posted: $e")
        }
    }

    /**
     * v6.97-wip9 LOCK GUARD: phone locked + (any call while the phone is silent | call or message from Papa / Mummy) = loud beep + one spoken line.
     * Never reads message text. Normal messages are ignored here (and held back by NovaService while locked). Rules: LockGuard.
     */
    private fun handleLock(sbn: StatusBarNotification) {
        try {
            val ctx = applicationContext
            if (!ExtCfg.lockGuard(ctx)) return
            val pkg = sbn.packageName ?: return
            if (pkg == packageName) return
            val n = sbn.notification ?: return
            if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
            if (!LockAlert.phoneLocked(ctx)) return
            val ex = n.extras ?: return
            val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            val actions = n.actions?.map { it.title?.toString() ?: "" } ?: emptyList()
            val silent = LockAlert.phoneSilent(ctx)
            val isGroup = ex.getBoolean("android.isGroupConversation", false)
            val a = LockGuard.classify(pkg, n.category, title, text, actions, isGroup, silent) ?: return
            val now = System.currentTimeMillis()
            if (!lockGate.allow(a, now)) return
            if (!LockGuard.shouldAlert(a, true, true, now, lockGate.lastRing(a))) return
            lockGate.record(a, now)
            val en = Cfg(ctx).lang == "en"
            val line = LockGuard.text(a, silent, en)
            LockAlert.start(ctx, line, en)
            NovaService.instance?.onLockAlertShown(line)
        } catch (e: Exception) {
            ErrLog.add("LockGuard", e.toString())   // one odd notification must never crash the listener
        }
    }

    /**
     * v6.53 owner request: reply at once, no yes needed; NOVA tells the owner afterwards and writes it into the history.
     * Rules and limits are in AutoReplyRules. Works even when NOVA is not ACTIVATED (the system keeps this listener alive).
     */
    private fun autoReply(m: Driving.Msg, isGroup: Boolean, lines: List<ReplyPresenceRules.Line> = emptyList()) {
        try {
            val ctx = applicationContext
            if (!ActionLog.autoReplyOn(ctx)) return
            val now = System.currentTimeMillis()
            if (WaBiz.active(now) && (m.pkg == "com.whatsapp" || m.pkg == "com.whatsapp.w4b")) return   // business watch has its own rules
            if (ReplyPresenceRules.takeover.active(m.sender, now)) {   // v7.99: the owner is in this chat right now
                ActionLog.add(ctx, m.app, m.sender, m.text, "skipped", ReplyPresenceRules.skipText(ReplyPresenceRules.Verdict.OWNER, true), m.pkg)
                return
            }
            val smart = true   // v7.46: every auto-reply is the AI's own answer, never a fixed text
            val pre = AutoReplyRules.skipReason(isGroup, m.canReply, m.hidden, m.text)
            val reason = pre ?: (if (smart) SmartReplyRules.gate.allow(m.sender, now) else AutoReplyRules.gate.allow(m.sender, now))
            if (reason != null) {
                if (AutoReplyRules.worthLogging(reason)) ActionLog.add(ctx, m.app, m.sender, m.text, "skipped", AutoReplyRules.reasonText(reason, true))
                return
            }
            smartAutoReply(ctx, m, false, lines)   // v7.46: the AI's own answer only; no fixed text path exists any more
        } catch (e: Exception) {
            // one odd notification must never crash the listener
        }
    }

    /**
     * Part 7 AI SMART REPLY: the AI answers in its own words (own thread: the AI call must never run on the main thread), then the owner is told
     * (history + phone notification + voice when idle). If no AI is available the owner's fixed text is sent instead. Rules: SmartReplyRules.
     */
    private fun smartAutoReply(ctx: Context, m: Driving.Msg, business: Boolean = false, lines: List<ReplyPresenceRules.Line> = emptyList()) {
        Thread {
            try {
                if (SmartReplyRules.convo.lastAssistant(m.sender) == m.text) return@Thread   // our own reply showing up again
                if (!SmartReplyRules.seen.fresh(m.sender, m.text, System.currentTimeMillis())) return@Thread   // the same notification posted twice
                val svc = NovaService.instance
                // v7.99 OWNER FIRST: give the owner a moment. Seen (opened / swiped), answered himself, or a newer message = the AI sends nothing for this one.
                if (!business) {
                    val stamp = System.nanoTime()
                    ReplyPresenceRules.pending.start(m.key, stamp)
                    val v = ReplyPresenceRules.waitVerdict(ReplyPresenceRules.pending, m.key, stamp) { ms -> try { Thread.sleep(ms) } catch (e: InterruptedException) { } }
                    ReplyPresenceRules.pending.done(m.key, stamp)
                    if (v != ReplyPresenceRules.Verdict.SEND) {
                        if (v != ReplyPresenceRules.Verdict.REPLACED) ActionLog.add(ctx, m.app, m.sender, m.text, "skipped", ReplyPresenceRules.skipText(v, true), m.pkg)
                        return@Thread
                    }
                    if (ReplyPresenceRules.takeover.active(m.sender, System.currentTimeMillis())) return@Thread
                }
                val recent = ReplyPresenceRules.context(lines)
                val res = try { svc?.smartDraft(m.sender, m.text, business, recent) } catch (e: Throwable) { null }
                if (res != null && res.skip) {
                    ActionLog.add(ctx, m.app, m.sender, m.text, "skipped", "Jawab ki zaroorat nahi thi", m.pkg)
                    return@Thread
                }
                // v7.46 owner rule: NO fixed / canned text is ever sent. Only the AI's own answer goes out; no AI answer = nothing is sent and the owner is told.
                val text = res?.text?.trim().orEmpty()
                val needOwner = res?.needOwner ?: true
                if (text.isEmpty()) {
                    ActionLog.add(ctx, m.app, m.sender, m.text, "skipped", "AI jawab nahi de paya ya message me code / password tha: kuch nahi bheja", m.pkg)
                    val held = SmartReplyRules.inform(m.sender, m.app, m.text, "(kuch nahi bheja)", true, false, true)
                    GmailStore.notifyPhone(ctx, "NOVA: zaruri message", held)
                    svc?.onSmartReplied(held)
                    return@Thread
                }
                ReplyPresenceRules.sent.mark(m.key, System.currentTimeMillis())   // v7.99: the echo of this reply is not the owner talking
                val ok = DrivingBridge.sendReply(ctx, m.key, text)
                ActionLog.add(ctx, m.app, m.sender, m.text, if (ok) "sent" else "failed", text, m.pkg)
                val line = SmartReplyRules.inform(m.sender, m.app, m.text, text, needOwner, ok, true)
                GmailStore.notifyPhone(ctx, if (needOwner) "NOVA: zaruri message" else "NOVA ne baat ki", line)
                svc?.onSmartReplied(line)
            } catch (e: Throwable) {
                ErrLog.add("SmartReply", e.toString())   // one failed chat must never crash the listener
            }
        }.start()
    }

    /** v6.93: a new Gmail notification = a new mail. Only sender + subject are used (numbers masked); nothing is uploaded. */
    private fun handleGmail(sbn: StatusBarNotification) {
        try {
            val n = sbn.notification ?: return
            if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
            if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return
            val ex = n.extras ?: return
            val sender = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: return
            val subject = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""
            if (sender.isEmpty()) return
            val ctx = applicationContext
            val now = System.currentTimeMillis()
            val watch = GmailStore.watches(ctx).firstOrNull { GmailRules.matchesWatch(it.name, sender, subject) }
            val important = watch != null || GmailRules.isImportant(sender, subject)
            if (!GmailStore.addRecent(ctx, sender, subject, now, important)) return
            if (!important) return
            if (watch != null) GmailStore.touchWatch(ctx, watch.name, now)
            val safeSubject = GmailRules.maskNumbers(subject)
            val svc = NovaService.instance
            val en = svc?.isEnglish() ?: false
            val line = if (watch != null) {
                if (en) "Your ${watch.name} mail has arrived: $sender, $safeSubject" else "${watch.name} ka mail aa gaya: $sender, $safeSubject"
            } else {
                if (en) "Important mail from $sender: $safeSubject" else "$sender ka zaruri mail: $safeSubject"
            }
            GmailStore.notifyPhone(ctx, if (watch != null) "NOVA: ${watch.name}" else "NOVA: important mail", line)
            svc?.onGmailAlert(line)
        } catch (e: Exception) {
            // one odd notification must never crash the listener
        }
    }

    /**
     * Part 8: AI SMART REPLY inside the Business Watch window. Runs only when ALL of these are ON: Auto-reply, AI SMART, and Business watch "auto on"
     * (the owner allowed replies in this window). The owner's catalogue answers still go first and are never replaced; WaBiz URGENT messages still
     * tell the owner. The business prompt forbids stock / size / rate / delivery claims, so the AI only greets and says the owner will confirm.
     */
    private fun bizSmart(pkg: String, key: String, title: String, body: String, canReply: Boolean, d: WaBiz.Decision?) {
        try {
            val ctx = applicationContext
            if (!ActionLog.autoReplyOn(ctx) || !ActionLog.smartReplyOn(ctx) || !WaBiz.autoReply) return
            if (d != null && (d.sendNow || d.item.draft.isNotBlank())) return   // the owner's own catalogue answer wins
            if (WaBiz.looksSensitive(body)) return
            val now = System.currentTimeMillis()
            val m = Driving.makeMsg(key, pkg, title, body, now, canReply) ?: return
            val reason = AutoReplyRules.skipReason(false, m.canReply, m.hidden, m.text) ?: SmartReplyRules.gate.allow(m.sender, now)
            if (reason != null) {
                if (AutoReplyRules.worthLogging(reason)) ActionLog.add(ctx, m.app, m.sender, m.text, "skipped", AutoReplyRules.reasonText(reason, true), m.pkg)
                return
            }
            smartAutoReply(ctx, m, true)
        } catch (e: Throwable) {
            ErrLog.add("BizSmart", e.toString())   // one odd notification must never crash the listener
        }
    }

    /** WhatsApp / WhatsApp Business only. Decision rules are in WaBiz; this only reads and sends via the notification's own reply button. */
    private fun handleBiz(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        if (pkg != "com.whatsapp" && pkg != "com.whatsapp.w4b") return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return
        val ex = n.extras ?: return
        if (ex.getBoolean("android.isGroupConversation", false)) return   // groups: never alert / auto-reply
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
        val body = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: return
        val key = sbn.key ?: "$pkg:${sbn.id}"
        val action = DrivingBridge.replyAction(n)
        if (action != null) DrivingBridge.remember(key, action)
        val dec = WaBiz.onMessage(title, body, System.currentTimeMillis(), action != null, key.hashCode())
        bizSmart(pkg, key, title, body, action != null, dec)   // Part 8: AI smart reply inside the Business Watch window
        val d = dec ?: return
        if (d.tellOwner) NovaService.instance?.onBizUrgent(d.item.sender, d.item.text)
        if (d.sendNow && d.item.draft.isNotBlank()) {
            val ctx = applicationContext
            val reply = d.item.draft
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    if (WaBiz.active(System.currentTimeMillis()) && WaBiz.autoReply) DrivingBridge.sendReply(ctx, key, reply)
                } catch (e: Exception) { /* one failed reply must never crash the listener */ }
            }, d.delayS * 1000L)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val k = sbn?.key ?: return
        DrivingBridge.forget(k)
    }
}

/** Android-side helpers for driving mode (the pure rules are in Driving.kt). */
object DrivingBridge {
    private const val MAX_ACTIONS = 12
    private val actions = ConcurrentHashMap<String, Notification.Action>()
    private val order = ArrayList<String>()

    /** The notification's inline "Reply" action, if it has one. */
    fun replyAction(n: Notification): Notification.Action? {
        val list = n.actions ?: return null
        for (a in list) {
            val ri = a.remoteInputs
            if (ri != null && ri.isNotEmpty() && a.actionIntent != null) return a
        }
        return null
    }

    fun remember(key: String, a: Notification.Action) {
        synchronized(order) {
            order.remove(key)
            order.add(key)
            actions[key] = a
            while (order.size > MAX_ACTIONS) actions.remove(order.removeAt(0))
        }
    }

    fun forget(key: String) {
        synchronized(order) { order.remove(key); actions.remove(key) }
    }

    fun forgetAll() {
        synchronized(order) { order.clear(); actions.clear() }
    }

    /** Sends [text] through the notification's own reply button. Call ONLY after the user's spoken yes. */
    fun sendReply(ctx: Context, key: String, text: String): Boolean {
        val a = actions[key] ?: return false
        val inputs = a.remoteInputs ?: return false
        if (inputs.isEmpty()) return false
        val results = Bundle()
        for (ri in inputs) results.putCharSequence(ri.resultKey, text)
        val intent = Intent()
        RemoteInput.addResultsToIntent(inputs, intent, results)
        return try {
            a.actionIntent.send(ctx, 0, intent)
            true
        } catch (e: PendingIntent.CanceledException) {
            false
        }
    }

    fun listenerEnabled(ctx: Context): Boolean {
        val s = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return false
        return s.split(":").any { it.startsWith(ctx.packageName + "/") || it.substringBefore("/") == ctx.packageName }
    }

    fun openListenerSettings(ctx: Context) {
        try {
            ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            // no settings screen on this device: the spoken reply still tells the user what to do
        }
    }
}
