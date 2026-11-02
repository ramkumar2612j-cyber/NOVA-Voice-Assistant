package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v7.158 AUTOPILOT pure-rules tests (the prompt's 13 core tests + the intake / caps / idempotency rules).
 * Pure Kotlin: no Android, no network, no clock reads - everything is passed in.
 */
class AutopilotRulesTest {

    // ---------------------------------------------------------------- media kind

    @Test fun mediaKindFromNameAndMime() {
        assertEquals(AutopilotRules.Kind.IMAGE, AutopilotRules.kindOf("IMG-20260101-WA0001.jpg", ""))
        assertEquals(AutopilotRules.Kind.IMAGE, AutopilotRules.kindOf("photo.HEIC", ""))
        assertEquals(AutopilotRules.Kind.VIDEO, AutopilotRules.kindOf("VID-20260101-WA0002.mp4", ""))
        assertEquals(AutopilotRules.Kind.VIDEO, AutopilotRules.kindOf("clip", "video/mp4"))
        assertEquals(AutopilotRules.Kind.IMAGE, AutopilotRules.kindOf("clip", "image/webp"))
        assertEquals(AutopilotRules.Kind.OTHER, AutopilotRules.kindOf("doc.pdf", "application/pdf"))
    }

    // ---------------------------------------------------------------- group-title match

    @Test fun groupTitleMatch() {
        assertTrue(AutopilotRules.matchesGroup("SK Traders Team: Rahul", "SK Traders Team"))
        assertTrue(AutopilotRules.matchesGroup("SK Traders Team", "sk traders team "))
        assertFalse(AutopilotRules.matchesGroup("SK Traders Team: Rahul", "SK Traders"))
        assertFalse(AutopilotRules.matchesGroup("Rahul", "SK Traders Team"))
        assertFalse(AutopilotRules.matchesGroup("anything", ""))
        assertFalse(AutopilotRules.matchesGroup("", "SK Traders Team"))
    }

    // ---------------------------------------------------------------- arming

    @Test fun armedNeedsGroupAndOneTargetToken() {
        assertFalse(AutopilotRules.armed(true, "", true, true, true, true, true, true).ok)
        assertFalse(AutopilotRules.armed(true, "G", false, false, false, true, true, true).ok)
        assertFalse(AutopilotRules.armed(false, "G", true, true, true, true, true, true).ok)
        // nothing switched on at all
        val none = AutopilotRules.armed(true, "G", true, true, true, false, false, false)
        assertFalse(none.ok)
        assertTrue(none.reasons.any { it.contains("target") })
        // site only, token present
        assertTrue(AutopilotRules.armed(true, "G", true, false, false, true, false, false).ok)
        // IG on but token missing -> reason names Instagram
        val ig = AutopilotRules.armed(true, "G", true, false, false, true, true, false)
        assertFalse(ig.ok)
        assertTrue(ig.reasons.any { it.contains("Instagram") })
        assertTrue(AutopilotRules.armed(true, "G", false, true, false, false, true, false).ok)
    }

    // ---------------------------------------------------------------- IST slots

    @Test fun nextSlotAlwaysFutureAndInIst() {
        val zone = java.time.ZoneId.of(AutopilotRules.IST_ZONE)
        // 2026-11-02 08:00 IST -> today 09:00
        val t1 = java.time.ZonedDateTime.of(2026, 11, 2, 8, 0, 0, 0, zone).toInstant().toEpochMilli()
        val (ms1, label1) = AutopilotRules.nextSlot(t1)
        val z1 = java.time.Instant.ofEpochMilli(ms1).atZone(zone)
        assertEquals(9, z1.hour); assertEquals(0, z1.minute); assertEquals("09:00 IST", label1)
        // 23:30 IST -> tomorrow 09:00
        val t2 = java.time.ZonedDateTime.of(2026, 11, 2, 23, 30, 0, 0, zone).toInstant().toEpochMilli()
        val (ms2, _) = AutopilotRules.nextSlot(t2)
        val z2 = java.time.Instant.ofEpochMilli(ms2).atZone(zone)
        assertEquals(3, z2.dayOfMonth); assertEquals(9, z2.hour)
        // 12:00 IST -> 12:30 today
        val t3 = java.time.ZonedDateTime.of(2026, 11, 2, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        val (ms3, _) = AutopilotRules.nextSlot(t3)
        val z3 = java.time.Instant.ofEpochMilli(ms3).atZone(zone)
        assertEquals(12, z3.hour); assertEquals(30, z3.minute)
    }

    // ---------------------------------------------------------------- pack prompt / parse / fallback

    @Test fun packPromptCarriesVisionBrandOfferAndPastHooks() {
        val p = AutopilotRules.packPrompt("photo", "red cotton kurta on a mannequin", "SK Fashions", "20% off code SK20", "hinglish",
            listOf("Pehla hook", "Doosra hook"), listOf("site", "instagram", "youtube"))
        assertTrue(p.contains("red cotton kurta"))
        assertTrue(p.contains("SK Fashions"))
        assertTrue(p.contains("20% off code SK20"))
        assertTrue(p.contains("Pehla hook"))
        assertTrue(p.contains("JSON"))
        assertFalse(p.contains("price mat banao ki ijazat"))   // the rule text asks NOT to invent prices
        assertTrue(p.lowercase().contains("mat banao"))
    }

    @Test fun parsePackReadsJsonAndFence() {
        val json = """{"ig_caption":"Naya red kurta drop - full look reel me!","hashtags":["kurta","fashion","india"],
            "story_text":"Kaunsa colour?","poll":"Red ya blue?","yt_title":"Red Kurta Look","yt_desc":"Full video",
            "yt_tags":["kurta","look"],"site_blurb":"Red kurta now live","lang":"hinglish"}"""
        val p = AutopilotRules.parsePack(json)
        assertNotNull(p)
        assertEquals("Naya red kurta drop - full look reel me!", p!!.igCaption)
        assertTrue(p.hashtags.contains("kurta"))
        assertEquals("Kaunsa colour?", p.storyText)
        assertTrue(p.igHashtagLine().startsWith(" #kurta"))
        val fenced = "```json\n$json\n```"
        assertEquals(p.igCaption, AutopilotRules.parsePack(fenced)!!.igCaption)
        // a caption that is too short is unusable
        assertNull(AutopilotRules.parsePack("""{"ig_caption":"hi"}"""))
        assertNull(AutopilotRules.parsePack("not json at all"))
    }

    @Test fun fallbackPackIsHonestAndHasHashtags() {
        val p = AutopilotRules.fallbackPack("photo", "blue saree close-up", "SK Fashions", "Flat 20% off", "hinglish")
        assertTrue(p.igCaption.contains("SK Fashions"))
        assertTrue(p.igCaption.contains("blue saree"))
        assertTrue(p.igCaption.contains("Flat 20% off"))
        assertTrue(p.hashtags.isNotEmpty())
        assertTrue(p.hashtags.contains("madeinindia"))
        // never-repeat hook line exists
        assertTrue(AutopilotRules.hookLine(p).isNotBlank())
    }

    // ---------------------------------------------------------------- offer

    @Test fun offerExtractionOnlyFromRealOfferLines() {
        assertEquals("20% off on everything", AutopilotRules.extractOffer("Welcome!\n20% off on everything. Shipping extra"))
        assertEquals("Flat 100 off code SK20", AutopilotRules.extractOffer("Flat 100 off code SK20"))
        assertNull(AutopilotRules.extractOffer("Hello, this is our about page"))
        assertNull(AutopilotRules.extractOffer(""))
    }

    @Test fun offerJsonRoundTripAndCode() {
        val j = AutopilotRules.offerJson("Diwali sale 25% off", "DIW25", 123L)
        assertEquals("Diwali sale 25% off (code DIW25)", AutopilotRules.offerFromJson(j))
        assertEquals("DIW25", AutopilotRules.offerCode("nova offer 25% off code DIW25"))
        assertEquals("SK20", AutopilotRules.offerCode("20% off, coupon SK20 lagega"))
        assertEquals("", AutopilotRules.offerCode("sirf aaj ka discount"))
    }

    // ---------------------------------------------------------------- discount reply

    @Test fun discountReplyUsesLiveOfferOnly() {
        assertEquals("Website se order karoge to discount milega. Abhi chal raha hai: 20% off code SK20",
            AutopilotRules.discountReply("20% off code SK20", false))
        assertEquals("Website se order karoge to discount milega - abhi ka offer website par dikhega.",
            AutopilotRules.discountReply("", false))
        assertTrue(AutopilotRules.discountReply("Sale 10%", true).startsWith("Order from our website"))
    }

    // ---------------------------------------------------------------- order machine

    @Test fun orderMachineWalksAllSteps() {
        val o = AutopilotRules.Order("o1", "Rahul", "com.whatsapp.w4b")
        var r = AutopilotRules.step(o, "red kurta")
        assertTrue(r.save); assertEquals(AutopilotRules.Step.SIZE, o.step)
        r = AutopilotRules.step(o, "L")
        r = AutopilotRules.step(o, "2")
        assertEquals(2, o.qty)
        r = AutopilotRules.step(o, "Rahul Sharma")
        assertEquals(AutopilotRules.Step.PHONE, o.step)
        // a bad phone is refused - the machine stays on PHONE
        r = AutopilotRules.step(o, "not a phone")
        assertEquals(AutopilotRules.Step.PHONE, o.step)
        assertTrue(r.reply.contains("10"))
        r = AutopilotRules.step(o, "9876543210")
        assertEquals(AutopilotRules.Step.ADDRESS, o.step)
        r = AutopilotRules.step(o, "12 Nehru Road, Delhi 110001")
        assertEquals(AutopilotRules.Step.CONFIRM, o.step)
        assertTrue(r.reply.lowercase().contains("haan") || r.reply.contains("yes"))
        r = AutopilotRules.step(o, "haan")
        assertTrue(r.done); assertEquals(AutopilotRules.Step.DONE, o.step)
        assertTrue(r.reply.contains("Rahul Sharma"))
    }

    @Test fun orderMachineCancelResets() {
        val o = AutopilotRules.Order("o2", "A", "com.whatsapp.w4b")
        AutopilotRules.step(o, "item")
        AutopilotRules.step(o, "M")
        val r = AutopilotRules.step(o, "nahi")
        assertTrue(r.cancelled)
        assertEquals(AutopilotRules.Step.ITEM, o.step)
        assertEquals("", o.item)
    }

    @Test fun phoneValidation() {
        assertTrue(AutopilotRules.validPhone("9876543210"))
        assertTrue(AutopilotRules.validPhone("+91 98765 43210"))
        assertFalse(AutopilotRules.validPhone("12345"))
        assertFalse(AutopilotRules.validPhone("phone number"))
    }

    // ---------------------------------------------------------------- feed + igHost

    @Test fun feedWithPrependsAndCaps() {
        val first = AutopilotRules.feedWith("", "a1", "image", "https://x/1.jpg", "https://x/1.jpg", "one", 100L)
        assertTrue(first.contains("\"id\":\"a1\""))
        val second = AutopilotRules.feedWith(first, "a2", "video", "https://x/2.mp4", "https://x/2.jpg", "two", 200L)
        assertTrue(second.indexOf("\"id\":\"a2\"") in 0..second.indexOf("\"id\":\"a1\""))   // newest first
        // the cap holds even with a corrupt old file
        var f = first
        for (i in 0 until 250) f = AutopilotRules.feedWith(f, "x$i", "image", "u", "t", "b", 300L + i)
        assertTrue(MiniJson.parse(f)?.let { (it as? Map<*, *>)?.get("items").let { a -> (a as? List<*>)?.size ?: 0 } }!! <= AutopilotRules.FEED_CAP)
    }

    @Test fun igHostByTokenPrefix() {
        assertEquals("https://graph.instagram.com", AutopilotRules.igHost("IGQVJxyz"))
        assertEquals("https://graph.facebook.com", AutopilotRules.igHost("EAAGxyz"))
    }

    // ---------------------------------------------------------------- intake rules (task B)

    @Test fun sentVerdictIsNotBlind() {
        // AUTO only accepts when the open chat really IS the group
        assertEquals("auto", AutopilotRules.sentVerdict(AutopilotRules.SentMode.AUTO, "SK Traders Team", "SK Traders Team"))
        assertEquals("review", AutopilotRules.sentVerdict(AutopilotRules.SentMode.AUTO, "Rahul (personal)", "SK Traders Team"))
        assertEquals("review", AutopilotRules.sentVerdict(AutopilotRules.SentMode.AUTO, "", "SK Traders Team"))
        assertEquals("ask", AutopilotRules.sentVerdict(AutopilotRules.SentMode.ASK, "SK Traders Team", "SK Traders Team"))
        assertEquals("skip", AutopilotRules.sentVerdict(AutopilotRules.SentMode.OFF, "SK Traders Team", "SK Traders Team"))
    }

    @Test fun documentsGifVoiceAreIgnored() {
        assertTrue(AutopilotRules.ignoredKind("note.pdf", "application/pdf"))
        assertTrue(AutopilotRules.ignoredKind("voice.ogg", "audio/ogg"))
        assertTrue(AutopilotRules.ignoredKind("anim.gif", "image/gif"))
        assertTrue(AutopilotRules.ignoredKind("list.csv", "text/csv"))
        assertFalse(AutopilotRules.ignoredKind("IMG-1.jpg", "image/jpeg"))
        assertFalse(AutopilotRules.ignoredKind("VID-1.mp4", "video/mp4"))
    }

    @Test fun ninetySecondBatching() {
        assertTrue(AutopilotRules.batchable(1_000_000L, 1_000_000L + 89_000L))
        assertFalse(AutopilotRules.batchable(1_000_000L, 1_000_000L + 91_000L))
        assertFalse(AutopilotRules.batchable(0L, 5_000L))
        assertFalse(AutopilotRules.batchable(2_000_000L, 1_000_000L))   // clock went backwards: not a batch
    }

    @Test fun sha256HexIsStable() {
        val h1 = AutopilotRules.sha256Hex("nova".toByteArray())
        assertEquals(64, h1.length)
        assertEquals(h1, AutopilotRules.sha256Hex("nova".toByteArray()))
        assertFalse(h1 == AutopilotRules.sha256Hex("NOVA".toByteArray()))
    }

    // ---------------------------------------------------------------- caps / spacing / idempotency

    @Test fun capsAndSpacingBlockPolite() {
        val caps = AutopilotRules.Caps()   // 6 feed, 15 story, 5 yt, 45 min spacing
        assertNull(AutopilotRules.capReason("ig_feed", 5, 0L, 1_000_000L, caps))
        assertEquals("aaj ki Instagram post limit 6 ho gayi", AutopilotRules.capReason("ig_feed", 6, 0L, 1_000_000L, caps))
        assertEquals("aaj ki story limit 15 ho gayi", AutopilotRules.capReason("ig_story", 15, 0L, 1_000_000L, caps))
        assertEquals("aaj ki YouTube limit 5 ho gayi", AutopilotRules.capReason("yt", 5, 0L, 1_000_000L, caps))
        // spacing: last post 10 min ago -> must wait
        val why = AutopilotRules.capReason("ig_feed", 1, 1_000_000L, 1_000_000L + 10 * 60_000L, caps)
        assertNotNull(why); assertTrue(why!!.contains("35 minute"))
        // 50 min ago -> free
        assertNull(AutopilotRules.capReason("ig_feed", 1, 1_000_000L, 1_000_000L + 50 * 60_000L, caps))
        // configurable
        val custom = AutopilotRules.Caps(igFeed = 2, igStory = 3, yt = 1, spacingMin = 15)
        assertEquals("aaj ki Instagram post limit 2 ho gayi", AutopilotRules.capReason("ig_feed", 2, 0L, 1L, custom))
        assertNull(AutopilotRules.capReason("ig_feed", 1, 1_000_000L, 1_000_000L + 20 * 60_000L, custom))
        // the website target is never capped and never spacing-blocked
        assertNull(AutopilotRules.capReason("site", 999, 0L, 1L, caps))
        assertNull(AutopilotRules.capReason("site", 5, 1_000_000L, 1_000_000L + 1000L, caps))
    }

    @Test fun idempotencyKey() {
        assertEquals("job1|ig_feed", AutopilotRules.idemKey("job1", "ig_feed"))
        assertFalse(AutopilotRules.idemKey("job1", "ig_feed") == AutopilotRules.idemKey("job1", "yt"))
        assertFalse(AutopilotRules.idemKey("job1", "ig_feed") == AutopilotRules.idemKey("job2", "ig_feed"))
    }

    // ---------------------------------------------------------------- v7.158 Part 2: media prep rules (task C)

    @Test fun videoTruthHonoursRotation() {
        val v = AutopilotRules.VideoInfo(120_000L, 1920, 1080, 90, "video/mp4", "audio/mp4a-latm")
        assertEquals(1080, v.displayW())    // recorded landscape, displayed portrait
        assertEquals(1920, v.displayH())
        assertTrue(AutopilotRules.isHorizontal(AutopilotRules.VideoInfo(1000, 1920, 1080, 0, "video/mp4", "")))
        assertFalse(AutopilotRules.isHorizontal(v))
    }

    @Test fun shortsOnlyWhenReallyShort() {
        assertTrue(AutopilotRules.shortOk(2 * 60_000L))
        assertTrue(AutopilotRules.shortOk(3 * 60_000L))
        assertFalse(AutopilotRules.shortOk(3 * 60_000L + 1))
        assertFalse(AutopilotRules.shortOk(0))
    }

    @Test fun reencodeOnlyForWrongCodecs() {
        assertFalse(AutopilotRules.needsReencode(null))
        assertFalse(AutopilotRules.needsReencode(AutopilotRules.VideoInfo(1, 1080, 1920, 0, "video/avc", "audio/mp4a-latm")))
        assertFalse(AutopilotRules.needsReencode(AutopilotRules.VideoInfo(1, 1080, 1920, 0, "", "")))   // unknown = honest keep
        assertTrue(AutopilotRules.needsReencode(AutopilotRules.VideoInfo(1, 1080, 1920, 0, "video/hevc", "")))
        assertTrue(AutopilotRules.needsReencode(AutopilotRules.VideoInfo(1, 1080, 1920, 0, "video/avc", "audio/opus")))
    }

    @Test fun reencodeNeedsChargingAndWifi() {
        assertFalse(AutopilotRules.reencodeAllowed(false, true))
        assertFalse(AutopilotRules.reencodeAllowed(true, false))
        assertFalse(AutopilotRules.reencodeAllowed(false, false))
        assertTrue(AutopilotRules.reencodeAllowed(true, true))
    }

    @Test fun hostCapVerdictIs45Mb() {
        assertEquals("ok", AutopilotRules.hostVerdict(1024))
        assertEquals("ok", AutopilotRules.hostVerdict(AutopilotRules.HOST_CAP_BYTES))
        assertEquals("yt-only", AutopilotRules.hostVerdict(AutopilotRules.HOST_CAP_BYTES + 1))
        assertEquals("never", AutopilotRules.hostVerdict(0))
        assertEquals("never", AutopilotRules.hostVerdict(200_000_000L))
    }

    // ---------------------------------------------------------------- v7.158 Part 2: carousel (task D3)

    @Test fun carouselGroupsOfTenMax() {
        // a single photo stays a single post
        assertEquals(listOf(listOf("a")), AutopilotRules.carouselGroups(listOf("a")))
        // 2-10 photos = ONE carousel
        assertEquals(listOf((1..7).map { "p$it" }), AutopilotRules.carouselGroups((1..7).map { "p$it" }))
        // 12 photos = 10 + 2
        val g = AutopilotRules.carouselGroups((1..12).map { "p$it" })
        assertEquals(2, g.size)
        assertEquals(10, g[0].size)
        assertEquals(2, g[1].size)
    }

    // ---------------------------------------------------------------- v7.158 Part 2: never-repeat (task D5)

    private fun pack(cap: String, tags: List<String>) = AutopilotRules.Pack(cap, tags, "s", "p", "t", "d", tags, "b", "hinglish")

    @Test fun repeatRuleFlagsSameHookOrTags() {
        val p = pack("Naya red kurta aaya hai. Dekho", listOf("kurta", "fashion", "india"))
        assertNull(AutopilotRules.repeatReason(p, "", emptyList()))
        assertNull(AutopilotRules.repeatReason(p, "Purana hook", listOf("saree", "silk")))
        assertEquals("hook same hai", AutopilotRules.repeatReason(p, "naya red kurta aaya hai", listOf("saree")))
        assertEquals("hashtags same hain", AutopilotRules.repeatReason(p, "dusra hook", listOf("kurta", "fashion", "india")))
    }

    @Test fun varyPackChangesHookAndTags() {
        val p = pack("Naya red kurta aaya hai", listOf("kurta", "fashion", "india", "reels", "viral"))
        val v = AutopilotRules.varyPack(p)
        assertFalse(AutopilotRules.hookLine(v).equals(AutopilotRules.hookLine(p), true))
        assertTrue(v.hashtags.size >= p.hashtags.size)
        assertTrue(v.igCaption.contains("."))
    }

    // ---------------------------------------------------------------- v7.158 Part 2: slot tuning (task D4)

    @Test fun slotIndexBucketsByIstTime() {
        val z = java.time.ZoneId.of(AutopilotRules.IST_ZONE)
        fun at(h: Int, m: Int) = java.time.ZonedDateTime.of(2026, 11, 2, h, m, 0, 0, z).toInstant().toEpochMilli()
        assertEquals(0, AutopilotRules.slotIndex(at(9, 30)))     // 9:00 slot
        assertEquals(1, AutopilotRules.slotIndex(at(13, 0)))     // 12:30 slot
        assertEquals(5, AutopilotRules.slotIndex(at(22, 30)))    // 22:00 slot
        assertEquals(0, AutopilotRules.slotIndex(at(8, 0)))      // before the first slot -> first slot
    }

    @Test fun tunedOrderPrefersProvenSlots() {
        val avg = listOf(100L, 500L, 100L, 100L, 100L, 100L)
        val n = listOf(5, 5, 5, 5, 5, 5)
        val order = AutopilotRules.tunedSlotOrder(avg, n)
        assertEquals(1, order.first())                            // the 5x slot leads
        // too few samples -> fixed order stays
        assertEquals(listOf(0, 1, 2, 3, 4, 5), AutopilotRules.tunedSlotOrder(avg, listOf(0, 1, 0, 0, 0, 0)))
        // no spread -> fixed order
        assertEquals(listOf(0, 1, 2, 3, 4, 5), AutopilotRules.tunedSlotOrder(listOf(100L, 100L, 100L, 100L, 100L, 100L), n))
    }

    @Test fun viralSignalNeeds3xAndHistory() {
        assertFalse(AutopilotRules.viralSignal(300, 0))
        assertFalse(AutopilotRules.viralSignal(200, 100))
        assertTrue(AutopilotRules.viralSignal(300, 100))
    }
}
