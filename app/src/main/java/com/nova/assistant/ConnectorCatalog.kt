package com.nova.assistant

/**
 * v6.86 CONNECTORS: the list of services NOVA can be connected to (GitHub, Vercel, Netlify, Cloudflare, Supabase, Notion,
 * Lovable via GitHub) plus the custom MCP slot. Pure data + pure rules (no Android classes), unit tested.
 *
 * How a connection works: the owner taps CONNECT, reads what NOVA will be allowed to do, taps ALLOW, the provider's own
 * token page opens (scopes pre-filled where the provider supports it), the owner copies the token, comes back, and NOVA
 * picks it from the clipboard by itself, verifies it against the provider and stores it encrypted (SecureStore).
 * Nothing is connected without that tap. Writing actions (create, deploy, commit) additionally need the WRITE level and a yes.
 */
object ConnectorCatalog {
    /** [write] true = changes something at the provider (needs permission level WRITE and a confirmation). */
    class Action(val id: String, val label: String, val write: Boolean, val argHint: String = "")

    class Def(
        val id: String,
        val name: String,
        val short: String,
        /** "token" = provider API with a personal access token, "link" = no public API (opens the site), "mcp" = custom MCP server. */
        val kind: String,
        val tokenUrl: String,
        val tokenHelp: String,
        val tokenRegex: Regex?,
        val readCan: List<String>,
        val writeCan: List<String>,
        val actions: List<Action>,
        val aliases: List<String>
    )

    const val GITHUB = "github"
    const val VERCEL = "vercel"
    const val NETLIFY = "netlify"
    const val CLOUDFLARE = "cloudflare"
    const val SUPABASE = "supabase"
    const val NOTION = "notion"
    const val LOVABLE = "lovable"
    const val MCP_PREFIX = "mcp_"

    const val PERM_OFF = "off"
    const val PERM_READ = "read"
    const val PERM_WRITE = "write"

    val ALL: List<Def> = listOf(
        Def(
            GITHUB, "GitHub", "Repos, issues, workflow runs. Code commit aur naya repo (WRITE).", "token",
            "https://github.com/settings/tokens/new?scopes=repo,workflow,read:user&description=NOVA",
            "GitHub token page khulega, scopes pehle se tick hain. Niche 'Generate token' dabao, token copy karo, NOVA par wapas aao.",
            Regex("^(ghp_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{40,}|gho_[A-Za-z0-9]{30,})$"),
            listOf("Apne repos ki list", "Issues aur workflow runs padhna"),
            listOf("Issue banana", "Naya private repo banana", "File commit karna"),
            listOf(
                Action("repos", "Mere repos", false),
                Action("issues", "Open issues", false, "owner/repo"),
                Action("runs", "Workflow runs", false, "owner/repo"),
                Action("create_issue", "Issue banao", true, "owner/repo | title"),
                Action("create_repo", "Naya private repo", true, "repo-name"),
                Action("commit_file", "File commit karo", true, "owner/repo | path | content")
            ),
            listOf("github", "git hub", "गिटहब")
        ),
        Def(
            VERCEL, "Vercel", "Projects, deployments, domains. Redeploy (WRITE).", "token",
            "https://vercel.com/account/tokens",
            "Vercel token page khulega. Create Token dabao, naam NOVA likho, token copy karke wapas aao.",
            Regex("^[A-Za-z0-9_]{24,80}$"),
            listOf("Projects aur deployments dekhna", "Domains ki list"),
            listOf("Redeploy chalana"),
            listOf(
                Action("projects", "Projects", false),
                Action("deployments", "Deployments", false),
                Action("domains", "Domains", false),
                Action("redeploy", "Redeploy (latest)", true, "project name")
            ),
            listOf("vercel", "versel")
        ),
        Def(
            NETLIFY, "Netlify", "Sites aur deploys. Naya build chalana (WRITE).", "token",
            "https://app.netlify.com/user/applications#personal-access-tokens",
            "Netlify page khulega. New access token banao, copy karke wapas aao.",
            Regex("^(nfp_[A-Za-z0-9]{20,}|[A-Za-z0-9_-]{40,60})$"),
            listOf("Sites ki list", "Deploy history"),
            listOf("Build trigger karna"),
            listOf(
                Action("sites", "Sites", false),
                Action("deploys", "Latest deploys", false, "site name"),
                Action("build", "Naya build", true, "site name")
            ),
            listOf("netlify")
        ),
        Def(
            CLOUDFLARE, "Cloudflare", "Zones (domains) aur Pages projects padhna.", "token",
            "https://dash.cloudflare.com/profile/api-tokens",
            "Cloudflare page khulega. Create Token > 'Read all resources' template, copy karke wapas aao.",
            Regex("^[A-Za-z0-9_-]{40}$"),
            listOf("Zones aur Pages projects dekhna"),
            emptyList(),
            listOf(
                Action("zones", "Zones (domains)", false),
                Action("pages", "Pages projects", false)
            ),
            listOf("cloudflare", "cloud flare")
        ),
        Def(
            SUPABASE, "Supabase", "Projects ki list aur status.", "token",
            "https://supabase.com/dashboard/account/tokens",
            "Supabase page khulega. Generate new token dabao, copy karke wapas aao.",
            Regex("^sbp_[A-Za-z0-9]{20,}$"),
            listOf("Projects aur unka status dekhna"),
            emptyList(),
            listOf(Action("projects", "Projects", false)),
            listOf("supabase", "super base")
        ),
        Def(
            NOTION, "Notion", "Pages aur databases mein search.", "token",
            "https://www.notion.so/profile/integrations",
            "Notion page khulega. New integration banao, 'Internal Integration Secret' copy karo. Jo pages dikhane hain unhe integration se share karna padta hai.",
            Regex("^(ntn_|secret_)[A-Za-z0-9]{30,}$"),
            listOf("Share kiye hue pages mein search"),
            emptyList(),
            listOf(Action("search", "Search", false, "kya dhundna hai")),
            listOf("notion", "nosion")
        ),
        Def(
            LOVABLE, "Lovable", "Lovable ka public API nahi hai. Apne project ka GitHub repo connect karo, NOVA wahin se kaam karta hai.", "link",
            "https://lovable.dev",
            "Lovable apne project ko GitHub se sync karta hai. GitHub connect karo, phir yahan se Lovable kholo.",
            null,
            listOf("Lovable kholna", "Project repo GitHub connector se"),
            emptyList(),
            listOf(Action("open", "Lovable kholo", false)),
            listOf("lovable", "lavable")
        )
    )

