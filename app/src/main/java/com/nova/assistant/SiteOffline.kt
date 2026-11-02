package com.nova.assistant

/**
 * v7.115 offline website: a complete, good-looking one-page site that needs NO AI answer (pure Kotlin, unit tested).
 * Used by SiteGen only when (a) no AI provider answered a NEW site, or (b) the AI page is a stub (< SiteQuality.MIN_LEN).
 * It is built to pass SiteQuality.check by itself: viewport, lang, one h1, landmarks, CSS variables, responsive grid,
 * hover / focus states, reduced-motion fallback, no remote images, no dead "#" links, no placeholder text.
 * The owner then edits it by voice ("naam badlo", "photo lagao") through the normal edit path.
 */
object SiteOffline {
    private class Theme(val key: String, val accent: String, val accent2: String, val bg: String, val tag: String,
                        val s1: String, val d1: String, val s2: String, val d2: String, val s3: String, val d3: String)

    private val FOOD = Theme("food", "#d9480f", "#f59f00", "#fff8f0", "Fresh, warm and made with care",
        "Fresh ingredients", "Every dish starts with ingredients chosen the same day.",
        "Family recipes", "Honest flavours the way they are cooked at home.",
        "Quick and friendly", "Order, pick up or sit down. We keep it easy.")
    private val BEAUTY = Theme("beauty", "#c2255c", "#f783ac", "#fff5f8", "Look good. Feel even better",
        "Skilled team", "Trained hands who listen before they start.",
        "Clean and safe", "Fresh tools and a spotless space for every visit.",
        "Your style", "Classic or new, we shape it around you.")
    private val FIT = Theme("fit", "#0b7285", "#fcc419", "#f1fbfd", "Stronger every single week",
        "Real coaching", "A plan built around your body and your goal.",
        "Open and friendly", "A place where beginners and regulars train side by side.",
        "Track progress", "Small wins are measured so you keep going.")
    private val TECH = Theme("tech", "#4c6ef5", "#15aabf", "#f4f6ff", "Smart tools that save your time",
        "Simple to use", "Clean screens and clear steps from the first tap.",
        "Built to last", "Reliable work you can depend on every day.",
        "Support that answers", "Real people who reply when you need help.")
    private val SHOP = Theme("shop", "#e8590c", "#2b8a3e", "#fff9f2", "Good products at honest prices",
        "Quality checked", "Every item is checked before it reaches you.",
        "Fair prices", "Clear pricing with no surprise at the counter.",
        "Easy buying", "Walk in, call or message. We make it quick.")
    private val GENERAL = Theme("general", "#0c8599", "#f08c00", "#f3fafb", "Quality work you can trust",
        "Careful work", "We take time to get the details right.",
        "Clear and honest", "You always know what to expect from us.",
        "Always reachable", "Message us and we reply without delay.")

    private val FOOD_KEYS = listOf("restaurant", "cafe", "food", "bakery", "biryani", "dhaba", "hotel", "kitchen", "sweet", "pizza", "burger", "chai", "tea", "coffee")
    private val BEAUTY_KEYS = listOf("salon", "parlour", "parlor", "spa", "makeup", "boutique", "fashion", "beauty", "mehndi", "tailor")
    private val FIT_KEYS = listOf("gym", "fitness", "yoga", "trainer", "sports", "academy", "coaching")
    private val TECH_KEYS = listOf("app", "software", "startup", "tech", "agency", "ai", "nova", "portfolio", "developer", "digital")
    private val SHOP_KEYS = listOf("shop", "store", "kirana", "mobile", "electronics", "furniture", "garments", "mart", "bazaar")

    private fun has(t: String, keys: List<String>): Boolean {
        val words = t.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        return keys.any { k -> words.contains(k) }
    }

    private fun theme(text: String): Theme = when {
        has(text, FOOD_KEYS) -> FOOD
        has(text, BEAUTY_KEYS) -> BEAUTY
        has(text, FIT_KEYS) -> FIT
        has(text, TECH_KEYS) -> TECH
        has(text, SHOP_KEYS) -> SHOP
        else -> GENERAL
    }

