package com.nova.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * v7.182 PART B (i): JARVIS UPGRADE LEDGER. The owner asks JARVIS for a feature; JARVIS writes a request, and NOTHING moves
 * forward without the owner's HAAN. This step is the ledger + HAAN gate + retry cap + undo list ONLY: it does not push, build or install.
 * The GitHub build loop and the real undo (previous tag / APK) come in the next parts.
 */
object UpgradeRules {
    const val DRAFT = "DRAFT"; const val APPROVED = "APPROVED"; const val REJECTED = "REJECTED"; const val BLOCKED = "BLOCKED"
    const val PUSHED = "PUSHED"; const val BUILD_OK = "BUILD_OK"; const val INSTALLED = "INSTALLED"; const val UNDONE = "UNDONE"
    const val MAX_TRIES = 5

    private val TRIGGERS = listOf("feature add karo", "feature add kro", "feature chahiye", "feature banao", "feature bnao", "naya feature", "upgrade karo", "khud ko upgrade", "ye add karo", "ye bana do")
    private val PROTECTED = listOf("build.yml", "keystore", "signing", "secret", "password", "token", "deny list", "denylist")

    /** The wish after the trigger phrase ("jarvis naya feature: shayari bhejo" -> "shayari bhejo"), or null. */
    fun parseRequest(norm: String): String? {
        val n = norm.trim()
        if (n.length < 12 || n.length > 600) return null
        val t = TRIGGERS.firstOrNull { n.contains(it) } ?: return null
        val rest = (n.substringAfter(t).trim().trimStart(':', '-', ',').trim()).ifBlank { n.substringBefore(t).trim() }
        val cleaned = rest.removePrefix("jarvis").removePrefix("jarvis").trim()
        return if (cleaned.length >= 6) cleaned else null
    }

    /** Same wish, but cut from the ORIGINAL text (keeps Hindi letters and punctuation that norm() strips); falls back to [fallback]. */
    fun wishFromRaw(raw: String, fallback: String): String {
        val low = raw.lowercase()
        val t = TRIGGERS.firstOrNull { low.contains(it) } ?: return fallback
        val rest = raw.substring(low.indexOf(t) + t.length).trim().trimStart(':', '-', ',', ' ').trim()
        return if (rest.length >= 6) rest.take(600) else fallback
    }

    fun isApprove(n: String) = n.contains("upgrade haan") || n.contains("upgrade manzoor") || n.contains("upgrade ha ")  || n.endsWith("upgrade ha")
    fun isReject(n: String) = n.contains("upgrade nahi") || n.contains("upgrade cancel") || n.contains("upgrade nhi")
    fun isList(n: String) = n.contains("upgrade list") || n.contains("upgrade status") || n.contains("upgrade undo list")

    /** Requests that ask to touch signing, secrets or the safety lists are never auto-built. */
    fun protectedHit(text: String): Boolean = PROTECTED.any { text.lowercase().contains(it) }

    fun canMove(from: String, to: String): Boolean = when (from) {
        DRAFT -> to == APPROVED || to == REJECTED
        APPROVED -> to == PUSHED || to == REJECTED
        PUSHED -> to == BUILD_OK || to == APPROVED
        BUILD_OK -> to == INSTALLED || to == REJECTED
        INSTALLED -> to == UNDONE
        else -> false
    }

    /** Every move that changes the owner's phone or GitHub needs the owner's explicit HAAN. */
    fun needsOwnerYes(to: String): Boolean = to == APPROVED || to == PUSHED || to == INSTALLED || to == UNDONE

    fun retryAllowed(tries: Int): Boolean = tries < MAX_TRIES

    fun tag(id: Int): String = "jarvis-up-$id"

