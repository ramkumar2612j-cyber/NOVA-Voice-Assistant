package com.nova.assistant

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Non-secret settings. The API key lives in SecureStore, never here. */
class Cfg(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("nova", Context.MODE_PRIVATE)

    var wake: String
        get() = Logic.wakeOrDefault(p.getString("wake", null))
        set(v) { p.edit().putString("wake", v).apply() }

    var lang: String
        get() = if (p.getString("lang", "hi") == "en") "en" else "hi"
        set(v) { p.edit().putString("lang", v).apply() }

    var rate: Float
        get() = Logic.clampRate(p.getFloat("rate", 0.95f))
        set(v) { p.edit().putFloat("rate", v).apply() }

    var pitch: Float
        get() = Logic.clampPitch(p.getFloat("pitch", 0.9f))
        set(v) { p.edit().putFloat("pitch", v).apply() }

    var model: String
        get() = Logic.cleanModel(p.getString("model", null))
        set(v) { p.edit().putString("model", v).apply() }

    /** Permanently OFF: NOVA never starts or reminds by itself after a restart. Kept only so old saved settings still load. */
    var autostart: Boolean
        get() = false
        set(v) { p.edit().putBoolean("autostart", v).apply() }

    /** True while the user has NOVA switched ON (set by ACTIVATE, cleared by STOP). */
    var active: Boolean
        get() = p.getBoolean("active", false)
        set(v) { p.edit().putBoolean("active", v).apply() }

    /** v6.52: default ON (owner request) for LOCAL screen monitoring of error/warning text. It still needs the Accessibility permission. Never uploads anything. */
    var screenMonitor: Boolean
        get() = p.getBoolean("screenMonitor", false)   // v7.4: default OFF - NOVA must not pop up and ask by itself; the owner turns it on in Settings
        set(v) { p.edit().putBoolean("screenMonitor", v).apply() }

    /** Default ON: command replies go to the chat / floating card silently. NOVA speaks only for alerts, confirmations and driving. */
    var quietReplies: Boolean
        get() = p.getBoolean("quietReplies", false)
        set(v) { p.edit().putBoolean("quietReplies", v).apply() }

    /** Part 3: persistent SILENT switch. Unlike quietReplies, this is a separate setting. */
    var silentMode: Boolean
        get() = p.getBoolean("silentMode", false)
        set(v) { p.edit().putBoolean("silentMode", v).apply() }

    /** v7.120: movie-style "Ultron" voice effect on NOVA's own spoken replies. v7.124d: ON by default (new key, so old phones that saved OFF also get it); the ULTRON button can still turn it off. */
    var ultronVoice: Boolean
        get() = p.getBoolean("ultronVoiceV2", true)
        set(v) { p.edit().putBoolean("ultronVoiceV2", v).apply() }

    /** v7.136: JARVIS voice (online, your own server). Works only when a server address is saved; any failure falls back to the voices below. The address is not a secret (the token is, in SecureStore). */
    /** v7.138: Jarvis-style voice from the phone's own British male voice (no server). Used when the server voice is off / fails and the reply is English. */
    var jarvisLocal: Boolean
        get() = p.getBoolean("jarvisLocal", true)
        set(v) { p.edit().putBoolean("jarvisLocal", v).apply() }

    var jarvisOn: Boolean
        get() = p.getBoolean("jarvisOn", true)
        set(v) { p.edit().putBoolean("jarvisOn", v).apply() }
    var jarvisUrl: String
        get() = p.getString("jarvisUrl", "") ?: ""
        set(v) { p.edit().putString("jarvisUrl", JarvisVoiceApi.cleanBase(v)).apply() }
    /** The model is English-only: by default Jarvis speaks only when NOVA's language is English. ON = also Hinglish / Hindi replies written in plain A-Z letters. */
    var jarvisAnyLang: Boolean
        get() = p.getBoolean("jarvisAnyLang", false)
        set(v) { p.edit().putBoolean("jarvisAnyLang", v).apply() }
    /** Longer replies use the existing voice (the model card says long synthesis can develop noise). */
    var jarvisMaxChars: Int
        get() = JarvisVoiceApi.clampMaxChars(p.getInt("jarvisMaxChars", JarvisVoiceApi.DEFAULT_MAX_CHARS))
        set(v) { p.edit().putInt("jarvisMaxChars", JarvisVoiceApi.clampMaxChars(v)).apply() }

    /** v7.120: how strong the Ultron effect is (0.2 .. 1.0). */
    var ultronAmount: Float
        get() = UltronFx.clampAmount(p.getFloat("ultronAmount", UltronFx.DEFAULT_AMOUNT))
        set(v) { p.edit().putFloat("ultronAmount", UltronFx.clampAmount(v)).apply() }

    /** Default OFF: NOVA never opens the mic by itself to ask "shall I remember this shortcut?" after a command. */
    var nudges: Boolean
        get() = p.getBoolean("nudges", false)
        set(v) { p.edit().putBoolean("nudges", v).apply() }

    /** The update feed the USER chose (https only). Empty = no update checks at all. */
    var feedUrl: String
        get() = p.getString("feedUrl", "") ?: ""
        set(v) { p.edit().putString("feedUrl", v).apply() }

    fun locale(): Locale = if (lang == "en") Locale("en", "IN") else Locale("hi", "IN")

    /** v7.138: the voice follows the language of the reply being spoken (ReplyLang); before the first reply it follows the app language. */
    fun voiceLocale(): Locale = when (ReplyLang.voiceLang) { "en" -> Locale("en", "IN"); "hi" -> Locale("hi", "IN"); else -> locale() }

    fun applyVoice(t: TextToSpeech?) {
        if (t == null) return
        val r = t.setLanguage(voiceLocale())
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            t.setLanguage(Locale.US)
        }
        pickBestVoice(t)
        t.setSpeechRate(rate)
        t.setPitch(pitch)
    }

    /** v16: of the voices already on the phone for this language, prefer the highest quality one that works offline. */
    private fun pickBestVoice(t: TextToSpeech) {
        try {
            val want = ReplyLang.voiceLang ?: (if (lang == "en") "en" else "hi")
            var best: android.speech.tts.Voice? = null
            for (v in t.voices ?: return) {
                if (v.locale.language != want) continue
                if (v.isNetworkConnectionRequired) continue
                val b = best
                if (b == null || v.quality > b.quality) best = v
            }
            val pick = best ?: return
            val cur = t.voice
            if (cur == null || cur.name != pick.name) t.setVoice(pick)
        } catch (e: Exception) { ErrLog.add("Cfg", e.toString()) }
    }
}
