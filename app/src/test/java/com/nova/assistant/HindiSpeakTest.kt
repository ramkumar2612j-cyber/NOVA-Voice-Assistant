package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

/** v7.159: Hinglish replies are spoken by the Hindi voice with Devanagari words; the English-only deep voice never gets them. */
class HindiSpeakTest {
    @Test fun batteryAlertBecomesDevanagari() {
        val s = HindiSpeak.forVoice("Sir, battery 9 percent hai, 10 percent se neeche gayi. Charger laga lijiye.")
        assertEquals("सर, बैटरी 9 परसेंट है, 10 परसेंट से नीचे गयी. चार्जर लगा लीजिए.", s)
    }
    @Test fun englishIsUntouched() {
        val t = "Sir, the battery is at nine percent. Please plug in the charger."
        assertEquals(t, HindiSpeak.forVoice(t))
    }
    @Test fun devanagariIsUntouched() {
        val t = "बैटरी कम है, चार्जर लगाइए"
        assertEquals(t, HindiSpeak.forVoice(t))
    }
    @Test fun unknownNamesStayRoman() {
        val s = HindiSpeak.forVoice("Tarun Kotwati Biswa ka message aaya hai")
        assertTrue(s.startsWith("Tarun Kotwati Biswa का मैसेज आया है"))
    }
    @Test fun digitsAndGluedWordsAreKept() {
        assertEquals("वाईफाई 6 चालू है", HindiSpeak.forVoice("wifi 6 chalu hai"))
        assertTrue(HindiSpeak.toDevanagari("wifi6 hai").contains("wifi6"))
    }
    @Test fun hinglishNeverGoesToTheEnglishDeepVoice() {
        assertFalse(JarvisDeepRules.use("Sir, battery 9 percent hai, charger laga lijiye.", on = true, modelReady = true))
        assertFalse(JarvisDeepRules.use("Theek hai sir, main ready hoon.", on = true, modelReady = true))
        assertTrue(JarvisDeepRules.use("Hello sir, system check complete.", on = true, modelReady = true))
    }
}
