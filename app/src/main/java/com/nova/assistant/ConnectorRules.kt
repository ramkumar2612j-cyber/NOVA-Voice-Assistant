package com.nova.assistant

/**
 * v6.86: spoken / typed connector commands ("github repos dikhao", "vercel redeploy karo mysite", "notion mein budget dhundo").
 * Pure (no Android classes), unit tested. Returns null for anything that is not clearly about a connector, so ordinary
 * sentences that merely contain a service name ("github jaisi website banao") are never hijacked.
 * The caller (NovaService) checks the connection, the permission level and, for write actions, asks for a spoken yes first.
 */
object ConnectorRules {
    /** [connector] = catalog id, or "" for the generic "connectors" screen. [action] = connect, disconnect, status, list or a catalog action id. */
    class Intent(val connector: String, val action: String, val arg: String = "")

    private val FILLER = setOf(
        "karo", "kar", "do", "de", "dena", "dikhao", "dikha", "batao", "bata", "show", "list", "my", "mere", "meri", "mera", "me", "mein", "main", "ke", "ki", "ka",
        "the", "a", "an", "please", "pls", "nova", "on", "par", "pe", "for", "of", "to", "se", "aur", "and", "ek", "naya", "new", "latest", "all", "sab", "saare", "open", "check",
        "banao", "bana", "create", "make", "add", "name", "title", "with", "called", "named", "wala", "wali",
        "pehle", "pehla", "usi", "isi", "wahi", "repo", "project", "site"
    )

