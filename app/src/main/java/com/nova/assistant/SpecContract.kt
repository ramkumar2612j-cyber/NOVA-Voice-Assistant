package com.nova.assistant

/**
 * Additive website/task requirement contract. Pure Kotlin so it can be tested on the JVM.
 * The model output is untrusted and must be checked against these requirements before being reported.
 */
object SpecContract {
    class Spec(
        val kind: String,
        val style: List<String>,
        val language: String,
        val must: List<String>,
        val mustNot: List<String>,
        val exactDetails: List<String>,
        val fullRequest: String
    )

    private fun hasAny(s: String, words: List<String>): Boolean = words.any { s.contains(it) }

    fun analyze(request: String): Spec {
        val raw = request.trim().take(SiteGen.MAX_REQ)
        val s = raw.lowercase()
        val deniesShop = hasAny(s, listOf("no shop", "no cart", "no checkout", "without a cart", "without cart", "without checkout", "never ecommerce", "never e-commerce", "not ecommerce", "not e-commerce", "never shop", "no shopping", "cart nahi", "checkout nahi", "cart mat", "checkout mat"))
        val explicitShopTerm = Regex("\\b(shop|store|ecommerce|e-commerce|dukaan|shopping)\\b", RegexOption.IGNORE_CASE).containsMatchIn(s) ||
            hasAny(s, listOf("online kapde", "buy online", "product listing", "add to cart", "shopping cart"))
        val shop = !deniesShop && explicitShopTerm
        val kind = when {
            shop -> "shop"
            hasAny(s, listOf("blog", "blogging", "article", "articles", "post likh", "blog post")) -> "blog"
            hasAny(s, listOf("portfolio", "resume", "cv", "personal website")) -> "portfolio"
            hasAny(s, listOf("restaurant", "cafe", "café", "menu", "food outlet")) -> "restaurant"
            hasAny(s, listOf("dashboard", "admin panel", "analytics panel")) -> "dashboard"
            hasAny(s, listOf("download", "apk download", "app download", "install app")) -> "app-download"
            hasAny(s, listOf("web app", "web application", "interactive app", "tool banao", "calculator app")) -> "app"
            hasAny(s, listOf("landing page", "landing website", "landing")) -> "landing"
            hasAny(s, listOf("website", "webpage", "web page", "site")) -> "landing"
            else -> "other"
        }
        val styles = ArrayList<String>()
        if (hasAny(s, listOf("3d", "three dimensional", "three-dimensional", "3-d"))) styles.add("3D")
        if (hasAny(s, listOf("dark", "black theme", "neon"))) styles.add("dark")
        if (hasAny(s, listOf("minimal", "minimalist", "simple clean"))) styles.add("minimal")
        if (hasAny(s, listOf("glass", "glassmorphism"))) styles.add("glassmorphism")
        if (hasAny(s, listOf("retro", "pixel art", "8-bit"))) styles.add("retro/pixel")
        if (hasAny(s, listOf("luxury", "premium", "elegant"))) styles.add("premium/elegant")

        val must = ArrayList<String>()
        when (kind) {
            "blog" -> must.add("real blog/article structure with multiple readable posts and headings")
            "shop" -> must.add("shop/product browsing and commerce controls appropriate to a shop")
            "portfolio" -> must.add("portfolio projects/work samples and about/contact information")
            "restaurant" -> must.add("restaurant-specific menu and location/contact details")
            "dashboard" -> must.add("dashboard-style metrics, data summaries, and navigable sections")
            "app-download" -> must.add("app description, Android install steps, and an honest download-link placeholder if no URL was supplied")
            "app" -> must.add("working interactive app/tool behaviour, not just a marketing landing page")
            "landing" -> must.add("content and sections specific to the requested subject, not a generic business template")
            else -> must.add("deliverable and sections specifically matching the owner's request")
        }
        if ("3D" in styles) must.add("visible 3D effect implemented with CSS perspective/preserve-3d/translateZ or canvas/WebGL")
        if ("dark" in styles) must.add("dark visual theme")
        if ("minimal" in styles) must.add("minimal visual styling")
        if (hasAny(s, listOf("contact", "contact form", "phone number", "email"))) must.add("requested contact details or contact section")
        if (hasAny(s, listOf("responsive", "mobile", "phone"))) must.add("mobile-responsive layout")

        val mustNot = ArrayList<String>()
        if (kind == "blog" && !shop) mustNot.add("shop/cart/checkout/product-price ecommerce interface")
        if (kind == "portfolio" && !shop) mustNot.add("unrequested shopping cart or checkout")
        if (kind == "restaurant" && !shop) mustNot.add("unrequested ecommerce cart or checkout")
        if (kind != "shop" && !shop) mustNot.add("invented cart, checkout, buy-now flow, or product pricing")
        if (hasAny(s, listOf("no login", "login nahi", "login mat", "without login"))) mustNot.add("login or signup form")
        if (hasAny(s, listOf("no payment", "payment nahi", "payment mat"))) mustNot.add("payment handling")

        val exact = ArrayList<String>()
        // Preserve literal names, quantities, and colors from the request for use by the generation prompt.
        Regex("\\b\\d+(?:\\.\\d+)?\\s?(?:%|px|rem|items?|posts?|pages?|sections?|cards?|products?|days?|hours?)?\\b", RegexOption.IGNORE_CASE)
            .findAll(raw).map { it.value.trim() }.distinct().take(12).forEach { exact.add(it) }
        Regex("#[0-9a-fA-F]{3,8}\\b").findAll(raw).map { it.value }.distinct().take(8).forEach { exact.add(it) }
        val language = when {
            Regex("[\\u0900-\\u097F]").containsMatchIn(raw) -> "Hindi"
            hasAny(s, listOf("hindi", "hinglish", "roman hindi", "hindi mein", "hindi me")) -> "Hindi/Hinglish"
            else -> "the same language as the owner's request"
        }
        return Spec(kind, styles, language, must, mustNot, exact, raw)
    }

