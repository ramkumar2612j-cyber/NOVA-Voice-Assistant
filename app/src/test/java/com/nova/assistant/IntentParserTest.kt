package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

class IntentParserTest {
    @Test fun recognizesCallAcrossEnglishAndHinglish() {
        assertEquals(IntentParser.Action.CALL, IntentParser.parse("call Rahul")?.action)
        assertEquals("rahul", IntentParser.parse("Rahul ko call karo")?.target)
        assertEquals(IntentParser.Action.CALL, IntentParser.parse("Ravi ko phone lagao")?.action)
    }

    @Test fun recognizesDevanagariHindiThroughExistingTransliterator() {
        assertEquals(IntentParser.Action.CALL, IntentParser.parse("राहुल को कॉल करो")?.action)
        assertEquals("whatsapp", IntentParser.parse("व्हाट्सएप खोलो")?.appId)
    }

    @Test fun recognizesWhatsAppAndGenericMessageIntent() {
        val wa = IntentParser.parse("WhatsApp Rahul ko message bhejo kal aa jana")
        assertEquals(IntentParser.Kind.APP_MESSAGE, wa?.kind)
        assertEquals("whatsapp", wa?.appId)
        assertEquals("Rahul", wa?.target)
        val sms = IntentParser.parse("send message to Rahul saying hello")
        assertEquals(IntentParser.Action.MESSAGE, sms?.action)
        assertEquals("sms", sms?.appId)
        assertEquals("hello", sms?.message)
    }

    @Test fun identifiesContactLookupWithoutTreatingItAsACall() {
        val parsed = IntentParser.parse("find contact Rahul")
        assertEquals(IntentParser.Kind.CONTACT, parsed?.kind)
        assertEquals(IntentParser.Action.UNKNOWN, parsed?.action)
        assertEquals("rahul", parsed?.target)
    }

    @Test fun recognizesOpenAppAndKeepsAppSeparateFromAction() {
        val parsed = IntentParser.parse("Instagram kholo")
        assertEquals(IntentParser.Action.OPEN, parsed?.action)
        assertEquals("instagram", parsed?.appId)
        assertEquals("instagram", parsed?.target)
    }

    @Test fun whatsappBusinessIdentityAndOpenAreDistinct() {
        val open = IntentParser.parse("WhatsApp kholo")
        assertEquals(IntentParser.Action.OPEN, open?.action)
        assertEquals("whatsapp", open?.appId)
        val business = IntentParser.parse("WhatsApp Business Rahul ko message bhejo hello")
        assertEquals("whatsapp_business", business?.appId)
        assertEquals("Rahul", business?.target)
    }

    @Test fun appSpecificCallDoesNotTurnIntoGenericCall() {
        val parsed = IntentParser.parse("WhatsApp call Rahul")
        assertEquals(IntentParser.Kind.APP_CALL, parsed?.kind)
        assertEquals("whatsapp", parsed?.appId)
        assertEquals("rahul", parsed?.target)
    }

    @Test fun incompleteAndMixedCommandsRequireClarification() {
        assertEquals(IntentParser.Kind.AMBIGUOUS, IntentParser.parse("open")?.kind)
        assertEquals(IntentParser.Kind.AMBIGUOUS, IntentParser.parse("message")?.kind)
        assertEquals(IntentParser.Kind.AMBIGUOUS, IntentParser.parse("call and open Instagram")?.kind)
        assertEquals(IntentParser.Confidence.LOW, IntentParser.parse("call")?.confidence)
    }

    @Test fun negatedCommandsNeverBecomeExecutableRoutes() {
        assertNull(IntentParser.parse("don't call Rahul"))
        assertNull(IntentParser.parse("Instagram mat kholo"))
    }

    @Test fun routerSeparatesStagesAndDeduplicatesClaims() {
        val plan = CommandRouter.plan("call Rahul")!!
        assertTrue(plan.needsContactResolution)
        assertTrue(plan.needsCapabilityValidation)
        assertTrue(plan.needsConfirmation)
        assertEquals("CALL", plan.intent.action.name)
        val inFlight = mutableSetOf<String>()
        assertTrue(CommandRouter.claim("call|rahul", inFlight))
        assertFalse(CommandRouter.claim("call|rahul", inFlight))
        CommandRouter.release("call|rahul", inFlight)
        assertTrue(CommandRouter.claim("call|rahul", inFlight))
    }

    @Test fun unknownCommandsUseSafeFallback() {
        assertNull(CommandRouter.plan("tell me a joke"))
    }

    @Test fun negationInsideMessagePayloadDoesNotBlockWhatsAppBusiness() {
        val p = IntentParser.parse("whatsapp business rahul ko message bhejo kal nahi aaunga")
        assertEquals(IntentParser.Kind.APP_MESSAGE, p?.kind)
        assertEquals("whatsapp_business", p?.appId)
        assertEquals("rahul", p?.target?.lowercase())
        assertEquals("kal nahi aaunga", p?.message)
        assertNull(IntentParser.parse("whatsapp rahul ko message mat bhejo"))
    }
}