    fun themeKey(text: String): String = theme(text).key

    private val NOISE = setOf("ek", "ki", "ka", "ke", "ko", "ye", "is", "ab", "mere", "meri", "mera", "apni", "apna",
        "website", "site", "webpage", "page", "banao", "bana", "do", "de", "dena", "karo", "kar", "nova", "please", "plz", "wali", "wala", "for", "a", "an", "the", "make", "build", "create", "me", "my", "mere liye")

    /** A short display name from the spoken subject ("ek restaurant ki website banao" -> "Restaurant"). */
    fun siteName(subject: String): String {
        val words = subject.trim().split(Regex("\\s+")).filter { it.isNotEmpty() && it.lowercase() !in NOISE }
        val name = words.take(4).joinToString(" ").take(40).trim()
        if (name.isEmpty()) return "Our Website"
        return name.split(" ").joinToString(" ") { w -> w.replaceFirstChar { c -> c.uppercaseChar() } }
    }

    fun esc(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    fun build(subject: String, request: String): String {
        val t = theme(subject + " " + request)
        val name = esc(siteName(subject.ifBlank { request }))
        val sb = StringBuilder(TEMPLATE)
        fun put(k: String, v: String) {
            var i = sb.indexOf(k)
            while (i >= 0) { sb.replace(i, i + k.length, v); i = sb.indexOf(k, i + v.length) }
        }
        put("@@NAME@@", name)
        put("@@ACCENT@@", t.accent)
        put("@@ACCENT2@@", t.accent2)
        put("@@BG@@", t.bg)
        put("@@TAG@@", esc(t.tag))
        put("@@S1@@", esc(t.s1)); put("@@D1@@", esc(t.d1))
        put("@@S2@@", esc(t.s2)); put("@@D2@@", esc(t.d2))
        put("@@S3@@", esc(t.s3)); put("@@D3@@", esc(t.d3))
        return sb.toString()
    }

    private const val TEMPLATE = """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>@@NAME@@</title>
<meta name="description" content="@@NAME@@ - @@TAG@@">
<style>
:root{--accent:@@ACCENT@@;--accent2:@@ACCENT2@@;--bg:@@BG@@;--ink:#1d2330;--muted:#5b6475;--card:#ffffff;--radius:18px;--gap:clamp(16px,4vw,32px);--font:system-ui,-apple-system,"Segoe UI",Roboto,Arial,sans-serif}
*{box-sizing:border-box}
html{scroll-behavior:smooth}
body{margin:0;font-family:var(--font);color:var(--ink);background:var(--bg);line-height:1.6}
a{color:inherit}
.wrap{width:min(1080px,92%);margin:0 auto}
header{position:sticky;top:0;z-index:5;background:rgba(255,255,255,.92);backdrop-filter:blur(8px);border-bottom:1px solid rgba(0,0,0,.06)}
.bar{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:12px 0;flex-wrap:wrap}
.logo{font-weight:800;font-size:1.15rem;letter-spacing:.2px;color:var(--accent);text-decoration:none}
nav{display:flex;gap:6px;flex-wrap:wrap}
nav a{text-decoration:none;padding:8px 14px;border-radius:999px;font-weight:600;font-size:.95rem;color:var(--ink);transition:background .2s,color .2s}
nav a:hover,nav a:focus{background:var(--accent);color:#fff;outline:none}
.hero{padding:clamp(48px,10vw,110px) 0;background:linear-gradient(135deg,var(--accent),var(--accent2));color:#fff}
.hero h1{font-size:clamp(2rem,7vw,3.6rem);line-height:1.1;margin:0 0 14px;animation:rise .7s ease both}
.hero p{font-size:clamp(1.05rem,2.6vw,1.3rem);max-width:34em;margin:0 0 26px;opacity:.96;animation:rise .9s ease both}
.btn{display:inline-block;background:#fff;color:var(--accent);font-weight:700;text-decoration:none;padding:13px 26px;border-radius:999px;box-shadow:0 8px 22px rgba(0,0,0,.18);transition:transform .2s,box-shadow .2s}
.btn:hover,.btn:focus{transform:translateY(-3px);box-shadow:0 12px 28px rgba(0,0,0,.24);outline:none}
.btn.alt{background:var(--accent);color:#fff}
section{padding:clamp(40px,8vw,84px) 0}
h2{font-size:clamp(1.5rem,4.5vw,2.2rem);margin:0 0 10px;color:var(--ink)}
.lead{color:var(--muted);max-width:40em;margin:0 0 28px}
.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(240px,1fr));gap:var(--gap)}
.card{background:var(--card);border-radius:var(--radius);padding:26px;box-shadow:0 6px 24px rgba(20,30,50,.08);border-top:5px solid var(--accent);transition:transform .25s,box-shadow .25s}
.card:hover,.card:focus-within{transform:translateY(-6px);box-shadow:0 14px 34px rgba(20,30,50,.14)}
.card h3{margin:0 0 8px;color:var(--accent)}
.card p{margin:0;color:var(--muted)}
.band{background:var(--card)}
.stats{display:flex;flex-wrap:wrap;gap:var(--gap)}
.stat{flex:1 1 180px;text-align:center;padding:20px;border-radius:var(--radius);background:var(--bg)}
.stat b{display:block;font-size:2rem;color:var(--accent)}
.cta{text-align:center;background:var(--accent);color:#fff;border-radius:var(--radius);padding:clamp(28px,6vw,54px)}
.cta h2{color:#fff}
footer{padding:28px 0;text-align:center;color:var(--muted);font-size:.92rem}
@keyframes rise{from{opacity:0;transform:translateY(18px)}to{opacity:1;transform:none}}
@media (max-width:600px){nav{width:100%}nav a{flex:1;text-align:center;padding:8px 6px}}
@media (prefers-reduced-motion:reduce){*{animation:none!important;transition:none!important;scroll-behavior:auto!important}}
</style>
</head>
<body id="top">
<header>
<div class="wrap bar">
<a class="logo" href="#top">@@NAME@@</a>
<nav aria-label="Main">
<a href="#services">Services</a>
<a href="#about">About</a>
<a href="#contact">Contact</a>
</nav>
</div>
</header>
<main>
<section class="hero" aria-label="Welcome">
<div class="wrap">
<h1>@@NAME@@</h1>
<p>@@TAG@@. Welcome, take a look around and get in touch whenever you are ready.</p>
<a class="btn" href="#contact">Get in touch</a>
</div>
</section>
<section id="services">
<div class="wrap">
<h2>What we offer</h2>
<p class="lead">Three things we promise to every person who walks in or messages us.</p>
<div class="grid">
<article class="card" tabindex="0"><h3>@@S1@@</h3><p>@@D1@@</p></article>
<article class="card" tabindex="0"><h3>@@S2@@</h3><p>@@D2@@</p></article>
<article class="card" tabindex="0"><h3>@@S3@@</h3><p>@@D3@@</p></article>
</div>
</div>
</section>
<section id="about" class="band">
<div class="wrap">
<h2>About us</h2>
<p class="lead">@@NAME@@ is built on simple values: do the work well, be honest, and treat every person with respect. We grow because people tell their friends.</p>
<div class="stats">
<div class="stat"><b>1</b>Team, one standard</div>
<div class="stat"><b>7</b>Days a week to reach us</div>
<div class="stat"><b>100%</b>Effort on every job</div>
</div>
</div>
</section>
<section id="contact">
<div class="wrap">
<div class="cta">
<h2>Let us talk</h2>
<p>Call or message us to book, order or ask anything. We reply quickly.</p>
<a class="btn" href="#top">Back to top</a>
</div>
</div>
</section>
</main>
<footer>
<div class="wrap">&copy; @@NAME@@. All rights reserved.</div>
</footer>
</body>
</html>
"""
}
