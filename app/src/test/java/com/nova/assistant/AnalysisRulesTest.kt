package com.nova.assistant

import com.nova.assistant.AnalysisRules.Gate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v7.52 Part 13A. Expected values worked out by hand. Uses only MiniJson (no org.json), so it runs in plain JUnit. */
class AnalysisRulesTest {
    private fun vis(
        summary: String = "s", seen: List<String> = emptyList(), text: List<String> = emptyList(), claims: List<String> = emptyList(),
        queries: List<String> = emptyList(), unc: List<String> = emptyList(), safety: String = "none", dropped: Int = 0
    ) = AnalysisRules.Vision(summary, seen, text, claims, queries, unc, safety, dropped)

    private fun res(n: Int) = AnalysisRules.Result("Title $n", "snippet $n", "https://site$n.com/page", "2026-10-1$n")

    private val GOOD = """{"summary":"Ek sadak par bheed hai","seen":["bheed","bus"],"text_seen":["SALE 50%"],"claims":["Sale 50% ka offer hai"],"search_queries":["store sale 50 percent offer"],"uncertain":[],"safety":"none"}"""

    // ---------------------------------------------------------------- gate

    @Test fun gateOkForNormalQuestions() {
        assertEquals(Gate.OK, AnalysisRules.gateQuestion(null))
        assertEquals(Gate.OK, AnalysisRules.gateQuestion(""))
        assertEquals(Gate.OK, AnalysisRules.gateQuestion("is photo me kya hai"))
        assertEquals(Gate.OK, AnalysisRules.gateQuestion("nova ye video analyse karo"))
        assertEquals(Gate.OK, AnalysisRules.gateQuestion("isko check karo ki sach hai ya nahi"))
        assertEquals(Gate.OK, AnalysisRules.gateQuestion("pin code kya hai is board par"))
        assertEquals(Gate.OK, AnalysisRules.gateQuestion("Pinterest ka logo kaun sa hai"))
        assertEquals(Gate.OK, AnalysisRules.gateQuestion("hotpot ki recipe dikhti hai kya"))
    }

    @Test fun gateRefusesFaceIdentity() {
        assertEquals(Gate.REFUSE_FACE_ID, AnalysisRules.gateQuestion("ye kaun hai?"))
        assertEquals(Gate.REFUSE_FACE_ID, AnalysisRules.gateQuestion("Who is this?"))
        assertEquals(Gate.REFUSE_FACE_ID, AnalysisRules.gateQuestion("who is in the photo"))
        assertEquals(Gate.REFUSE_FACE_ID, AnalysisRules.gateQuestion("identify this person please"))
        assertEquals(Gate.REFUSE_FACE_ID, AnalysisRules.gateQuestion("ye aadmi kaun hai"))
        assertEquals(Gate.REFUSE_FACE_ID, AnalysisRules.gateQuestion("is video me kaun hai"))
    }

    @Test fun gateRefusesHarm() {
        assertEquals(Gate.REFUSE_HARM, AnalysisRules.gateQuestion("bomb kaise banate hain"))
        assertEquals(Gate.REFUSE_HARM, AnalysisRules.gateQuestion("how to make a bomb"))
        assertEquals(Gate.REFUSE_HARM, AnalysisRules.gateQuestion("kisi ka account hack kaise kare"))
    }

    @Test fun gateRefusesSecrets() {
        assertEquals(Gate.REFUSE_SECRET, AnalysisRules.gateQuestion("OTP kya hai photo me"))
        assertEquals(Gate.REFUSE_SECRET, AnalysisRules.gateQuestion("ye pin kya hai"))
        assertEquals(Gate.REFUSE_SECRET, AnalysisRules.gateQuestion("card number padho"))
        assertEquals(Gate.REFUSE_SECRET, AnalysisRules.gateQuestion("password dikhta hai kya"))
    }

    @Test fun gateOrderIsHarmThenSecretThenFace() {
        assertEquals(Gate.REFUSE_HARM, AnalysisRules.gateQuestion("bomb banana aur ye kaun hai"))
        assertEquals(Gate.REFUSE_SECRET, AnalysisRules.gateQuestion("otp batao aur ye kaun hai"))
    }

