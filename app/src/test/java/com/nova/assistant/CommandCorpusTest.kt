package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v6.75 (Part 5): a corpus of spoken commands (English / Hindi / Hinglish: apps, torch, volume, brightness, media, navigation, settings,
 * alarm / timer, call, screen control, things Android does not allow, negations) run through Understand.classify (= what NovaService uses
 * before it asks a cloud AI). n(...) = the local pipeline must give NO answer (the sentence goes to the AI).
 *
 * The expected values were produced by tools/classify_mirror.py (a Python port) and then reviewed by hand; tools/test_command_corpus_contract.py
 * re-checks this file against the port. The REAL proof is this JUnit class run by Gradle / GitHub Actions.
 * arg is compared only when given; num only when given (alarm / timer labels are not compared).
 */
class CommandCorpusTest {
    private class Case(val say: String, val kind: String?, val arg: String?, val num: Int?)
    private fun c(say: String, kind: String, arg: String? = null, num: Int? = null) = Case(say, kind, arg, num)
    private fun n(say: String) = Case(say, null, null, null)

    private val corpus: List<Case> = listOf(
        c("open whatsapp", "open_app", "whatsapp"),
        c("whatsapp kholo", "open_app", "whatsapp"),
        c("instagram open karo", "open_app", "instagram"),
        c("chrome khol do", "open_app", "chrome"),
        c("chrome ko kholo", "open_app", "chrome"),
        c("youtube kholo", "open_app", "youtube"),
        c("open youtube", "open_app", "youtube"),
        c("launch spotify", "open_app", "spotify"),
        c("gmail khol do", "open_app", "gmail"),
        c("maps kholo", "open_app", "maps"),
        c("facebook open karo", "open_app", "facebook"),
        c("open telegram app", "open_app", "telegram"),
        c("snapchat kholo", "open_app", "snapchat"),
        c("phonepe khol do", "open_app", "phonepe"),
        c("open camera", "camera"),
        c("camera kholo", "camera"),
        c("mera camera kholo", "camera"),
        n("front camera se video kholo"),
        n("is store open"),
        c("open settings", "settings", "main"),
        c("settings kholo", "settings", "main"),
        c("wifi settings kholo", "settings", "wifi"),
        c("bluetooth settings", "settings", "bluetooth"),
        c("display settings", "settings", "display"),
        c("sound settings kholo", "settings", "sound"),
        c("battery settings", "settings", "battery"),
        c("app settings", "settings", "apps"),
        c("location settings", "settings", "location"),
        c("do not disturb", "settings", "dnd"),
        c("torch on", "torch_on"),
        c("torch chalu karo", "torch_on"),
        c("torch jalao", "torch_on"),
        c("torch band karo", "torch_off"),
        c("torch off", "torch_off"),
        c("flashlight on", "torch_on"),
        c("flashlight band karo", "torch_off"),
        c("torch bujhao", "torch_off"),
        c("torch jala do", "torch_on"),
        c("mera torch chalu kar do", "torch_on"),
        n("torch on mat karo"),
        n("torch band mat karo"),
        c("volume up", "volume", "up"),
        c("volume badhao", "volume", "up"),
        c("volume kam karo", "volume", "down"),
        c("volume down", "volume", "down"),
        c("volume full", "volume", "max"),
        c("volume max karo", "volume", "max"),
        c("volume mute", "volume", "mute"),
        c("awaaz kam karo", "volume", "down"),
        c("awaaz badhao", "volume", "up"),
        c("volume 50", "volume", "set", num = 50),
        c("volume fifty", "volume", "set", num = 50),
        c("awaaz zyada karo", "volume", "up"),
        c("volume thoda kam karo", "volume", "down"),
        n("volume kam mat karo"),
        n("volume badhao mat"),
        n("volume mat badhao"),
        c("awaaz band karo", "volume", "mute"),
        c("brightness up", "brightness_step", num = 10),
        c("brightness badhao", "brightness_step", num = 10),
        c("brightness kam karo", "brightness_step", num = -10),
        c("brightness max", "brightness_set", num = 100),
        c("brightness full", "brightness_set", num = 100),
        c("brightness 70", "brightness_set", num = 70),
        c("brightness minimum", "brightness_set", num = 5),
        c("roshni badhao", "brightness_step", num = 10),
        n("brightness kam mat karo"),
        c("brightness dim", "brightness_step", num = -10),
        c("pause", "media", "pause"),
        c("pause music", "media", "pause"),
        c("play music", "media", "play"),
        c("resume", "media", "play"),
        c("next song", "media", "next"),
        c("next", "media", "next"),
        c("agla gaana", "media", "next"),
        c("previous song", "media", "previous"),
        c("pichla gaana", "media", "previous"),
        c("gaana band karo", "media", "pause"),
        c("gaana chalao", "media", "play"),
        c("music pause karo", "media", "pause"),
        c("song stop", "media", "pause"),
        n("gaana bajao tum hi ho"),
        n("play kesariya"),
        n("play shape of you"),
        c("home", "global", "home"),
        c("go home", "global", "home"),
        c("home jao", "global", "home"),
        c("back", "global", "back"),
        c("go back", "global", "back"),
        c("wapas jao", "global", "back"),
        c("recents", "global", "recents"),
        c("recent apps", "global", "recents"),
        c("notifications", "global", "notifications"),
        c("notification panel kholo", "global", "notifications"),
        c("quick settings", "global", "quick_settings"),
        c("lock phone", "global", "lock"),
        c("phone lock karo", "global", "lock"),
        c("screen lock", "global", "lock"),
        c("mobile lock kar do", "global", "lock"),
        c("mere mobile ko turant lock kar do", "global", "lock"),
        n("lock mat karo"),
        c("power off", "unsupported", "power"),
        c("phone restart karo", "unsupported", "power"),
        c("shutdown", "unsupported", "power"),
        c("reboot phone", "unsupported", "power"),
        c("factory reset", "unsupported", "reset"),
        c("wipe phone", "unsupported", "reset"),
        c("cancel alarm", "unsupported", "cancel_alarm"),
        c("timer cancel karo", "unsupported", "cancel_timer"),
        c("alarm cancel karo", "unsupported", "cancel_alarm"),
        c("cancel timer", "unsupported", "cancel_timer"),
        c("alarm band karo", "wake_off"),
        c("unlock phone", "unlock"),
        c("wifi on", "settings", "wifi", num = 2),
        c("wifi band karo", "settings", "wifi", num = 2),
        c("bluetooth on", "settings", "bluetooth", num = 2),
        c("bluetooth chalu", "settings", "bluetooth", num = 2),
        c("hotspot on", "settings", "network", num = 2),
        c("hotspot chalu karo", "settings", "network", num = 2),
        c("mobile data off", "settings", "network", num = 2),
        c("mobile data band karo", "settings", "network", num = 2),
        c("airplane mode on", "settings", "airplane", num = 2),
        c("flight mode on", "settings", "airplane", num = 2),
        c("location on", "settings", "location", num = 2),
        c("location band karo", "settings", "location", num = 2),
        c("dnd on", "settings", "dnd", num = 2),
        c("do not disturb on", "settings", "dnd", num = 2),
        c("time kya hai", "time"),
        c("what time is it", "time"),
        c("kitne baje hain", "time"),
        c("date kya hai", "date"),
        c("aaj ki date", "date"),
        c("tarikh batao", "date"),
        c("battery kitni hai", "battery"),
        c("battery status", "battery"),
        c("charging check karo", "battery"),
        c("samay batao", "time"),
        c("set alarm for 7 am", "alarm", num = 420),
        c("7 baje ka alarm lagao", "alarm_any", num = 420),
        c("5 minute ka timer lagao", "timer", num = 300),
        c("timer 10 minutes", "timer", num = 600),
        c("set timer for 5 minutes", "timer", num = 300),
        c("alarm 6 30 am", "alarm", num = 390),
        c("do minute ka timer", "timer", num = 120),
        c("call mom", "call", "mom"),
        c("mummy ko call karo", "call", "mummy"),
        c("papa ko call lagao", "call", "papa"),
        c("call rahul", "call", "rahul"),
        c("rahul call karo", "call", "rahul"),
        n("message rahul ko hi bhejo"),
        n("whatsapp message bhejo"),
        c("scroll down", "scroll", "down"),
        c("scroll up", "scroll", "up"),
        c("neeche scroll karo", "scroll", "down"),
        c("upar scroll karo", "scroll", "up"),
        c("tap login", "tap", "login"),
        c("tap on search", "tap", "search"),
        c("login dabao", "tap", "login"),
        c("type hello", "type", "hello"),
        c("likho namaste", "type", "namaste"),
        c("screen dekho", "analyze"),
        c("screen padho", "analyze"),
        c("what is on screen", "analyze"),
        c("driving mode on", "drive_on"),
        c("driving mode off", "drive_off"),
        c("message mode on", "message_on"),
        c("message mode off", "message_off"),
        c("yaad rakh mera naam rahul hai", "mem_save"),
        c("kya yaad hai", "mem_list"),
        c("quiet mode on", "quiet_on"),
        c("voice replies on", "quiet_off"),
        c("stop", "stop"),
        c("chup", "stop"),
        c("cancel", "stop"),
        c("nova torch on", "torch_on"),
        c("hey nova volume badhao", "volume", "up"),
        c("please volume up", "volume", "up"),
        c("nova mere phone ko lock kar do", "global", "lock"),
        n("kal subah 6 baje uthana"),
        c("aaj ka schedule", "calendar_today"),
        c("check updates", "update_check"),
    )

