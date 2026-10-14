package com.nova.assistant

import org.json.JSONArray
import org.json.JSONObject

/**
 * v6.86: turns provider JSON replies into short readable lines (pure, unit tested with sample replies).
 * Never throws: bad or unexpected JSON gives an empty list. Output lines are capped so a chat bubble stays readable.
 */
object ConnectorParse {
    /** Android's org.json turns a JSON null into the text "null"; this returns an empty string for it. */
    private fun JSONObject.s(k: String): String = if (isNull(k)) "" else optString(k)

    private const val MAX_LINES = 12
    private const val MAX_LEN = 110

    private fun cut(s: String): String {
        val t = s.replace('\n', ' ').replace('\r', ' ').trim()
        return if (t.length > MAX_LEN) t.take(MAX_LEN - 1) + "…" else t
    }

    private fun arr(body: String, key: String? = null): JSONArray? = try {
        val t = body.trim()
        if (key == null) {
            if (t.startsWith("[")) JSONArray(t) else null
        } else JSONObject(t).optJSONArray(key)
    } catch (e: Exception) { null }

    private inline fun lines(a: JSONArray?, f: (JSONObject) -> String?): List<String> {
        if (a == null) return emptyList()
        val out = ArrayList<String>()
        for (i in 0 until a.length()) {
            if (out.size >= MAX_LINES) break
            val o = a.optJSONObject(i) ?: continue
            val s = try { f(o) } catch (e: Exception) { null }
            if (!s.isNullOrBlank()) out.add(cut(s))
        }
        return out
    }

    fun githubRepos(body: String): List<String> = lines(arr(body)) {
        it.s("full_name").ifEmpty { null }?.let { n -> n + if (it.optBoolean("private")) " (private)" else "" }
    }

    fun githubIssues(body: String): List<String> = lines(arr(body)) {
        if (it.has("pull_request")) null else "#" + it.optInt("number") + " " + it.s("title")
    }

    fun githubRuns(body: String): List<String> = lines(arr(body, "workflow_runs")) {
        val st = it.s("conclusion").ifEmpty { it.s("status") }
        it.s("name") + " [" + it.s("head_branch") + "] " + st
    }

    fun vercelProjects(body: String): List<String> = lines(arr(body, "projects")) {
        it.s("name") + (it.s("framework").takeIf { f -> f.isNotEmpty() }?.let { f -> " ($f)" } ?: "")
    }

    fun vercelDeployments(body: String): List<String> = lines(arr(body, "deployments")) {
        val st = it.s("readyState").ifEmpty { it.s("state") }
        it.s("name") + " " + st + (it.s("target").takeIf { t -> t.isNotEmpty() }?.let { t -> " ($t)" } ?: "") +
            (it.s("url").takeIf { u -> u.isNotEmpty() }?.let { u -> " - $u" } ?: "")
    }

    fun vercelDomains(body: String): List<String> = lines(arr(body, "domains")) { it.s("name") }

    fun netlifySites(body: String): List<String> = lines(arr(body)) {
        it.s("name") + (it.s("ssl_url").ifEmpty { it.s("url") }.takeIf { u -> u.isNotEmpty() }?.let { u -> " - $u" } ?: "")
    }

    fun netlifyDeploys(body: String): List<String> = lines(arr(body)) {
        it.s("state") + " [" + it.s("branch") + "] " + it.s("created_at").take(16)
    }

    fun cloudflareZones(body: String): List<String> = lines(arr(body, "result")) { it.s("name") + " (" + it.s("status") + ")" }

    fun cloudflarePages(body: String): List<String> = lines(arr(body, "result")) {
        it.s("name") + (it.s("subdomain").takeIf { s -> s.isNotEmpty() }?.let { s -> " - $s" } ?: "")
    }

    fun supabaseProjects(body: String): List<String> = lines(arr(body)) {
        it.s("name") + " (" + it.s("region") + ", " + it.s("status") + ")"
    }

    fun notionSearch(body: String): List<String> = lines(arr(body, "results")) { r ->
        var title = ""
        val t = r.optJSONArray("title")
        if (t != null && t.length() > 0) title = t.optJSONObject(0)?.s("plain_text") ?: ""
        if (title.isEmpty()) {
            val props = r.optJSONObject("properties")
            if (props != null) {
                val keys = props.keys()
                while (keys.hasNext() && title.isEmpty()) {
                    val p = props.optJSONObject(keys.next()) ?: continue
                    if (p.s("type") == "title") {
                        val ta = p.optJSONArray("title")
                        if (ta != null && ta.length() > 0) title = ta.optJSONObject(0)?.s("plain_text") ?: ""
                    }
                }
            }
        }
        (if (title.isEmpty()) "(untitled)" else title) + " [" + r.s("object") + "]"
    }

