package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

class SiteV7Test {
    @Test fun longBriefIsStillABuildRequestAndKeptWhole() {
        val brief = "Build a world-class premium fully functional dental clinic website. " + "Use photos, images, animations, booking and 3D sections with great care. ".repeat(40)
        val p = SiteIntent.parse(brief)
        assertNotNull(p)
        assertTrue(p!!.raw.length > 1500)
    }

    @Test fun fillerBeforeVerbIsNotALongBrief() {
        assertNull(SiteIntent.parse("a b ".repeat(40) + "website banao"))
    }

    @Test fun backgroundEditIsAWebsiteEdit() {
        val p = SiteIntent.parseEdit("Background colour white chahiye")
        assertNotNull(p)
        assertTrue(p!!.followUp)
        assertNotNull(SiteIntent.parseEdit("hero mein button ka rang lal karo"))
    }

    @Test fun ordinaryChatIsNotAnEdit() {
        assertNull(SiteIntent.parseEdit("dark mode on karo"))
        assertNull(SiteIntent.parseEdit("aaj ka mausam batao"))
        assertNull(SiteIntent.parseEdit("website publish karo"))
        assertNull(SiteIntent.parseEdit("ek billi ki photo banao"))
        assertNotNull(SiteIntent.parseEdit("ye photo lagao", true))
    }

    @Test fun patchAppliesAndRejectsBadBlocks() {
        val base = "<!DOCTYPE html><html><head><style>body{background:#000}</style></head><body><p>hi</p></body></html>"
        val ok = SiteGen.applyPatch(base, "<<<FIND\nbody{background:#000}\n===REPLACE\nbody{background:#fff}\n>>>END")
        assertNotNull(ok)
        assertTrue(ok!!.contains("#fff") && ok.contains("<p>hi</p>"))
        assertNull(SiteGen.applyPatch(base, "<<<FIND\nnot in page\n===REPLACE\nx\n>>>END"))
        assertNull(SiteGen.applyPatch(base, "no blocks"))
    }

    @Test fun userPromptCarriesWholeRequestDesignAndMedia() {
        val req = "Build a dental website. " + "x".repeat(3000) + " END_MARK"
        val p = SiteGen.userPrompt("dental", req, "MEDIA_TEXT")
        assertTrue(p.contains("END_MARK"))
        assertTrue(p.contains("DESIGN DIRECTION"))
        assertTrue(p.contains("MEDIA_TEXT"))
    }

    @Test fun designVariesAndRespectsOwnerColours() {
        val names = (0L..40L).map { SiteDesign.pick("gym website", it).name }.toSet()
        assertTrue(names.size >= 3)
        assertTrue(SiteDesign.ownerChoseLook("background white chahiye"))
        assertFalse(SiteDesign.ownerChoseLook("dental clinic website"))
        assertTrue(SiteDesign.brief("white background site", 1).contains("ONLY layout"))
    }
}
