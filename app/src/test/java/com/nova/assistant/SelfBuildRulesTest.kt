package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

class SelfBuildRulesTest {
    @Test fun triggerPhrases() {
        assertTrue(SelfBuildRules.isTrigger("jarvis apk banao"))
        assertTrue(SelfBuildRules.isTrigger("khud apk banao"))
        assertFalse(SelfBuildRules.isTrigger("apk status"))
        assertFalse(SelfBuildRules.isTrigger("gaana bajao"))
    }

    @Test fun statusPhrases() {
        assertTrue(SelfBuildRules.isStatus("apk status"))
        assertTrue(SelfBuildRules.isStatus("build kaha tak"))
        assertFalse(SelfBuildRules.isStatus("apk banao"))
    }

    @Test fun installPhrases() {
        assertTrue(SelfBuildRules.isInstall("apk install karo"))
        assertFalse(SelfBuildRules.isInstall("apk banao"))
    }

    @Test fun pickRunPicksWorkflowDispatchOnMainAfterSince() {
        val json = """{"workflow_runs":[
          {"event":"push","head_branch":"main","created_at":"2026-11-16T10:00:00Z","status":"completed"},
          {"event":"workflow_dispatch","head_branch":"feature","created_at":"2026-11-16T10:00:00Z","status":"completed"},
          {"event":"workflow_dispatch","head_branch":"main","created_at":"2026-11-16T09:00:00Z","status":"completed"},
          {"event":"workflow_dispatch","head_branch":"main","created_at":"2026-11-16T10:05:00Z","status":"in_progress"}
        ]}"""
        val since = SelfBuildRules.parseIso("2026-11-16T10:05:00Z")
        val r = SelfBuildRules.pickRun(json, since)
        assertNotNull(r)
        assertEquals("in_progress", r!!.optString("status"))
    }

    @Test fun pickRunReturnsNullWhenNoneMatch() {
        val json = """{"workflow_runs":[{"event":"push","head_branch":"main","created_at":"2026-11-16T10:00:00Z"}]}"""
        assertNull(SelfBuildRules.pickRun(json, SelfBuildRules.parseIso("2026-11-16T10:00:00Z")))
    }

    @Test fun pickRunHandlesGarbageJson() {
        assertNull(SelfBuildRules.pickRun("not json", 0L))
        assertNull(SelfBuildRules.pickRun("{}", 0L))
    }

    @Test fun pickAssetsFindsApkAndSha() {
        val json = """{"assets":[
          {"name":"test-report.zip","id":1},
          {"name":"app-debug.apk","id":2},
          {"name":"app-debug.apk.sha256","id":3}
        ]}"""
        val (apk, sha) = SelfBuildRules.pickAssets(json)
        assertNotNull(apk); assertEquals(2L, apk!!.optLong("id"))
        assertNotNull(sha); assertEquals(3L, sha!!.optLong("id"))
    }

    @Test fun pickAssetsMissingApk() {
        val (apk, sha) = SelfBuildRules.pickAssets("""{"assets":[{"name":"readme.txt","id":1}]}""")
        assertNull(apk); assertNull(sha)
    }

    @Test fun authOnlyForGitHubApi() {
        assertTrue(SelfBuildRules.authFor("api.github.com"))
        assertTrue(SelfBuildRules.authFor("API.GITHUB.COM"))
        assertFalse(SelfBuildRules.authFor("objects.githubusercontent.com"))
        assertFalse(SelfBuildRules.authFor("evil.example.com"))
    }

    @Test fun firstTokenHandlesShaSumFormat() {
        assertEquals("abc123", SelfBuildRules.firstToken("abc123  app-debug.apk"))
        assertEquals("abc123", SelfBuildRules.firstToken("ABC123"))
        assertEquals("", SelfBuildRules.firstToken(""))
    }

    @Test fun phaseLabelsAreNonEmptyForEveryPhase() {
        val phases = listOf(
            SelfBuildRules.IDLE, SelfBuildRules.DISPATCHING, SelfBuildRules.QUEUED, SelfBuildRules.RUNNING,
            SelfBuildRules.GREEN, SelfBuildRules.RED, SelfBuildRules.DOWNLOADING, SelfBuildRules.READY,
            SelfBuildRules.INSTALLED, SelfBuildRules.ERROR
        )
        for (p in phases) assertTrue(p, SelfBuildRules.phaseLabel(p).isNotEmpty())
    }
}