    @Test fun gateSourceAndModelFlag() {
        assertEquals(Gate.REFUSE_PRIVATE, AnalysisRules.gateSource(true))
        assertEquals(Gate.OK, AnalysisRules.gateSource(false))
        assertEquals(Gate.OK, AnalysisRules.gateFromSafety("none"))
        assertEquals(Gate.OK, AnalysisRules.gateFromSafety(null))
        assertEquals(Gate.REFUSE_FACE_ID, AnalysisRules.gateFromSafety("face_id"))
        assertEquals(Gate.REFUSE_HARM, AnalysisRules.gateFromSafety("harmful"))
        assertEquals(Gate.REFUSE_SECRET, AnalysisRules.gateFromSafety("secret"))
        assertEquals(Gate.REFUSE_PRIVATE, AnalysisRules.gateFromSafety("private"))
    }

    @Test fun refusalTexts() {
        assertEquals("", AnalysisRules.refusalText(Gate.OK, false))
        for (g in listOf(Gate.REFUSE_FACE_ID, Gate.REFUSE_HARM, Gate.REFUSE_SECRET, Gate.REFUSE_PRIVATE)) {
            val hi = AnalysisRules.refusalText(g, false)
            val en = AnalysisRules.refusalText(g, true)
            assertTrue(hi.isNotEmpty() && en.isNotEmpty())
            assertTrue(hi != en)
        }
        assertTrue(AnalysisRules.refusalText(Gate.REFUSE_FACE_ID, false).contains("chehre"))
    }

    // ---------------------------------------------------------------- identity filter

    @Test fun identityClaims() {
        assertTrue(AnalysisRules.identityClaim("He is Rahul Kumar"))
        assertTrue(AnalysisRules.identityClaim("The man is Rahul Kumar wearing a suit"))
        assertTrue(AnalysisRules.identityClaim("Ye aadmi Rahul Sharma hai"))
        assertTrue(AnalysisRules.identityClaim("singer Arijit Singh on stage"))
        assertFalse(AnalysisRules.identityClaim("The man is wearing a red shirt"))
        assertFalse(AnalysisRules.identityClaim("a man standing near the Eiffel Tower"))
        assertFalse(AnalysisRules.identityClaim("Ye aadmi lal shirt me hai"))
        assertFalse(AnalysisRules.identityClaim("She is holding a phone"))
        assertFalse(AnalysisRules.identityClaim("A person is near Red Fort"))
        assertFalse(AnalysisRules.identityClaim(null))
        assertFalse(AnalysisRules.identityClaim(""))
    }

    // ---------------------------------------------------------------- clean

    @Test fun cleanRemovesUrlsControlCharsAndCaps() {
        assertEquals("see now", AnalysisRules.clean("see https://evil.com/x now"))
        assertEquals("go ok", AnalysisRules.clean("go www.evil.com/a ok"))
        assertEquals("a b", AnalysisRules.clean("a\nb"))
        assertEquals("", AnalysisRules.clean(null))
        assertEquals(600, AnalysisRules.clean("a".repeat(700)).length)
        assertEquals(10, AnalysisRules.clean("a".repeat(700), 10).length)
    }

    // ---------------------------------------------------------------- parseVision

    @Test fun parseVisionGood() {
        val v = AnalysisRules.parseVision(GOOD)
        assertNotNull(v)
        v!!
        assertEquals("Ek sadak par bheed hai", v.summary)
        assertEquals(listOf("bheed", "bus"), v.seen)
        assertEquals(listOf("SALE 50%"), v.textSeen)
        assertEquals(listOf("Sale 50% ka offer hai"), v.claims)
        assertEquals(listOf("store sale 50 percent offer"), v.queries)
        assertEquals(0, v.uncertain.size)
        assertEquals("none", v.safety)
        assertEquals(0, v.dropped)
    }

    @Test fun parseVisionAcceptsFence() {
        val v = AnalysisRules.parseVision("```json\n" + GOOD + "\n```")
        assertNotNull(v)
        assertEquals("Ek sadak par bheed hai", v!!.summary)
    }

    @Test fun parseVisionRejectsBadShapes() {
        assertNull(AnalysisRules.parseVision(null))
        assertNull(AnalysisRules.parseVision(""))
        assertNull(AnalysisRules.parseVision("photo me ek bus hai"))
        assertNull(AnalysisRules.parseVision("""{"seen":["a"]}"""))                                  // summary missing
        assertNull(AnalysisRules.parseVision("""{"summary":"a","seen":"x"}"""))                      // list expected
        assertNull(AnalysisRules.parseVision("""{"summary":"a","seen":[1]}"""))                      // string expected
        assertNull(AnalysisRules.parseVision("""{"summary":"a","safety":"weird"}"""))                // unknown safety
        assertNull(AnalysisRules.parseVision("""{"summary":"a","safety":3}"""))
        assertNull(AnalysisRules.parseVision("""{"summary":5}"""))
        assertNull(AnalysisRules.parseVision("""["summary"]"""))
        assertNull(AnalysisRules.parseVision("""{"summary":"a"} extra"""))
        assertNull(AnalysisRules.parseVision("{\"summary\":\"" + "x".repeat(13000) + "\"}"))          // over MAX_RAW
    }

