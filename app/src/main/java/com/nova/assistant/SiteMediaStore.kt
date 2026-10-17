package com.nova.assistant

/**
 * v7.03 G: the photos / videos the owner picked from the phone gallery for the website (max 6, kept 6 hours, memory only).
 * Each item already holds its final data: URI, so the website stays ONE self-contained html file. Pure Kotlin (no Android classes).
 * The Android side (MainActivity) reads and shrinks the file, then calls add().
 */
object SiteMediaStore {
    class Item(val id: String, val kind: String, val uri: String, val thumb: String, val addedAt: Long, @Volatile var place: MediaPlace.Place?)

    const val MAX_ITEMS = 6
    const val MAX_TOTAL_CHARS = 12_000_000      // data URIs are base64: ~9 MB of real files; the whole page must stay far below 16 MB
    private const val KEEP_MS = 6 * 60 * 60_000L
    private val list = ArrayList<Item>()

    @Synchronized private fun prune(now: Long) { list.removeAll { now - it.addedAt > KEEP_MS } }

    @Synchronized fun items(now: Long = System.currentTimeMillis()): List<Item> { prune(now); return ArrayList(list) }

    /** Returns the new id (m1..m6) or null when full / too big. kind = "image" | "video". */
    @Synchronized fun add(kind: String, uri: String, thumb: String, now: Long = System.currentTimeMillis()): String? {
        prune(now)
        if (kind != "image" && kind != "video") return null
        if (list.size >= MAX_ITEMS) return null
        if (list.sumOf { it.uri.length } + uri.length > MAX_TOTAL_CHARS) return null
        val used = list.map { it.id }.toSet()
        val id = (1..MAX_ITEMS).map { "m$it" }.firstOrNull { it !in used } ?: return null
        list.add(Item(id, kind, uri, thumb, now, null))
        return id
    }

    @Synchronized fun setPlace(id: String, place: MediaPlace.Place?): Boolean {
        val it = list.firstOrNull { x -> x.id == id } ?: return false
        it.place = place
        return true
    }

    @Synchronized fun clear() { list.clear() }

    /**
     * Typed / spoken placement: "dusri photo gallery mein lagao" (item 2), "ye photo hero mein" (the newest item that has no place yet).
     * Returns how many items got a place. Never touches an item that already has a place unless the sentence names its number.
     */
    @Synchronized fun applyText(text: String, now: Long = System.currentTimeMillis()): Int {
        prune(now)
        if (list.isEmpty()) return 0
        val place = MediaPlace.parse(text) ?: return 0
        val idx = MediaPlace.itemIndex(text)
        val target: Item? = when {
            idx == null -> list.lastOrNull { it.place == null }
            idx == -1 -> list.last()
            else -> list.getOrNull(idx - 1)
        }
        if (target == null) return 0
        target.place = place
        return 1
    }
}
