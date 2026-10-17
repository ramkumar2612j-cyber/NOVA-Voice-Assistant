package com.nova.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * v6.86: what is connected, who the account is and which permission level the owner chose. Tokens live ONLY in
 * SecureStore (AES-GCM, Android Keystore key) and are never given to the web page, never logged, never put in a URL.
 * The GitHub connector shares the token slot of "website publish" so one GitHub login serves both.
 */
object ConnectorStore {
    private const val PREF = "nova_connectors"
    private const val MCP_LIST = "mcp_list"

    fun slot(id: String): String = if (id == ConnectorCatalog.GITHUB) GitHubPublish.SLOT else "conn_$id"

    private fun p(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun token(ctx: Context, id: String): String = SecureStore.getSlot(ctx, slot(id))

    /** Lovable has no token: it counts as "connected" once GitHub is connected. */
    fun connected(ctx: Context, id: String): Boolean =
        if (id == ConnectorCatalog.LOVABLE) SecureStore.hasSlot(ctx, slot(ConnectorCatalog.GITHUB))
        else if (id == ConnectorCatalog.GMAIL) GmailStore.gmailInstalled(ctx) && DrivingBridge.listenerEnabled(ctx)
        else SecureStore.hasSlot(ctx, slot(id))

    fun account(ctx: Context, id: String): String = p(ctx).getString("acc_$id", "") ?: ""

    /** Default after a new connection is READ. The owner raises it to WRITE on purpose. A saved token with no level yet (older GitHub token) is READ too. */
    fun perm(ctx: Context, id: String): String {
        if (!connected(ctx, id)) return ConnectorCatalog.PERM_OFF
        return ConnectorCatalog.normPerm(p(ctx).getString("perm_$id", ConnectorCatalog.PERM_READ) ?: ConnectorCatalog.PERM_READ)
    }

    fun setPerm(ctx: Context, id: String, level: String): Boolean {
        if (!connected(ctx, id)) return false
        return p(ctx).edit().putString("perm_$id", ConnectorCatalog.normPerm(level)).commit()
    }

    /** Saves a verified token. [level] is kept if the owner already chose one for this connector. */
    fun save(ctx: Context, id: String, token: String, account: String): Boolean {
        if (!SecureStore.saveSlot(ctx, slot(id), token)) return false
        val e = p(ctx).edit().putString("acc_$id", account.take(80)).putLong("at_$id", System.currentTimeMillis())
        if (!p(ctx).contains("perm_$id")) e.putString("perm_$id", ConnectorCatalog.PERM_READ)
        return e.commit()
    }

    fun lastOk(ctx: Context, id: String): Long = p(ctx).getLong("at_$id", 0L)

    fun disconnect(ctx: Context, id: String) {
        SecureStore.clearSlot(ctx, slot(id))
        p(ctx).edit().remove("acc_$id").remove("perm_$id").remove("at_$id").commit()
        if (ConnectorCatalog.isMcpId(id)) removeMcp(ctx, id)
        if (ConnectorCatalog.isHookId(id) || ConnectorCatalog.isN8nId(id)) removeDyn(ctx, id)
    }

    // ---- GitHub OAuth device login (optional): the owner's own OAuth app client id (public value, not a secret)
    fun clientId(ctx: Context): String = p(ctx).getString("gh_client_id", "") ?: ""
    fun setClientId(ctx: Context, v: String): Boolean {
        val s = v.trim()
        if (s.length > 60 || !s.all { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' }) return false
        return p(ctx).edit().putString("gh_client_id", s).commit()
    }

    // ---- custom MCP connectors
    class Mcp(val id: String, val name: String, val url: String)

    fun mcpList(ctx: Context): List<Mcp> = try {
        val a = JSONArray(p(ctx).getString(MCP_LIST, "[]") ?: "[]")
        List(a.length()) { i -> a.getJSONObject(i).let { Mcp(it.getString("id"), it.getString("name"), it.getString("url")) } }
            .filter { ConnectorCatalog.isMcpId(it.id) }
    } catch (e: Exception) { emptyList() }

    fun mcpOf(ctx: Context, id: String): Mcp? = mcpList(ctx).firstOrNull { it.id == id }

    /** Adds (or replaces by URL) a custom connector. Returns its id, or "" when name/URL are not acceptable or the list is full (8). */
    fun addMcp(ctx: Context, name0: String, url0: String): String {
        val name = name0.trim().take(40)
        val url = url0.trim()
        if (name.isEmpty() || !ConnectorCatalog.mcpUrlOk(url)) return ""
        val cur = mcpList(ctx)
        val same = cur.firstOrNull { it.url == url }
        if (same == null && cur.size >= 8) return ""
        val id = same?.id ?: (ConnectorCatalog.MCP_PREFIX + java.lang.Long.toString(System.currentTimeMillis() % 100000000L, 36))
        val next = cur.filter { it.id != id } + Mcp(id, name, url)
        val a = JSONArray()
        next.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("url", it.url)) }
        return if (p(ctx).edit().putString(MCP_LIST, a.toString()).commit()) id else ""
    }

    fun removeMcp(ctx: Context, id: String) {
        val a = JSONArray()
        mcpList(ctx).filter { it.id != id }.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("url", it.url)) }
        p(ctx).edit().putString(MCP_LIST, a.toString()).commit()
    }

    // ---- v6.97 automation connectors: webhooks (hook_) and the n8n REST API (n8n_)
    private const val DYN_LIST = "dyn_list"
    class Dyn(val id: String, val name: String, val url: String)

    fun dynList(ctx: Context): List<Dyn> = try {
        val a = JSONArray(p(ctx).getString(DYN_LIST, "[]") ?: "[]")
        List(a.length()) { i -> a.getJSONObject(i).let { Dyn(it.getString("id"), it.getString("name"), it.getString("url")) } }
            .filter { ConnectorCatalog.isHookId(it.id) || ConnectorCatalog.isN8nId(it.id) }
    } catch (e: Exception) { emptyList() }

    fun dynOf(ctx: Context, id: String): Dyn? = dynList(ctx).firstOrNull { it.id == id }

    private fun writeDyn(ctx: Context, list: List<Dyn>): Boolean {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("url", it.url)) }
        return p(ctx).edit().putString(DYN_LIST, a.toString()).commit()
    }

    /** Adds (or replaces by prefix + address) a webhook / n8n connector. Returns its id, or "" when not acceptable or the list is full (10). */
    fun addDyn(ctx: Context, prefix: String, name0: String, url0: String): String {
        val name = name0.trim().take(40)
        val url = url0.trim()
        if (name.isEmpty() || url.isEmpty() || url.length > 300) return ""
        val cur = dynList(ctx)
        val same = cur.firstOrNull { it.url == url && it.id.startsWith(prefix) }
        if (same == null && cur.size >= 10) return ""
        val id = same?.id ?: (prefix + java.lang.Long.toString(System.currentTimeMillis() % 100000000L, 36))
        return if (writeDyn(ctx, cur.filter { it.id != id } + Dyn(id, name, url))) id else ""
    }

    fun removeDyn(ctx: Context, id: String) { writeDyn(ctx, dynList(ctx).filter { it.id != id }) }

    private fun dynActions(id: String): List<ConnectorCatalog.Action> = when {
        ConnectorCatalog.isHookId(id) -> listOf(ConnectorCatalog.Action("trigger", "Automation chalao", true, "message (optional)"))
        ConnectorCatalog.isN8nId(id) -> listOf(
            ConnectorCatalog.Action("workflows", "Workflows ki list", false),
            ConnectorCatalog.Action("executions", "Taza runs", false, "workflow id (optional)"),
            ConnectorCatalog.Action("activate", "Workflow ON karo", true, "workflow id ya naam"),
            ConnectorCatalog.Action("deactivate", "Workflow OFF karo", true, "workflow id ya naam"))
        else -> listOf(ConnectorCatalog.Action("tools", "Tools ki list", false),
            ConnectorCatalog.Action("call", "Tool chalao", true, "tool | {\"arg\":\"value\"}"))
    }

    /** A catalog-style description of one owner-added connector, so voice, chat and the page use the same flow as GitHub or Vercel. */
    fun dynDef(ctx: Context, id: String): ConnectorCatalog.Def? {
        val hook = ConnectorCatalog.isHookId(id)
        val n8n = ConnectorCatalog.isN8nId(id)
        val name: String
        val short: String
        if (hook || n8n) {
            val d = dynOf(ctx, id) ?: return null
            name = d.name; short = d.url
        } else if (ConnectorCatalog.isMcpId(id)) {
            val m = mcpOf(ctx, id) ?: return null
            name = m.name; short = m.url
        } else return null
        val kind = if (hook) "hook" else if (n8n) "n8n" else "mcp"
        val read = if (hook) listOf("Kuch nahi padhta") else if (n8n) listOf("Workflows aur runs dekhna") else listOf("Server ke tools ki list")
        val write = if (hook) listOf("Is automation ko chalana") else if (n8n) listOf("Workflow ON/OFF karna") else listOf("Server ke tools chalana")
        return ConnectorCatalog.Def(id, name, short, kind, "", "", null, read, write, dynActions(id), listOf(name.lowercase()))
    }

    /** JSON for the page. Never contains a token. */
    fun summaryJson(ctx: Context): String {
        val out = JSONArray()
        for (d in ConnectorCatalog.ALL) {
            val acts = JSONArray()
            d.actions.forEach { acts.put(JSONObject().put("id", it.id).put("label", it.label).put("write", it.write).put("hint", it.argHint)) }
            out.put(
                JSONObject().put("id", d.id).put("name", d.name).put("short", d.short).put("kind", d.kind)
                    .put("connected", connected(ctx, d.id)).put("account", account(ctx, d.id)).put("perm", perm(ctx, d.id))
                    .put("help", d.tokenHelp).put("readCan", JSONArray(d.readCan)).put("writeCan", JSONArray(d.writeCan)).put("actions", acts)
                    .put("needsGithub", d.id == ConnectorCatalog.LOVABLE).put("gmailInstalled", d.id == ConnectorCatalog.GMAIL && GmailStore.gmailInstalled(ctx))
            )
        }
        for (m in mcpList(ctx)) {
            val acts = JSONArray()
                .put(JSONObject().put("id", "tools").put("label", "Tools ki list").put("write", false).put("hint", ""))
                .put(JSONObject().put("id", "call").put("label", "Tool chalao").put("write", true).put("hint", "tool | {\"arg\":\"value\"}"))
            out.put(
                JSONObject().put("id", m.id).put("name", m.name).put("short", m.url).put("kind", "mcp")
                    .put("connected", connected(ctx, m.id)).put("account", account(ctx, m.id)).put("perm", perm(ctx, m.id))
                    .put("help", "Custom MCP server. Token sirf tab jab server maange.")
                    .put("readCan", JSONArray(listOf("Server ke tools ki list"))).put("writeCan", JSONArray(listOf("Server ke tools chalana")))
                    .put("actions", acts).put("needsGithub", false)
            )
        }
        for (d in dynList(ctx)) {
            val acts = JSONArray()
            dynActions(d.id).forEach { acts.put(JSONObject().put("id", it.id).put("label", it.label).put("write", it.write).put("hint", it.argHint)) }
            val hook = ConnectorCatalog.isHookId(d.id)
            out.put(
                JSONObject().put("id", d.id).put("name", d.name).put("short", d.url).put("kind", if (hook) "hook" else "n8n")
                    .put("connected", connected(ctx, d.id)).put("account", account(ctx, d.id)).put("perm", perm(ctx, d.id))
                    .put("help", if (hook) "Automation webhook. Trigger ke baad platform sirf 'mil gaya' kehta hai, kaam poora hua ya nahi wo wahin dekho." else "n8n REST API (X-N8N-API-KEY).")
                    .put("readCan", JSONArray(if (hook) emptyList<String>() else listOf("Workflows aur runs dekhna")))
                    .put("writeCan", JSONArray(if (hook) listOf("Is automation ko chalana") else listOf("Workflow ON/OFF karna")))
                    .put("actions", acts).put("needsGithub", false)
            )
        }
        return out.toString()
    }
}
