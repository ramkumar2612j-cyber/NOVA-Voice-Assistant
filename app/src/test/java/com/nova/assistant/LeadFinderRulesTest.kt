package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

class LeadFinderRulesTest {
    private val json = """{"places":[
      {"displayName":{"text":"Good Boutique"},"internationalPhoneNumber":"+91 98765 43210","rating":4.5,"userRatingCount":120,"businessStatus":"OPERATIONAL","formattedAddress":"Sec 18, Noida","websiteUri":"https://gb.example"},
      {"displayName":{"text":"Landline Shop"},"nationalPhoneNumber":"0120 4567890","rating":4.8,"userRatingCount":300,"businessStatus":"OPERATIONAL"},
      {"displayName":{"text":"Closed Shop"},"internationalPhoneNumber":"+91 91234 56789","rating":4.9,"userRatingCount":50,"businessStatus":"CLOSED_PERMANENTLY"},
      {"displayName":{"text":"Low Rated"},"internationalPhoneNumber":"+91 90000 11111","rating":2.1,"userRatingCount":40,"businessStatus":"OPERATIONAL"},
      {"displayName":{"text":"New Unknown"},"internationalPhoneNumber":"+91 90000 22222","rating":5.0,"userRatingCount":1,"businessStatus":"OPERATIONAL"},
      {"displayName":{"text":"Known Lead"},"internationalPhoneNumber":"+91 97777 12345","rating":4.0,"userRatingCount":30,"businessStatus":"OPERATIONAL"},
      {"displayName":{"text":"Second Good"},"internationalPhoneNumber":"+91 96666 54321","rating":4.0,"userRatingCount":60,"businessStatus":"OPERATIONAL"},
      {"displayName":{"text":"Dup Of Good"},"internationalPhoneNumber":"098765 43210","rating":4.0,"userRatingCount":10,"businessStatus":"OPERATIONAL"}
    ]}"""

    @Test fun parseReadsFields() {
        val l = LeadFinderRules.parse(json)
        assertEquals(8, l.size); assertEquals("Good Boutique", l[0].name); assertEquals(120, l[0].reviews)
        assertTrue(LeadFinderRules.parse("not json").isEmpty()); assertTrue(LeadFinderRules.parse("{}").isEmpty())
    }

    @Test fun mobileOnly() {
        assertEquals("+919876543210", LeadFinderRules.mobile("+91 98765 43210"))
        assertEquals("+919876543210", LeadFinderRules.mobile("098765 43210"))
        assertEquals("", LeadFinderRules.mobile("0120 4567890"))
        assertEquals("", LeadFinderRules.mobile(""))
    }

    @Test fun pickFiltersGenuineAndNew() {
        val picks = LeadFinderRules.pick(LeadFinderRules.parse(json), setOf("919777712345"), emptySet(), 10)
        assertEquals(listOf("Good Boutique", "Second Good"), picks.map { it.first.name })
        assertEquals("+919876543210", picks[0].second)
        assertEquals(1, LeadFinderRules.pick(LeadFinderRules.parse(json), emptySet(), setOf("919876543210"), 1).size)
    }

    @Test fun dailyGoal() {
        assertEquals(3, LeadFinderRules.remainingToday(8, 5))
        assertEquals(0, LeadFinderRules.remainingToday(8, 9))
        assertEquals(5, LeadFinderRules.remainingToday(1, 0))
        assertEquals(10, LeadFinderRules.remainingToday(99, 0))
    }

    @Test fun bodyIsJson() {
        val b = LeadFinderRules.requestBody("boutique in Noida")
        assertTrue(b.contains("\"textQuery\":\"boutique in Noida\"")); assertTrue(b.contains("IN"))
    }
}
