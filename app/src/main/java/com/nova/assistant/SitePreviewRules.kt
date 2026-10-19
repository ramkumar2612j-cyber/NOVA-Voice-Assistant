package com.nova.assistant

/**
 * v7.75: "website dikhao" / "website ka preview dikhao" / "show website preview" = open the in-app preview of the newest built website.
 * Pure Kotlin. Local rule: no AI call. A build / publish / Chrome / edit sentence is never matched (those have their own routes).
 */
object SitePreviewRules {
    private val SITE = setOf("website", "site", "webpage", "wesbite", "vebsite", "websit")
    private val SHOW = setOf("dikhao", "dikha", "dikhado", "dikhana", "dekho", "dekhna", "dekhao", "show", "display")
    private val PREVIEW = setOf("preview", "privew", "previu", "priview")
    private val BLOCK = setOf(
        "chrome", "browser", "tab", "banao", "bana", "banado", "banwao", "banani", "build", "create", "make", "publish", "github", "vercel", "lovable",
        "download", "edit", "badlo", "sudhaar", "sudhar", "professional", "delete", "hatao", "share", "bhejo", "screen", "kya", "kaun", "analyze", "analyse", "google"
    )

    fun isPreview(text: String): Boolean {
        val t = SiteIntent.tokens(text)
        if (t.isEmpty() || t.size > 10) return false
        if (t.any { it in BLOCK }) return false
        val site = t.any { it in SITE }
        val prev = t.any { it in PREVIEW }
        val show = t.any { it in SHOW }
        return (prev && (site || t.size <= 4)) || (site && show)
    }
}