    private fun norm(t: String): String = t.lowercase().replace(Regex("[^a-z0-9/_.\\-\\u0900-\\u097F ]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun has(words: List<String>, vararg keys: String): Boolean = keys.any { k ->
        if (k.contains(' ')) words.joinToString(" ").contains(k) else words.any { it == k || (k.length >= 5 && it.startsWith(k)) }
    }

    private val REF = setOf("pehle", "pehla", "first", "usi", "isi", "us", "is", "wahi", "same", "last", "latest")

    /** "us repo ka", "usi project ko", "pehle repo": refers to the first item of the previous step's list. */
    private fun refLast(words: List<String>): Boolean = words.any { it in setOf("pehle", "pehla", "first", "usi", "isi", "wahi", "same") } ||
        words.zipWithNext().any { (a, b) -> (a == "us" || a == "is") && b in setOf("repo", "project", "site", "repository", "deployment") }

    const val LAST = "@last"

    private fun repoToken(words: List<String>): String = words.firstOrNull { Regex("^[a-z0-9_.\\-]+/[a-z0-9_.\\-]+$").matches(it) } ?: ""

    /** Words that are neither filler nor a keyword, joined: the "argument" part of the sentence. */
    private fun rest(words: List<String>, drop: Set<String>): String =
        words.filter { it !in FILLER && it !in drop }.joinToString(" ").trim()

    fun connectorOf(text: String): ConnectorCatalog.Def? {
        val n = " " + norm(text) + " "
        return ConnectorCatalog.ALL.firstOrNull { d -> d.aliases.any { a -> n.contains(" " + a.lowercase() + " ") || n.contains(" " + a.lowercase() + "s ") } }
    }

    fun parse(text: String): Intent? {
        val n = norm(text)
        if (n.isEmpty() || n.length > 220) return null
        val words = n.split(' ')

        // The generic screen: "connectors kholo", "connections dikhao".
        if (words.any { it == "connectors" || it == "connector" || it == "connections" } && words.size <= 6) return Intent("", "list")

        val d = connectorOf(n) ?: return null
        val id = d.id
        // Permission level by voice: "github write on karo", "vercel read only", "notion off karo".
        val nn = " $n "
        val writeOn = nn.contains(" write on ") || nn.contains(" write allow ") || nn.contains(" write chalu ") || nn.contains(" likhne ki ijazat ") ||
            nn.contains(" likhna allow ") || nn.contains(" read write ") || nn.contains(" read and write ") || nn.contains(" full access ")
        val readOnly = nn.contains(" read only ") || nn.contains(" sirf padhna ") || nn.contains(" sirf read ") || nn.contains(" write off ") ||
            nn.contains(" write band ") || nn.contains(" likhna band ")
        if (writeOn) return Intent(d.id, "perm_write")
        if (readOnly) return Intent(d.id, "perm_read")
        if (nn.contains(" off karo ") || nn.contains(" band karo ") || nn.contains(" pause ")) return Intent(d.id, "perm_off")
        val verbsConnect = has(words, "connect", "jodo", "jod", "link karo", "link kar", "jodna", "setup")
        val verbsDisconnect = has(words, "disconnect", "logout", "unlink", "hatao", "hata do", "tod do", "remove")
        if (verbsDisconnect) return Intent(id, "disconnect")
        if (verbsConnect) return Intent(id, "connect")

        val repo = repoToken(words)
        val keys = d.aliases.flatMap { it.lowercase().split(' ') }.toSet()
        when (id) {
            ConnectorCatalog.GITHUB -> {
                if (has(words, "issue", "issues") && has(words, "banao", "bana", "create", "make", "add", "naya", "new", "likho", "file")) {
                    val title = rest(words, keys + setOf("issue", "issues", repo, "pe", "par"))
                    return Intent(id, "create_issue", (if (repo.isNotEmpty()) "$repo | " else "") + title)
                }
                if (has(words, "file") && has(words, "commit", "banao", "bana", "likho", "save", "update") && repo.isNotEmpty()) {
                    // "github file banao owner/repo notes/a.txt hello world" -> repo | path | content
                    val after = words.drop(words.indexOf(repo) + 1)
                    val path = after.firstOrNull { it.contains('.') || it.contains('/') } ?: ""
                    if (path.isNotEmpty()) {
                        val content = after.drop(after.indexOf(path) + 1).joinToString(" ")
                        return Intent(id, "commit_file", "$repo | $path | $content")
                    }
                }
                if (has(words, "issue", "issues")) return Intent(id, "issues", if (repo.isEmpty() && refLast(words)) LAST else repo)
                if (has(words, "run", "runs", "workflow", "workflows", "actions")) return Intent(id, "runs", if (repo.isEmpty() && refLast(words)) LAST else repo)
                if (has(words, "repo", "repos", "repository", "repositories", "repositor") && has(words, "banao", "bana", "create", "make", "naya", "new")) {
                    val name = words.firstOrNull { it !in FILLER && it !in keys && !it.startsWith("repo") && it != "private" && it != "public" } ?: ""
                    return Intent(id, "create_repo", name)
                }
                if (has(words, "repo", "repos", "repository", "repositories")) return Intent(id, "repos")
            }
            ConnectorCatalog.VERCEL -> {
                if (has(words, "redeploy", "dobara deploy", "deploy again", "phir se deploy")) {
                    return Intent(id, "redeploy", if (refLast(words)) LAST else rest(words, keys + setOf("redeploy", "dobara", "deploy", "again", "phir")))
                }
                if (has(words, "deployment", "deployments", "deploys", "deploy")) return Intent(id, "deployments", if (refLast(words)) LAST else "")
                if (has(words, "domain", "domains")) return Intent(id, "domains")
                if (has(words, "project", "projects")) return Intent(id, "projects")
            }
            ConnectorCatalog.NETLIFY -> {
                if (has(words, "build", "rebuild", "trigger")) return Intent(id, "build", if (refLast(words)) LAST else rest(words, keys + setOf("build", "rebuild", "trigger", "site")))
                if (has(words, "deploy", "deploys", "deployments")) return Intent(id, "deploys", if (refLast(words)) LAST else rest(words, keys + setOf("deploy", "deploys", "deployments", "site")))
                if (has(words, "site", "sites")) return Intent(id, "sites")
            }
            ConnectorCatalog.CLOUDFLARE -> {
                if (has(words, "zone", "zones", "domain", "domains")) return Intent(id, "zones")
                if (has(words, "page", "pages", "project", "projects")) return Intent(id, "pages")
            }
            ConnectorCatalog.SUPABASE -> {
                if (has(words, "project", "projects", "database", "databases")) return Intent(id, "projects")
            }
            ConnectorCatalog.NOTION -> {
                if (has(words, "search", "dhundo", "dhoondo", "khojo", "find", "dhundh")) {
                    return Intent(id, "search", rest(words, keys + setOf("search", "dhundo", "dhoondo", "khojo", "find", "dhundh", "page", "pages", "notes")))
                }
                if (has(words, "page", "pages", "notes")) return Intent(id, "search", "")
            }
            ConnectorCatalog.LOVABLE -> {
                if (has(words, "kholo", "open", "khol", "chalao", "launch", "start")) return Intent(id, "open")
            }
        }
        // A bare "github" / "nova vercel": only when the sentence is tiny, so it is clearly a request for the status.
        if (words.size <= 3) return Intent(id, "status")
        return null
    }

    /**
     * v6.97: spoken / typed commands for connectors the owner added (webhook automations, n8n API, custom MCP). [items] = (id, name).
     * The sentence must contain the connector's own name as whole words. A webhook starts only with an explicit start word
     * ("chalao", "trigger", "run", "bhejo" ...); a bare mention is not a command. NovaService still asks yes/no before any write.
     */
    fun parseDynamic(text: String, items: List<Pair<String, String>>): Intent? {
        val n = norm(text)
        if (n.isEmpty() || n.length > 220 || items.isEmpty()) return null
        val nn = " $n "
        val hit = items.filter { val nm = norm(it.second); nm.length >= 2 && nn.contains(" $nm ") }.maxByOrNull { it.second.length } ?: return null
        val id = hit.first
        val nameWords = norm(hit.second).split(' ').toSet()
        val words = n.split(' ')
        val verbsDisconnect = has(words, "disconnect", "logout", "unlink", "hatao", "hata do", "tod do", "remove")
        if (verbsDisconnect) return Intent(id, "disconnect")
        if (nn.contains(" write on ") || nn.contains(" write allow ") || nn.contains(" write chalu ") || nn.contains(" read write ") || nn.contains(" full access ")) return Intent(id, "perm_write")
        if (nn.contains(" read only ") || nn.contains(" sirf padhna ") || nn.contains(" write off ") || nn.contains(" write band ")) return Intent(id, "perm_read")
        val drop = nameWords + setOf("workflow", "workflows", "flow", "flows", "execution", "executions", "runs", "history", "activate", "deactivate", "on", "off", "tools", "tool")
        if (ConnectorCatalog.isHookId(id)) {
            if (has(words, "chalao", "chala", "trigger", "run", "start", "bhejo", "bhej", "send", "fire", "shuru", "execute"))
                return Intent(id, "trigger", rest(words, drop + setOf("chalao", "chala", "trigger", "run", "start", "bhejo", "bhej", "send", "fire", "shuru", "execute", "automation", "webhook", "hook")))
        } else if (ConnectorCatalog.isN8nId(id)) {
            val tail = setOf("activate", "deactivate", "workflow", "workflows", "flow", "flows", "execution", "executions", "runs", "history", "pause", "rok", "on", "off", "chalu", "band")
            if (has(words, "deactivate", "pause", "rok") || (has(words, "band") && has(words, "workflow", "flow"))) return Intent(id, "deactivate", rest(words, nameWords + tail))
            if (has(words, "activate") || (has(words, "chalu") && has(words, "workflow", "flow"))) return Intent(id, "activate", rest(words, nameWords + tail))
            if (has(words, "execution", "executions", "runs", "history")) return Intent(id, "executions", rest(words, nameWords + tail))
            if (has(words, "workflow", "workflows", "flow", "flows")) return Intent(id, "workflows")
        } else if (ConnectorCatalog.isMcpId(id)) {
            if (has(words, "tools", "tool")) return Intent(id, "tools")
        }
        if (words.size <= 3) return Intent(id, "status")
        return null
    }

    private val SEQ = Regex("\\s+(?:aur phir|and then|uske baad|uske bad|after that|phir|fir|then)\\s+")

    /**
     * "github repos dikhao phir vercel deployments dikhao" -> two steps, run one after the other.
     * Only explicit sequencing words split a sentence ("aur" alone does not: it appears inside titles). A later step without a
     * service name uses the previous step's service. Null unless there are 2 to 4 steps and every one is a clear connector command.
     */
    fun parsePlan(text: String): List<Intent>? {
        val parts = norm(text).split(SEQ).map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size < 2 || parts.size > 4) return null
        val out = ArrayList<Intent>()
        var prev: ConnectorCatalog.Def? = null
        for (seg in parts) {
            val own = connectorOf(seg)
            val step = parse(if (own == null && prev != null) prev.aliases[0] + " " + seg else seg) ?: return null
            if (step.connector.isEmpty() || step.action == "status" || step.action == "list") return null
            prev = ConnectorCatalog.byId(step.connector)
            out.add(step)
        }
        return out
    }

    /** Splits "a | b | c" arguments (UI and voice use the same shape). */
    fun args(arg: String): List<String> = arg.split('|').map { it.trim() }

    /** True for "owner/name" with only letters, digits, '.', '-', '_'. */
    fun repoOk(s: String): Boolean = Regex("^[A-Za-z0-9_.\\-]{1,100}/[A-Za-z0-9_.\\-]{1,100}$").matches(s)

    /** Repo / project / branch-like single name. */
    fun nameOk(s: String): Boolean = Regex("^[A-Za-z0-9_.\\-]{1,100}$").matches(s)
}
