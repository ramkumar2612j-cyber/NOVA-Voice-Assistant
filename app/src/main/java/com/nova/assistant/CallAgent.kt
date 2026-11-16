package com.nova.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * v7.178 CALL AGENT (Part 2). The Android side: places the REAL call, watches the call state,
 * speaks the script on the speaker, and reports back honestly.
 *
 * Reality on Android (this is not a limitation of this app, it is the OS):
 *  - A normal app CANNOT tap the call's audio stream (Android 10+ blocks downlink voice for third-party
 *    apps) and this code does not try to get around that. v7.180 hears the far side the plain way: the call
 *    is on SPEAKER, the phone MIC picks it up, Whisper (Groq key) transcribes it, one short rule-checked
 *    reply is spoken. Phones that silence the mic during calls are detected and reported honestly.
 *    The incoming auto-answer stays a one-way greeting.
 *  - What really works and what this does: ACTION_CALL dial (CALL_PHONE permission), speakerphone
 *    script through TTS while the call is live, real call-state tracking (DIALING / ON CALL /
 *    ENDED via TelephonyManager), a live card in the UI, and an honest spoken report at the end
 *    with a CALLBACK NEEDED tag when the call never connected.
 *
 * One live agent at a time. Everything goes through [CallAgentRules] first (parsing, deny,
 * script, report): no model decides what is said on a call.
 */
object CallAgent {

    @Volatile var active = false
        private set

    @Volatile private var state = CallAgentRules.State.IDLE
    @Volatile private var name = ""
    @Volatile private var number = ""
    @Volatile private var script = ""
    @Volatile private var startedAt = 0L
    @Volatile private var wasOnCall = false
    private val lock = Any()

    /** UI push: called with (role, text) exactly like JarvisService.emit. Roles: "callcard" lines are JSON. */
    @Volatile var emitter: ((role: String, text: String) -> Unit)? = null

    /** speak(text) speaks through the service TTS; stopSpeak() cuts it. Set by JarvisService. */
    @Volatile var speaker: ((text: String) -> Boolean)? = null
    @Volatile var speechStop: (() -> Unit)? = null

    // ---- v7.180 TWO-WAY TALK: set by JarvisService. hearer = Whisper (16 kHz mono PCM -> text, "" = nothing),
    //      canHear = a speech-to-text key exists, thinker = one short model reply (system, user -> text).
    @Volatile var hearer: ((ByteArray) -> String)? = null
    @Volatile var canHear: (() -> Boolean)? = null
    @Volatile var thinker: ((system: String, user: String) -> String?)? = null

    /** True while the mic loop must hand every chunk to [feed] (the far side is heard ONLY by the call agent). */
    @Volatile var conversing = false
        private set

    @Volatile private var talkOn = false
    @Volatile private var talkGoal = ""
    private val turns = ArrayList<CallAgentRules.Turn>()
    private var lastSpoken = ""
    private var heardAny = false
    private var micBlocked = false
    private var sensitive = false
    private var hangUp = false


    // ------------------------------------------------------------------ v7.180 far-side listening (mic thread)

    private val utt = java.io.ByteArrayOutputStream()
    private var preChunk: ByteArray? = null
    private var heardSpeech = false
    private var silentMs = 0
    private var uttMs = 0
    private var speechMs = 0
    private var noise = 300.0
    @Volatile private var needReset = false
    @Volatile private var muteUntil = 0L
    @Volatile private var lastTtsBusyAt = 0L
    @Volatile private var ttsSeen = false
    @Volatile private var listenMs = 0L
    @Volatile private var maxRms = 0.0
    private val queue = java.util.concurrent.LinkedBlockingQueue<ByteArray>()

    private fun resetUtt() {
        utt.reset(); preChunk = null; heardSpeech = false; silentMs = 0; uttMs = 0; speechMs = 0
    }

