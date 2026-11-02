package com.nova.assistant

/**
 * v7.148 STEP 8 OF 10: "WATCH SE BOLKE KAAM" diagnosis. Read-only. The watch bug has no proven root cause, so NOVA now remembers the last few
 * watch-button events (in memory, lost when the app process dies) and can say WHERE the chain stopped:
 * button signal arrived -> service on -> not busy / not locked -> watch mic found (or phone mic used).
 * It changes no behaviour. Pure Kotlin (no Android), unit tested.
 */
object WatchDiag {
    const val BUTTON = "button"        // Android delivered VOICE_COMMAND to NOVA
    const val SERVICE_OFF = "service_off"
    const val BUSY = "busy"
    const val LOCKED = "locked"
    const val MIC_WATCH = "mic_watch"  // watch mic (Bluetooth SCO) was found
    const val MIC_PHONE = "mic_phone"  // no SCO input: phone mic used

    class Ev(val at: Long, val stage: String)

    private val items = ArrayList<Ev>()
    private const val MAX = 6

    @Synchronized fun add(stage: String, now: Long = System.currentTimeMillis()) {
        items.add(Ev(now, stage))
        while (items.size > MAX) items.removeAt(0)
    }

    @Synchronized fun all(): List<Ev> = ArrayList(items)

    @Synchronized fun clear() { items.clear() }

    /** The last event that is NOT the plain "button" mark = where the last press ended; null if there was no press. */
    fun verdict(list: List<Ev>): String? = list.lastOrNull { it.stage != BUTTON }?.stage ?: list.lastOrNull()?.stage

    fun reply(list: List<Ev>, en: Boolean): String {
        if (list.isEmpty()) return if (en)
            "NOVA has not received any watch button signal since it started. So the watch or the phone did not send it. In the watch or phone settings choose NOVA as the voice assistant, tap ALWAYS, then press the watch button again."
        else "Jab se NOVA chalu hua, watch button ka koi signal NOVA tak nahi aaya. Matlab watch ya phone ne bheja hi nahi. Watch ya phone settings me voice assistant NOVA chuno, ALWAYS dabao, phir watch ka button dobara dabao."
        val n = list.count { it.stage == BUTTON }
        return when (verdict(list)) {
            SERVICE_OFF -> if (en) "The button signal came ($n times) but NOVA was not running. Open NOVA and keep it on." else "Button signal aaya ($n baar) par NOVA chalu nahi tha. NOVA kholo aur chalu rakho."
            BUSY -> if (en) "The button signal came ($n times) but NOVA was busy, so it ignored it. Press again when NOVA is free." else "Button signal aaya ($n baar) par NOVA kaam me busy tha, isliye chhod diya. Free hone par dobara dabao."
            LOCKED -> if (en) "The button signal came ($n times) but the phone is locked, so NOVA ignored it. Unlock and try." else "Button signal aaya ($n baar) par phone locked hai, isliye NOVA ne chhod diya. Unlock karke try karo."
            MIC_PHONE -> if (en) "The button signal came ($n times) but the watch did not give its mic (Bluetooth call audio). NOVA listened on the phone mic. The watch must support call audio and be connected."
                         else "Button signal aaya ($n baar) par watch ne apna mic nahi diya (Bluetooth call audio). NOVA ne phone ke mic se suna. Watch me call audio support aur connection zaroori hai."
            MIC_WATCH -> if (en) "The button signal came ($n times) and the watch mic was found. If it still does not understand you, the watch mic quality is the next thing to check."
                         else "Button signal aaya ($n baar) aur watch ka mic mil gaya. Phir bhi samajh na aaye to watch ke mic ki awaaz agli cheez dekhne ki hai."
            else -> if (en) "The button signal came ($n times) but nothing after it was recorded." else "Button signal aaya ($n baar) par uske baad kuch record nahi hua."
        }
    }
}
