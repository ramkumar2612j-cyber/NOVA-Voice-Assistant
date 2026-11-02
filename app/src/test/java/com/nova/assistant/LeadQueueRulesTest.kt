package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LeadQueueRulesTest {
    @Test fun leadCaptureOnlyAcceptsVisiblePublicBusinessSources() {
        val candidate = LeadRules.parseVisible(
            "com.google.android.apps.maps",
            listOf("Green Park Cafe", "98765 43210", "Hazratganj Road, Lucknow, Uttar Pradesh 226001", "greenpark.example")
        )
        assertNotNull(candidate)
        assertEquals("Green Park Cafe", candidate?.name)
        assertEquals("com.google.android.apps.maps", candidate?.sourcePackage)
        assertNull(LeadRules.parseVisible("com.whatsapp", listOf("Private chat", "98765 43210")))
        assertFalse(LeadRules.isAllowedPublicSource("com.android.settings"))
    }

    @Test fun leadAcquisitionRequestDoesNotLookLikeWebsiteRequest() {
        assertTrue(LeadRules.isLeadAcquisitionRequest("mere shop ke liye leads lao"))
        assertTrue(LeadRules.isLeadAcquisitionRequest("business leads dhundo"))
        assertFalse(LeadRules.isLeadAcquisitionRequest("mere shop ke liye website banao"))
        assertFalse(LeadRules.isLeadAcquisitionRequest("hello nova"))
    }

    @Test fun leadCommandsAndDraftsAreBoundedAndPolite() {
        assertEquals("lead_capture", LeadRules.command("lead capture")?.kind)
        assertEquals("en", LeadRules.command("lead draft english")?.arg)
        assertEquals("lead_send", LeadRules.command("lead bhejo")?.kind)
        assertTrue(LeadRules.draft("Green Park Cafe", "Lucknow", "en").contains("Could you share the details?"))
        assertTrue(LeadRules.cleanDraft("Please send OTP 123456 to confirm. Hello").length <= 450)
        assertTrue(LeadRules.clean("a\nb\u0000c").none { it == '\n' || it == '\u0000' })
    }

    @Test fun offlineQueueOnlyAllowsCloudJobsAndRejectsScreenTasks() {
        assertEquals("queue_list", OfflineQueueRules.command("queue dikhao")?.kind)
        assertEquals("queue_clear", OfflineQueueRules.command("queue saaf karo")?.kind)
        assertTrue(OfflineQueueRules.mayQueue(OfflineQueueRules.Kind.WEBSITE))
        assertTrue(OfflineQueueRules.mayQueue(OfflineQueueRules.Kind.LEARN))
        assertTrue(OfflineQueueRules.mayQueue(OfflineQueueRules.Kind.IMAGE))
        assertTrue(OfflineQueueRules.mayQueue(OfflineQueueRules.Kind.LEAD_DRAFTS))
        assertFalse(OfflineQueueRules.mayQueue(OfflineQueueRules.Kind.WEBSITE, containsScreenInstructions = true))
        assertEquals(OfflineQueueRules.MAX_PAYLOAD_CHARS, OfflineQueueRules.cleanPayload("x".repeat(5000)).length)
        assertEquals(AgentTaskRules.State.REPORTED_COMPLETE, OfflineQueueRules.statusForModelCompletion())
    }

    @Test fun recurringHinglishWeatherScheduleParsesCorrectly() {
        val spec = ScheduledTaskRules.parse("roz subah 8 baje weather bolo")
        assertNotNull(spec)
        assertEquals(8, spec?.hour)
        assertEquals(0, spec?.minute)
        assertTrue(spec?.repeatDaily == true)
        assertEquals(ScheduledTaskRules.Kind.WEATHER, spec?.kind)
        assertNull(ScheduledTaskRules.parse("roz subah weather bolo"))
    }

    @Test fun checkpointEligibilityDoesNotAllowUnsafeUiReplay() {
        assertTrue(AgentTaskRules.canResumeCheckpoint("open_app", true))
        assertTrue(AgentTaskRules.canResumeCheckpoint("wait", true))
        assertFalse(AgentTaskRules.canResumeCheckpoint("tap", true))
        assertFalse(AgentTaskRules.canResumeCheckpoint("type", true))
        assertFalse(AgentTaskRules.canResumeCheckpoint("open_app", false))
        assertTrue(AgentTaskRules.isHistoryQuery("task history"))
    }
}
