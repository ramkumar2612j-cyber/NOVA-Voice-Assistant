package com.nova.assistant

/**
 * v7.197 SELF PATCH rules (PURE Kotlin, no Android / no network - so they are really compiled and run in tools/test_self_patch_contract.py).
 * Owner decision (2026-11-16): JARVIS may write new Kotlin code with AI and push it straight to main WITHOUT asking each time.
 * The safety net that replaces asking: (1) path + content guards below, (2) GitHub Actions builds the push, (3) if that build
 * goes RED, GitHubSelfPatch automatically restores the previous file. HARD DENY (payment / order / password / OTP / PIN / CVV)
 * is never writable by this feature: wishes about it are refused, and the files that hold secrets, the installer, GitHub
 * access and this feature itself are protected.
 */
object SelfPatchRules {
    const val IDLE = "idle"
    const val WRITING = "writing"
    const val PUSHED = "pushed"
    const val OK = "ok"
    const val REVERTED = "reverted"
    const val UNDONE = "undone"
    const val ERROR = "error"

    const val DIR = "app/src/main/java/com/nova/assistant/"
    const val MAX_PER_DAY = 5
    const val MAX_EDIT_BYTES = 40000
    const val MAX_NEW_BYTES = 60000
    const val MIN_CODE = 120

    private val TRIGGER = listOf("khud code likho", "khud code likh", "code likh ke push", "khud likh ke daalo", "khud likh ke daal do", "self code likho", "code khud likho")
    private val STATUS = listOf("code status", "patch status", "code kaha tak")
    private val UNDO = listOf("code wapas karo", "patch wapas karo", "code undo", "patch undo")

    /** Files this feature must never touch: secrets, installer, GitHub access, payment / block rules, the feature itself. */
    val PROTECTED = setOf(
        "SecureStore", "UpdateManager", "GitHubSelfPatch", "SelfPatchRules", "GitHubSelfBuild", "GitHubUpgradePublish",
        "GitHubPublish", "UpgradeLedger", "BlockedRules", "PayKit", "PayRules", "Cfg", "Logic"
    )

    /** Words that make a wish a no-go (HARD DENY + things that would let code reach secrets or the build itself). */
    private val DENY_WISH = listOf(
        "payment", "pay karo", "paise bhej", "paisa bhej", "paise transfer", "upi pin", " pin ", "otp", "password", "cvv", "card number",
        "order de", "keystore", "signing key", "secret", "github token", "build.yml", "workflow"
    )

    fun isTrigger(norm: String): Boolean = TRIGGER.any { norm.contains(it) }
    fun isStatus(norm: String): Boolean = STATUS.any { norm.contains(it) }
    fun isUndo(norm: String): Boolean = UNDO.any { norm.contains(it) }

    /** The wish after the trigger phrase, cut from the ORIGINAL text (keeps capitals of file names). Null if too short. */
    fun parseWish(norm: String, raw: String): String? {
        val low = raw.lowercase()
        val t = TRIGGER.firstOrNull { low.contains(it) }
        val rest = if (t != null) raw.substring(low.indexOf(t) + t.length).trim().trimStart(':', '-', ',', ' ').trim() else {
            val nt = TRIGGER.firstOrNull { norm.contains(it) } ?: return null
            norm.substringAfter(nt).trim()
        }
        return if (rest.length in 6..600) rest else null
    }

    fun wishBlocked(wish: String): Boolean {
        val w = " " + wish.lowercase() + " "
        return DENY_WISH.any { w.contains(it) }
    }

    private val FILE_RE = Regex("\\b([A-Z][A-Za-z0-9_]{1,40})\\.kt\\b")

    /** "HelpGuide.kt me ..." -> "HelpGuide" (file to EDIT), else null (AI makes a NEW file). */
    fun targetName(wish: String): String? = FILE_RE.find(wish)?.groupValues?.get(1)