    fun byId(id: String): Def? = ALL.firstOrNull { it.id == id }

    /** v6.97 AUTOMATION: webhook connectors (n8n webhook, Zapier, Make, IFTTT, Pipedream ... any https hook) and the n8n REST API. */
    const val HOOK_PREFIX = "hook_"
    const val N8N_PREFIX = "n8n_"

    private fun dynIdOk(id: String, prefix: String): Boolean = id.startsWith(prefix) && id.length in (prefix.length + 1)..(prefix.length + 24) &&
        id.drop(prefix.length).all { it.isLetterOrDigit() || it == '_' }

    fun isHookId(id: String): Boolean = dynIdOk(id, HOOK_PREFIX)
    fun isN8nId(id: String): Boolean = dynIdOk(id, N8N_PREFIX)
    /** MCP, webhook and n8n connectors are added by the owner at run time (they are not in [ALL]). */
    fun isDynId(id: String): Boolean = isMcpId(id) || isHookId(id) || isN8nId(id)

    /** n8n base address as the owner types it: "https://x.app.n8n.cloud", with or without a trailing slash or "/api/v1". Empty when not acceptable. */
    fun n8nBase(u0: String): String {
        val u = u0.trim().trimEnd('/').removeSuffix("/api/v1").trimEnd('/')
        if (u.contains('?') || u.contains('#')) return ""
        return if (mcpUrlOk(u)) u else ""
    }

    fun isMcpId(id: String): Boolean = id.startsWith(MCP_PREFIX) && id.length in (MCP_PREFIX.length + 1)..(MCP_PREFIX.length + 24) &&
        id.drop(MCP_PREFIX.length).all { it.isLetterOrDigit() || it == '_' }

    /** True when [s] could be a token for [id]. Clipboard sniffing only: the provider's reply is the real check. */
    fun looksLikeToken(id: String, s0: String): Boolean {
        val s = s0.trim()
        if (s.length < 20 || s.length > 255 || s.any { it.isWhitespace() }) return false
        if (s.startsWith("http://") || s.startsWith("https://")) return false
        val d = byId(id)
        if (d?.tokenRegex != null) return d.tokenRegex.matches(s)
        return s.all { it.isLetterOrDigit() || it == '_' || it == '-' || it == '.' }
    }

    /** Permission level check. Unknown level counts as off. */
    fun allows(level: String, write: Boolean): Boolean = when (level) {
        PERM_WRITE -> true
        PERM_READ -> !write
        else -> false
    }

    fun normPerm(p: String): String = if (p == PERM_READ || p == PERM_WRITE) p else PERM_OFF

    /** A custom MCP address must be https, with a public-looking host (no localhost, no private ranges, no user:pass@). */
    fun mcpUrlOk(u0: String): Boolean {
        val u = u0.trim()
        if (u.length > 300 || !u.startsWith("https://")) return false
        val rest = u.removePrefix("https://")
        val host = rest.substringBefore('/').substringBefore('?').substringBefore('#')
        if (host.isEmpty() || host.contains('@') || host.contains(' ')) return false
        val h = host.substringBefore(':').lowercase()
        if (!h.contains('.') || h == "localhost" || h.endsWith(".local") || h.endsWith(".internal")) return false
        if (h.all { it.isDigit() || it == '.' }) {
            val p = h.split('.').mapNotNull { it.toIntOrNull() }
            if (p.size != 4) return false
            if (p[0] == 10 || p[0] == 127 || p[0] == 0 || (p[0] == 169 && p[1] == 254) || (p[0] == 192 && p[1] == 168) ||
                (p[0] == 172 && p[1] in 16..31) || (p[0] == 100 && p[1] in 64..127)) return false
        }
        return h.all { it.isLetterOrDigit() || it == '.' || it == '-' }
    }
}