    fun label(state: String): String = when (state) {
        DRAFT -> "request likhi, tumhari HAAN baaki"
        APPROVED -> "request pakki (build abhi shuru nahi hua)"
        REJECTED -> "tumne mana kiya"
        BLOCKED -> "surakshit cheez chhune wali: auto-build band"
        PUSHED -> "GitHub par bheji gayi"
        BUILD_OK -> "build green, install ki HAAN baaki"
        INSTALLED -> "install ho chuka (undo ho sakta hai)"
        UNDONE -> "undo ho chuka"
        else -> state
    }
}

object UpgradeLedger {
    class Entry(val id: Int, val text: String, val state: String, val tries: Int, val at: Long)

    private val lock = Any()
    private fun file(ctx: Context) = File(ctx.filesDir, "upgrade_ledger.json")

    private fun load(ctx: Context): MutableList<Entry> = synchronized(lock) {
        val out = ArrayList<Entry>()
        try {
            val f = file(ctx)
            if (f.exists()) {
                val a = JSONArray(f.readText())
                for (i in 0 until a.length()) { val o = a.getJSONObject(i); out.add(Entry(o.optInt("id"), o.optString("text"), o.optString("state"), o.optInt("tries"), o.optLong("at"))) }
            }
        } catch (e: Exception) { ErrLog.addOnce("UpgradeLedger", "load: " + e.toString()) }
        out
    }

    private fun save(ctx: Context, items: List<Entry>) = synchronized(lock) {
        try {
            val a = JSONArray()
            items.takeLast(100).forEach { a.put(JSONObject().put("id", it.id).put("text", it.text).put("state", it.state).put("tries", it.tries).put("at", it.at)) }
            file(ctx).writeText(a.toString())
        } catch (e: Exception) { ErrLog.addOnce("UpgradeLedger", "save: " + e.toString()) }
    }

    /** A new wish becomes a DRAFT (or BLOCKED if it asks to touch protected things). Nothing is pushed or built. */
    fun add(ctx: Context, text: String, now: Long = System.currentTimeMillis()): Entry {
        val items = load(ctx)
        val id = (items.maxOfOrNull { it.id } ?: 0) + 1
        val e = Entry(id, text.take(600), if (UpgradeRules.protectedHit(text)) UpgradeRules.BLOCKED else UpgradeRules.DRAFT, 0, now)
        items.add(e); save(ctx, items); return e
    }

    /** Moves one entry if the move is allowed. Returns the new state, or null (not found / not allowed). */
    fun move(ctx: Context, id: Int, to: String): String? {
        val items = load(ctx)
        val i = items.indexOfFirst { it.id == id }
        if (i < 0 || !UpgradeRules.canMove(items[i].state, to)) return null
        val o = items[i]
        items[i] = Entry(o.id, o.text, to, o.tries, o.at)
        save(ctx, items); return to
    }

    fun latestDraft(ctx: Context): Entry? = load(ctx).lastOrNull { it.state == UpgradeRules.DRAFT }

    fun listJson(ctx: Context): String {
        val a = JSONArray()
        load(ctx).asReversed().take(30).forEach {
            a.put(JSONObject().put("id", it.id).put("text", it.text).put("state", it.state).put("label", UpgradeRules.label(it.state)).put("tries", it.tries).put("tag", UpgradeRules.tag(it.id)))
        }
        return a.toString()
    }

    fun summary(ctx: Context): String {
        val items = load(ctx)
        if (items.isEmpty()) return "Abhi koi upgrade request nahi hai. Bolo: \"jarvis naya feature: <kya chahiye>\"."
        val sb = StringBuilder("Upgrade requests:\n")
        items.asReversed().take(6).forEach { sb.append("#").append(it.id).append(" ").append(it.text.take(60)).append(" - ").append(UpgradeRules.label(it.state)).append('\n') }
        return sb.toString().trim()
    }