    fun pathFor(name: String): String = DIR + name + ".kt"

    private val PATH_RE = Regex("^" + Regex.escape(DIR) + "([A-Z][A-Za-z0-9_]{1,60})\\.kt$")

    fun isAllowedPath(path: String): Boolean {
        val m = PATH_RE.find(path) ?: return false
        return m.groupValues[1] !in PROTECTED
    }

    /** Code that must never be pushed by this feature (reaching secrets, installer, shell, dynamic code, GitHub itself). */
    private val FORBIDDEN_CODE = listOf(
        "SecureStore", "ProcessBuilder", "Runtime.getRuntime", "DexClassLoader", "PathClassLoader", "PackageInstaller",
        "api.github.com", "System.exit", "UpdateManager", "GitHubSelf", "PayKit"
    )

    fun forbiddenIn(code: String): String? = FORBIDDEN_CODE.firstOrNull { code.contains(it) }

    /** Cheap bracket balance that skips strings, char literals and comments. False if braces / parens / brackets do not match. */
    fun balanced(code: String): Boolean {
        val st = ArrayList<Char>()
        var i = 0
        val n = code.length
        while (i < n) {
            val c = code[i]
            val nx = if (i + 1 < n) code[i + 1] else ' '
            if (c == '/' && nx == '/') { while (i < n && code[i] != '\n') i++; continue }
            if (c == '/' && nx == '*') {
                var depth = 1; i += 2
                while (i < n && depth > 0) {
                    if (code[i] == '/' && i + 1 < n && code[i + 1] == '*') { depth++; i += 2 }
                    else if (code[i] == '*' && i + 1 < n && code[i + 1] == '/') { depth--; i += 2 }
                    else i++
                }
                continue
            }
            if (c == '"' && code.startsWith("\"\"\"", i)) {
                i += 3
                while (i < n && !code.startsWith("\"\"\"", i)) i++
                i += 3
                continue
            }
            if (c == '"') {
                i++
                while (i < n && code[i] != '"') { if (code[i] == '\\') i++; i++ }
                i++
                continue
            }
            if (c == '\'') {
                i++
                while (i < n && code[i] != '\'') { if (code[i] == '\\') i++; i++ }
                i++
                continue
            }
            when (c) {
                '{', '(', '[' -> st.add(c)
                '}' -> { if (st.isEmpty() || st.removeAt(st.size - 1) != '{') return false }
                ')' -> { if (st.isEmpty() || st.removeAt(st.size - 1) != '(') return false }
                ']' -> { if (st.isEmpty() || st.removeAt(st.size - 1) != '[') return false }
            }
            i++
        }
        return st.isEmpty()
    }

    class Patch(val path: String, val summary: String, val code: String)

    /**
     * Reply format asked from the AI (no JSON, so no escaping problems):
     *   PATH: app/src/main/java/com/nova/assistant/Name.kt
     *   SUMMARY: one line
     *   <<<CODE
     *   ...whole file...
     *   CODE>>>
     */
    fun parseReply(text: String?): Patch? {
        if (text.isNullOrBlank()) return null
        val a = text.indexOf("<<<CODE")
        val b = text.lastIndexOf("CODE>>>")
        if (a < 0 || b <= a) return null
        val head = text.substring(0, a)
        val path = Regex("PATH:\\s*(\\S+)").find(head)?.groupValues?.get(1)?.trim('`', '"', '\'') ?: return null
        val summary = Regex("SUMMARY:\\s*(.+)").find(head)?.groupValues?.get(1)?.trim() ?: "naya code"
        var code = text.substring(a + "<<<CODE".length, b).trim('\r', '\n')
        if (code.trimStart().startsWith("```")) code = code.substringAfter("\n")
        if (code.trimEnd().endsWith("```")) code = code.trimEnd().removeSuffix("```")
        code = code.trim('\r', '\n') + "\n"
        return Patch(path, summary.take(120), code)
    }

