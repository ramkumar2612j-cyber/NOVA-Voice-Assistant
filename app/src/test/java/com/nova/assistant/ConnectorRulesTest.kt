package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v6.86 connectors: voice parsing, permission levels, token sniffing, URL safety. Pure JVM. */
class ConnectorRulesTest {
    private fun p(s: String) = ConnectorRules.parse(s)

    @Test fun readCommandsRouteToTheRightConnector() {
        assertEquals("repos", p("nova github repos dikhao")!!.action)
        assertEquals(ConnectorCatalog.GITHUB, p("github mere repos dikhao")!!.connector)
        val i = p("github issues vercel/next.js")!!
        assertEquals("issues", i.action); assertEquals("vercel/next.js", i.arg)
        assertEquals("runs", p("github workflow runs owner/app")!!.action)
        assertEquals("deployments", p("nova vercel deployments dikhao")!!.action)
        assertEquals("projects", p("vercel projects")!!.action)
        assertEquals("domains", p("vercel domains batao")!!.action)
        assertEquals("sites", p("netlify sites dikhao")!!.action)
        assertEquals("zones", p("cloudflare zones dikhao")!!.action)
        assertEquals("pages", p("cloudflare pages projects")!!.action)
        assertEquals("projects", p("supabase projects dikhao")!!.action)
        assertEquals("open", p("lovable kholo")!!.action)
    }

    @Test fun writeCommandsCarryTheirArgument() {
        val r = p("vercel redeploy karo mysite")!!
        assertEquals("redeploy", r.action); assertEquals("mysite", r.arg)
        assertTrue(ConnectorApi.isWrite(r.connector, r.action))
        val b = p("netlify build karo shop")!!
        assertEquals("build", b.action); assertEquals("shop", b.arg)
        val c = p("github naya repo banao myapp")!!
        assertEquals("create_repo", c.action); assertEquals("myapp", c.arg)
        val i = p("github issue banao acme/web login bug")!!
        assertEquals("create_issue", i.action); assertEquals("acme/web | login bug", i.arg)
        assertTrue(ConnectorApi.isWrite(ConnectorCatalog.GITHUB, "create_issue"))
        assertFalse(ConnectorApi.isWrite(ConnectorCatalog.GITHUB, "repos"))
    }

    @Test fun notionSearchTakesTheQuery() {
        val s = p("nova notion mein budget dhundo")!!
        assertEquals("search", s.action); assertEquals("budget", s.arg)
    }

    @Test fun connectAndDisconnectAreRecognised() {
        assertEquals("connect", p("github connect karo")!!.action)
        assertEquals("connect", p("vercel jodo")!!.action)
        assertEquals("disconnect", p("github disconnect")!!.action)
        assertEquals("disconnect", p("netlify hatao")!!.action)
        assertEquals("list", p("connectors kholo")!!.action)
        assertEquals("", p("connectors dikhao")!!.connector)
    }

    @Test fun ordinarySentencesAreNeverHijacked() {
        assertNull(p("ek github jaisi website banao jisme meri photo ho aur bahut saari cheezein"))
        assertNull(p("battery kitni hai"))
        assertNull(p("nova torch jala do"))
        assertNull(p("mujhe vercel ke baare mein ek lamba kahani sunao jo kabhi khatam na ho"))
        assertNull(p(""))
        assertNotNull(p("github"))
    }

    @Test fun permissionLevels() {
        assertTrue(ConnectorCatalog.allows("read", false))
        assertFalse(ConnectorCatalog.allows("read", true))
        assertTrue(ConnectorCatalog.allows("write", true))
        assertTrue(ConnectorCatalog.allows("write", false))
        assertFalse(ConnectorCatalog.allows("off", false))
        assertFalse(ConnectorCatalog.allows("bogus", false))
        assertEquals("off", ConnectorCatalog.normPerm("admin"))
        assertEquals("write", ConnectorCatalog.normPerm("write"))
    }