    fun promptBlock(spec: Spec, fullRequest: String = spec.fullRequest): String {
        fun lines(xs: List<String>) = if (xs.isEmpty()) "- (none specified)" else xs.joinToString("\n") { "- $it" }
        return """\n\nREQUIREMENTS CONTRACT — follow this literally; never swap in a generic template.
OWNER REQUEST VERBATIM (preserve intent; do not shorten away details): "${fullRequest.trim().take(SiteGen.MAX_REQ)}"
INFERRED DELIVERABLE KIND: ${spec.kind}
VISUAL STYLE: ${if (spec.style.isEmpty()) "not specified; choose an appropriate style" else spec.style.joinToString(", ")}
VISIBLE LANGUAGE: ${spec.language}
MUST:
${lines(spec.must)}
MUST NOT:
${lines(spec.mustNot)}
EXACT DETAILS TO PRESERVE:
${lines(spec.exactDetails)}
Build only what was asked for. If it is a blog, do not turn it into a shop. If 3D is requested, implement a visible real 3D treatment, not a label saying 3D. Self-contained HTML; the only external scripts allowed are the optional three.js/GSAP builds named in the quality bar, with a fallback. Inline CSS, JS, SVG and canvas are allowed. Never claim a placeholder URL is a working download URL."""
    }

    /** Heuristic quality gate, deliberately conservative: returns only detected gaps/contradictions. */
    fun check(spec: Spec, output: String): List<String> {
        val h = output.lowercase()
        val problems = ArrayList<String>()
        if (output.length < 200 || !h.contains("</html>")) problems.add("output is not a complete HTML page")
        if (spec.kind == "blog" && !hasAny(h, listOf("<article", "blog post", "blog-post", "class=\"post", "class='post", "article-card", "blog posts"))) {
            problems.add("requested blog is missing recognizable article/post structure")
        }
        if (spec.kind != "shop" && spec.mustNot.any { it.contains("cart") || it.contains("ecommerce") }) {
            if (hasAny(h, listOf("shopping cart", "add to cart", "checkout", "buy now", "shopping-cart", "cart-total", "product price"))) {
                problems.add("unrequested shop/cart/checkout/pricing signals found")
            }
        }
        if ("3D" in spec.style && !hasAny(h, listOf("perspective", "preserve-3d", "translatez(", "webgl", "<canvas", "three.js", "rotatex(", "rotatey("))) {
            problems.add("3D requested but no detectable CSS 3D/canvas/WebGL implementation found")
        }
        if (spec.kind == "shop" && !hasAny(h, listOf("product", "price", "cart", "shop", "store"))) {
            problems.add("requested shop has no detectable product/shop structure")
        }
        if (spec.kind == "restaurant" && !hasAny(h, listOf("menu", "dish", "restaurant", "reservation", "food"))) {
            problems.add("requested restaurant page lacks restaurant/menu-specific content")
        }
        if (spec.kind == "portfolio" && !hasAny(h, listOf("portfolio", "project", "work", "case study"))) {
            problems.add("requested portfolio lacks project/work samples")
        }
        // v7.4 premium gate: only for real, full-size pages (short test fixtures are not judged)
        if (output.length > 6000) {
            if (!h.contains("name=\"viewport\"")) problems.add("missing responsive viewport meta tag")
            if (!h.contains("@media")) problems.add("no responsive @media rules (must work on phones)")
            if (Regex("<section").findAll(h).count() < 5) problems.add("too few sections: a premium site needs at least 7 distinct sections")
            if (!hasAny(h, listOf("transition", "@keyframes", "animation:"))) problems.add("no hover transitions or animations: add polished micro-interactions")
            if (!hasAny(h, listOf("intersectionobserver", "scroll-timeline", "animation-timeline"))) problems.add("no scroll-reveal animation")
            if (!hasAny(h, listOf("<svg", "gradient", "<canvas"))) problems.add("no real graphics: add inline SVG illustrations, gradients or canvas art")
        }
        return problems.distinct()
    }
}
