package com.nova.assistant

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v7.05 H: the pure parts of VercelPublish (needs the real org.json, like the other JSON tests). */
class VercelPublishTest {
    @Test fun projectNameIsSafe() {
        val n = VercelPublish.projectName("Mera Gym & Fitness!!", 12345L)
        assertTrue(n.matches(Regex("[a-z0-9-]+")))
        assertTrue(n.startsWith("jarvis-")); assertTrue(n.length <= 60)
    }
    @Test fun bodyHasIndexHtmlAndProduction() {
        val o = JSONObject(VercelPublish.buildBody("jarvis-x-1", "<html>hi \"q\"</html>"))
        assertEquals("jarvis-x-1", o.getString("name")); assertEquals("production", o.getString("target"))
        val f = o.getJSONArray("files").getJSONObject(0)
        assertEquals("index.html", f.getString("file")); assertEquals("utf-8", f.getString("encoding")); assertEquals("<html>hi \"q\"</html>", f.getString("data"))
    }
    @Test fun urlFromAliasThenUrl() {
        assertEquals("https://my-site.vercel.app", VercelPublish.parseUrl("{\"url\":\"jarvis-x-1-abc.vercel.app\",\"alias\":[\"my-site.vercel.app\"]}"))
        assertEquals("https://jarvis-x-1-abc.vercel.app", VercelPublish.parseUrl("{\"url\":\"jarvis-x-1-abc.vercel.app\"}"))
    }
    @Test fun neverInventsAUrl() {
        assertEquals("", VercelPublish.parseUrl("{}"))
        assertEquals("", VercelPublish.parseUrl("not json"))
        assertEquals("", VercelPublish.parseUrl("{\"url\":\"evil.com/x?a=b\"}"))
    }
}