    @Test fun parseVisionMinimalAndUnknownKeysIgnored() {
        val v = AnalysisRules.parseVision("""{"summary":"ek photo","url":"https://evil.com","extra":[1,2]}""")
        assertNotNull(v)
        assertEquals("ek photo", v!!.summary)
        assertEquals(0, v.seen.size)
        assertEquals("none", v.safety)
    }

    @Test fun parseVisionSafetyFlagEmptiesEverything() {
        val v = AnalysisRules.parseVision("""{"summary":"bank screen hai","seen":["x"],"claims":["y"],"safety":"face_id"}""")
        assertNotNull(v)
        v!!
        assertEquals("face_id", v.safety)
        assertEquals("", v.summary)
        assertEquals(0, v.seen.size)
        assertEquals(0, v.claims.size)
    }

    @Test fun parseVisionStripsEveryUrl() {
        val v = AnalysisRules.parseVision(
            """{"summary":"x http://evil.com y","seen":["www.evil.com","bus"],"text_seen":["go to https://evil.com now"],"claims":["see https://evil.com/a"],"search_queries":["https://evil.com","ok query here"]}"""
        )
        assertNotNull(v)
        v!!
        assertEquals("x y", v.summary)
        assertEquals(listOf("bus"), v.seen)
        assertEquals(listOf("go to now"), v.textSeen)
        assertEquals(listOf("see"), v.claims)
        assertEquals(listOf("ok query here"), v.queries)
        val all = listOf(v.summary) + v.seen + v.textSeen + v.claims + v.queries
        assertTrue(all.none { it.contains("http") || it.contains("www") })
    }

    @Test fun parseVisionDropsIdentityLinesButKeepsWrittenText() {
        val v = AnalysisRules.parseVision(
            """{"summary":"He is Rahul Kumar","seen":["He is Rahul Kumar","ek bus"],"text_seen":["He is Rahul Kumar"],"claims":["Ye aadmi Rahul Sharma hai","Bus 5 baje chalti hai"]}"""
        )
        assertNotNull(v)
        v!!
        assertEquals(listOf("ek bus"), v.seen)
        assertEquals("ek bus", v.summary)                              // empty summary falls back to the first seen line
        assertEquals(listOf("He is Rahul Kumar"), v.textSeen)          // written text is not filtered
        assertEquals(listOf("Bus 5 baje chalti hai"), v.claims)
        assertEquals(3, v.dropped)                                     // seen line + claim + summary
    }

    @Test fun parseVisionCapsLists() {
        val claims = (1..8).joinToString(",") { "\"claim number $it\"" }
        val seen = (1..12).joinToString(",") { "\"line $it\"" }
        val v = AnalysisRules.parseVision("{\"summary\":\"s\",\"seen\":[$seen],\"claims\":[$claims]}")
        assertNotNull(v)
        assertEquals(6, v!!.claims.size)
        assertEquals(8, v.seen.size)
    }

    @Test fun parseVisionRemovesDuplicatesIgnoringCase() {
        val v = AnalysisRules.parseVision("""{"summary":"s","seen":["Bus","bus","BUS","car"]}""")
        assertEquals(listOf("Bus", "car"), v!!.seen)
    }

    // ---------------------------------------------------------------- merge + batches

    @Test fun mergeVision() {
        assertNull(AnalysisRules.mergeVision(emptyList()))
        val a = vis(summary = "A", seen = listOf("x", "y"), claims = listOf("c1"), dropped = 1)
        val b = vis(summary = "B", seen = listOf("Y", "z"), claims = listOf("c2"), safety = "private", dropped = 2)
        val m = AnalysisRules.mergeVision(listOf(a, b))!!
        assertEquals("A B", m.summary)
        assertEquals(listOf("x", "y", "z"), m.seen)
        assertEquals(listOf("c1", "c2"), m.claims)
        assertEquals("private", m.safety)
        assertEquals(3, m.dropped)
        assertEquals("none", AnalysisRules.mergeVision(listOf(a))!!.safety)
    }

