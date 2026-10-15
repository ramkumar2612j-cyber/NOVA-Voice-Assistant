package com.nova.assistant

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * v6.86: the real network side of the connectors. HTTPS only, no redirects, timeouts, capped bodies, the token only in an
 * Authorization header (never in a URL, never logged). Never throws: errors become [Out] with a short Hinglish reason.
 * Write actions re-check the permission level here too, so no caller can skip it. The spoken/UI confirmation is done by the caller.
 * Endpoints are written from the providers' public REST docs; nothing here was reachable from the build sandbox.
 */
object ConnectorApi {
    class Out(val ok: Boolean, val lines: List<String> = emptyList(), val err: String = "")
    class Res(val code: Int, val body: String, val session: String = "")

    private const val MAX_BODY = 300_000
    const val NO_TOKEN = "-"   // custom MCP servers without a token store this marker (an empty secret cannot be stored)

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    fun http(method: String, url: String, headers: Map<String, String>, body: String?, contentType: String = "application/json"): Res {
        var c: HttpURLConnection? = null
        try {
            val u = URL(url)
            if (u.protocol != "https") return Res(0, "")
            c = u.openConnection() as HttpURLConnection
            c.requestMethod = method
            c.connectTimeout = 15000
            c.readTimeout = 40000
            c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", "NOVA-assistant")
            for ((k, v) in headers) c.setRequestProperty(k, v)
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", contentType)
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val st = if (code in 200..299) c.inputStream else c.errorStream
            val text = if (st == null) "" else {
                val bo = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                st.use { s -> while (true) { val n = s.read(buf); if (n < 0) break; if (bo.size() + n > MAX_BODY) break; bo.write(buf, 0, n) } }
                String(bo.toByteArray(), Charsets.UTF_8)
            }
            return Res(code, text, c.getHeaderField("Mcp-Session-Id") ?: "")
        } catch (e: java.net.UnknownHostException) {
            return Res(598, "")
        } catch (e: java.net.ConnectException) {
            return Res(598, "")
        } catch (e: Exception) {
            return Res(599, "")
        } finally {
            try { c?.disconnect() } catch (e: Exception) { }
        }
    }

    fun failFor(code: Int, name: String): String = when (code) {
        401 -> "$name ne token mana nahi kiya. Token galat ya expire hai"
        403 -> "$name ne mana kiya. Token mein is kaam ka permission nahi hai"
        404 -> "$name par ye cheez nahi mili (naam check karo)"
        422 -> "$name ne request qabool nahi ki (naam pehle se ho sakta hai)"
        429 -> "$name ne abhi rok diya (rate limit). Thodi der baad try karo"
        598 -> "Internet nahi mil raha"
        in 500..599 -> "$name abhi theek se jawab nahi de raha ($code)"
        else -> "$name se jawab nahi aaya ($code)"
    }

    private fun bearer(t: String, extra: Map<String, String> = emptyMap()): Map<String, String> {
        val m = LinkedHashMap<String, String>()
        m["Accept"] = "application/json"
        if (t.isNotEmpty() && t != NO_TOKEN) m["Authorization"] = "Bearer $t"
        m.putAll(extra)
        return m
    }

    private fun gh(t: String) = bearer(t, mapOf("Accept" to "application/vnd.github+json", "X-GitHub-Api-Version" to "2022-11-28"))
    private fun notion(t: String) = bearer(t, mapOf("Notion-Version" to "2022-06-28"))

    // ------------------------------------------------------------------ verify
    /** Asks the provider who this token belongs to. Pair(true, account label) or Pair(false, reason). */
    fun verify(id: String, token: String): Pair<Boolean, String> {
        val d = ConnectorCatalog.byId(id) ?: return Pair(false, "Ye connector nahi mila")
        val (url, hdr) = when (id) {
            ConnectorCatalog.GITHUB -> Pair("https://api.github.com/user", gh(token))
            ConnectorCatalog.VERCEL -> Pair("https://api.vercel.com/v2/user", bearer(token))
            ConnectorCatalog.NETLIFY -> Pair("https://api.netlify.com/api/v1/user", bearer(token))
            ConnectorCatalog.CLOUDFLARE -> Pair("https://api.cloudflare.com/client/v4/user/tokens/verify", bearer(token))
            ConnectorCatalog.SUPABASE -> Pair("https://api.supabase.com/v1/projects", bearer(token))
            ConnectorCatalog.NOTION -> Pair("https://api.notion.com/v1/users/me", notion(token))
            else -> return Pair(false, "Is connector mein token nahi lagta")
        }
        val r = http("GET", url, hdr, null)
        if (r.code !in 200..299) return Pair(false, failFor(r.code, d.name))
        val acc = ConnectorParse.account(id, r.body)
        if (acc.isEmpty()) return Pair(false, "${d.name} ka jawab samajh nahi aaya. Token dobara check karo")
        return Pair(true, acc)
    }