    /** MCP tools/list reply (plain JSON). */
    fun mcpTools(body: String): List<String> {
        val res = try { JSONObject(body).optJSONObject("result") } catch (e: Exception) { null }
        return lines(res?.optJSONArray("tools")) {
            it.s("name") + (it.s("description").takeIf { d -> d.isNotEmpty() }?.let { d -> " - $d" } ?: "")
        }
    }

    /** Text of an MCP tools/call reply: joins the text parts of result.content. */
    fun n8nWorkflows(body: String): List<String> = lines(arr(body, "data")) {
        val id = it.s("id")
        if (id.isEmpty()) null else id + " " + it.s("name") + (if (it.optBoolean("active")) " [on]" else " [off]")
    }

    fun n8nExecutions(body: String): List<String> = lines(arr(body, "data")) {
        val st = it.s("status").ifEmpty { if (it.optBoolean("finished")) "success" else "running" }
        "#" + it.s("id") + " wf " + it.s("workflowId") + " " + st + " " + it.s("startedAt").take(16).replace('T', ' ')
    }

    /** A short error text when an MCP answer is a JSON-RPC error or a tool result with isError=true, else null. */
    fun mcpError(body: String): String? {
        val j = try { JSONObject(body) } catch (e: Exception) { return null }
        val er = j.optJSONObject("error")
        if (er != null) return cut(er.optString("message").ifBlank { "server ne error diya" })
        val res = j.optJSONObject("result") ?: return null
        if (!res.optBoolean("isError", false)) return null
        val c = res.optJSONArray("content")
        var t = ""
        if (c != null) for (i in 0 until c.length()) {
            val o = c.optJSONObject(i) ?: continue
            if (o.s("type") == "text" && o.s("text").isNotBlank()) { t = o.s("text").trim().split('\n')[0]; break }
        }
        return cut(t.ifBlank { "tool ne error diya" })
    }

    /** Vercel team ids from GET /v2/teams (a personal token also sees team projects only with ?teamId=). */
    fun vercelTeamIds(body: String): List<String> {
        val a = try { JSONObject(body).optJSONArray("teams") } catch (e: Exception) { null } ?: return emptyList()
        val out = ArrayList<String>()
        for (i in 0 until a.length()) {
            val id = a.optJSONObject(i)?.optString("id") ?: continue
            if (id.isNotEmpty() && id.length <= 60 && id.all { it.isLetterOrDigit() || it == '_' || it == '-' }) out.add(id)
            if (out.size >= 3) break
        }
        return out
    }

    fun mcpCallText(body: String): List<String> {
        val res = try { JSONObject(body).optJSONObject("result") } catch (e: Exception) { null } ?: return emptyList()
        val c = res.optJSONArray("content") ?: return emptyList()
        val out = ArrayList<String>()
        for (i in 0 until c.length()) {
            if (out.size >= MAX_LINES) break
            val o = c.optJSONObject(i) ?: continue
            if (o.s("type") == "text") for (ln in o.s("text").split('\n')) if (ln.isNotBlank() && out.size < MAX_LINES) out.add(cut(ln))
        }
        return out
    }

    /** Streamable-HTTP MCP servers may answer as text/event-stream. Returns the JSON of the last "data:" line, or the body itself. */
    fun mcpJson(body: String): String {
        val t = body.trim()
        if (t.startsWith("{")) return t
        var last = ""
        for (ln in t.lines()) {
            val s = ln.trim()
            if (s.startsWith("data:")) { val d = s.removePrefix("data:").trim(); if (d.startsWith("{")) last = d }
        }
        return last.ifEmpty { t }
    }

    /** Account label from the verify call. Empty = the reply did not look like this provider. */
    fun account(id: String, body: String): String = try {
        when (id) {
            ConnectorCatalog.GITHUB -> JSONObject(body).s("login")
            ConnectorCatalog.VERCEL -> JSONObject(body).optJSONObject("user")?.let { it.s("username").ifEmpty { it.s("email") } } ?: ""
            ConnectorCatalog.NETLIFY -> JSONObject(body).let { it.s("full_name").ifEmpty { it.s("email") } }
            ConnectorCatalog.CLOUDFLARE -> JSONObject(body).optJSONObject("result")?.s("status")?.let { if (it == "active") "token active" else "" } ?: ""
            ConnectorCatalog.SUPABASE -> JSONArray(body).length().toString() + " projects"
            ConnectorCatalog.NOTION -> JSONObject(body).let { o ->
                val ws = o.optJSONObject("bot")?.s("workspace_name") ?: ""
                if (ws.isNotEmpty()) ws else o.s("name")
            }
            else -> ""
        }
    } catch (e: Exception) { "" }
}