    private fun chunkBytes(buf: ShortArray, n: Int): ByteArray {
        val out = ByteArray(n * 2)
        for (i in 0 until n) {
            out[i * 2] = (buf[i].toInt() and 0xff).toByte()
            out[i * 2 + 1] = ((buf[i].toInt() shr 8) and 0xff).toByte()
        }
        return out
    }

    /**
     * Mic thread, every 100 ms chunk, only while [conversing]. While JARVIS's own voice plays (ttsBusy) nothing is taken
     * (the speaker is also what the mic hears), and 0.8 s after it too. A finished utterance (speech, then 1.1 s quiet)
     * goes to the queue; the call thread transcribes it. Never blocks.
     */
    fun feed(buf: ShortArray, n: Int, ttsBusy: Boolean) {
        if (!conversing || n <= 0) return
        val now = System.currentTimeMillis()
        if (needReset) { needReset = false; resetUtt() }
        if (ttsBusy) { ttsSeen = true; lastTtsBusyAt = now; muteUntil = now + 800L; resetUtt(); return }
        if (now < muteUntil) return
        var sum = 0.0
        for (i in 0 until n) sum += buf[i].toDouble() * buf[i]
        val rms = Math.sqrt(sum / n)
        val ms = n * 1000 / 16000
        listenMs += ms
        if (rms > maxRms) maxRms = rms
        val speaking = rms > maxOf(400.0, noise * 2.5)
        if (!speaking) noise = noise * 0.95 + rms * 0.05
        val bytes = chunkBytes(buf, n)
        if (speaking) {
            if (!heardSpeech) {
                heardSpeech = true
                val pc = preChunk
                if (pc != null) { utt.write(pc, 0, pc.size); uttMs += pc.size / 32 }
            }
            silentMs = 0
            speechMs += ms
        } else if (heardSpeech) silentMs += ms
        if (heardSpeech) { utt.write(bytes, 0, bytes.size); uttMs += ms } else preChunk = bytes
        if (heardSpeech && (silentMs >= 1100 || uttMs >= 12000)) {
            val enough = speechMs >= 350
            val data = utt.toByteArray()
            resetUtt()
            if (enough) queue.offer(data)
        }
    }

    /** Waits until our own TTS line has really finished (or gives up). Call thread. */
    private fun waitSpeechDone(maxMs: Long) {
        val t0 = System.currentTimeMillis()
        while (active && !ttsSeen && System.currentTimeMillis() - t0 < 4000L) Thread.sleep(100)
        if (!ttsSeen) return
        while (active && System.currentTimeMillis() - t0 < maxMs) {
            if (System.currentTimeMillis() - lastTtsBusyAt > 900L) break
            Thread.sleep(150)
        }
    }

    /** Speak one line on the call and wait for it to end; whatever the mic caught meanwhile is not an answer. Call thread. */
    private fun sayAndWait(text: String, maxMs: Long = 22_000L) {
        lastSpoken = text
        ttsSeen = false
        try { speaker?.invoke(text) } catch (ignored: Exception) { }
        waitSpeechDone(maxMs)
        queue.clear()
        needReset = true
    }

    private fun endCallSafely(ctx: Context) {
        try {
            val tc = ctx.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            if (tc != null && ctx.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) tc.endCall()
        } catch (ignored: Exception) { }
    }

