package com.nova.assistant

/**
 * v7.159 HINDI VOICE FIX (pure Kotlin, no Android imports, unit tested).
 *
 * Problem: NOVA writes Hinglish replies in Roman letters ("Sir, battery 9 percent hai, charger laga lijiye").
 * A Hindi TTS voice reads Roman letters with English sounds, so the Hindi came out wrong. The English-only deep
 * voice ("alan") was even worse. Fix: before the Hindi system voice speaks a Hinglish reply, the common Hinglish
 * words are rewritten in Devanagari (the voice then pronounces them properly). Words that are not in the table
 * (names, rare English words) stay in Roman letters and are read as English by the same voice, which is how
 * Hindi speakers actually say them. Only SPEECH changes: chat text, HUD text and the stop-word echo guard
 * still use the original reply.
 */
object HindiSpeak {

    private val WORDS: Map<String, String> = mapOf(
        // verbs / helpers
        "hai" to "है", "hain" to "हैं", "hu" to "हूँ", "hoon" to "हूँ", "ho" to "हो", "tha" to "था", "thi" to "थी",
        "hoga" to "होगा", "hogi" to "होगी", "honge" to "होंगे", "hota" to "होता", "hoti" to "होती", "hote" to "होते", "hona" to "होना",
        "gaya" to "गया", "gayi" to "गयी", "gaye" to "गये", "raha" to "रहा", "rahi" to "रही", "rahe" to "रहे",
        "rahega" to "रहेगा", "rahegi" to "रहेगी", "diya" to "दिया", "di" to "दी", "liya" to "लिया", "kiya" to "किया", "kiye" to "किये",
        "kare" to "करे", "karo" to "करो", "kro" to "करो", "kar" to "कर", "karna" to "करना", "karne" to "करने", "karke" to "करके",
        "karta" to "करता", "karti" to "करती", "karte" to "करते", "karunga" to "करूंगा", "karungi" to "करूंगी", "karenge" to "करेंगे",
        "kardiya" to "कर दिया", "kardo" to "कर दो", "sakta" to "सकता", "sakti" to "सकती", "sakte" to "सकते",
        "paya" to "पाया", "paye" to "पाये", "paaya" to "पाया", "lijiye" to "लीजिए", "kijiye" to "कीजिए", "dijiye" to "दीजिए",
        "laga" to "लगा", "lagao" to "लगाओ", "lagana" to "लगाना", "lagayi" to "लगायी", "lagaya" to "लगाया",
        "chahiye" to "चाहिए", "chahta" to "चाहता", "chahte" to "चाहते", "milega" to "मिलेगा", "mila" to "मिला", "mili" to "मिली",
        "bolo" to "बोलो", "batao" to "बताओ", "bata" to "बता", "bataiye" to "बताइए", "suno" to "सुनो", "dekho" to "देखो",
        "dekh" to "देख", "dekhna" to "देखना", "khol" to "खोल", "kholo" to "खोलो", "kholta" to "खोलता", "kholna" to "खोलना",
        "khula" to "खुला", "chalu" to "चालू", "chalao" to "चलाओ", "chalo" to "चलो", "ruko" to "रुको", "ruk" to "रुक",
        "jao" to "जाओ", "aao" to "आओ", "dena" to "देना", "lena" to "लेना", "samajh" to "समझ", "samjha" to "समझा",
        "aaya" to "आया", "aayi" to "आयी", "dobara" to "दोबारा", "dobaara" to "दोबारा", "shuru" to "शुरू", "khatam" to "खत्म",
        "bhejo" to "भेजो", "bheja" to "भेजा", "band" to "बंद", "bandh" to "बंद", "uthao" to "उठाओ", "uthana" to "उठाना",
        // particles / pronouns
        "nahi" to "नहीं", "nahin" to "नहीं", "nhi" to "नहीं", "mat" to "मत", "haan" to "हाँ", "ji" to "जी",
        "theek" to "ठीक", "thik" to "ठीक", "accha" to "अच्छा", "achha" to "अच्छा", "sahi" to "सही", "galat" to "गलत", "zaroor" to "ज़रूर",
        "kya" to "क्या", "kyu" to "क्यों", "kyun" to "क्यों", "kyunki" to "क्योंकि", "kaise" to "कैसे", "kaisa" to "कैसा", "kaisi" to "कैसी",
        "kab" to "कब", "kahan" to "कहाँ", "kaun" to "कौन", "kon" to "कौन", "kitna" to "कितना", "kitne" to "कितने", "kitni" to "कितनी",
        "konsa" to "कौनसा", "kaunsa" to "कौनसा", "aap" to "आप", "aapka" to "आपका", "aapki" to "आपकी", "aapke" to "आपके",
        "mujhe" to "मुझे", "tumhe" to "तुम्हें", "tumhara" to "तुम्हारा", "hume" to "हमें", "humko" to "हमको",
        "mera" to "मेरा", "meri" to "मेरी", "mere" to "मेरे", "tera" to "तेरा", "teri" to "तेरी", "tere" to "तेरे",
        "uska" to "उसका", "uski" to "उसकी", "uske" to "उसके", "unka" to "उनका", "iska" to "इसका", "iski" to "इसकी", "iske" to "इसके",
        "isko" to "इसको", "usko" to "उसको", "unko" to "उनको", "inko" to "इनको", "mai" to "मैं", "main" to "मैं", "mein" to "में",
        "hum" to "हम", "tum" to "तुम", "yeh" to "यह", "ye" to "ये", "wo" to "वो", "woh" to "वो", "vo" to "वो",
        "yahan" to "यहाँ", "wahan" to "वहाँ", "idhar" to "इधर", "udhar" to "उधर",
        "ka" to "का", "ki" to "की", "ke" to "के", "ko" to "को", "se" to "से", "par" to "पर", "pe" to "पे", "aur" to "और", "ya" to "या",
        "bhi" to "भी", "toh" to "तो", "to" to "तो", "lekin" to "लेकिन", "magar" to "मगर", "isliye" to "इसलिए", "jab" to "जब", "tab" to "तब",
        "phir" to "फिर", "fir" to "फिर", "abhi" to "अभी", "kal" to "कल", "aaj" to "आज", "bahut" to "बहुत", "bohot" to "बहुत",
        "zyada" to "ज़्यादा", "kuch" to "कुछ", "koi" to "कोई", "sab" to "सब", "sabhi" to "सभी", "ek" to "एक", "do" to "दो",
        "wala" to "वाला", "wali" to "वाली", "wale" to "वाले", "neeche" to "नीचे", "niche" to "नीचे", "upar" to "ऊपर", "tak" to "तक", "liye" to "लिए",
        "shukriya" to "शुक्रिया", "dhanyawad" to "धन्यवाद", "namaste" to "नमस्ते", "kripya" to "कृपया", "dost" to "दोस्त", "yaar" to "यार", "bhai" to "भाई",
        "baje" to "बजे", "subah" to "सुबह", "shaam" to "शाम", "raat" to "रात", "din" to "दिन", "ghanta" to "घंटा", "ghante" to "घंटे", "gaana" to "गाना",
        // English words NOVA says all the time (Hindi speakers say them with Hindi sounds)
        "sir" to "सर", "madam" to "मैडम", "battery" to "बैटरी", "percent" to "परसेंट", "charger" to "चार्जर", "charge" to "चार्ज",
        "wifi" to "वाईफाई", "whatsapp" to "व्हाट्सऐप", "message" to "मैसेज", "messages" to "मैसेज", "call" to "कॉल", "volume" to "वॉल्यूम",
        "torch" to "टॉर्च", "alarm" to "अलार्म", "timer" to "टाइमर", "camera" to "कैमरा", "video" to "वीडियो", "photo" to "फोटो",
        "music" to "म्यूज़िक", "song" to "सॉन्ग", "bluetooth" to "ब्लूटूथ", "internet" to "इंटरनेट", "app" to "ऐप", "apps" to "ऐप्स",
        "phone" to "फोन", "mobile" to "मोबाइल", "settings" to "सेटिंग्स", "setting" to "सेटिंग", "screen" to "स्क्रीन", "lock" to "लॉक",
        "unlock" to "अनलॉक", "status" to "स्टेटस", "update" to "अपडेट", "download" to "डाउनलोड", "notification" to "नोटिफिकेशन",
        "email" to "ईमेल", "gmail" to "जीमेल", "youtube" to "यूट्यूब", "instagram" to "इंस्टाग्राम", "website" to "वेबसाइट", "link" to "लिंक",
        "contact" to "कॉन्टैक्ट", "contacts" to "कॉन्टैक्ट्स", "reminder" to "रिमाइंडर", "meeting" to "मीटिंग", "reply" to "रिप्लाई",
        "ok" to "ओके", "okay" to "ओके", "on" to "ऑन", "off" to "ऑफ", "nova" to "नोवा", "jarvis" to "जार्विस", "autopilot" to "ऑटोपायलट",
        "post" to "पोस्ट", "story" to "स्टोरी", "reel" to "रील", "order" to "ऑर्डर", "offer" to "ऑफर", "sorry" to "सॉरी",
        "thanks" to "थैंक्स", "hello" to "हैलो", "please" to "प्लीज़", "mode" to "मोड", "voice" to "वॉइस", "chat" to "चैट", "search" to "सर्च",
        "file" to "फाइल", "gallery" to "गैलरी", "location" to "लोकेशन", "data" to "डेटा", "network" to "नेटवर्क", "signal" to "सिग्नल",
        "storage" to "स्टोरेज", "memory" to "मेमोरी", "error" to "एरर", "report" to "रिपोर्ट", "check" to "चेक", "test" to "टेस्ट",
        "start" to "स्टार्ट", "stop" to "स्टॉप", "ready" to "रेडी", "low" to "लो", "full" to "फुल", "time" to "टाइम",
        "minute" to "मिनट", "minutes" to "मिनट", "second" to "सेकंड", "seconds" to "सेकंड"
    )

    /** Devanagari already there, or no Hinglish at all -> the text is returned untouched. */
    fun forVoice(text: String): String {
        if (text.isEmpty()) return text
        if (!ReplyLang.isHindi(text)) return text
        return toDevanagari(text)
    }

    /** Word-by-word rewrite of every Roman word that is in the table; everything else (digits, punctuation, names) is kept. */
    fun toDevanagari(text: String): String {
        val sb = StringBuilder(text.length + 16)
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            if (c in 'A'..'Z' || c in 'a'..'z') {
                var j = i
                while (j < n && (text[j] in 'A'..'Z' || text[j] in 'a'..'z')) j++
                // a word glued to digits / apostrophes (wifi6, don't) is left alone
                val glued = (j < n && (text[j].isDigit() || text[j] == '\'' || text[j] == '’')) || (i > 0 && (text[i - 1].isDigit() || text[i - 1] == '\'' || text[i - 1] == '’'))
                val word = text.substring(i, j)
                val hit = if (glued) null else WORDS[word.lowercase()]
                sb.append(hit ?: word)
                i = j
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }
}