    @Test fun everyActionIsKnownAndWriteFlagsMatchTheCatalog() {
        for (d in ConnectorCatalog.ALL) for (a in d.actions) assertEquals(a.write, ConnectorApi.isWrite(d.id, a.id))
        assertTrue("unknown actions count as write", ConnectorApi.isWrite(ConnectorCatalog.GITHUB, "delete_everything"))
        assertTrue(ConnectorApi.isWrite("mcp_ab1", "call"))
        assertFalse(ConnectorApi.isWrite("mcp_ab1", "tools"))
        // read-only providers have no write action at all
        for (id in listOf(ConnectorCatalog.CLOUDFLARE, ConnectorCatalog.SUPABASE, ConnectorCatalog.NOTION)) {
            assertTrue(ConnectorCatalog.byId(id)!!.actions.none { it.write })
        }
    }

    @Test fun tokenSniffingOnlyAcceptsTokenShapes() {
        assertTrue(ConnectorCatalog.looksLikeToken("github", "ghp_" + "a".repeat(36)))
        assertTrue(ConnectorCatalog.looksLikeToken("github", "github_pat_" + "A1_".repeat(20)))
        assertFalse(ConnectorCatalog.looksLikeToken("github", "hello world this is not a token"))
        assertFalse(ConnectorCatalog.looksLikeToken("github", "https://github.com/settings/tokens/new?scopes=repo"))
        assertTrue(ConnectorCatalog.looksLikeToken("supabase", "sbp_" + "0123456789abcdef".repeat(3)))
        assertFalse(ConnectorCatalog.looksLikeToken("supabase", "ghp_" + "a".repeat(36)))
        assertTrue(ConnectorCatalog.looksLikeToken("vercel", "A".repeat(24)))
        assertFalse(ConnectorCatalog.looksLikeToken("vercel", "short"))
        assertFalse(ConnectorCatalog.looksLikeToken("notion", "ntn_ with space " + "a".repeat(30)))
        assertTrue(ConnectorCatalog.looksLikeToken("notion", "ntn_" + "a".repeat(40)))
    }

    @Test fun customMcpAddressMustBePublicHttps() {
        assertTrue(ConnectorCatalog.mcpUrlOk("https://mcp.example.com/mcp"))
        assertFalse(ConnectorCatalog.mcpUrlOk("http://mcp.example.com/mcp"))
        assertFalse(ConnectorCatalog.mcpUrlOk("https://localhost/mcp"))
        assertFalse(ConnectorCatalog.mcpUrlOk("https://192.168.1.5/mcp"))
        assertFalse(ConnectorCatalog.mcpUrlOk("https://10.0.0.2:8443/mcp"))
        assertFalse(ConnectorCatalog.mcpUrlOk("https://127.0.0.1/x"))
        assertFalse(ConnectorCatalog.mcpUrlOk("https://169.254.169.254/latest"))
        assertFalse(ConnectorCatalog.mcpUrlOk("https://user:pw@evil.example.com/x"))
        assertFalse(ConnectorCatalog.mcpUrlOk("https://printer.local/x"))
        assertFalse(ConnectorCatalog.mcpUrlOk("https://singlelabel/x"))
        assertFalse(ConnectorCatalog.mcpUrlOk("file:///etc/passwd"))
        assertTrue(ConnectorCatalog.isMcpId("mcp_abc12"))
        assertFalse(ConnectorCatalog.isMcpId("github"))
        assertFalse(ConnectorCatalog.isMcpId("mcp_"))
    }

    @Test fun inputShapesAreValidated() {
        assertTrue(ConnectorRules.repoOk("vercel/next.js"))
        assertFalse(ConnectorRules.repoOk("../etc/passwd"))
        assertFalse(ConnectorRules.repoOk("a/b/c"))
        assertFalse(ConnectorRules.repoOk("a b/c"))
        assertTrue(ConnectorRules.nameOk("my-site_1"))
        assertFalse(ConnectorRules.nameOk("my site"))
        assertFalse(ConnectorRules.nameOk("x?y=1"))
        assertEquals(listOf("a", "b", "c"), ConnectorRules.args("a | b |c"))
    }