    /**
     * The conversation loop: hear the far side -> Whisper -> safe reply -> speak. Ends on goodbye, a money/secret
     * word, the turn limit, a quiet line, a blocked mic, the call ending or the call cap. Call thread.
     */
    private fun converse(appCtx: Context, en: Boolean, tm: TelephonyManager?, cap: Long) {
        val sys = CallAgentRules.convSystem(ActionLog.ownerName(appCtx), talkGoal, en)
        var turnsDone = 0
        var lastActivity = System.currentTimeMillis()
        var prompted = false
        while (System.currentTimeMillis() < cap && active) {
            val cs = try { tm?.callState ?: 2 } catch (e: Exception) { 2 }
            if (CallAgentRules.fromTelephony(cs, wasOnCall) == CallAgentRules.State.ENDED) return
            if (!heardAny && listenMs >= CallAgentRules.MIC_SILENT_AFTER_MS && maxRms < CallAgentRules.MIC_SILENT_RMS) { micBlocked = true; return }
            val pcm = queue.poll(400, java.util.concurrent.TimeUnit.MILLISECONDS)
            if (pcm == null) {
                val quiet = System.currentTimeMillis() - lastActivity
                if (!prompted && quiet >= CallAgentRules.FAR_SILENCE_PROMPT_MS) {
                    prompted = true
                    sayAndWait(CallAgentRules.stillThereLine(en))
                    lastActivity = System.currentTimeMillis()
                } else if (prompted && quiet >= CallAgentRules.FAR_SILENCE_END_MS - CallAgentRules.FAR_SILENCE_PROMPT_MS) {
                    hangUp = true; return
                }
                continue
            }
            val heard = CallAgentRules.cleanHeard(try { hearer?.invoke(pcm) ?: "" } catch (e: Exception) { "" })
            if (heard.isBlank()) continue
            if (CallAgentRules.isEcho(heard, lastSpoken)) continue
            heardAny = true; prompted = false; lastActivity = System.currentTimeMillis()
            turns.add(CallAgentRules.Turn(true, heard))
            try { emitter?.invoke("ai", "🗣 " + heard) } catch (ignored: Exception) { }
            poke(CallAgentRules.State.ON_CALL, heard)
            if (CallAgentRules.farSideSensitive(heard)) {
                sensitive = true
                val safe = CallAgentRules.safeLine(en)
                turns.add(CallAgentRules.Turn(false, safe))
                sayAndWait(safe)
                hangUp = true; return
            }
            if (CallAgentRules.isGoodbye(heard)) {
                val bye = CallAgentRules.closingLine(en)
                turns.add(CallAgentRules.Turn(false, bye))
                sayAndWait(bye)
                hangUp = true; return
            }
            turnsDone++
            if (turnsDone > CallAgentRules.MAX_TURNS) {
                val bye = CallAgentRules.closingLine(en)
                turns.add(CallAgentRules.Turn(false, bye))
                sayAndWait(bye)
                hangUp = true; return
            }
            val raw = try { thinker?.invoke(sys, CallAgentRules.convUser(turns, en)) } catch (e: Exception) { null }
            var reply = CallAgentRules.cleanReply(raw, en)
            var last = false
            if (reply.isBlank()) { reply = CallAgentRules.unsureLine(en); last = true }
            turns.add(CallAgentRules.Turn(false, reply))
            poke(CallAgentRules.State.ON_CALL, reply)
            sayAndWait(reply)
            if (last) { hangUp = true; return }
        }
    }

    // ------------------------------------------------------------------ state push

    private fun poke(stateNew: CallAgentRules.State, text: String) {
        state = stateNew
        val json = JSONObject()
            .put("s", stateNew.name)
            .put("name", name)
            .put("num", number)
            .put("txt", text)
            .put("secs", if (startedAt > 0) (System.currentTimeMillis() - startedAt) / 1000L else 0L)
        try { emitter?.invoke("callcard", json.toString()) } catch (ignored: Exception) { }
    }

    /** The whole live card as JSON (bridge pulls this when the app opens). */
    fun snapshotJson(): String = synchronized(lock) {
        JSONObject()
            .put("active", active)
            .put("s", state.name)
            .put("name", name)
            .put("num", number)
            .put("script", script)
            .put("report", lastReport)
            .put("secs", if (startedAt > 0) (System.currentTimeMillis() - startedAt) / 1000L else 0L)
            .toString()
    }

    // ------------------------------------------------------------------ start