    // ------------------------------------------------------------------ MCP (custom connector)
    private fun mcpHeaders(token: String, session: String): Map<String, String> {
        val m = LinkedHashMap<String, String>()
        m["Accept"] = "application/json, text/event-stream"
        if (token.isNotEmpty() && token != NO_TOKEN) m["Authorization"] = "Bearer $token"
        if (session.isNotEmpty()) m["Mcp-Session-Id"] = session
        return m
    }

    private fun rpc(id: Int?, method: String, params: JSONObject?): String {
        val o = JSONObject().put("jsonrpc", "2.0").put("method", method)
        if (id != null) o.put("id", id)
        if (params != null) o.put("params", params)
        return o.toString()
    }

    /** initialize + initialized. Pair(ok, session id or error text). */
    fun mcpOpen(url: String, token: String): Pair<Boolean, String> {
        if (!ConnectorCatalog.mcpUrlOk(url)) return Pair(false, "Server ka address https wala public address hona chahiye")
        val init = http("POST", url, mcpHeaders(token, ""), rpc(1, "initialize", JSONObject()
            .put("protocolVersion", "2025-03-26").put("capabilities", JSONObject())
            .put("clientInfo", JSONObject().put("name", "NOVA").put("version", "6.97"))))
        if (init.code !in 200..299) return Pair(false, failFor(init.code, "MCP server"))
        val j = try { JSONObject(ConnectorParse.mcpJson(init.body)) } catch (e: Exception) { return Pair(false, "Ye MCP server nahi lagta (jawab JSON nahi hai)") }
        if (j.optJSONObject("result") == null) return Pair(false, "Ye MCP server nahi lagta (initialize ka jawab nahi mila)")
        http("POST", url, mcpHeaders(token, init.session), rpc(null, "notifications/initialized", null))
        return Pair(true, init.session)
    }

    private fun mcpRun(ctx: Context, id: String, action: String, arg: String): Out {
        val m = ConnectorStore.mcpOf(ctx, id) ?: return Out(false, err = "Ye custom connector nahi mila")
        val tok = ConnectorStore.token(ctx, id)
        val (ok, sess) = mcpOpen(m.url, tok)
        if (!ok) return Out(false, err = sess)
        return when (action) {
            "tools" -> {
                val r = http("POST", m.url, mcpHeaders(tok, sess), rpc(2, "tools/list", JSONObject()))
                if (r.code !in 200..299) Out(false, err = failFor(r.code, m.name))
                else if (ConnectorParse.mcpError(ConnectorParse.mcpJson(r.body)) != null) Out(false, err = m.name + ": " + ConnectorParse.mcpError(ConnectorParse.mcpJson(r.body)))
                else ConnectorParse.mcpTools(ConnectorParse.mcpJson(r.body)).let { if (it.isEmpty()) Out(true, listOf("Is server par koi tool nahi mila")) else Out(true, it) }
            }
            "call" -> {
                val parts = ConnectorRules.args(arg)
                val tool = parts.getOrElse(0) { "" }
                if (!ConnectorRules.nameOk(tool)) return Out(false, err = "Tool ka naam sahi likho")
                val argsJson = try { if (parts.size > 1 && parts[1].isNotEmpty()) JSONObject(parts.drop(1).joinToString(" | ")) else JSONObject() } catch (e: Exception) { return Out(false, err = "Arguments sahi JSON mein likho") }
                val r = http("POST", m.url, mcpHeaders(tok, sess), rpc(3, "tools/call", JSONObject().put("name", tool).put("arguments", argsJson)))
                if (r.code !in 200..299) Out(false, err = failFor(r.code, m.name))
                else if (ConnectorParse.mcpError(ConnectorParse.mcpJson(r.body)) != null) Out(false, err = m.name + ": " + ConnectorParse.mcpError(ConnectorParse.mcpJson(r.body)))
                else {
                    val lines = ConnectorParse.mcpCallText(ConnectorParse.mcpJson(r.body))
                    Out(true, if (lines.isEmpty()) listOf("Tool chal gaya, text jawab nahi aaya") else lines)
                }
            }
            else -> Out(false, err = "Ye kaam is connector mein nahi hai")
        }
    }

