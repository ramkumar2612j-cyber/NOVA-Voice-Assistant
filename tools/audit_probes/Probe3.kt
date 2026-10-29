package com.nova.assistant

object ProbeMain3 {
    @JvmStatic fun main(a: Array<String>) {
        for (p in listOf("play store pe whatsapp download karo", "play store me whatsapp install karo", "play store par instagram download karo", "play store se facebook download karo", "install youtube music", "youtube music download karo", "download karo youtube music")) {
            println("DL  " + p + " -> " + OpenWebRules.parseDownload(p)?.let { it.name + "/" + it.label + "/" + it.pkg } + " | AppSearch=" + AppSearchRules.parse(p)?.let { it.kind.name + ":" + it.appPhrase + ":" + it.query + ":" + it.play })
        }
        for (p in listOf("torch", "se on karo", "se wifi on karo", "wifi on karo aur", "ye gaana", "se chalao", "bluetooth on karo")) println("CLS " + p + " -> " + Understand.classify(p)?.let { it.kind + ":" + it.arg })
        for (p in listOf("kal 5 baje anand ko whatsapp pe hello bhejo", "kal subah 8 baje mummy ko whatsapp pe hello bhejo", "roz subah 8 baje mummy ko whatsapp pe hello bhejo", "aaj shaam 6 baje papa ko message bhejo ki aa raha hu")) {
            val s = ScheduledTaskRules.parse(p)
            println("SCH " + p + " -> " + (s != null) + " | meeting=" + (MeetingEventRules.parse(p) != null))
        }
        println(Logic.classify("call 112")?.let { it.kind + ":" + it.arg })
        println("SPLIT-PHIR " + CmdQueue.split("pehle torch on karo phir wifi on karo"))
    }
}
