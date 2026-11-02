package com.nova.assistant

/**
 * v6.74 (Part 5): the REAL reason why wake-word listening or spoken replies do not work, as one short sentence.
 *
 * Before this file the reason was lost in three places: a refused foreground service start only called stopSelf() (no message),
 * a TextToSpeech init error was ignored (the row just said "not ready"), and the loop's fatal() text was shown once in the chat and
 * forgotten. NovaService now stores the facts (start failure, TTS init code, last fatal text) and FeatureStatus / ConfigReport
 * turn them into text with the functions below.
 *
 * Pure Kotlin (no Android classes), so it is unit tested. It describes the state; it never claims a feature "works".
 */
object VoiceDiagnosis {
    /** TextToSpeech.SUCCESS is 0 and TextToSpeech.ERROR is -1; this value means "init callback not called yet". */
    const val TTS_PENDING = Int.MIN_VALUE

    enum class StartFailure { NONE, MIC_PERMISSION, BACKGROUND_NOT_ALLOWED, OTHER }

    /** One status line: state is "ok" / "warn" / "off" (same words as FeatureStatus), fix is a FeatureStatus fix key or "". */
    class Row(val state: String, val why: String, val fix: String)

    /**
     * Why did startForeground(...) throw? [exceptionName] is the exception's simple or full class name, [message] its text.
     * Android 12+ throws ForegroundServiceStartNotAllowedException when a microphone service is started from the background;
     * Android 14+ throws SecurityException when RECORD_AUDIO is not granted for a microphone-type service.
     */
    fun classifyStartFailure(exceptionName: String, message: String?, micGranted: Boolean): StartFailure {
        val name = exceptionName
        val msg = (message ?: "").lowercase()
        return when {
            !micGranted -> StartFailure.MIC_PERMISSION
            name.contains("ForegroundServiceStartNotAllowed") -> StartFailure.BACKGROUND_NOT_ALLOWED
            msg.contains("record_audio") -> StartFailure.MIC_PERMISSION
            msg.contains("while-in-use") || msg.contains("background") -> StartFailure.BACKGROUND_NOT_ALLOWED
            else -> StartFailure.OTHER
        }
    }

    fun startFailureText(f: StartFailure, en: Boolean): String = when (f) {
        StartFailure.NONE -> ""
        StartFailure.MIC_PERMISSION ->
            if (en) "NOVA could not start: the microphone permission is not granted. Allow the microphone, then press ACTIVATE."
            else "NOVA start nahi hua: microphone permission nahi mili. Mic allow karo, phir ACTIVATE dabao."
        StartFailure.BACKGROUND_NOT_ALLOWED ->
            if (en) "Android refused to start NOVA's microphone service in the background. Open the NOVA app and press ACTIVATE while it is on the screen."
            else "Android ne background se NOVA ki mic service start karne se mana kar diya. NOVA app kholo aur screen par rehte hue ACTIVATE dabao."
        StartFailure.OTHER ->
            if (en) "NOVA could not start its foreground service. Open the app and press ACTIVATE again; if it repeats, restart the phone."
            else "NOVA ki foreground service start nahi hui. App kholo aur dobara ACTIVATE dabao; baar baar ho to phone restart karo."
    }

    /**
     * The "wake word listening" row. [fatalText] is the last text of NovaService.fatal() (mic busy, model missing, ...) and is shown only
     * while the service is not running, because a running service has obviously recovered.
     */
    fun wake(mic: Boolean, running: Boolean, startFailure: StartFailure, fatalText: String, en: Boolean): Row = when {
        !mic -> Row("off", if (en) "Microphone permission is not granted" else "Microphone permission nahi hai", "mic")
        running -> Row("ok", if (en) "Service is running and the microphone is listening" else "Service chal rahi hai aur mic sun raha hai", "")
        startFailure != StartFailure.NONE -> Row("off", startFailureText(startFailure, en), "activate")
        fatalText.isNotBlank() -> Row("off", fatalText.take(200), "activate")
        else -> Row("off", if (en) "NOVA is not active (press ACTIVATE below)" else "NOVA abhi ACTIVATE nahi hai (neeche ACTIVATE dabao)", "activate")
    }

    /**
     * The "spoken replies" row. [initCode] is the status given to the TextToSpeech init callback (0 = success, -1 = error) or [TTS_PENDING].
     * [hindiVoice] is "ok", "missing" or "unknown" (from TextToSpeech.isLanguageAvailable).
     */
    fun tts(running: Boolean, ready: Boolean, initCode: Int, hindiVoice: String, en: Boolean): Row = when {
        !running -> Row("off", if (en) "NOVA is off, so it cannot speak" else "NOVA band hai, isliye bol nahi sakta", "activate")
        initCode != TTS_PENDING && initCode != 0 -> Row("off",
            if (en) "The phone's text-to-speech engine did not start (code $initCode). In Android Settings > Text-to-speech choose an engine and install a voice."
            else "Phone ka text-to-speech engine start nahi hua (code $initCode). Android Settings > Text-to-speech me engine chuno aur voice install karo.", "tts")
        !ready -> Row("warn", if (en) "Text-to-speech is still starting" else "Text-to-speech abhi shuru ho raha hai", "")
        hindiVoice == "missing" -> Row("warn",
            if (en) "No Hindi voice on the phone (Android Settings > Text-to-speech > install Hindi voice data). English replies still work."
            else "Phone mein Hindi voice nahi hai (Android Settings > Text-to-speech mein Hindi voice daalo). English jawab chalenge.", "")
        else -> Row("ok", if (en) "Text-to-speech is ready" else "Text-to-speech taiyaar hai", "")
    }

    private val AGGRESSIVE_MAKERS = setOf("xiaomi", "redmi", "poco", "oppo", "realme", "oneplus", "vivo", "iqoo", "huawei", "honor", "samsung", "tecno", "infinix", "itel")

    /** Battery / background row. [maker] is Build.MANUFACTURER (any case). */
    fun battery(ignoring: Boolean, maker: String, en: Boolean): Row {
        if (ignoring) return Row("ok", if (en) "Battery: no restrictions" else "Battery: No restrictions", "")
        val m = maker.trim().lowercase()
        val aggressive = m in AGGRESSIVE_MAKERS
        val extra = when {
            !aggressive -> ""
            m == "xiaomi" || m == "redmi" || m == "poco" ->
                if (en) " On Xiaomi also turn Autostart ON for NOVA." else " Xiaomi par NOVA ke liye Autostart bhi ON karo."
            else -> if (en) " This phone brand often stops background apps; also allow auto-start / background activity for NOVA."
                    else " Is brand ka phone background apps band kar deta hai; NOVA ke liye auto-start / background activity bhi allow karo."
        }
        return Row("warn",
            (if (en) "Battery restriction is on: Android may stop NOVA's microphone after a while." else "Battery restriction laga hai: Android kuch der baad NOVA ka mic band kar sakta hai.") + extra,
            "battery")
    }
}