    // ------------------------------------------------------------------ actions
    /** Whether [action] of connector [id] changes something at the provider. */
    fun isWrite(id: String, action: String): Boolean {
        if (ConnectorCatalog.isMcpId(id)) return action == "call"
        if (ConnectorCatalog.isHookId(id)) return true   // a webhook always starts something
        if (ConnectorCatalog.isN8nId(id)) return action != "workflows" && action != "executions"
        return ConnectorCatalog.byId(id)?.actions?.firstOrNull { it.id == action }?.write ?: true   // unknown = treated as write
    }

    fun run(ctx: Context, id: String, action: String, arg: String): Out {
        val d = ConnectorCatalog.byId(id)
        val isMcp = ConnectorCatalog.isMcpId(id)
        val isHook = ConnectorCatalog.isHookId(id)
        val isN8n = ConnectorCatalog.isN8nId(id)
        if (d == null && !isMcp && !isHook && !isN8n) return Out(false, err = "Ye connector nahi mila")
        val name = d?.name ?: (if (isHook || isN8n) ConnectorStore.dynOf(ctx, id)?.name else ConnectorStore.mcpOf(ctx, id)?.name) ?: "MCP"
        if (!ConnectorStore.connected(ctx, id)) return Out(false, err = "$name connect nahi hai. CONNECT tab mein jodo")
        val write = isWrite(id, action)
        if (!ConnectorCatalog.allows(ConnectorStore.perm(ctx, id), write)) {
            return Out(false, err = if (write) "$name par sirf padhne ki ijazat hai. CONNECT tab mein WRITE chalu karo" else "$name band hai. CONNECT tab mein READ chalu karo")
        }
        if (isMcp) return mcpRun(ctx, id, action, arg)
        if (isHook) return hookRun(ctx, id, name, arg)
        if (isN8n) return n8nRun(ctx, id, name, action, arg)
        if (id == ConnectorCatalog.LOVABLE) return if (action == "open") Out(true, listOf("https://lovable.dev")) else Out(false, err = "Lovable mein ye kaam nahi hai")
        val t = ConnectorStore.token(ctx, id)
        return try {
            when (id) {
                ConnectorCatalog.GITHUB -> github(t, action, arg)
                ConnectorCatalog.VERCEL -> vercel(t, action, arg)
                ConnectorCatalog.NETLIFY -> netlify(t, action, arg)
                ConnectorCatalog.CLOUDFLARE -> cloudflare(t, action)
                ConnectorCatalog.SUPABASE -> supabase(t, action)
                ConnectorCatalog.NOTION -> notionRun(t, action, arg)
                else -> Out(false, err = "Ye kaam abhi nahi hai")
            }
        } catch (e: Exception) {
            Out(false, err = "$name ka kaam poora nahi ho paya")
        }
    }

    // ------------------------------------------------------------------ automation: webhooks (n8n webhook, Zapier, Make, IFTTT, Pipedream ...)
    /**
     * POSTs {"source":"NOVA","text":...,"time":...} to the owner's https hook. A 2xx only means the platform ACCEPTED the trigger; whether the
     * workflow then succeeded is that platform's business, so the answer says exactly that and never claims more.
     */
    private fun hookRun(ctx: Context, id: String, name: String, arg: String): Out {
        val h = ConnectorStore.dynOf(ctx, id) ?: return Out(false, err = "Ye automation nahi mili")
        if (!ConnectorCatalog.mcpUrlOk(h.url)) return Out(false, err = "Automation ka address https wala public address hona chahiye")
        val tok = ConnectorStore.token(ctx, id)
        val hdr = LinkedHashMap<String, String>()
        hdr["Accept"] = "application/json, text/plain, */*"
        if (tok.isNotEmpty() && tok != NO_TOKEN) hdr["Authorization"] = "Bearer $tok"
        val body = JSONObject().put("source", "NOVA").put("text", arg.trim().take(2000)).put("time", System.currentTimeMillis()).toString()
        val r = http("POST", h.url, hdr, body)
        if (r.code !in 200..299) return Out(false, err = failFor(r.code, name))
        val lines = ArrayList<String>()
        lines.add("Trigger bhej diya, $name ne qabool kar liya (code ${r.code}). Kaam poora hua ya nahi, wo $name mein dekho")
        val reply = r.body.trim().replace('\n', ' ')
        if (reply.isNotEmpty() && reply.length <= 160 && !reply.startsWith("<")) lines.add("Jawab: $reply")
        return Out(true, lines)
    }

