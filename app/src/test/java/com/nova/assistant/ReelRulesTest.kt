package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

/** v7.114: reel / Instagram friend sentences -> Plan. Pure text, no Android. */
class ReelRulesTest {
    private fun p(s: String?, app: Boolean = false) = ReelRules.parse(s, app)

    @Test fun plainScrollIsOneNextReel() {
        val a = p("reel scroll karo")!!
        assertEquals(1, a.count); assertTrue(a.scroll); assertFalse(a.acts); assertNull(a.friend); assertFalse(a.auto)
    }
    @Test fun nextReelPhrases() {
        for (s in listOf("agli reel", "next reel", "dusri reel dikhao", "reel badlo", "nova agli reel chalao")) assertNotNull(s, p(s))
    }
    @Test fun previousReel() { assertTrue(p("pichli reel", true)!!.back); assertTrue(p("previous reel")!!.back) }
    @Test fun countAndDwell() {
        val a = p("10 reel scroll karo har reel 5 second dekho")!!
        assertEquals(10, a.count); assertEquals(5, a.dwellSec); assertTrue(a.scroll)
    }
    @Test fun hindiNumber() { assertEquals(5, p("paanch reels scroll karo")!!.count) }
    @Test fun keepScrolling() {
        val a = p("reels scroll karte raho")!!
        assertTrue(a.auto); assertEquals(ReelRules.MAX_SCROLL_ONLY, a.count)
    }
    @Test fun likeCurrentReel() {
        val a = p("is reel ko like karo", true)!!
        assertTrue(a.like); assertEquals(1, a.count); assertFalse(a.scroll); assertFalse(a.needsConfirm)
    }
    @Test fun likeWithoutAppOrReelWordIsNotOurs() {
        assertNull(p("mujhe ye gaana like hai"))
        assertNull(p("neeche scroll karo", false))
    }
    @Test fun directionScrollStaysOldRoute() {
        assertNull(p("scroll down karo", false))
        assertNull(p("upar scroll karo", false))
    }
    @Test fun plainScrollInsideReelApp() { assertNotNull(p("scroll karo", true)) }
    @Test fun saveAndCommentWithText() {
        val a = p("is reel ko save karo aur comment karo mast hai bhai", true)!!
        assertTrue(a.save); assertEquals("mast hai bhai", a.comment); assertTrue(a.needsConfirm)
    }
    @Test fun commentEndsAtNextAction() {
        val a = p("comment karo nice aur like karo", true)!!
        assertEquals("nice", a.comment); assertTrue(a.like)
    }
    @Test fun commentWithoutWordsGetsDefault() {
        val a = p("reel par comment karo", true)!!
        assertEquals("", a.comment)
        assertTrue(ReelRules.defaultComment(false, 0).isNotBlank())
        assertEquals(ReelRules.defaultComment(false, 1), ReelRules.commentFor(a, false, 1))
    }
    @Test fun commentCap() {
        val a = p("20 reel par comment karo mast", true)!!
        assertEquals(20, a.count); assertEquals(ReelRules.COMMENT_CAP, ReelRules.commentsIn(a))
        assertNull(ReelRules.commentFor(a, false, 10)); assertNotNull(ReelRules.commentFor(a, false, 9))
    }
    @Test fun friendProfileReels() {
        val a = p("instagram pe rahul_sharma ki id pe jao aur uski 5 reels pe like aur comment karo nice")!!
        assertEquals("rahul_sharma", a.friend); assertEquals(5, a.count); assertTrue(a.like); assertEquals("nice", a.comment); assertTrue(a.scroll)
    }
    @Test fun spokenUnderscoreAndDot() {
        assertEquals("rahul_sharma", ReelRules.friendOf("insta pe rahul underscore sharma ki reels like karo"))
        assertEquals("rahul.sharma", ReelRules.friendOf("rahul dot sharma ki id par jao reels like karo"))
    }
    @Test fun atHandle() { assertEquals("neha_01", ReelRules.friendOf("@neha_01 ki reels like karo")) }
    @Test fun dostWithoutUsernameIsAsked() {
        val a = p("dost ki id pe jao uski reels pe like karo")!!
        assertNull(a.friend); assertTrue(a.friendAsked)
    }
    @Test fun watchNewPost() {
        val a = p("rahul_sharma jaise hi post kare to like aur comment kar do nice")!!
        assertTrue(a.watch); assertEquals("rahul_sharma", a.friend); assertTrue(a.like); assertTrue(a.needsConfirm); assertEquals(1, a.count)
    }
    @Test fun shareNeedsName() {
        assertTrue(p("is reel ko share karo", true)!!.shareAsked)
        val a = p("is reel ko rahul ko share karo", true)!!
        assertEquals("rahul", a.shareTo); assertFalse(a.shareAsked); assertTrue(a.needsConfirm)
    }
    @Test fun otherRoutesAreNotTaken() {
        assertNull(p("reel me kya chal raha hai", true))
        assertNull(p("instagram kholo"))
        assertNull(p("ye video download karo", true))
        assertNull(p("rahul ko unfollow karo reel"))
        assertNull(p("ye reel kon si movie hai", true))
        assertNull(p(""))
        assertNull(p(null))
    }
    @Test fun countIsCapped() {
        assertEquals(ReelRules.MAX_ITEMS, p("500 reels like karo", true)!!.count)
        assertEquals(ReelRules.MAX_ITEMS, p("sabhi reels like karo", true)!!.count)
    }
    @Test fun textsAreHonest() {
        val a = p("5 reels like aur comment karo mast", true)!!
        val q = ReelRules.confirmQuestion(a, false)
        assertTrue(q.contains("like")); assertTrue(q.contains("mast"))
        assertTrue(ReelRules.resultText(5, 5, 0, 5, 0, a, false, "", false).contains("5 like"))
        assertTrue(ReelRules.isWatchOff("watch band karo"))
        assertFalse(ReelRules.isWatchOff("reel like karo"))
    }
}

class ReelWatchRulesTest {
    @Test fun friendPostNotifications() {
        assertTrue(ReelRules.isFriendPost("rahul_sharma", "Instagram", "rahul_sharma posted a new reel."))
        assertTrue(ReelRules.isFriendPost("rahul_sharma", "rahul_sharma", "shared a post"))
        assertTrue(ReelRules.isFriendPost("rahul_sharma", "Instagram", "Rahul Sharma uploaded a video"))
        assertFalse(ReelRules.isFriendPost("rahul_sharma", "Instagram", "rahul_sharma liked your photo"))
        assertFalse(ReelRules.isFriendPost("rahul_sharma", "Instagram", "rahul_sharma added to their story"))
        assertFalse(ReelRules.isFriendPost("rahul_sharma", "Instagram", "neha posted a new reel"))
        assertFalse(ReelRules.isFriendPost(null, "Instagram", "rahul posted"))
        assertFalse(ReelRules.isFriendPost("", "Instagram", "rahul posted"))
    }
}
