package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v6.86 connectors: provider JSON -> readable lines (sample replies written from the public API docs). */
class ConnectorParseTest {
    @Test fun githubRepos() {
        val j = """[{"full_name":"me/app","private":true},{"full_name":"me/site","private":false}]"""
        assertEquals(listOf("me/app (private)", "me/site"), ConnectorParse.githubRepos(j))
    }

    @Test fun githubIssuesSkipPullRequests() {
        val j = """[{"number":3,"title":"Bug"},{"number":4,"title":"PR","pull_request":{}}]"""
        assertEquals(listOf("#3 Bug"), ConnectorParse.githubIssues(j))
    }

    @Test fun githubRuns() {
        val j = """{"workflow_runs":[{"name":"CI","head_branch":"main","status":"completed","conclusion":"success"},{"name":"CI","head_branch":"dev","status":"in_progress","conclusion":null}]}"""
        val l = ConnectorParse.githubRuns(j)
        assertEquals("CI [main] success", l[0])
        assertTrue(l[1].contains("in_progress"))
    }

    @Test fun vercel() {
        assertEquals(listOf("shop (nextjs)", "blog"), ConnectorParse.vercelProjects("""{"projects":[{"name":"shop","framework":"nextjs"},{"name":"blog"}]}"""))
        val d = ConnectorParse.vercelDeployments("""{"deployments":[{"name":"shop","readyState":"READY","target":"production","url":"shop-abc.vercel.app"}]}""")
        assertEquals("shop READY (production) - shop-abc.vercel.app", d[0])
        assertEquals(listOf("a.com"), ConnectorParse.vercelDomains("""{"domains":[{"name":"a.com"}]}"""))
    }

    @Test fun netlifyCloudflareSupabase() {
        assertEquals("site1 - https://site1.netlify.app", ConnectorParse.netlifySites("""[{"name":"site1","ssl_url":"https://site1.netlify.app"}]""")[0])
        assertEquals("ready [main] 2026-10-01T10:00", ConnectorParse.netlifyDeploys("""[{"state":"ready","branch":"main","created_at":"2026-10-01T10:00:00Z"}]""")[0])
        assertEquals("a.com (active)", ConnectorParse.cloudflareZones("""{"result":[{"name":"a.com","status":"active"}]}""")[0])
        assertEquals("docs - docs.pages.dev", ConnectorParse.cloudflarePages("""{"result":[{"name":"docs","subdomain":"docs.pages.dev"}]}""")[0])
        assertEquals("db1 (ap-south-1, ACTIVE_HEALTHY)", ConnectorParse.supabaseProjects("""[{"name":"db1","region":"ap-south-1","status":"ACTIVE_HEALTHY"}]""")[0])
    }

    @Test fun notionSearchReadsPageAndDatabaseTitles() {
        val j = """{"results":[{"object":"page","properties":{"Name":{"type":"title","title":[{"plain_text":"Budget 2026"}]}}},{"object":"database","title":[{"plain_text":"Tasks"}]},{"object":"page","properties":{}}]}"""
        assertEquals(listOf("Budget 2026 [page]", "Tasks [database]", "(untitled) [page]"), ConnectorParse.notionSearch(j))
    }

    @Test fun mcpReplies() {
        val tools = """{"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"echo","description":"Echo text"},{"name":"add"}]}}"""
        assertEquals(listOf("echo - Echo text", "add"), ConnectorParse.mcpTools(tools))
        val call = """{"jsonrpc":"2.0","id":3,"result":{"content":[{"type":"text","text":"line1\nline2"},{"type":"image","data":"x"}]}}"""
        assertEquals(listOf("line1", "line2"), ConnectorParse.mcpCallText(call))
        val sse = "event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"tools\":[]}}\n\n"
        assertTrue(ConnectorParse.mcpJson(sse).startsWith("{"))
        assertEquals(emptyList<String>(), ConnectorParse.mcpTools(ConnectorParse.mcpJson(sse)))
    }

    @Test fun accountLabels() {
        assertEquals("octocat", ConnectorParse.account("github", """{"login":"octocat"}"""))
        assertEquals("shiv", ConnectorParse.account("vercel", """{"user":{"username":"shiv","email":"a@b.c"}}"""))
        assertEquals("a@b.c", ConnectorParse.account("vercel", """{"user":{"email":"a@b.c"}}"""))
        assertEquals("token active", ConnectorParse.account("cloudflare", """{"result":{"status":"active"}}"""))
        assertEquals("2 projects", ConnectorParse.account("supabase", """[{},{}]"""))
        assertEquals("Acme", ConnectorParse.account("notion", """{"bot":{"workspace_name":"Acme"}}"""))
        assertEquals("", ConnectorParse.account("github", "<html>not json</html>"))
    }

    @Test fun badJsonNeverThrowsAndLinesAreCapped() {
        assertEquals(emptyList<String>(), ConnectorParse.githubRepos("not json"))
        assertEquals(emptyList<String>(), ConnectorParse.vercelProjects(""))
        val big = (1..40).joinToString(",", "[", "]") { """{"full_name":"u/r$it"}""" }
        assertEquals(12, ConnectorParse.githubRepos(big).size)
        val long = ConnectorParse.githubRepos("""[{"full_name":"${"x".repeat(500)}"}]""")[0]
        assertTrue(long.length <= 110)
    }

    @Test fun mcpErrorsAreNotShownAsSuccess() {
        assertEquals("boom", ConnectorParse.mcpError("""{"jsonrpc":"2.0","id":3,"error":{"code":-32000,"message":"boom"}}"""))
        assertEquals("bad arg", ConnectorParse.mcpError("""{"result":{"isError":true,"content":[{"type":"text","text":"bad arg\nmore"}]}}"""))
        assertEquals(null, ConnectorParse.mcpError("""{"result":{"content":[{"type":"text","text":"ok"}]}}"""))
        assertEquals(null, ConnectorParse.mcpError("not json"))
    }

    @Test fun vercelTeamIdsAreCappedAndChecked() {
        val body = """{"teams":[{"id":"team_a1"},{"id":"bad id!"},{"id":"team_b2"},{"id":"team_c3"},{"id":"team_d4"}]}"""
        assertEquals(listOf("team_a1", "team_b2", "team_c3"), ConnectorParse.vercelTeamIds(body))
        assertEquals(emptyList<String>(), ConnectorParse.vercelTeamIds("nope"))
    }
}
