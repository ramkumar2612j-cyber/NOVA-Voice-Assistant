package com.nova.assistant

import android.content.Context

/**
 * v7.183 SELF KNOWLEDGE: JARVIS can say who it is, what it does on phone / laptop / business, what it does NOT do, and how it is
 * checked and upgraded. Answered locally (no AI call). The texts only claim what the source and the status files say; every
 * area says plainly that it is not phone-tested yet. Pure text rules in SelfKnowledgeRules (unit-tested).
 */
object SelfKnowledgeRules {
    const val OVERVIEW = "OVERVIEW"; const val PHONE = "PHONE"; const val LAPTOP = "LAPTOP"; const val BUSINESS = "BUSINESS"; const val LIMITS = "LIMITS"; const val UPGRADE = "UPGRADE"

    private val ASK = listOf("apne baare me", "apne bare me", "tumhare baare me", "tumhare bare me", "tum kaun ho", "tu kaun hai", "apna intro", "apna parichay", "about yourself", "who are you", "tumhari limit", "tumhari limits", "kya nahi kar sakte", "jarvis kaun hai", "jarvis kaun hai")

    fun isAsk(norm: String): Boolean = norm.isNotBlank() && norm.length <= 80 && ASK.any { norm.contains(it) }

    fun section(norm: String): String = when {
        norm.contains("laptop") || norm.contains("computer") || norm.contains("pc") -> LAPTOP
        norm.contains("business") || norm.contains("biz") || norm.contains("vyapar") || norm.contains("dukan") -> BUSINESS
        norm.contains("limit") || norm.contains("nahi kar") -> LIMITS
        norm.contains("upgrade") || norm.contains("khud ko") || norm.contains("sudhar") -> UPGRADE
        norm.contains("mobile") || norm.contains("phone") -> PHONE
        else -> OVERVIEW
    }

    fun text(sec: String, version: String, liveLine: String): String = when (sec) {
        PHONE -> "MOBILE par main: battery, time, torch, volume, brightness, media, Home/Back/Recents, app kholna (bina key ke, offline). Call lagane par dialer khulta hai, call tum dabate ho. WhatsApp/SMS, alarm, timer, calendar, navigation, YouTube, web search (AI key ya free chain se). Accessibility ON ho to screen par tap, type, scroll. Wake word offline sunta hoon. Sab kaam ek-ek karke ledger me likhta hoon, adhura rahe to dobara chalane se pehle poochta hoon. NOT TESTED: asli phone par poora test abhi baaki hai."
        LAPTOP -> "LAPTOP: laptop app (Electron) phone se LAPTOP LINK se judta hai (code phone me paste karo). Phir \"laptop par chrome kholo\" ya laptop se \"phone par torch jalao\". Laptop par notes, reminder, workspace (ek command se kaam ki jagah khulti hai) aur UNDO hai. Delete, shutdown, password wale kaam refuse karta hoon. Laptop awaaz: push-to-talk (Groq key). NOT TESTED: exe build aur phone+laptop end-to-end abhi nahi hua."
        BUSINESS -> "BUSINESS: \"jarvis biz ...\" se profile (product, price, advance, UPI, delivery). Genuine leads (Google Places key ya free OpenStreetMap) aur message draft: bhejna sirf tumhari approval ke baad. Order confirm par advance + UPI link. Call Agent: tumhare kehne par outgoing call, speaker par baat, aur mic+Whisper se sunkar jawab (agar phone mic chalne de), phir report. WhatsApp Business se website, Instagram, YouTube autopilot, Video Studio, Payment QR, App Builder. Payment auto-verify nahi karta. NOT TESTED: asli keys, asli network aur phone par abhi nahi."
        LIMITS -> "LIMITS (sach): payment, OTP, bank, password, delete jaise kaam khud nahi karta, tumhari HAAN ke bina nahi. Phone lock, OTP, captcha ya kisi app/service ki security todne wala bypass nahi karta; kaam ke jo seedhe raaste hain (intent, accessibility, shortcut, App Builder pipeline) bejhijhak use karta hoon. Incoming call par sirf ek-tarfa announce. Wi-Fi/Bluetooth Android khud toggle nahi karne deta. Jo cheez test nahi hui, uspar PASS nahi kehta."
        UPGRADE -> "KHUD KI JAANCH aur SUDHAAR: \"jarvis kya nahi chal raha\" bolo to saari jaanch ek report me batata hoon (Settings > TEST REPORT > POORA REPORT, copy bhi). Naya feature chahiye to bolo \"jarvis naya feature: ...\": request likhta hoon, tumhari HAAN ke baad hi aage. Abhi build/install shuru nahi hota (agla step); jab hoga to har step par HAAN, retry 5 tak, aur undo."
        else -> "Main JARVIS (JARVIS) hoon, tumhara phone + laptop + business assistant, version $version. $liveLine\nBolo: \"apne baare me mobile / laptop / business / limits / upgrade\" aur main wahi hissa bataunga."
    }
}

object SelfKnowledge {
    private fun version(ctx: Context): String = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?" } catch (e: Throwable) { "?" }

    fun handle(ctx: Context, norm: String): String? {
        if (!SelfKnowledgeRules.isAsk(norm)) return null
        val sec = SelfKnowledgeRules.section(norm)
        val live = if (sec == SelfKnowledgeRules.OVERVIEW) try { FullCheck.answer(ctx).lines().first() } catch (e: Throwable) { "" } else ""
        return SelfKnowledgeRules.text(sec, version(ctx), live)
    }
}
