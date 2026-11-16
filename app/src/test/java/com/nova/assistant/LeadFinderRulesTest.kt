package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

/**
 * v7.179 LEAD FINDER rules unit tests. Mirrors tools/test_lead_finder_contract.py but as a
 * real JUnit test (so the GitHub Actions build runs it). Tests the NEW API:
 * parse, score, filter, deny, genuineOnly. The old v7.169 Google-Places-only API (parse(json),
 * mobile(), pick(), remainingToday(), requestBody()) was replaced — see git history.
 */
class LeadFinderRulesTest {

    @Test fun isLeadJobAcceptsSearchPhrases() {
        assertTrue(LeadFinderRules.isLeadJob("jarvis delhi me restaurant ke genuine leads dhoondo"))
        assertTrue(LeadFinderRules.isLeadJob("jarvis mumbai me salon ke clients lao"))
        assertTrue(LeadFinderRules.isLeadJob("jaipur me gym ke business dhundke la"))
    }

    @Test fun isLeadJobRejectsNonSearch() {
        assertFalse(LeadFinderRules.isLeadJob("rahul ko call karo"))          // plain call
        assertFalse(LeadFinderRules.isLeadJob("mausam kaisa hai"))            // weather
        assertFalse(LeadFinderRules.isLeadJob(""))                           // empty
    }

    @Test fun isLeadJobRejectsDenyNiches() {
        assertFalse(LeadFinderRules.isLeadJob("delhi me drug ke leads dhoondo"))
        assertFalse(LeadFinderRules.isLeadJob("mumbai me weapon ke clients lao"))
        assertFalse(LeadFinderRules.isLeadJob("goa me gambling ke business dhundo"))
    }

    @Test fun deniedCatchesDenyWords() {
        assertTrue(LeadFinderRules.denied("fake leads dhoondo"))
        assertTrue(LeadFinderRules.denied("blackmoney wale clients"))
        assertFalse(LeadFinderRules.denied("restaurant ke genuine leads"))
    }

    @Test fun nicheExtractsBusinessType() {
        assertEquals("restaurant", LeadFinderRules.niche("delhi me restaurant ke leads dhoondo"))
        assertEquals("salon", LeadFinderRules.niche("jaipur me salon ke clients lao"))
        assertEquals("gym", LeadFinderRules.niche("mumbai me gym ke business dhundo"))
    }

    @Test fun cityExtractsRegion() {
        assertEquals("delhi", LeadFinderRules.city("delhi me restaurant ke leads dhoondo"))
        assertEquals("mumbai", LeadFinderRules.city("mumbai me salon ke clients lao"))
        assertEquals("", LeadFinderRules.city("restaurant ke leads dhoondo"))   // no city
    }

    @Test fun genuineOnlyDetectsGenuineWords() {
        assertTrue(LeadFinderRules.genuineOnly("delhi me restaurant ke genuine leads dhoondo"))
        assertTrue(LeadFinderRules.genuineOnly("mumbai me asli clients lao"))
        assertTrue(LeadFinderRules.genuineOnly("jaipur me top business dhundo"))
        assertFalse(LeadFinderRules.genuineOnly("delhi me restaurant ke leads dhoondo"))   // no genuine word
    }

    @Test fun scoreFullLeadIs100() {
        val l = LeadFinderRules.Lead("Spice Hub", "+919876543210", "https://spicehub.in", "CP, Delhi", "restaurant", 120, 4.5, 30, "Google Places", "url")
        assertEquals(100, l.score())
    }

    @Test fun scoreMidLeadNoWebRatingBelow4() {
        // name(10) + phone(25) + no-web(0) + addr(10) + rev5(15) + rate3.8(5) + active90(10) = 75
        val l = LeadFinderRules.Lead("Tandoor Express", "+919812345678", "", "Karol Bagh", "restaurant", 5, 3.8, 90, "OpenStreetMap", "url")
        assertEquals(75, l.score())
    }

    @Test fun scoreOnlyNameLeadIs10() {
        val l = LeadFinderRules.Lead("Closed Diner", "", "", "", "restaurant", 0, 0.0, 400, "OpenStreetMap", "url")
        assertEquals(10, l.score())
    }

    @Test fun filterRemovesDuplicatesAndJunk() {
        val hi = LeadFinderRules.Lead("Spice Hub", "+919876543210", "https://spicehub.in", "CP, Delhi", "restaurant", 120, 4.5, 30, "Google Places", "url")
        val mid = LeadFinderRules.Lead("Tandoor Express", "+919812345678", "", "Karol Bagh", "restaurant", 5, 3.8, 90, "OpenStreetMap", "url")
        val lo = LeadFinderRules.Lead("Closed Diner", "", "", "", "restaurant", 0, 0.0, 400, "OpenStreetMap", "url")
        val dupPhone = LeadFinderRules.Lead("Dup Phone", "+919876543210", "", "", "restaurant", 0, 0.0, 0, "OpenStreetMap", "url")
        val dupName = LeadFinderRules.Lead("Spice Hub", "", "", "", "restaurant", 0, 0.0, 0, "OpenStreetMap", "url")
        val noName = LeadFinderRules.Lead("", "+919999999999", "", "", "restaurant", 0, 0.0, 0, "OpenStreetMap", "url")
        val out = LeadFinderRules.filter(listOf(hi, mid, lo, dupPhone, dupName, noName), false)
        assertEquals(3, out.size)   // hi + mid + lo (dups + no-name removed)
        assertEquals("Spice Hub", out[0].name)   // highest score first
    }

    @Test fun filterGenuineOnlyDropsBelowMin() {
        val hi = LeadFinderRules.Lead("Spice Hub", "+919876543210", "https://spicehub.in", "CP, Delhi", "restaurant", 120, 4.5, 30, "Google Places", "url")
        val mid = LeadFinderRules.Lead("Tandoor Express", "+919812345678", "", "Karol Bagh", "restaurant", 5, 3.8, 90, "OpenStreetMap", "url")
        val lo = LeadFinderRules.Lead("Closed Diner", "", "", "", "restaurant", 0, 0.0, 400, "OpenStreetMap", "url")
        val out = LeadFinderRules.filter(listOf(hi, mid, lo), true)
        assertEquals(2, out.size)   // hi(100) + mid(75); lo(10) dropped (below GENUINE_MIN=55)
        assertTrue(out.all { it.score() >= LeadFinderRules.GENUINE_MIN })
    }

    @Test fun filterCapsAtMaxLeads() {
        val raw = (1..30).map { i ->
            LeadFinderRules.Lead("Biz $i", "+91${"9".repeat(9)}$i", "https://b$i.in", "Addr $i", "restaurant", 10, 4.0, 30, "src", "url")
        }
        val out = LeadFinderRules.filter(raw, false)
        assertTrue(out.size <= LeadFinderRules.MAX_LEADS_PER_SEARCH)
    }
}