    @Test fun corpusHasAtLeast150Phrases() {
        assertTrue("corpus size " + corpus.size, corpus.size >= 150)
        assertEquals("duplicate phrases in corpus", corpus.size, corpus.map { it.say }.toSet().size)
    }

    @Test fun everyPhraseGivesTheExpectedLocalCommand() {
        val bad = ArrayList<String>()
        for (k in corpus) {
            val got = Understand.classify(k.say)
            if (k.kind == null) {
                if (got != null) bad.add(k.say + " -> expected none, got " + got)
                continue
            }
            if (got == null) { bad.add(k.say + " -> expected " + k.kind + ", got none"); continue }
            if (got.kind != k.kind) bad.add(k.say + " -> expected kind " + k.kind + ", got " + got)
            else if (k.arg != null && got.arg != k.arg) bad.add(k.say + " -> expected arg " + k.arg + ", got " + got)
            else if (k.num != null && got.num != k.num) bad.add(k.say + " -> expected num " + k.num + ", got " + got)
        }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    // ---- the 4 Part 5 bugs, each on its own so a failure names the bug
    @Test fun negationNeverRunsThePositiveCommand() {
        for (s in listOf("volume kam mat karo", "volume badhao mat", "torch on mat karo", "torch band mat karo", "brightness kam mat karo", "lock mat karo", "awaaz mat badhao")) {
            assertNull(s, Understand.classify(s))
        }
        // the words are only a guard inside those blocks: do-not-disturb and the plain commands still work
        assertEquals("dnd", Understand.classify("do not disturb on")!!.arg)
        assertEquals("down", Understand.classify("volume kam karo")!!.arg)
    }

    @Test fun openAppWithKholDoAndOpenKaro() {
        assertEquals(Logic.Cmd("open_app", "instagram"), Understand.classify("instagram open karo"))
        assertEquals(Logic.Cmd("open_app", "chrome"), Understand.classify("chrome khol do"))
        assertEquals(Logic.Cmd("open_app", "chrome"), Understand.classify("chrome ko kholo"))
        assertNull("a bare trailing open is not a command", Understand.classify("is store open"))
    }

    @Test fun namedSongIsNotAPlainPlayCommand() {
        assertNull(Understand.simplify("gaana bajao tum hi ho"))
        assertNull(Understand.classify("gaana bajao tum hi ho"))
        assertEquals("play music", Understand.simplify("gaana bajao"))
        assertEquals("pause music", Understand.simplify("gaana band karo"))
    }

    @Test fun cancelAlarmAndTimerGetAnHonestAnswer() {
        for (s in listOf("cancel alarm", "alarm cancel karo")) {
            assertEquals(Logic.Cmd("unsupported", "cancel_alarm"), Understand.classify(s))
        }
        for (s in listOf("timer cancel karo", "cancel timer")) {
            assertEquals(Logic.Cmd("unsupported", "cancel_timer"), Understand.classify(s))
        }
        // the wake-up alarm switch is a different feature and must keep working
        assertEquals("wake_off", Understand.classify("alarm band karo")!!.kind)
        for (id in listOf(UnsupportedRules.POWER, UnsupportedRules.RESET, UnsupportedRules.CANCEL_ALARM, UnsupportedRules.CANCEL_TIMER)) {
            assertTrue(id, UnsupportedRules.reply(id, true).isNotBlank() && UnsupportedRules.reply(id, false).isNotBlank())
        }
    }

    // ---- safety: nothing in the corpus is allowed to produce a harmful local action
    @Test fun unsupportedThingsNeverBecomeAnAction() {
        for (s in listOf("power off", "shutdown", "reboot phone", "factory reset", "wipe phone", "phone restart karo")) {
            assertEquals(s, "unsupported", Understand.classify(s)!!.kind)
        }
        assertEquals("unlock", Understand.classify("unlock phone")!!.kind)   // the stub that says unlocking was removed
    }
}