    @Test fun batchSizes() {
        assertEquals(emptyList<Int>(), AnalysisRules.batchSizes(0))
        assertEquals(listOf(1), AnalysisRules.batchSizes(1))
        assertEquals(listOf(3), AnalysisRules.batchSizes(3))
        assertEquals(listOf(6), AnalysisRules.batchSizes(6))
        assertEquals(listOf(4, 3), AnalysisRules.batchSizes(7))
        assertEquals(listOf(6, 6), AnalysisRules.batchSizes(12))
        assertEquals(listOf(6, 6), AnalysisRules.batchSizes(13))     // at most 2 calls, 12 frames
        assertEquals(listOf(6, 6), AnalysisRules.batchSizes(14))
        assertEquals(emptyList<Int>(), AnalysisRules.batchSizes(5, 0))
    }

    // ---------------------------------------------------------------- queries

    @Test fun cleanQuery() {
        assertEquals("What is the Eiffel tower height", AnalysisRules.cleanQuery("  What is  the  \"Eiffel\" tower height  "))
        assertEquals("", AnalysisRules.cleanQuery("ab"))
        assertEquals("", AnalysisRules.cleanQuery("https://evil.com/x"))
        assertEquals("", AnalysisRules.cleanQuery("He is Rahul Kumar"))
        assertEquals("", AnalysisRules.cleanQuery("otp kya hai"))
        assertEquals("pin code delhi", AnalysisRules.cleanQuery("pin code delhi"))
        assertEquals(79, AnalysisRules.cleanQuery("word ".repeat(30).trim()).length)   // cut at 80, back to the last space
    }

    @Test fun buildQueries() {
        assertEquals(emptyList<String>(), AnalysisRules.buildQueries(null, "kuch bhi sawal"))
        assertEquals(emptyList<String>(), AnalysisRules.buildQueries(vis(safety = "private", queries = listOf("q one")), "x"))
        assertEquals(listOf("q one", "q two"), AnalysisRules.buildQueries(vis(queries = listOf("q one", "q two", "Q ONE")), null))
        assertEquals(listOf("claim alpha long text"), AnalysisRules.buildQueries(vis(claims = listOf("claim alpha long text")), null))
        assertEquals(emptyList<String>(), AnalysisRules.buildQueries(vis(), "kya ye sach hai"))                // nothing to check, no search
        assertEquals(listOf("kya ye sach hai"), AnalysisRules.buildQueries(vis(claims = listOf("ab")), "kya ye sach hai"))
        assertEquals(4, AnalysisRules.buildQueries(vis(queries = (1..6).map { "query number $it" }), null).size)
    }

    // ---------------------------------------------------------------- urls + results

    @Test fun validUrl() {
        assertEquals("https://example.com/a?x=1&utm_source=z", AnalysisRules.validUrl("https://example.com/a?x=1&utm_source=z"))
        assertEquals("https://example.com/Path", AnalysisRules.validUrl("HTTPS://Example.COM/Path"))
        assertEquals("https://example.com", AnalysisRules.validUrl("https://example.com"))
        assertEquals("https://example.com:8080/x", AnalysisRules.validUrl("https://example.com:8080/x"))
        assertNull(AnalysisRules.validUrl(null))
        assertNull(AnalysisRules.validUrl("http://example.com/x"))
        assertNull(AnalysisRules.validUrl("javascript:alert(1)"))
        assertNull(AnalysisRules.validUrl("https://localhost/x"))
        assertNull(AnalysisRules.validUrl("https://127.0.0.1/x"))
        assertNull(AnalysisRules.validUrl("https://user@evil.com/"))
        assertNull(AnalysisRules.validUrl("https://exa mple.com/"))
        assertNull(AnalysisRules.validUrl("https://example/"))
        assertNull(AnalysisRules.validUrl("https://example.com:abc/x"))
        assertNull(AnalysisRules.validUrl("https://.example.com/"))
        assertNull(AnalysisRules.validUrl("https://a..b.com/"))
        assertNull(AnalysisRules.validUrl("https://exa_mple.com/"))
        assertNull(AnalysisRules.validUrl("https://example.com/" + "a".repeat(500)))
    }

    @Test fun hostAndCanon() {
        assertEquals("example.com", AnalysisRules.hostOf("https://example.com:8080/x?y"))
        assertEquals("a.com", AnalysisRules.hostOf("https://a.com"))
        assertEquals(AnalysisRules.canonUrl("https://a.com/x"), AnalysisRules.canonUrl("https://a.com/x/#top"))
    }