    // ------------------------------------------------------------------ automation: n8n REST API
    private fun n8nHeaders(t: String): Map<String, String> = mapOf("Accept" to "application/json", "X-N8N-API-KEY" to t)

    /** Real check of an n8n address + API key (read-only call). Pair(ok, label or reason). */
    fun n8nVerify(base: String, key: String): Pair<Boolean, String> {
        if (base.isEmpty()) return Pair(false, "n8n ka address https wala public address hona chahiye (jaise https://naam.app.n8n.cloud)")
        val r = http("GET", "$base/api/v1/workflows?limit=1", n8nHeaders(key), null)
        if (r.code !in 200..299) return Pair(false, if (r.code == 404) "n8n par public API nahi mili. Settings > n8n API mein API chalu karo aur address check karo" else failFor(r.code, "n8n"))
        val ok = try { JSONObject(r.body).has("data") } catch (e: Exception) { false }
        return if (ok) Pair(true, base.removePrefix("https://").substringBefore('/').take(60)) else Pair(false, "Ye n8n ka jawab nahi lagta. Address check karo")
    }

    private fun n8nWorkflowId(base: String, h: Map<String, String>, nameOrId: String): String {
        if (nameOrId.isEmpty()) return ""
        val r = http("GET", "$base/api/v1/workflows?limit=100", h, null)
        if (r.code !in 200..299) return ""
        return try {
            val a = JSONObject(r.body).optJSONArray("data") ?: return ""
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                if (o.optString("id") == nameOrId || o.optString("name").equals(nameOrId, true)) return o.optString("id")
            }
            ""
        } catch (e: Exception) { "" }
    }

    private fun n8nRun(ctx: Context, id: String, name: String, action: String, arg: String): Out {
        val d = ConnectorStore.dynOf(ctx, id) ?: return Out(false, err = "Ye n8n connector nahi mila")
        val base = ConnectorCatalog.n8nBase(d.url)
        if (base.isEmpty()) return Out(false, err = "n8n ka address sahi nahi hai")
        val h = n8nHeaders(ConnectorStore.token(ctx, id))
        return when (action) {
            "workflows" -> list(http("GET", "$base/api/v1/workflows?limit=12", h, null), name, ConnectorParse::n8nWorkflows, "Koi workflow nahi mila")
            "executions" -> {
                val wf = arg.trim()
                val q = if (wf.isNotEmpty() && ConnectorRules.nameOk(wf)) "&workflowId=" + enc(wf) else ""
                list(http("GET", "$base/api/v1/executions?limit=8$q", h, null), name, ConnectorParse::n8nExecutions, "Koi run nahi mila")
            }
            "activate", "deactivate" -> {
                val key = arg.trim()
                if (key.isEmpty() || key.length > 100) Out(false, err = "Workflow ka naam ya id batao")
                else {
                    val wid = n8nWorkflowId(base, h, key)
                    if (wid.isEmpty() || !ConnectorRules.nameOk(wid)) Out(false, err = "n8n par '$key' workflow nahi mila")
                    else {
                        val r = http("POST", "$base/api/v1/workflows/$wid/$action", h, "{}")
                        if (r.code !in 200..299) Out(false, err = failFor(r.code, name))
                        else Out(true, listOf("Workflow " + (if (action == "activate") "ON" else "OFF") + " ho gaya: $key"))
                    }
                }
            }
            else -> Out(false, err = "n8n mein ye kaam nahi hai")
        }
    }

    private fun list(r: Res, name: String, f: (String) -> List<String>, empty: String): Out =
        if (r.code !in 200..299) Out(false, err = failFor(r.code, name))
        else f(r.body).let { if (it.isEmpty()) Out(true, listOf(empty)) else Out(true, it) }

    // ---- GitHub
    private fun github(t: String, a: String, arg: String): Out {
        val parts = ConnectorRules.args(arg)
        val repo = parts.getOrElse(0) { "" }
        fun needRepo() = !ConnectorRules.repoOk(repo)
        val msgRepo = "owner/repo likho, jaise vercel/next.js"
        return when (a) {
            "repos" -> list(http("GET", "https://api.github.com/user/repos?per_page=12&sort=updated", gh(t), null), "GitHub", ConnectorParse::githubRepos, "Koi repo nahi mila")
            "issues" -> if (needRepo()) Out(false, err = msgRepo) else
                list(http("GET", "https://api.github.com/repos/$repo/issues?state=open&per_page=12", gh(t), null), "GitHub", ConnectorParse::githubIssues, "Koi open issue nahi")
            "runs" -> if (needRepo()) Out(false, err = msgRepo) else
                list(http("GET", "https://api.github.com/repos/$repo/actions/runs?per_page=6", gh(t), null), "GitHub", ConnectorParse::githubRuns, "Koi workflow run nahi")
            "create_issue" -> {
                val title = parts.getOrElse(1) { "" }
                if (needRepo() || title.isEmpty() || title.length > 200) Out(false, err = "Aise likho: owner/repo | issue ka title")
                else {
                    val r = http("POST", "https://api.github.com/repos/$repo/issues", gh(t), JSONObject().put("title", title).put("body", "Created by NOVA").toString())
                    if (r.code !in 200..299) Out(false, err = failFor(r.code, "GitHub"))
                    else Out(true, listOf("Issue #" + (try { JSONObject(r.body).optInt("number") } catch (e: Exception) { 0 }) + " ban gaya", (try { JSONObject(r.body).optString("html_url") } catch (e: Exception) { "" })))
                }
            }
            "create_repo" -> {
                val name = parts.getOrElse(0) { "" }
                if (!ConnectorRules.nameOk(name)) Out(false, err = "Repo ka naam sirf letters, digits, - _ . mein likho")
                else {
                    val r = http("POST", "https://api.github.com/user/repos", gh(t), JSONObject().put("name", name).put("private", true).put("auto_init", true).toString())
                    if (r.code !in 200..299) Out(false, err = failFor(r.code, "GitHub"))
                    else Out(true, listOf("Private repo ban gaya: $name", (try { JSONObject(r.body).optString("html_url") } catch (e: Exception) { "" })))
                }
            }
            "commit_file" -> {
                val path = parts.getOrElse(1) { "" }
                val content = parts.drop(2).joinToString(" | ")
                if (needRepo() || path.isEmpty() || path.length > 200 || path.startsWith("/") || path.contains("..") || path.any { it == '\\' || it.isISOControl() } || content.length > 100_000)
                    Out(false, err = "Aise likho: owner/repo | folder/file.txt | content")
                else {
                    val encPath = path.split('/').joinToString("/") { enc(it).replace("+", "%20") }
                    val cur = http("GET", "https://api.github.com/repos/$repo/contents/$encPath", gh(t), null)
                    val sha = if (cur.code == 200) (try { JSONObject(cur.body).optString("sha") } catch (e: Exception) { "" }) else ""
                    if (cur.code != 200 && cur.code != 404) Out(false, err = failFor(cur.code, "GitHub"))
                    else {
                        val body = JSONObject().put("message", "NOVA: update $path")
                            .put("content", Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
                        if (sha.isNotEmpty()) body.put("sha", sha)
                        val r = http("PUT", "https://api.github.com/repos/$repo/contents/$encPath", gh(t), body.toString())
                        if (r.code !in 200..299) Out(false, err = failFor(r.code, "GitHub"))
                        else Out(true, listOf("Commit ho gaya: $repo/$path"))
                    }
                }
            }
            else -> Out(false, err = "GitHub mein ye kaam nahi hai")
        }
    }

    // ---- Vercel
    /** "" (personal account) plus up to 3 team scopes as "&teamId=..." suffixes. */
    private fun vercelScopes(h: Map<String, String>): List<String> {
        val out = ArrayList<String>()
        out.add("")
        val r = http("GET", "https://api.vercel.com/v2/teams?limit=20", h, null)
        if (r.code in 200..299) for (id in ConnectorParse.vercelTeamIds(r.body)) out.add("&teamId=" + enc(id))
        return out
    }

    private fun vercel(t: String, a: String, arg: String): Out {
        val h = bearer(t)
        return when (a) {
            "projects" -> {
                val lines = ArrayList<String>()
                var firstErr: Res? = null
                for (sc in vercelScopes(h)) {
                    val r = http("GET", "https://api.vercel.com/v9/projects?limit=12$sc", h, null)
                    if (r.code !in 200..299) { if (firstErr == null) firstErr = r; continue }
                    for (l in ConnectorParse.vercelProjects(r.body)) if (lines.size < 20) lines.add(l)
                }
                if (lines.isNotEmpty()) Out(true, lines)
                else if (firstErr != null) Out(false, err = failFor(firstErr.code, "Vercel"))
                else Out(true, listOf("Koi project nahi"))
            }
            "deployments" -> {
                val q = if (ConnectorRules.nameOk(arg.trim())) "&app=" + enc(arg.trim()) else ""
                var res: Out? = null
                for (sc in vercelScopes(h)) {
                    val r = http("GET", "https://api.vercel.com/v6/deployments?limit=6$q$sc", h, null)
                    val o = list(r, "Vercel", ConnectorParse::vercelDeployments, "")
                    if (!o.ok) { if (res == null) res = o; continue }
                    if (o.lines.any { it.isNotEmpty() }) return o
                }
                res ?: Out(true, listOf("Koi deployment nahi"))
            }
            "domains" -> {
                val lines = ArrayList<String>()
                var firstErr: Res? = null
                for (sc in vercelScopes(h)) {
                    val r = http("GET", "https://api.vercel.com/v5/domains?limit=12$sc", h, null)
                    if (r.code !in 200..299) { if (firstErr == null) firstErr = r; continue }
                    for (l in ConnectorParse.vercelDomains(r.body)) if (lines.size < 20) lines.add(l)
                }
                if (lines.isNotEmpty()) Out(true, lines)
                else if (firstErr != null) Out(false, err = failFor(firstErr.code, "Vercel"))
                else Out(true, listOf("Koi domain nahi"))
            }
            "redeploy" -> {
                val proj = arg.trim()
                if (!ConnectorRules.nameOk(proj)) Out(false, err = "Project ka naam batao")
                else {
                    var uid = ""
                    var scope = ""
                    var lastCode = 200
                    for (sc in vercelScopes(h)) {
                        val last = http("GET", "https://api.vercel.com/v6/deployments?limit=1&app=" + enc(proj) + sc, h, null)
                        lastCode = last.code
                        if (last.code !in 200..299) continue
                        val u = try { JSONObject(last.body).optJSONArray("deployments")?.optJSONObject(0)?.optString("uid") ?: "" } catch (e: Exception) { "" }
                        if (u.isNotEmpty()) { uid = u; scope = sc; break }
                    }
                    if (uid.isEmpty()) Out(false, err = if (lastCode !in 200..299) failFor(lastCode, "Vercel") else "Is project ka koi purana deployment nahi mila")
                    else {
                        val r = http("POST", "https://api.vercel.com/v13/deployments?forceNew=1$scope", h,
                            JSONObject().put("name", proj).put("deploymentId", uid).put("target", "production").toString())
                        if (r.code !in 200..299) Out(false, err = failFor(r.code, "Vercel"))
                        else Out(true, listOf("Redeploy shuru ho gaya: $proj", (try { JSONObject(r.body).optString("url").let { if (it.isEmpty()) "" else "https://$it" } } catch (e: Exception) { "" })))
                    }
                }
            }
            else -> Out(false, err = "Vercel mein ye kaam nahi hai")
        }
    }

    // ---- Netlify
    private fun netlifySiteId(t: String, nameOrId: String): String {
        val r = http("GET", "https://api.netlify.com/api/v1/sites?per_page=100", bearer(t), null)
        if (r.code !in 200..299) return ""
        return try {
            val a = JSONArray(r.body)
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                if (o.optString("name").equals(nameOrId, true) || o.optString("id") == nameOrId) return o.optString("id")
            }
            ""
        } catch (e: Exception) { "" }
    }

    private fun netlify(t: String, a: String, arg: String): Out {
        val h = bearer(t)
        return when (a) {
            "sites" -> list(http("GET", "https://api.netlify.com/api/v1/sites?per_page=12", h, null), "Netlify", ConnectorParse::netlifySites, "Koi site nahi")
            "deploys", "build" -> {
                val nm = arg.trim()
                if (!ConnectorRules.nameOk(nm)) Out(false, err = "Site ka naam batao")
                else {
                    val sid = netlifySiteId(t, nm)
                    if (sid.isEmpty()) Out(false, err = "Netlify par '$nm' site nahi mili")
                    else if (a == "deploys") list(http("GET", "https://api.netlify.com/api/v1/sites/$sid/deploys?per_page=6", h, null), "Netlify", ConnectorParse::netlifyDeploys, "Koi deploy nahi")
                    else {
                        val r = http("POST", "https://api.netlify.com/api/v1/sites/$sid/builds", h, "{}")
                        if (r.code !in 200..299) Out(false, err = failFor(r.code, "Netlify")) else Out(true, listOf("Naya build shuru ho gaya: $nm"))
                    }
                }
            }
            else -> Out(false, err = "Netlify mein ye kaam nahi hai")
        }
    }

    // ---- Cloudflare
    private fun cloudflare(t: String, a: String): Out {
        val h = bearer(t)
        return when (a) {
            "zones" -> list(http("GET", "https://api.cloudflare.com/client/v4/zones?per_page=12", h, null), "Cloudflare", ConnectorParse::cloudflareZones, "Koi zone nahi")
            "pages" -> {
                val acc = http("GET", "https://api.cloudflare.com/client/v4/accounts?per_page=1", h, null)
                if (acc.code !in 200..299) Out(false, err = failFor(acc.code, "Cloudflare"))
                else {
                    val aid = try { JSONObject(acc.body).optJSONArray("result")?.optJSONObject(0)?.optString("id") ?: "" } catch (e: Exception) { "" }
                    if (aid.isEmpty() || !aid.all { it.isLetterOrDigit() }) Out(false, err = "Token se account nahi dikha. Token mein Account read permission do")
                    else list(http("GET", "https://api.cloudflare.com/client/v4/accounts/$aid/pages/projects", h, null), "Cloudflare", ConnectorParse::cloudflarePages, "Koi Pages project nahi")
                }
            }
            else -> Out(false, err = "Cloudflare mein ye kaam nahi hai")
        }
    }

    // ---- Supabase
    private fun supabase(t: String, a: String): Out =
        if (a == "projects") list(http("GET", "https://api.supabase.com/v1/projects", bearer(t), null), "Supabase", ConnectorParse::supabaseProjects, "Koi project nahi")
        else Out(false, err = "Supabase mein ye kaam nahi hai")

    // ---- Notion
    private fun notionRun(t: String, a: String, arg: String): Out {
        if (a != "search") return Out(false, err = "Notion mein ye kaam nahi hai")
        val body = JSONObject().put("query", arg.trim().take(100)).put("page_size", 10).toString()
        return list(http("POST", "https://api.notion.com/v1/search", notion(t), body), "Notion", ConnectorParse::notionSearch, "Kuch nahi mila. Pages ko integration se share karna padta hai")
    }

    // ------------------------------------------------------------------ GitHub OAuth device login (optional)
    class Device(val deviceCode: String, val userCode: String, val url: String, val intervalSec: Int, val expiresSec: Int)

    fun deviceStart(clientId: String): Device? {
        if (clientId.isEmpty()) return null
        val r = http("POST", "https://github.com/login/device/code", mapOf("Accept" to "application/json"),
            "client_id=" + enc(clientId) + "&scope=" + enc("repo workflow read:user"), "application/x-www-form-urlencoded")
        if (r.code !in 200..299) return null
        return try {
            val j = JSONObject(r.body)
            val dc = j.optString("device_code")
            if (dc.isEmpty()) null else Device(dc, j.optString("user_code"), j.optString("verification_uri", "https://github.com/login/device"),
                j.optInt("interval", 5).coerceIn(5, 30), j.optInt("expires_in", 900).coerceIn(60, 900))
        } catch (e: Exception) { null }
    }

    /** "token:<value>", "wait", "slow", "denied" or "expired". */
    fun devicePoll(clientId: String, deviceCode: String): String {
        val r = http("POST", "https://github.com/login/oauth/access_token", mapOf("Accept" to "application/json"),
            "client_id=" + enc(clientId) + "&device_code=" + enc(deviceCode) + "&grant_type=" + enc("urn:ietf:params:oauth:grant-type:device_code"),
            "application/x-www-form-urlencoded")
        if (r.code !in 200..299) return "wait"
        return try {
            val j = JSONObject(r.body)
            val tk = j.optString("access_token")
            if (tk.isNotEmpty()) "token:$tk" else when (j.optString("error")) {
                "slow_down" -> "slow"
                "access_denied" -> "denied"
                "expired_token" -> "expired"
                else -> "wait"
            }
        } catch (e: Exception) { "wait" }
    }
}