    /**
     * Voice / chat entry. Returns the one sentence JARVIS says right now; the call itself runs on
     * its own thread. `askPermission` is invoked (on the UI thread by the caller) when the Call
     * permission is missing, exactly like the old direct-call path.
     * v7.178b: `custom` non-blank means the owner said "call karke bolo <msg>" -> that message is spoken.
     */
    fun start(ctx: Context, number: String, name: String, detail: String, place: String, en: Boolean,
               askPermission: () -> Unit, onDone: (spoken: String) -> Unit, custom: String = ""): String {
        if (active) return if (en) "One call agent is already on a call. Say stop to end it first." else "Ek call agent pehle se call par hai. Pehle ruko bolo."
        if (number.isBlank()) return if (en) "I did not catch a phone number to call." else "Call karne ke liye number samajh nahi aaya."
        if (CallAgentRules.denied(detail + " " + place + " " + custom)) return if (en) "I do not say payment or account things on a call." else "Payment ya account wali baat main call par nahi karta."
        if (ctx.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            askPermission()
            return if (en) "The Call permission is needed once. Allow it, then say the command again." else "Call ki permission ek baar chahiye. Allow karo, phir wahi command bolo."
        }
        synchronized(lock) {
            active = true
            state = CallAgentRules.State.DIALING
            this.number = number
            this.name = name.ifBlank { number }
            this.script = CallAgentRules.script(place, detail, en, custom)
            startedAt = 0L
            wasOnCall = false
            talkOn = CallAgentRules.talkEnabled(try { canHear?.invoke() == true } catch (e: Exception) { false })
            talkGoal = CallAgentRules.goalText(place, detail, custom, en)
            turns.clear(); lastSpoken = ""; heardAny = false; micBlocked = false; sensitive = false; hangUp = false
        }
        Thread { run(ctx, en, onDone) }.start()
        val noEar = if (talkOn) "" else (if (en) " (No speech key, so I can only speak, not hear.)" else " (Speech key nahi hai, isliye sirf bolunga, unka jawab sun nahi paunga.)")
        val said = if (custom.isNotBlank())
            (if (en) "Calling ${this.name}, I will pass your message." else "${this.name} ko call laga raha hoon, aapka paigam bolunga.")
        else
            (if (en) "Calling ${this.name}, I will report back." else "${this.name} ko call laga raha hoon, report deta hoon, sir.")
        return said + noEar
    }

    /**
     * v7.178b INCOMING call entry. A call is ringing; the importance decision (answer/tell/skip) is
     * already taken by CallAgentRules.incomingDecision(). This accepts the call, greets the caller,
     * waits INCOMING_GREET_MS, hangs up, and reports. Requires ANSWER_PHONE_CALLS permission.
     * Returns the one sentence JARVIS says right now; the call runs on its own thread.
     */
    fun answerIncoming(ctx: Context, callerName: String, callerNumber: String, en: Boolean,
                        askPermission: () -> Unit, onDone: (spoken: String) -> Unit): String {
        if (active) return if (en) "I am already on another call." else "Main pehle hi ek call par hoon."
        if (ctx.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED) {
            askPermission()
            return if (en) "The Answer-calls permission is needed once. Allow it." else "Call uthane ki permission ek baar chahiye. Allow karo."
        }
        synchronized(lock) {
            active = true
            state = CallAgentRules.State.ON_CALL
            this.number = callerNumber
            this.name = callerName.ifBlank { callerNumber }
            this.script = CallAgentRules.incomingGreeting(ActionLog.ownerName(ctx), en)
            startedAt = 0L
            wasOnCall = false
        }
        Thread { runIncoming(ctx, en, onDone) }.start()
        return if (en) "Answering ${this.name}, greeting them now." else "${this.name} ki call utha raha hoon, unhe greeting deta hoon."
    }