    @Test fun catalogHasTheRequestedServices() {
        val ids = ConnectorCatalog.ALL.map { it.id }
        for (want in listOf("github", "vercel", "lovable", "netlify", "cloudflare", "supabase", "notion")) assertTrue(want, want in ids)
        // every real provider opens an https token page; Lovable is honest that it has no API
        for (d in ConnectorCatalog.ALL) assertTrue(d.tokenUrl.startsWith("https://"))
        assertEquals("link", ConnectorCatalog.byId("lovable")!!.kind)
    }

    @Test fun permissionLevelByVoice() {
        assertEquals("perm_write", p("github write on karo")!!.action)
        assertEquals("perm_write", p("nova vercel likhne ki ijazat do")!!.action)
        assertEquals("perm_read", p("github read only karo")!!.action)
        assertEquals("perm_read", p("netlify write off karo")!!.action)
        assertEquals("perm_off", p("notion off karo")!!.action)
        assertEquals(ConnectorCatalog.VERCEL, p("vercel write on karo")!!.connector)
    }

    @Test fun plansChainOnlyOnExplicitSequenceWords() {
        val a = ConnectorRules.parsePlan("github repos dikhao phir vercel deployments dikhao")!!
        assertEquals(2, a.size)
        assertEquals("repos", a[0].action); assertEquals(ConnectorCatalog.VERCEL, a[1].connector); assertEquals("deployments", a[1].action)
        // a later step without a service name uses the previous service
        val b = ConnectorRules.parsePlan("github repos dikhao uske baad issues owner/app dikhao")!!
        assertEquals(ConnectorCatalog.GITHUB, b[1].connector); assertEquals("issues", b[1].action); assertEquals("owner/app", b[1].arg)
        val c = ConnectorRules.parsePlan("github naya repo banao myapp and then vercel projects dikhao")!!
        assertEquals("create_repo", c[0].action); assertEquals("myapp", c[0].arg)
        assertTrue(ConnectorApi.isWrite(c[0].connector, c[0].action))
        // plain "aur" is part of a title, not a separator
        assertNull(ConnectorRules.parsePlan("github issue banao acme/web login aur signup bug"))
        // one step is not a plan; unknown or non-connector steps cancel the whole plan
        assertNull(ConnectorRules.parsePlan("github repos dikhao"))
        assertNull(ConnectorRules.parsePlan("github repos dikhao phir battery kitni hai"))
        assertNull(ConnectorRules.parsePlan("github repos dikhao phir vercel projects phir netlify sites phir cloudflare zones phir supabase projects"))
    }

    @Test fun connectFlowTracksOneWaitingConnectorWithAnExpiry() {
        ConnectorFlow.clear()
        assertEquals("", ConnectorFlow.active())
        ConnectorFlow.start("github", 1_000L)
        assertEquals("github", ConnectorFlow.active(2_000L))
        assertEquals("", ConnectorFlow.active(1_000L + ConnectorFlow.WAIT_MS))
        ConnectorFlow.start("vercel", 5_000L)
        assertEquals("vercel", ConnectorFlow.active(6_000L))
        ConnectorFlow.clear()
        assertEquals("", ConnectorFlow.active(6_000L))
    }

    @Test fun previousStepReferences() {
        assertEquals(ConnectorRules.LAST, p("github us repo ke issues dikhao")!!.arg)
        assertEquals("issues", p("github pehle repo ke issues dikhao")!!.action)
        val r = p("vercel usi project ko redeploy karo")!!
        assertEquals("redeploy", r.action); assertEquals(ConnectorRules.LAST, r.arg)
        assertEquals(ConnectorRules.LAST, p("netlify us site ka build karo")!!.arg)
        val plan = ConnectorRules.parsePlan("github repos dikhao phir us repo ke issues dikhao")!!
        assertEquals("repos", plan[0].action); assertEquals("issues", plan[1].action); assertEquals(ConnectorRules.LAST, plan[1].arg)
        // an explicit name still wins
        assertEquals("a/b", p("github issues a/b")!!.arg)
    }