    @Test fun sanitizeResults() {
        val raw = listOf(
            AnalysisRules.Result("First https://evil.com/x page", "snip", "https://a.com/x", "2026-10-01"),
            AnalysisRules.Result("Dup", "snip", "https://a.com/x/#top", ""),
            AnalysisRules.Result("Bad scheme", "snip", "http://b.com/x", ""),
            AnalysisRules.Result("", "snippet", "https://c.com/y", "")
        )
        val out = AnalysisRules.sanitizeResults(raw)
        assertEquals(2, out.size)
        assertEquals("First page", out[0].title)
        assertEquals("c.com", out[1].title)                       // empty title = host
        val many = (1..12).map { res(it) }
        assertEquals(8, AnalysisRules.sanitizeResults(many).size)
        assertEquals(300, AnalysisRules.sanitizeResults(listOf(AnalysisRules.Result("t", "s".repeat(500), "https://a.com/x", ""))).first().snippet.length)
    }

    // ---------------------------------------------------------------- compose answer

    @Test fun sameClaim() {
        assertTrue(AnalysisRules.sameClaim("Sale 50% offer", "sale 50 offer"))
        assertFalse(AnalysisRules.sameClaim("abc", "abc d"))
        assertTrue(AnalysisRules.sameClaim("ye store 1990 me bana tha", "1990 me bana"))
        assertFalse(AnalysisRules.sameClaim("", "abc"))
    }

    @Test fun parseComposeBindsClaimsToRealNumbers() {
        val raw = """{"summary":"ok","verified":[{"claim":"Sale 50% ka offer hai","src":[1,2]},{"claim":"Ye store 1990 me bana","src":[7]},{"claim":"Dusra dawa","src":[]}],"unverified":["Teesra dawa"]}"""
        val c = AnalysisRules.parseCompose(raw, 2)
        assertNotNull(c)
        c!!
        assertEquals("ok", c.summary)
        assertEquals(listOf(AnalysisRules.Claim("Sale 50% ka offer hai", listOf(1, 2))), c.verified)
        assertEquals(listOf("Ye store 1990 me bana", "Dusra dawa", "Teesra dawa"), c.unverified)
    }

    @Test fun parseComposeNumbersAreCleaned() {
        val c = AnalysisRules.parseCompose("""{"verified":[{"claim":"A claim","src":[2,1,2,1.5,"a",0,-1]}]}""", 2)!!
        assertEquals(listOf(AnalysisRules.Claim("A claim", listOf(1, 2))), c.verified)
        val none = AnalysisRules.parseCompose("""{"verified":[{"claim":"A claim","src":[1]}]}""", 0)!!
        assertEquals(0, none.verified.size)
        assertEquals(listOf("A claim"), none.unverified)
    }

    @Test fun parseComposeRejectsBadShapes() {
        assertNull(AnalysisRules.parseCompose(null, 2))
        assertNull(AnalysisRules.parseCompose("sab sahi hai", 2))
        assertNull(AnalysisRules.parseCompose("""{"verified":"x"}""", 2))
        assertNull(AnalysisRules.parseCompose("""{"verified":["x"]}""", 2))
        assertNull(AnalysisRules.parseCompose("""{"verified":[{"src":[1]}]}""", 2))
        assertNull(AnalysisRules.parseCompose("""{"verified":[{"claim":"a","src":"1"}]}""", 2))
        assertNull(AnalysisRules.parseCompose("""{"unverified":[1]}""", 2))
        assertNull(AnalysisRules.parseCompose("""{"summary":4}""", 2))
    }

    @Test fun parseComposeOtherRules() {
        val fenced = AnalysisRules.parseCompose("```json\n{\"summary\":\"see https://evil.com ok\"}\n```", 1)!!
        assertEquals("see ok", fenced.summary)
        val dup = AnalysisRules.parseCompose("""{"verified":[{"claim":"Sale 50% ka offer hai","src":[1]}],"unverified":["sale 50% ka offer hai","Alag dawa"]}""", 1)!!
        assertEquals(listOf("Alag dawa"), dup.unverified)                      // a verified claim is not also unverified
        val id = AnalysisRules.parseCompose("""{"summary":"He is Rahul Kumar","verified":[{"claim":"He is Rahul Kumar","src":[1]}],"unverified":["Ye aadmi Rahul Sharma hai"]}""", 1)!!
        assertEquals("", id.summary)
        assertEquals(0, id.verified.size)
        assertEquals(0, id.unverified.size)
        assertEquals(3, id.dropped)
    }

    // ---------------------------------------------------------------- confidence

