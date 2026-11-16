import com.nova.assistant.CallAgentRules as R
var f = 0
fun ck(c: Boolean, l: String) { println((if (c) "PASS " else "FAIL ") + l); if (!c) f++ }
fun main() {
    ck(R.isGoodbye("ok bye bye"), "goodbye bye")
    ck(R.isGoodbye("thanks"), "short thanks ends")
    ck(!R.isGoodbye("haan 8 baje ki table mil jayegi shukriya aap aa jaiye"), "long sentence with shukriya does not end")
    ck(R.isEcho("namaste main jarvis assistant", "Namaste, main JARVIS assistant se bol raha hoon"), "echo of own opener")
    ck(!R.isEcho("7 baje full hai", "Namaste main JARVIS assistant se bol raha hoon"), "real answer is not echo")
    ck(R.farSideSensitive("pehle advance payment karna hoga"), "payment from far side is sensitive")
    ck(!R.farSideSensitive("7 baje full hai 8 baje milegi"), "plain answer not sensitive")
    ck(R.cleanReply("Theek hai, 8 baje chalega.\n", false) == "Theek hai, 8 baje chalega.", "clean ok reply")
    ck(R.cleanReply("**Aap UPI se pay kar do**", false) == R.safeLine(false), "deny word in reply -> safe line")
    ck(R.cleanReply("mera number 9876543210 hai", false) == "", "long number never spoken")
    ck(R.cleanReply(null, false) == "" && R.cleanReply("  ", true) == "", "null/blank reply")
    ck(R.cleanReply("a".repeat(400) + ".", false).length <= R.MAX_REPLY, "reply capped")
    ck(R.cleanReply("YOU: haan theek hai", false) == "haan theek hai", "YOU: prefix stripped")
    val t = listOf(R.Turn(false, "Namaste"), R.Turn(true, "7 baje full hai"), R.Turn(true, "8 baje milegi"))
    ck(R.fallbackSummary(t, false).contains("8 baje milegi"), "fallback summary uses far side")
    ck(R.convUser(t, false).contains("THEY: 7 baje full hai"), "convUser transcript")
    ck(R.convSystem("Sheetal", R.goalText("restaurant", "do log 8 baje", "", false), false).contains("NEVER give, ask for or agree to payment"), "system prompt has money rule")
    ck(R.goalText("", "", "main late aaunga", false).contains("main late aaunga"), "custom goal")
    ck(R.talkReport("Rahul", "98", 40, "8 baje milegi", true, false, false, false).contains("8 baje milegi"), "report has summary")
    ck(R.talkReport("Rahul", "98", 40, "", false, true, false, false).contains("mic band"), "report honest when mic blocked")
    ck(R.talkReport("Rahul", "98", 40, "", false, false, false, true).contains("Call back needed"), "report no speech")
    ck(R.talkReport("Rahul", "98", 40, "x", true, false, true, false).contains("paise"), "report notes sensitive stop")
    ck(R.talkEnabled(true) && !R.talkEnabled(false), "talk needs a hearing key")
    ck(R.cleanSummary("a\nb  c").let { it == "a b c" }, "cleanSummary")
    // old behaviour intact
    ck(R.denied("bank manager ko call karo"), "old deny intact")
    ck(R.script("restaurant", "do log", false).contains("JARVIS assistant"), "old script intact")
    println(if (f == 0) "ALL KT RULE CHECKS PASS" else "FAILS: $f")
    if (f != 0) System.exit(1)
}
