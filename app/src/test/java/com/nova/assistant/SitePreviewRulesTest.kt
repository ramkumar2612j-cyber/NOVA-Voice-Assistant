package com.nova.assistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SitePreviewRulesTest {
    @Test fun previewSentences() {
        assertTrue(SitePreviewRules.isPreview("website dikhao"))
        assertTrue(SitePreviewRules.isPreview("website ka preview dikhao"))
        assertTrue(SitePreviewRules.isPreview("preview dikhao"))
        assertTrue(SitePreviewRules.isPreview("show website preview"))
        assertTrue(SitePreviewRules.isPreview("meri website dekho"))
    }
    @Test fun otherRoutesAreNotStolen() {
        assertFalse(SitePreviewRules.isPreview("ek restaurant ki website banao"))
        assertFalse(SitePreviewRules.isPreview("website chrome mein kholo"))
        assertFalse(SitePreviewRules.isPreview("website publish karo"))
        assertFalse(SitePreviewRules.isPreview("website ko professional banao"))
        assertFalse(SitePreviewRules.isPreview("screen dekho"))
        assertFalse(SitePreviewRules.isPreview("youtube kholo"))
        assertFalse(SitePreviewRules.isPreview("google website dikhao"))
    }
}
