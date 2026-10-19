package com.nova.assistant

/**
 * v7.40 PART 4 step B: COMMAND QUEUE, pure (no Android) so JUnit can run it.
 * If NOVA is busy and the owner gives another command, it WAITS. Up to MAX (10) commands run one by one.
 * Evidence level: written. Not compiled here (no network), not phone tested.
 */
object CmdQueue {
    const val MAX = 10

    enum class State { WAITING, RUNNING, DONE }

    class Item(val n: Int, val text: String, val offline: Boolean, var state: State, val voice: Boolean = false)

    private val STRONG = Regex("(?:\\r?\\n)+|;|(?:^|(?<=\\s))\\d{1,2}\\s*[.)](?=\\s)")
    private val SOFT = listOf("uske baad", "iske baad", "aur phir", "and then", "after that", "phir", "then")
    private val CONTENT_CUES = listOf("ki ", "message", "msg", "bhejo", "likho", "send", "tell", "reply")

    private fun hasCue(s: String): Boolean {
        val l = " " + s.lowercase() + " "
        return CONTENT_CUES.any { l.contains(it) }
    }

    /** Splits one utterance into 1..MAX commands. Never splits on a plain "aur". Extra items beyond MAX are cut off (see [splitDropped]). */
    fun split(text: String): List<String> {
        val t = text.trim()
        if (t.isEmpty()) return emptyList()
        var parts: List<String> = STRONG.split(t).map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size <= 1 && !hasCue(t)) {
            val alt = SOFT.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
            val re = Regex("(?:^|\\s)(?:" + alt + ")(?=\\s)", RegexOption.IGNORE_CASE)
            val p = re.split(t).map { it.trim().trim(',', '.') }.filter { it.isNotEmpty() }
            val best: List<String> = if (p.size > 1) p else listOf(t)
            parts = best
        }
        if (parts.isEmpty()) parts = listOf(t)
        return parts.take(MAX)
    }

    /** How many commands were cut off because the text had more than MAX. */
    fun splitDropped(text: String): Int {
        val t = text.trim()
        if (t.isEmpty()) return 0
        val strong = STRONG.split(t).map { it.trim() }.filter { it.isNotEmpty() }
        return if (strong.size > MAX) strong.size - MAX else 0
    }

    /** Label shown in the UI: offline (local command) or online (goes to the AI). [isLocal] = Logic.classify(text) != null. */
    fun route(isLocal: Boolean): String = if (isLocal) "OFFLINE" else "ONLINE"

    /** Thread-safe queue state. One instance lives in NovaService. */
    class Queue {
        private val items = ArrayList<Item>()
        private var counter = 0
        private var total = 0

        /** Adds commands; returns how many were dropped because the queue is full (cap MAX waiting + running). */
        @Synchronized fun addAll(list: List<String>, isLocal: (String) -> Boolean, voice: Boolean = false): Int {
            var dropped = 0
            for (s in list) {
                val live = items.count { it.state != State.DONE }
                if (live >= MAX) { dropped++; continue }
                if (items.none { it.state != State.DONE }) { items.clear(); counter = 0 }
                counter++
                total = counter
                items.add(Item(counter, s, isLocal(s), State.WAITING, voice))
            }
            return dropped
        }

        /** Marks the running one DONE and returns the next waiting one as RUNNING, or null. */
        @Synchronized fun next(): Item? {
            items.firstOrNull { it.state == State.RUNNING }?.state = State.DONE
            val n = items.firstOrNull { it.state == State.WAITING } ?: return null
            n.state = State.RUNNING
            return n
        }

        /** Marks the running one DONE without starting another. */
        @Synchronized fun finishRunning() { items.firstOrNull { it.state == State.RUNNING }?.state = State.DONE }

        @Synchronized fun hasWaiting(): Boolean = items.any { it.state == State.WAITING }
        @Synchronized fun isEmpty(): Boolean = items.none { it.state != State.DONE }
        @Synchronized fun total(): Int = total
        @Synchronized fun clear() { items.clear(); counter = 0; total = 0 }
        @Synchronized fun snapshot(): List<Item> = items.map { Item(it.n, it.text, it.offline, it.state, it.voice) }

        /** JSON list for the page: [{"n":1,"t":"...","r":"OFFLINE","s":"running"}, ...]. Hand-built, no org.json (JUnit-safe). */
        @Synchronized fun json(): String {
            val sb = StringBuilder("[")
            var first = true
            for (q in items) {
                if (!first) sb.append(',')
                first = false
                sb.append("{\"n\":").append(q.n)
                    .append(",\"t\":\"").append(esc(q.text)).append("\"")
                    .append(",\"r\":\"").append(route(q.offline)).append("\"")
                    .append(",\"s\":\"").append(q.state.name.lowercase()).append("\"}")
            }
            return sb.append(']').toString()
        }

        private fun esc(s: String): String {
            val sb = StringBuilder()
            for (c in s) when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> {}
                '\t' -> sb.append(' ')
                else -> if (c < ' ') sb.append(' ') else sb.append(c)
            }
            return sb.toString()
        }
    }
}