    /** v7.184 PART B (ii): owner ki HAAN ke baad (state APPROVED) hi branch push hota hai. Koi .kt file nahi chhuti, sirf note. */
    fun push(ctx: Context, id: Int): String {
        val items = load(ctx)
        val e = items.firstOrNull { it.id == id } ?: return "Request #$id nahi mili"
        if (e.state != UpgradeRules.APPROVED) return "Request #$id abhi push ke layak nahi (state: " + UpgradeRules.label(e.state) + ")"
        val out = GitHubUpgradePublish.pushBranch(ctx, id, e.text)
        if (!out.ok) return out.msg
        move(ctx, id, UpgradeRules.PUSHED)
        return "Request #$id ka branch GitHub par push ho gaya. Build status ke liye \"upgrade poll $id\" bolo."
    }

    /** v7.184: PUSHED request ka Actions build poll karta hai. Green -> BUILD_OK. Red -> tries+1 (cap MAX_TRIES). */
    fun poll(ctx: Context, id: Int): String {
        val items = load(ctx)
        val i = items.indexOfFirst { it.id == id }
        if (i < 0) return "Request #$id nahi mili"
        val e = items[i]
        if (e.state != UpgradeRules.PUSHED && e.state != UpgradeRules.APPROVED) return "Request #$id abhi poll ke layak nahi (state: " + UpgradeRules.label(e.state) + ")"
        val p = GitHubUpgradePublish.pollLatestRun(ctx, id)
        return when {
            p.status == "completed" && p.conclusion == "success" -> {
                move(ctx, id, UpgradeRules.BUILD_OK)
                "Request #$id ka build GREEN hai. Install karne ke liye \"upgrade haan\" (install step) bolo."
            }
            p.status == "completed" && p.conclusion == "failure" -> {
                val tries = e.tries + 1
                items[i] = Entry(e.id, e.text, e.state, tries, e.at); save(ctx, items)
                if (!UpgradeRules.retryAllowed(tries))
                    "Request #$id ka build RED hai, $tries baar try ho chuka (limit ${UpgradeRules.MAX_TRIES}). Aage try karu?"
                else
                    "Request #$id ka build RED hai (try $tries/${UpgradeRules.MAX_TRIES}). GitHub Actions ki failed step ki red lines bhejo."
            }
            p.status == "error" -> p.msg
            p.status == "not_started" -> p.msg
            else -> "Request #$id ka build abhi chal raha hai (" + p.status + "). Thodi der baad \"upgrade poll $id\" bolna."
        }
    }

    /** Voice / chat entry. Returns JARVIS's reply, or null when the text is not an upgrade phrase. */
    fun handle(ctx: Context, norm: String, raw: String = norm): String? {
        if (UpgradeRules.isList(norm)) return summary(ctx)
        if (UpgradeRules.isApprove(norm)) {
            val d = latestDraft(ctx) ?: return "Koi request tumhari HAAN ka intezaar nahi kar rahi."
            move(ctx, d.id, UpgradeRules.APPROVED)
            return "Request #" + d.id + " pakki kar di. Abhi sirf list me likhi hai, GitHub par bhejna ya build shuru karna agle step me hoga aur wahan bhi tumse HAAN poochunga."
        }
        if (UpgradeRules.isReject(norm)) {
            val d = latestDraft(ctx) ?: return "Koi request pending nahi hai."
            move(ctx, d.id, UpgradeRules.REJECTED)
            return "Request #" + d.id + " cancel kar di."
        }
        val wish = UpgradeRules.parseRequest(norm) ?: return null
        val e = add(ctx, UpgradeRules.wishFromRaw(raw, wish))
        if (e.state == UpgradeRules.BLOCKED) return "Ye request signing, secret ya safety list jaisi surakshit cheez chhuti hai, isliye main ise khud nahi banaunga. Request #" + e.id + " sirf note ki hai."
        return "Samjha: \"" + wish.take(120) + "\". Request #" + e.id + " likh li. Ye theek hai? Pakki karne ke liye bolo \"upgrade haan\", mana karne ke liye \"upgrade nahi\", ya aur detail do. Abhi kuch build ya install nahi hoga."
    }
}