    /**
     * Null = OK to push. [expectedPath] is set when the owner named a file; [oldLen] > 0 when an existing file is replaced
     * (a reply that is far shorter than the original is treated as a broken / truncated answer, not as an edit).
     */
    fun validate(p: Patch, expectedPath: String?, oldLen: Int): String? {
        if (!isAllowedPath(p.path)) return "ye file path allowed nahi hai (" + p.path.takeLast(40) + ")"
        if (expectedPath != null && p.path != expectedPath) return "AI ne alag file ka naam diya"
        val bytes = p.code.toByteArray(Charsets.UTF_8).size
        if (p.code.length < MIN_CODE) return "code bahut chhota / adhoora hai"
        if (bytes > MAX_NEW_BYTES) return "code bahut bada hai"
        if (!p.code.trimStart().startsWith("package com.nova.assistant")) return "code 'package com.nova.assistant' se shuru nahi hua"
        if (!balanced(p.code)) return "code me bracket / quote theek band nahi hain"
        forbiddenIn(p.code)?.let { return "code me mana kiya hua cheez hai: " + it }
        if (oldLen > 0 && p.code.length < oldLen / 3) return "naya code purane se bahut chhota hai (adhoora jawab lagta hai)"
        return null
    }

    fun underCap(countToday: Int): Boolean = countToday < MAX_PER_DAY

    fun dayKey(ms: Long): Long = ms / 86_400_000L

    fun commitMessage(summary: String): String = "JARVIS self-code: " + summary.replace("\n", " ").take(90)

    /** What a reverted NEW file is overwritten with (keeps the build green without needing a DELETE call). */
    const val STUB = "package com.nova.assistant\n\n// JARVIS self-code: wapas liya gaya (build RED tha ya owner ne undo kiya). Ye file ab khali hai.\n"

    fun phaseLabel(p: String): String = when (p) {
        IDLE -> "koi self-code kaam nahi chal raha"
        WRITING -> "AI code likh raha hai"
        PUSHED -> "code main par daal diya, build chal raha hai"
        OK -> "build GREEN, code rakh liya"
        REVERTED -> "build RED tha, code khud wapas le liya"
        UNDONE -> "code wapas le liya gaya"
        ERROR -> "error"
        else -> p
    }

    fun systemPrompt(): String =
        "You are a senior Kotlin/Android engineer working on the JARVIS app (package com.nova.assistant, Kotlin 1.9, Android minSdk 26, org.json available, no external libraries beyond Android SDK). " +
        "Write ONE complete Kotlin source file. Rules: the file must start with 'package com.nova.assistant'; it must compile as part of the existing app; " +
        "use only Android SDK, Kotlin stdlib and org.json; never touch passwords, OTP, PIN, CVV, payments, keys, GitHub, installing apps or running shell commands; " +
        "do not rename or remove public things that other files may use when editing; keep it small and safe. " +
        "Reply in EXACTLY this format and nothing else:\n" +
        "PATH: <repo path, e.g. " + DIR + "Name.kt>\nSUMMARY: <one line in Hinglish>\n<<<CODE\n<the whole file>\nCODE>>>"

    fun userPrompt(wish: String, path: String?, existing: String?): String {
        val sb = StringBuilder()
        sb.append("Owner wish (Hinglish): ").append(wish).append("\n\n")
        if (path != null && existing != null) {
            sb.append("Edit this EXISTING file. Return the COMPLETE updated file at the same PATH: ").append(path).append("\n\n--- current file ---\n").append(existing)
        } else if (path != null) {
            sb.append("Create this NEW file at PATH: ").append(path)
        } else {
            sb.append("Create a NEW file. Choose a new unique PascalCase file name under ").append(DIR).append(" (it must not already exist), e.g. Gen").append("Something.kt. Put all new code in it as top-level objects/classes/functions.")
        }
        return sb.toString()
    }
}
