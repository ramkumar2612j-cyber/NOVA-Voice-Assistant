package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class SiteSubjectTest {
    @Test fun cleanSubjectBeforeNoun() {
        assertEquals("gym", SiteIntent.smartSubject(SiteIntent.tokens("make a gym website")))
    }
    @Test fun businessWordWhenNounHasNoCleanNeighbour() {
        val t = SiteIntent.tokens("thodi achi app itna futuristic h to or luxury international fitness brand ki website banao")
        assertEquals("international fitness brand", SiteIntent.smartSubject(t))
    }
    @Test fun fallbackIsWebsite() {
        assertEquals("website", SiteIntent.smartSubject(SiteIntent.tokens("banao website")))
    }
}
