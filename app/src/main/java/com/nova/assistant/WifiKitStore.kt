package com.nova.assistant

import android.content.Context
import org.json.JSONArray
import java.io.File

/**
 * v7.153 WIFI KIT: the owner's OWN Wi-Fi names + passwords, kept on the phone only.
 * The password never goes to a log, a server, a chat or an error line: it is encrypted with the
 * Android Keystore (SecureStore, AES-256-GCM, one slot per network). Only the SSID names live in a
 * small bounded local JSON file (wifi_kit.json in filesDir). Other people's networks are never added:
 * the password arrives only from the owner (typed / spoken order) or from their own Wi-Fi QR photo.
 */
object WifiKitStore {
    private const val FILE = "wifi_kit.json"
    private const val MAX = 12
    private const val MAX_FILE = 4_000

    @Synchronized private fun load(ctx: Context): MutableList<String> {
        val out = ArrayList<String>()
        try {
            val f = File(ctx.filesDir, FILE)
            if (!f.isFile || f.length() > MAX_FILE) return out
            val a = JSONArray(f.readText(Charsets.UTF_8))
            for (i in 0 until minOf(a.length(), MAX)) {
                val ssid = a.optString(i)
                if (WifiKitRules.validSsid(ssid) && out.none { it.equals(ssid, true) }) out.add(ssid)
            }
        } catch (e: Exception) { ErrLog.add("WifiKitStore", e.toString()) }
        return out
    }

    @Synchronized private fun saveList(ctx: Context, items: List<String>): Boolean = try {
        val a = JSONArray()
        items.take(MAX).forEach { a.put(it) }
        val f = File(ctx.filesDir, FILE); val tmp = File(ctx.filesDir, "$FILE.tmp")
        tmp.writeText(a.toString(), Charsets.UTF_8)
        if (!tmp.renameTo(f)) { f.writeText(a.toString(), Charsets.UTF_8); tmp.delete() }
        true
    } catch (e: Exception) { false }

    /** Keystore slot name for one SSID (hashed, so the raw name never sits in a pref key). */
    private fun slot(ssid: String): String = "wifi_" + WifiKitRules.sha256(ssid.trim().lowercase())

    /** Saves/updates one of the owner's own networks. pass = "" means an open network (still remembered by name). */
    @Synchronized fun save(ctx: Context, ssid: String, pass: String): Boolean {
        val s = ssid.trim()
        if (!WifiKitRules.validSsidPass(s, pass)) return false
        if (!SecureStore.saveSlot(ctx, slot(s), pass)) return false
        val list = load(ctx)
        if (list.none { it.equals(s, true) }) {
            if (list.size >= MAX) {
                val oldest = list.removeAt(0)
                try { SecureStore.clearSlot(ctx, slot(oldest)) } catch (e: Exception) { ErrLog.add("WifiKitStore", e.toString()) }
            }
            list.add(s)
        }
        return saveList(ctx, list)
    }

    /** Returns the stored password ("" for an open network, "" too when unknown - callers must check [has]). */
    @Synchronized fun pass(ctx: Context, ssid: String): String = try {
        val s = ssid.trim()
        if (has(ctx, s)) SecureStore.getSlot(ctx, slot(s)) else ""
    } catch (e: Exception) { "" }

    @Synchronized fun has(ctx: Context, ssid: String): Boolean = try {
        load(ctx).any { it.equals(ssid.trim(), true) }
    } catch (e: Exception) { false }

    @Synchronized fun all(ctx: Context): List<String> = try { load(ctx) } catch (e: Exception) { emptyList() }

    @Synchronized fun forget(ctx: Context, ssid: String): Boolean {
        val s = ssid.trim()
        val list = load(ctx)
        val filtered = list.filterNot { it.equals(s, true) }
        return if (filtered.size != list.size) {
            try { SecureStore.clearSlot(ctx, slot(s)) } catch (e: Exception) { ErrLog.add("WifiKitStore", e.toString()) }
            saveList(ctx, filtered)
        } else false
    }
}
