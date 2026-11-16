package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BusinessHomeRulesTest {
    private fun snap(
        leads: Int = 0, leadsTotal: Int = 0, orders: Int = 0, open: Int = 0,
        store: Int = 0, storeTotal: Int = -1, src: String = "", on: Boolean = false, report: String = ""
    ) = BusinessHomeRules.Snap(leads, leadsTotal, orders, open, store, storeTotal, src, on, report)

    @Test fun emptyDataSaysNoneNeverFakeNumbers() {
        val lines = BusinessHomeRules.cardLines(snap())
        assertEquals("Naye orders aaj: abhi koi nahi", lines[0])
        assertEquals("Naye leads aaj: abhi koi nahi", lines[1])
        assertEquals("Autopilot: band", lines[2])
        assertEquals("Aakhri report: abhi koi nahi", lines[3])
    }

    @Test fun realNumbersAreShown() {
        val s = snap(leads = 3, leadsTotal = 14, orders = 1, open = 2, store = 2, storeTotal = 20, src = "woocommerce", on = true, report = "5 leads mile")
        val l = BusinessHomeRules.cardLines(s)
        assertEquals("Naye orders aaj: 3  (2 chat order adhoore)", l[0])
        assertEquals("Naye leads aaj: 3  (kul 14)", l[1])
        assertEquals("Autopilot: chalu", l[2])
        assertEquals("Aakhri report: 5 leads mile", l[3])
    }

    @Test fun negativeStoreCountNeverSubtracts() {
        assertEquals(1, BusinessHomeRules.totalOrdersToday(snap(orders = 1, store = -5)))
    }

    @Test fun voiceReportHindiAndEnglish() {
        val hi = BusinessHomeRules.voiceReport(snap(orders = 2, leads = 0, on = true), false)
        assertTrue(hi.contains("Naye orders: 2"))
        assertTrue(hi.contains("Naye leads: abhi koi nahi"))
        assertTrue(hi.contains("Autopilot chalu hai"))
        assertTrue(hi.contains("Abhi koi report nahi"))
        val en = BusinessHomeRules.voiceReport(snap(), true)
        assertTrue(en.contains("New orders: none yet"))
        assertTrue(en.contains("Autopilot is off"))
    }

    @Test fun todayUsesIndiaDay() {
        // 2026-11-15 20:00 UTC = 2026-11-16 01:30 IST
        val now = java.time.Instant.parse("2026-11-15T20:00:00Z").toEpochMilli()
        val earlier = java.time.Instant.parse("2026-11-15T19:00:00Z").toEpochMilli()   // 00:30 IST, same IST day
        val yesterdayIst = java.time.Instant.parse("2026-11-15T10:00:00Z").toEpochMilli() // 15:30 IST on the 15th
        assertTrue(BusinessHomeRules.isToday(earlier, now))
        assertFalse(BusinessHomeRules.isToday(yesterdayIst, now))
        assertFalse(BusinessHomeRules.isToday(0L, now))
        assertEquals(1, BusinessHomeRules.countToday(listOf(earlier, yesterdayIst, 0L), now))
    }

    @Test fun tapCommandsUseExistingGrammar() {
        assertEquals("jarvis orders dikhao", BusinessHomeRules.commandFor(BusinessHomeRules.Tap.ORDERS, "", ""))
        assertEquals("jarvis leads dikhao", BusinessHomeRules.commandFor(BusinessHomeRules.Tap.LEADS, "", ""))
        assertEquals("jarvis autopilot status", BusinessHomeRules.commandFor(BusinessHomeRules.Tap.AUTOPILOT, "", ""))
        assertNull(BusinessHomeRules.commandFor(BusinessHomeRules.Tap.REPORT, "Delhi", "restaurant"))
    }

    @Test fun findNeedsSavedCityAndCategory() {
        assertNull(BusinessHomeRules.commandFor(BusinessHomeRules.Tap.FIND, "", "restaurant"))
        assertNull(BusinessHomeRules.commandFor(BusinessHomeRules.Tap.FIND, "Delhi", ""))
        assertEquals("jarvis Delhi me restaurant ke genuine leads dhoondo", BusinessHomeRules.commandFor(BusinessHomeRules.Tap.FIND, "Delhi", "restaurant"))
        // junk characters are stripped, a deny word never goes out
        assertEquals("jarvis Delhi me cafe ke genuine leads dhoondo", BusinessHomeRules.commandFor(BusinessHomeRules.Tap.FIND, "Delhi;", "cafe!!"))
        assertNull(BusinessHomeRules.commandFor(BusinessHomeRules.Tap.FIND, "Delhi", "otp"))
    }

    @Test fun overlayCards() {
        assertEquals("Naya lead: Rahul Sharma", BusinessHomeRules.newLeadCard("Rahul Sharma"))
        assertEquals("Naya lead: koi", BusinessHomeRules.newLeadCard("  "))
        assertEquals("Naya order: 2 x Kurta - Asha", BusinessHomeRules.newOrderCard(2, "Kurta", "Asha"))
        assertEquals("Naya order: item - customer", BusinessHomeRules.newOrderCard(0, "", ""))
    }

    @Test fun freshIdsOnly() {
        assertEquals(listOf("b", "c"), BusinessHomeRules.fresh(listOf("a", "b", "", "c"), setOf("a")))
        assertTrue(BusinessHomeRules.fresh(emptyList(), setOf("a")).isEmpty())
    }

    @Test fun websiteInfoIsHonest() {
        assertTrue(BusinessHomeRules.websiteInfo("", false, "").contains("set nahi hai"))
        assertTrue(BusinessHomeRules.websiteInfo("shopify", false, "token galat").contains("nahi aa paye"))
        assertTrue(BusinessHomeRules.websiteInfo("woocommerce", false, "").contains("check nahi hue"))
        assertTrue(BusinessHomeRules.websiteInfo("woocommerce", true, "").contains("15 minute"))
    }

    @Test fun parseWooOrders() {
        val raw = """[{"id":501,"total":"1499.00","currency":"INR","date_created_gmt":"2026-11-15T09:30:00",
            "billing":{"first_name":"Asha","last_name":"Verma","phone":"9999999999","address_1":"secret street"},
            "line_items":[{"name":"Silk Kurta","quantity":2},{"name":"Dupatta","quantity":1}]},{"id":502}]"""
        val l = BusinessHomeRules.parseWoo(raw)
        assertEquals(2, l.size)
        assertEquals("woo-501", l[0].id)
        assertEquals("Asha Verma", l[0].who)
        assertEquals("1499.00 INR", l[0].total)
        assertEquals("2 x Silk Kurta, Dupatta", l[0].items)
        assertEquals(java.time.Instant.parse("2026-11-15T09:30:00Z").toEpochMilli(), l[0].createdAt)
        // no phone / address ever kept in the model
        assertFalse((l[0].who + l[0].items + l[0].total).contains("9999"))
    }

    @Test fun parseShopifyOrders() {
        val raw = """{"orders":[{"id":7001,"total_price":"799.00","currency":"INR","created_at":"2026-11-15T10:12:00+05:30",
            "customer":{"first_name":"Ravi","last_name":"K"},"line_items":[{"title":"Shirt","quantity":1}]}]}"""
        val l = BusinessHomeRules.parseShopify(raw)
        assertEquals(1, l.size)
        assertEquals("shop-7001", l[0].id)
        assertEquals("Ravi K", l[0].who)
        assertEquals("799.00 INR", l[0].total)
        assertEquals("Shirt", l[0].items)
        assertEquals(java.time.Instant.parse("2026-11-15T04:42:00Z").toEpochMilli(), l[0].createdAt)
        assertEquals("Website order: Ravi K - Shirt (799.00 INR)", BusinessHomeRules.newStoreOrderCard(l[0]))
    }

    @Test fun badJsonGivesEmptyList() {
        assertTrue(BusinessHomeRules.parseWoo("").isEmpty())
        assertTrue(BusinessHomeRules.parseWoo("{not json").isEmpty())
        assertTrue(BusinessHomeRules.parseShopify("[]").isEmpty())
        assertTrue(BusinessHomeRules.parseShopify("").isEmpty())
    }
}