    @Test fun confidenceTable() {
        val M = AnalysisRules.MODE_METADATA
        val F = AnalysisRules.MODE_FULL
        val V = AnalysisRules.MODE_VISION_ONLY
        assertEquals("low", AnalysisRules.confidence(M, 0, 0, 0, 0, false).first)
        assertEquals(Pair("high", "4/5 dawe sources se mile"), AnalysisRules.confidence(F, 4, 4, 1, 0, false))
        assertEquals("high", AnalysisRules.confidence(F, 4, 2, 0, 0, false).first)
        assertEquals("medium", AnalysisRules.confidence(F, 1, 1, 0, 0, false).first)           // one verified claim is never "high"
        assertEquals(Pair("low", "sirf 1/4 dawe verify hue"), AnalysisRules.confidence(F, 4, 1, 3, 0, false))
        assertEquals(Pair("low", "koi dawa verify nahi hua"), AnalysisRules.confidence(V, 1, 0, 2, 0, false))
        assertEquals("medium", AnalysisRules.confidence(V, 1, 0, 0, 0, false).first)
        assertEquals("low", AnalysisRules.confidence(V, 0, 0, 0, 0, false).first)
        assertEquals("low", AnalysisRules.confidence(V, 1, 0, 0, 1, false).first)
        val down = AnalysisRules.confidence(F, 4, 4, 1, 3, false)
        assertEquals("medium", down.first)
        assertTrue(down.second.contains("kai cheezein"))
        assertEquals("low", AnalysisRules.confidence(F, 4, 1, 1, 3, false).first)             // medium goes down to low
        assertEquals(Pair("high", "4/5 claims found in sources"), AnalysisRules.confidence(F, 4, 4, 1, 0, true))
    }

    // ---------------------------------------------------------------- report

    @Test fun reportFullEveryClaimIsBoundOrUnverified() {
        val v = vis(summary = "Ek dukaan ka board", seen = listOf("board"), text = listOf("SALE 50%"), claims = listOf("Sale 50% ka offer hai", "Dukaan 1990 me bani"))
        val composed = AnalysisRules.Composed("Board par sale likha hai", listOf(AnalysisRules.Claim("Sale 50% ka offer hai", listOf(1))), emptyList(), 0)
        val r = AnalysisRules.buildReport(v, composed, listOf(res(1), res(2)), 1, false, true, false)
        assertEquals("full", r.mode)
        assertEquals("Board par sale likha hai", r.summary)
        assertEquals(1, r.verified.size)
        assertEquals(listOf("Dukaan 1990 me bani"), r.unverified)
        assertEquals(2, r.sources.size)
        assertEquals(Pair("medium", "1/2 dawe sources se mile"), Pair(r.confidence, r.why))
        assertEquals(2, r.limits.size)                                   // face line + source-is-not-proof line
    }

    @Test fun reportWithoutSearchMarksEverythingUnverified() {
        val v = vis(claims = listOf("Sale 50% ka offer hai", "Dukaan 1990 me bani"))
        val r = AnalysisRules.buildReport(v, null, emptyList(), 1, false, false, false)
        assertEquals("vision_only", r.mode)
        assertEquals(0, r.verified.size)
        assertEquals(2, r.unverified.size)
        assertEquals("low", r.confidence)
        assertEquals("koi dawa verify nahi hua", r.why)
        assertTrue(r.limits.any { it.contains("Web search nahi chali") })
    }

    @Test fun reportMisalignedResultsNeverVerify() {
        val composed = AnalysisRules.Composed("s", listOf(AnalysisRules.Claim("A claim text", listOf(1))), emptyList(), 0)
        val bad = AnalysisRules.Result("Bad", "x", "http://nope.com/x", "")
        val r = AnalysisRules.buildReport(vis(claims = listOf("A claim text")), composed, listOf(res(1), bad), 1, false, true, false)
        assertEquals(0, r.verified.size)
        assertEquals(listOf("A claim text"), r.unverified)
    }

    @Test fun reportClaimOutOfRangeNumberIsUnverified() {
        val composed = AnalysisRules.Composed("s", listOf(AnalysisRules.Claim("A claim text", listOf(3))), emptyList(), 0)
        val r = AnalysisRules.buildReport(vis(claims = listOf("A claim text")), composed, listOf(res(1)), 1, false, true, false)
        assertEquals(0, r.verified.size)
        assertEquals(listOf("A claim text"), r.unverified)
    }

