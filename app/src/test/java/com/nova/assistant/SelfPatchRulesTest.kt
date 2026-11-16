package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

class SelfPatchRulesTest {
    private val good = "package com.nova.assistant\n\nobject GenQuote {\n    private val q = listOf(\"a\", \"b { c\")\n    fun pick(i: Int): String = q[i % q.size]\n    // padding padding padding padding padding\n}\n"

    @Test fun phrases() {
        assertTrue(SelfPatchRules.isTrigger("jarvis khud code likho foo"))
        assertFalse(SelfPatchRules.isTrigger("apk banao"))
        assertTrue(SelfPatchRules.isStatus("code status"))
        assertTrue(SelfPatchRules.isUndo("code wapas karo"))
    }

    @Test fun hardDenyWishesBlocked() {
        assertTrue(SelfPatchRules.wishBlocked("upi pin bhejo"))
        assertTrue(SelfPatchRules.wishBlocked("password save karo"))
        assertTrue(SelfPatchRules.wishBlocked("build.yml badlo"))
        assertFalse(SelfPatchRules.wishBlocked("quote of the day jodo"))
    }

    @Test fun pathsProtected() {
        assertTrue(SelfPatchRules.isAllowedPath(SelfPatchRules.DIR + "GenQuote.kt"))
        assertFalse(SelfPatchRules.isAllowedPath(SelfPatchRules.DIR + "SecureStore.kt"))
        assertFalse(SelfPatchRules.isAllowedPath(".github/workflows/build.yml"))
        assertFalse(SelfPatchRules.isAllowedPath(SelfPatchRules.DIR + "../Evil.kt"))
    }

    @Test fun balancedIgnoresStrings() {
        assertTrue(SelfPatchRules.balanced(good))
        assertFalse(SelfPatchRules.balanced("object A {"))
    }

    @Test fun parseAndValidate() {
        val reply = "PATH: " + SelfPatchRules.DIR + "GenQuote.kt\nSUMMARY: quote\n<<<CODE\n" + good + "CODE>>>"
        val p = SelfPatchRules.parseReply(reply)
        assertNotNull(p)
        assertNull(SelfPatchRules.validate(p!!, null, 0))
        assertNotNull(SelfPatchRules.validate(p, SelfPatchRules.DIR + "Other.kt", 0))
        assertNotNull(SelfPatchRules.validate(SelfPatchRules.Patch(p.path, "x", good + "val x = SecureStore\n"), null, 0))
        assertNull(SelfPatchRules.parseReply("nothing"))
    }

    @Test fun capAndStub() {
        assertTrue(SelfPatchRules.underCap(4)); assertFalse(SelfPatchRules.underCap(5))
        assertTrue(SelfPatchRules.balanced(SelfPatchRules.STUB))
    }
}