    /** STOP ("ruko", "bas karo") and the bridge both come here. Safe to call any time. */
    fun stop(ctx: Context): Boolean {
        if (!active) return false
        try {
            val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            if (tm != null && ctx.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                tm.endCall()
            }
        } catch (ignored: Exception) { }
        try { speechStop?.invoke() } catch (ignored: Exception) { }
        conversing = false
        poke(CallAgentRules.State.ENDED, "stopped")
        active = false
        return true
    }

    // ------------------------------------------------------------------ the call loop

    private fun run(ctx: Context, en: Boolean, onDone: (String) -> Unit) {
        val appCtx = ctx.applicationContext
        try {
            poke(CallAgentRules.State.DIALING, if (en) "Dialing..." else "Call lag rahi hai...")
            // 1) place the real call
            val placed = try {
                appCtx.startActivity(
                    Intent(Intent.ACTION_CALL, Uri.fromParts("tel", number, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                true
            } catch (e: SecurityException) { false } catch (e: Exception) { false }
            if (!placed) {
                poke(CallAgentRules.State.FAILED, "dial failed")
                active = false
                onDone(CallAgentRules.report(CallAgentRules.State.FAILED, name, number, 0, en))
                return
            }
            val tm = appCtx.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            // 2) wait for OFFHOOK (call picked up) with the dial timeout
            val t0 = System.currentTimeMillis()
            var offhook = false
            while (System.currentTimeMillis() - t0 < CallAgentRules.DIAL_TIMEOUT_MS && active) {
                val cs = try { tm?.callState ?: 0 } catch (e: Exception) { 0 }
                val st = CallAgentRules.fromTelephony(cs, wasOnCall)
                if (st == CallAgentRules.State.ON_CALL) { offhook = true; break }
                if (st == CallAgentRules.State.ENDED && wasOnCall) break   // caller hung up before pickup
                Thread.sleep(400)
            }
            if (!offhook || !active) {
                val fin = if (offhook) CallAgentRules.State.ENDED else CallAgentRules.State.FAILED
                val rep = CallAgentRules.report(fin, name, number, (System.currentTimeMillis() - t0) / 1000L, en)
                poke(fin, rep)
                active = false
                lastReport = rep
                onDone(rep)
                return
            }
            // 3) on the call: speaker ON, then speak the script
            wasOnCall = true
            startedAt = System.currentTimeMillis()
            poke(CallAgentRules.State.ON_CALL, if (en) "On the call - I'll report back, sir." else "Call par hoon - report deta hoon, sir.")
            try {
                val am = appCtx.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                am?.isSpeakerphoneOn = true
            } catch (ignored: Exception) { }
            Thread.sleep(2500)   // let the other side say hello first
            val cap = System.currentTimeMillis() + CallAgentRules.CALL_TIMEOUT_MS
            if (talkOn) {
                // v7.180: TWO-WAY. The mic loop hands every chunk to feed(); the opener is spoken, then we listen and answer.
                queue.clear(); resetUtt(); listenMs = 0L; maxRms = 0.0; noise = 300.0; muteUntil = 0L
                conversing = true
                sayAndWait(script)
                converse(appCtx, en, tm, cap)
                conversing = false
                if (hangUp) { Thread.sleep(600); endCallSafely(appCtx) }
            } else {
                try { speaker?.invoke(script) } catch (ignored: Exception) { }
            }
            // 4) stay on the line until the call ends, or the call cap
            while (System.currentTimeMillis() < cap && active) {
                val cs = try { tm?.callState ?: 2 } catch (e: Exception) { 2 }
                val st = CallAgentRules.fromTelephony(cs, wasOnCall)
                if (st == CallAgentRules.State.ENDED) break
                Thread.sleep(500)
            }
            val secs = if (startedAt > 0) (System.currentTimeMillis() - startedAt) / 1000L else 0L
            val rep = if (talkOn) {
                val summary = if (heardAny) {
                    val raw = try { thinker?.invoke(CallAgentRules.summarySystem(en), CallAgentRules.summaryUser(turns)) } catch (e: Exception) { null }
                    CallAgentRules.cleanSummary(raw).ifBlank { CallAgentRules.fallbackSummary(turns, en) }
                } else ""
                CallAgentRules.talkReport(name, number, secs, summary, heardAny, micBlocked, sensitive, en)
            } else CallAgentRules.report(CallAgentRules.State.ENDED, name, number, secs, en)
            poke(CallAgentRules.State.ENDED, rep)
            active = false
            lastReport = rep
            onDone(rep)
        } catch (t: Throwable) {
            ErrLog.add("CallAgent", t.toString())
            val rep = CallAgentRules.report(CallAgentRules.State.FAILED, name, number, 0, en)
            poke(CallAgentRules.State.FAILED, rep)
            active = false
            lastReport = rep
            onDone(rep)
        } finally {
            conversing = false
            queue.clear()
        }
    }

    /** The report of the last finished call (bridge shows it on the card when the app opens). */
    @Volatile var lastReport = ""
        private set

    // ------------------------------------------------------------------ incoming call loop (v7.178b)

    private fun runIncoming(ctx: Context, en: Boolean, onDone: (String) -> Unit) {
        val appCtx = ctx.applicationContext
        try {
            poke(CallAgentRules.State.ON_CALL, if (en) "Answering..." else "Call utha raha hoon...")
            // 1) accept the ringing call
            val accepted = try {
                val tm = appCtx.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                if (tm != null && appCtx.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                    tm.acceptRingingCall(); true
                } else false
            } catch (e: SecurityException) { false } catch (e: Exception) { false }
            if (!accepted) {
                poke(CallAgentRules.State.FAILED, "accept failed")
                active = false
                onDone(CallAgentRules.report(CallAgentRules.State.FAILED, name, number, 0, en))
                return
            }
            // 2) speaker ON + speak the greeting
            wasOnCall = true
            startedAt = System.currentTimeMillis()
            poke(CallAgentRules.State.ON_CALL, if (en) "On the call - greeting the caller." else "Call par hoon - caller ko greeting de raha hoon.")
            try {
                val am = appCtx.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                am?.isSpeakerphoneOn = true
            } catch (ignored: Exception) { }
            Thread.sleep(1500)   // let the caller start speaking
            try { speaker?.invoke(script) } catch (ignored: Exception) { }
            // 3) hold the line for the greet window, then end
            val tm = appCtx.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val cap = System.currentTimeMillis() + CallAgentRules.INCOMING_GREET_MS
            while (System.currentTimeMillis() < cap && active) {
                val cs = try { tm?.callState ?: 2 } catch (e: Exception) { 2 }
                if (CallAgentRules.fromTelephony(cs, wasOnCall) == CallAgentRules.State.ENDED) break   // caller hung up
                Thread.sleep(500)
            }
            // 4) hang up (graceful: TelecomManager.endCall)
            try {
                val tc = appCtx.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                if (tc != null && appCtx.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) tc.endCall()
            } catch (ignored: Exception) { }
            val secs = if (startedAt > 0) (System.currentTimeMillis() - startedAt) / 1000L else 0L
            val rep = CallAgentRules.incomingReport(name, secs, en)
            poke(CallAgentRules.State.ENDED, rep)
            active = false
            lastReport = rep
            onDone(rep)
        } catch (t: Throwable) {
            ErrLog.add("CallAgent", "incoming: " + t.toString())
            val rep = CallAgentRules.incomingReport(name, 0, en)
            poke(CallAgentRules.State.FAILED, rep)
            active = false
            lastReport = rep
            onDone(rep)
        }
    }

    // ------------------------------------------------------------------ bridge helpers

    /** The bridge exposes this so the JS card can check a dial request really went out. */
    fun placedRecent(ms: Long = 60_000L): Boolean = active || (System.currentTimeMillis() - startedAt) < ms
}
