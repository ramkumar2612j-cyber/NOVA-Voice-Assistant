package com.nova.assistant

/**
 * v7.110 PART 7: which bigger on-device brain NOVA OFFERS, chosen by the phone's RAM and free storage.
 * The owner picks; nothing here starts a download. PURE Kotlin (no Android classes), unit tested.
 *
 * Why: the built-in Qwen2.5-0.5B is too weak to plan multi-step jobs. 1.5B and 3B plan much better, but they are big
 * (about 1 GB and 2 GB), so they are only OFFERED, never downloaded without the owner's tap.
 * Honest limit: a bigger model is slower on a phone and the real speed is NOT TESTED on a phone.
 */
object BrainChoiceRules {
    private const val MB = 1024L * 1024L

    /** Free space that must stay free AFTER the download (same value as the pack: the phone never runs out of storage because of NOVA). */
    const val KEEP_FREE_BYTES = OfflinePack.KEEP_FREE_BYTES

    /** At or above this RAM the 3B brain is the recommended one; below it (but >= 4000) the 1.5B is. */
    const val RECOMMEND_3B_RAM_MB = 8000L
    const val RECOMMEND_15B_RAM_MB = 4000L

    enum class State { INSTALLED, OFFER, NEEDS_RAM, NEEDS_SPACE }

    class Option(
        val id: String,
        val label: String,
        val approxMb: Long,
        val state: State,
        val recommended: Boolean,
        val note: String
    )

    /** The brains the owner can pick from, strongest first. 1.5B = a model that already exists in the pack; 3B = owner-pick only. */
    private val IDS = listOf("qwen25-3b", "qwen25-15b")

    private fun approxMb(m: OfflinePack.Model): Long = (m.minBytes + m.maxBytes) / 2L / MB

    fun recommendedId(ramMb: Long): String? = when {
        ramMb >= RECOMMEND_3B_RAM_MB -> "qwen25-3b"
        ramMb >= RECOMMEND_15B_RAM_MB -> "qwen25-15b"
        else -> null
    }

    fun options(ramMb: Long, freeBytes: Long, installed: Set<String>): List<Option> {
        val rec = recommendedId(ramMb)
        val out = ArrayList<Option>()
        for (id in IDS) {
            val m = OfflinePack.byId(id) ?: continue
            val state = when {
                id in installed -> State.INSTALLED
                ramMb < m.minRamMb -> State.NEEDS_RAM
                freeBytes < m.maxBytes + KEEP_FREE_BYTES -> State.NEEDS_SPACE
                else -> State.OFFER
            }
            out.add(Option(id, m.label, approxMb(m), state, id == rec, noteFor(state, m, rec == id)))
        }
        return out
    }

    private fun noteFor(state: State, m: OfflinePack.Model, recommended: Boolean): String = when (state) {
        State.INSTALLED -> "Installed. NOVA tries it before the smaller brains."
        State.NEEDS_RAM -> "Phone RAM is too low for this brain (needs about " + (m.minRamMb / 1000L) + " GB)."
        State.NEEDS_SPACE -> "Not enough free storage (needs the file plus " + (KEEP_FREE_BYTES / MB) + " MB spare)."
        State.OFFER -> if (recommended) "Recommended for this phone. Download starts only when you tap." else "Download starts only when you tap."
    }

    /**
     * null = the owner's tap may start the download of [id]. Otherwise a short reason.
     * [ownerTapped] must be true: a download is never started by NOVA on its own for these brains.
     */
    fun refusal(id: String, ownerTapped: Boolean, ramMb: Long, freeBytes: Long, unmetered: Boolean, online: Boolean, installed: Set<String>): String? {
        if (!ownerTapped) return "owner has not confirmed"
        if (id !in IDS) return "unknown brain"
        val m = OfflinePack.byId(id) ?: return "unknown brain"
        if (id in installed) return "already installed"
        if (ramMb < m.minRamMb) return "phone RAM too low"
        if (freeBytes < m.maxBytes + KEEP_FREE_BYTES) return "not enough free storage"
        if (!online) return "no internet"
        if (!unmetered) return "needs Wi-Fi (mobile data is not used for big files)"
        return null
    }

    /** One short Hinglish line for the card header. */
    fun headline(ramMb: Long, installed: Set<String>): String {
        val rec = recommendedId(ramMb)
        val have = IDS.firstOrNull { it in installed }
        return when {
            have != null -> "Bada brain ON: " + (OfflinePack.byId(have)?.label ?: have) + ". Planning pehle isse hogi."
            rec == null -> "Is phone ki RAM kam hai: chhota brain + online AI hi theek rahega."
            else -> "Is phone ke liye suggested: " + (OfflinePack.byId(rec)?.label ?: rec) + ". Aap chuno, tabhi download hoga."
        }
    }

    /** Accepts only ids this card may start (the bridge uses it before anything else). */
    fun isOfferedId(id: String): Boolean = id in IDS
}