    @Test fun fileCommitByVoice() {
        val c = p("github file banao acme/web notes/todo.txt kal ka kaam")!!
        assertEquals("commit_file", c.action)
        assertEquals("acme/web | notes/todo.txt | kal ka kaam", c.arg)
        assertTrue(ConnectorApi.isWrite(c.connector, c.action))
        assertNull("no path -> not a commit", p("github file banao acme/web bahut sundar")?.takeIf { it.action == "commit_file" })
    }

    // ---- v6.97 automation connectors (written, NOT RUN: no Kotlin compile in the sandbox)
    private val items = listOf(Pair("hook_a1", "morning flow"), Pair("n8n_b2", "n8n"), Pair("mcp_c3", "mera server"))

    @Test fun webhookNeedsAnExplicitStartWord() {
        val c = ConnectorRules.parseDynamic("nova morning flow chalao", items)!!
        assertEquals("hook_a1", c.connector); assertEquals("trigger", c.action)
        val m = ConnectorRules.parseDynamic("morning flow ko message bhejo kal subah jaldi uthna", items)!!
        assertEquals("trigger", m.action); assertTrue(m.arg.contains("kal subah"))
        assertNull("a long sentence that only mentions the name is not a command", ConnectorRules.parseDynamic("mujhe batao morning flow kab banaya tha maine", items))
        assertTrue(ConnectorApi.isWrite("hook_a1", "trigger"))
    }

    @Test fun n8nVoiceActions() {
        assertEquals("workflows", ConnectorRules.parseDynamic("n8n workflows dikhao", items)!!.action)
        assertEquals("executions", ConnectorRules.parseDynamic("n8n runs dikhao", items)!!.action)
        val a = ConnectorRules.parseDynamic("n8n workflow activate karo daily report", items)!!
        assertEquals("activate", a.action); assertEquals("daily report", a.arg)
        assertEquals("deactivate", ConnectorRules.parseDynamic("n8n workflow pause karo daily report", items)!!.action)
        assertTrue(ConnectorApi.isWrite("n8n_b2", "activate")); assertFalse(ConnectorApi.isWrite("n8n_b2", "workflows"))
    }

    @Test fun dynamicIdsAndBase() {
        assertTrue(ConnectorCatalog.isHookId("hook_abc1")); assertTrue(ConnectorCatalog.isN8nId("n8n_x9")); assertFalse(ConnectorCatalog.isHookId("hook_"))
        assertTrue(ConnectorCatalog.isDynId("mcp_zz")); assertFalse(ConnectorCatalog.isDynId("github"))
        assertEquals("https://me.app.n8n.cloud", ConnectorCatalog.n8nBase("https://me.app.n8n.cloud/api/v1/"))
        assertEquals("", ConnectorCatalog.n8nBase("http://me.app.n8n.cloud"))
        assertEquals("", ConnectorCatalog.n8nBase("https://localhost:5678"))
        assertEquals("", ConnectorCatalog.n8nBase("https://192.168.1.5"))
    }

    @Test fun n8nParsers() {
        assertEquals(listOf("12 Daily report [on]", "13 Backup [off]"),
            ConnectorParse.n8nWorkflows("""{"data":[{"id":"12","name":"Daily report","active":true},{"id":"13","name":"Backup","active":false}]}"""))
        val ex = ConnectorParse.n8nExecutions("""{"data":[{"id":5,"workflowId":"12","status":"error","startedAt":"2026-10-15T08:30:00.000Z"}]}""")
        assertTrue(ex[0].contains("error") && ex[0].contains("2026-10-15 08:30"))
        assertEquals(emptyList<String>(), ConnectorParse.n8nWorkflows("nope"))
    }
}