    @Test fun reportDescriptionOnlyAndFallbacks() {
        val r = AnalysisRules.buildReport(vis(), null, emptyList(), 1, false, false, false)
        assertEquals("medium", r.confidence)
        assertEquals(1, r.limits.size)                                    // nothing to check, so no "search did not run" line
        val empty = AnalysisRules.buildReport(vis(summary = ""), null, emptyList(), 1, false, false, false)
        assertEquals("Koi saaf cheez nahi dikhi.", empty.summary)
        val enEmpty = AnalysisRules.buildReport(vis(summary = ""), null, emptyList(), 1, false, false, true)
        assertEquals("Nothing clear was visible.", enEmpty.summary)
        val fromSeen = AnalysisRules.buildReport(vis(summary = "", seen = listOf("ek bus")), null, emptyList(), 1, false, false, false)
        assertEquals("ek bus", fromSeen.summary)
    }

    @Test fun reportLimitsMentionFramesAndDroppedLines() {
        val r = AnalysisRules.buildReport(vis(dropped = 2), null, emptyList(), 4, true, false, false)
        assertTrue(r.limits.contains("Sirf 4 frames dekhe, poori video nahi."))
        assertTrue(r.limits.any { it.contains("hata di gayi") })
        val en = AnalysisRules.buildReport(vis(), null, emptyList(), 4, true, false, true)
        assertTrue(en.limits.contains("Only 4 frames were looked at, not the whole video."))
    }

    // ---------------------------------------------------------------- metadata (offline) report

    @Test fun sizeAndDurationText() {
        assertEquals("0 B", AnalysisRules.sizeText(0))
        assertEquals("500 B", AnalysisRules.sizeText(500))
        assertEquals("1.5 KB", AnalysisRules.sizeText(1536))
        assertEquals("1.0 MB", AnalysisRules.sizeText(1_048_576))
        assertEquals("5.0 MB", AnalysisRules.sizeText(5_242_880))
        assertEquals("?", AnalysisRules.sizeText(-1))
        assertEquals("", AnalysisRules.durationText(0))
        assertEquals("1 sec", AnalysisRules.durationText(500))
        assertEquals("12 sec", AnalysisRules.durationText(12_400))
        assertEquals("1 min 0 sec", AnalysisRules.durationText(59_500))
        assertEquals("1 min 5 sec", AnalysisRules.durationText(65_000))
    }

    @Test fun metadataReportIsHonest() {
        val r = AnalysisRules.metadataReport(true, 5_242_880, 12_400, false)
        assertEquals("metadata_only", r.mode)
        assertEquals("Images offline analyse nahi ho sakti. Sirf file ki jankari: video, 5.0 MB, 12 sec.", r.summary)
        assertEquals("low", r.confidence)
        assertEquals(0, r.seen.size + r.textSeen.size + r.verified.size + r.unverified.size + r.sources.size)
        assertEquals(2, r.limits.size)
        val p = AnalysisRules.metadataReport(false, 500, 99_000, true)
        assertEquals("Images cannot be analysed offline. Only file facts: photo, 500 B.", p.summary)   // a photo has no duration
    }

    // ---------------------------------------------------------------- json + text

    @Test fun toJsonRoundTrip() {
        val v = vis(summary = "Ek \"board\"\nnaya", seen = listOf("board"), text = listOf("SALE 50%"), claims = listOf("Sale 50% ka offer hai"))
        val composed = AnalysisRules.Composed("Ek \"board\"\nnaya", listOf(AnalysisRules.Claim("Sale 50% ka offer hai", listOf(1))), emptyList(), 0)
        val r = AnalysisRules.buildReport(v, composed, listOf(res(1)), 1, false, true, false)
        val o = MiniJson.parse(AnalysisRules.toJson(r))
        assertEquals("full", MiniJson.field(o, "mode"))
        assertEquals("Ek \"board\"\nnaya", MiniJson.field(o, "summary"))
        assertEquals("SALE 50%", MiniJson.at(MiniJson.field(o, "text_seen"), 0))
        val ver0 = MiniJson.at(MiniJson.field(o, "verified"), 0)
        assertEquals("Sale 50% ka offer hai", MiniJson.field(ver0, "claim"))
        assertEquals(1.0, MiniJson.at(MiniJson.field(ver0, "src"), 0))
        val s0 = MiniJson.at(MiniJson.field(o, "sources"), 0)
        assertEquals(1.0, MiniJson.field(s0, "n"))
        assertEquals("https://site1.com/page", MiniJson.field(s0, "url"))
        assertEquals("medium", MiniJson.field(o, "confidence"))       // 1 of 1 verified is medium, never high (see confidenceTable)
    }

    @Test fun reportTextHasNoAddressAndSpeechIsShort() {
        val v = vis(summary = "Ek board", seen = listOf("board"), text = listOf("SALE"), claims = listOf("Sale ka offer hai"))
        val composed = AnalysisRules.Composed("Ek board hai", listOf(AnalysisRules.Claim("Sale ka offer hai", listOf(1))), emptyList(), 0)
        val r = AnalysisRules.buildReport(v, composed, listOf(res(1)), 1, false, true, false)
        val txt = AnalysisRules.reportText(r, false)
        assertTrue(txt.startsWith("Ek board hai"))
        assertTrue(txt.contains("Kya dikha:"))
        assertTrue(txt.contains("Sale ka offer hai [1]"))
        assertTrue(txt.contains("[1] Title 1 (site1.com)"))
        assertTrue(txt.contains("Bharosa: medium"))
        assertFalse(txt.contains("https://"))
        assertTrue(AnalysisRules.reportText(r, true).contains("What I saw:"))
        assertEquals("Ek board hai Bharosa medium.", AnalysisRules.speechSummary(r, false))
        assertEquals("Ek board hai Confidence medium.", AnalysisRules.speechSummary(r, true))
    }

    // ---------------------------------------------------------------- prompts

    @Test fun visionPromptsCarryTheSafetyRules() {
        val hi = AnalysisRules.visionSystemPrompt(false)
        val en = AnalysisRules.visionSystemPrompt(true)
        for (p in listOf(hi, en)) {
            assertTrue(p.contains("NEVER name or identify a real person"))
            assertTrue(p.contains("ONE JSON object"))
            assertTrue(p.contains("Never write a web address"))
            assertTrue(p.contains("face_id") && p.contains("harmful") && p.contains("private") && p.contains("secret"))
            assertTrue(p.contains("At most 6") && p.contains("at most 4"))
        }
        assertTrue(hi.contains("Hinglish"))
        assertFalse(en.contains("Hinglish"))
        assertTrue(en.contains("simple English"))
    }

    @Test fun visionUserPrompt() {
        val vid = AnalysisRules.visionUserPrompt("  kya sach hai?  ", true, 4, listOf(0, 3, 6, 9), false)
        assertTrue(vid.startsWith("Question: kya sach hai?\n"))
        assertTrue(vid.contains("Media: a video, 4 frames taken at seconds 0, 3, 6, 9."))
        assertTrue(vid.contains("Analyse the attached frames now."))
        val photo = AnalysisRules.visionUserPrompt("", false, 1, emptyList(), true)
        assertTrue(photo.contains("(none, describe it"))
        assertTrue(photo.contains("Media: one photo."))
        assertTrue(photo.contains("Analyse the attached photo now."))
    }

    @Test fun composePromptsNumberTheRealResultsAndNeverShowAddresses() {
        val v = vis(summary = "Ek board", claims = listOf("Sale 50% ka offer hai", "Dukaan 1990 me bani"))
        val p = AnalysisRules.composeUserPrompt("sach hai?", v, listOf(res(1), res(2)), false)
        assertTrue(p.contains("1. Sale 50% ka offer hai\n2. Dukaan 1990 me bani\n"))
        assertTrue(p.contains("[1] Title 1 - snippet 1 (2026-10-11)\n"))
        assertTrue(p.contains("[2] Title 2 - snippet 2 (2026-10-12)\n"))
        assertFalse(p.contains("https://"))
        val empty = AnalysisRules.composeUserPrompt(null, vis(), emptyList(), false)
        assertTrue(empty.contains("Claims:\n(none)\n") && empty.contains("Web results:\n(none)\n"))
        val sys = AnalysisRules.composeSystemPrompt(true)
        assertTrue(sys.contains("Never write a web address") && sys.contains("exactly as given") && sys.contains("ONE JSON object"))
    }

    // ---------------------------------------------------------------- stage lines

    @Test fun stageMessages() {
        assertEquals("Frames nikal rahe hain 3/6", AnalysisRules.stageMessage("FRAMES", 3, 6, false))
        assertEquals("Extracting frames 3/6", AnalysisRules.stageMessage("FRAMES", 3, 6, true))
        assertEquals("AI tasveer dekh raha hai", AnalysisRules.stageMessage("VISION", 0, 0, false))
        assertEquals("Checking on the web 4/4", AnalysisRules.stageMessage("SEARCH", 9, 4, true))
        assertEquals("Report ban rahi hai", AnalysisRules.stageMessage("COMPOSE", 0, 0, false))
        assertEquals("Report taiyar", AnalysisRules.stageMessage("DONE", 1, 1, false))
        assertEquals("", AnalysisRules.stageMessage("NOPE", 1, 1, false))
        for (s in AnalysisStage.STAGES) assertTrue(AnalysisRules.stageMessage(s, 1, 2, false).isNotEmpty())
    }
}
