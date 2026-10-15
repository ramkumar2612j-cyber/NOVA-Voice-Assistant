package com.nova.assistant

/**
 * v7.0 WEBSITE DESIGN DIRECTOR. Pure Kotlin (JVM testable). Fixes "every site looks the same":
 * each build gets ONE distinct design direction (palette, type, layout, motion) chosen from the subject and a seed,
 * unless the owner already named colours / a theme (then the owner's words win and only layout + motion are suggested).
 * Nothing here talks to the network.
 */
object SiteDesign {
    class Direction(val name: String, val palette: String, val type: String, val layout: String, val motion: String)

    val DIRECTIONS: List<Direction> = listOf(
        Direction("Editorial warm paper", "paper #FAF6EF, ink #1C1A17, accent terracotta #C4553A, muted sand #E9DFD0",
            "serif display headings (Georgia, 'Times New Roman', serif) at very large sizes + clean sans body", "asymmetric magazine grid, oversized numerals, thin rules, overlapping image frames",
            "slow fade-up reveals, image mask reveals, marquee strip"),
        Direction("Clinical fresh light", "white #FFFFFF, mist #F2F8F8, teal #0E9AA7, deep slate #12343B, soft mint #D7F2EE",
            "rounded geometric sans (system-ui) with generous line height", "airy cards with big radius, soft shadows, icon rows, a sticky booking bar",
            "gentle floating shapes, count-up numbers, smooth card lift on hover"),
        Direction("Swiss minimal", "off-white #F5F5F2, black #0B0B0B, signal red #E5322D",
            "huge tight-tracking grotesk headings (Helvetica, Arial, sans-serif), small caps labels", "strict 12-column grid, giant headline, hairline dividers, lots of white space",
            "text line-by-line reveal, underline sweeps, cursor-follow highlight"),
        Direction("Luxury noir gold", "black #0A0A0B, graphite #17171A, champagne gold #C9A96A, ivory #F3EDE0",
            "high-contrast serif headings (Georgia italic accents) + letter-spaced uppercase labels", "full-bleed hero, centred symmetric sections, thin gold frames",
            "slow parallax, shimmering gold lines, soft spotlight following the pointer"),
        Direction("Aurora glass", "deep indigo #0D1030, violet #7A5CFF, pink #FF5CA8, cyan #3DE0FF, glass rgba(255,255,255,.08)",
            "modern sans (system-ui), bold gradient headings", "mesh-gradient background, frosted glass cards, floating blurred orbs",
            "animated gradient mesh, glass tilt cards, orbit animation"),
        Direction("Neo-brutalist pop", "cream #FFF4D6, black #111, hot yellow #FFD60A, pink #FF4D8D, blue #3A5BFF",
            "heavy grotesk + monospace labels", "thick black borders, hard offset shadows, sticker badges, chunky buttons",
            "bouncy hover offsets, rotating sticker, wiggle on scroll"),
        Direction("Organic nature", "cream #F6F3EA, sage #9CB59A, forest #1F3D2B, clay #D9A47E",
            "soft serif headings + humanist sans body", "blob-shaped image masks, wavy section dividers, leaf-like shapes in SVG",
            "slow drifting blobs, gentle parallax, grow-in reveals"),
        Direction("Bold gradient energy", "midnight #120B2E, electric purple #7B2FF7, orange #FF8A00, magenta #F107A3, white",
            "very bold sans headings, tight leading", "diagonal section cuts, big shapes, stat counters, punchy CTAs",
            "gradient text shimmer, shape morphing, scroll-linked scale"),
        Direction("Retro-future neon 3D", "space #05060F, neon cyan #00F0FF, neon magenta #FF2BD6, lime #B6FF3B",
            "monospace + wide display sans", "perspective grid floor, 3D rotating objects, HUD-style panels",
            "three-dimensional rotation, scanlines, glitch hover, scroll-driven camera feel"),
        Direction("Pastel playful", "butter #FFF3C4, peach #FFD3B6, lilac #D8C8FF, sky #BFE6FF, ink #2B2B3A",
            "rounded friendly sans, large friendly headings", "pill buttons, stacked rounded cards, hand-drawn SVG doodles",
            "springy hover, floating stickers, confetti on CTA"),
        Direction("Corporate trust blue", "white #FFFFFF, navy #0B2A5B, royal #1F5FD6, ice #EAF1FF, amber accent #F5A623",
            "clear sans-serif, strong hierarchy", "structured hero + proof bar, feature grid, testimonial slider, FAQ accordion",
            "subtle slide-in, animated underline, progress bars"),
        Direction("Warm earth rustic", "sand #F2E6D3, espresso #3B2A20, saffron #E69A28, brick #A8442A",
            "slab/serif headings (Georgia bold) + sans body", "textured panels, ribbon badges, menu-card style lists",
            "steam/ember particles, warm glow hover, slow zoom on photos")
    )

    private fun has(s: String, vararg w: String) = w.any { s.contains(it) }

    /** Indexes of directions that suit the subject best (first choice pool). Empty = any. */
    fun pool(request: String): List<Int> {
        val s = request.lowercase()
        return when {
            has(s, "luxury", "luxurious", "premium", "professional", "world class", "world-class", "high end", "high-end", "elegant", "executive") -> listOf(3, 0, 2, 10)
            has(s, "dental", "dentist", "clinic", "doctor", "hospital", "medical", "health", "pharmacy", "physio") -> listOf(1, 10, 2, 6)
            has(s, "restaurant", "cafe", "café", "food", "bakery", "biryani", "menu", "sweet", "dhaba", "hotel") -> listOf(0, 11, 6, 9)
            has(s, "salon", "spa", "beauty", "jewel", "luxury", "boutique", "wedding", "perfume") -> listOf(3, 0, 6, 4)
            has(s, "gym", "fitness", "sport", "crossfit", "yoga") -> listOf(7, 5, 8, 2)
            has(s, "game", "gaming", "tech", "ai ", "robot", "crypto", "startup", "saas", "app") -> listOf(4, 8, 7, 10)
            has(s, "kid", "school", "toy", "children", "play", "education", "coaching") -> listOf(9, 5, 1, 10)
            has(s, "portfolio", "photograph", "designer", "artist", "architect", "agency") -> listOf(2, 0, 5, 3)
            has(s, "farm", "organic", "garden", "nature", "ayurved", "herbal") -> listOf(6, 11, 0)
            has(s, "law", "finance", "bank", "insurance", "consult", "real estate", "property") -> listOf(10, 3, 2)
            else -> listOf(3, 0, 2, 10, 1)   // v7.4: nothing specific named = a refined, premium direction, never a loud template
        }
    }

    /** Owner already chose colours or a theme: then the design brief must not override them. */
    fun ownerChoseLook(request: String): Boolean {
        val s = request.lowercase()
        if (Regex("#[0-9a-f]{3,8}\\b").containsMatchIn(s)) return true
        val colours = listOf("white", "black", "blue", "red", "green", "yellow", "orange", "purple", "pink", "gold", "golden", "silver", "grey", "gray", "cyan", "teal",
            "safed", "kala", "neela", "lal", "hara", "peela", "colour", "color", "rang", "theme", "background", "dark mode", "light mode", "dark theme", "light theme")
        return colours.any { Regex("\\b" + Regex.escape(it) + "\\b").containsMatchIn(s) }
    }

    fun pick(request: String, seed: Long): Direction {
        val pool = pool(request)
        val idx = if (pool.isEmpty()) Math.floorMod(seed, DIRECTIONS.size.toLong()).toInt()
        else pool[Math.floorMod(seed, pool.size.toLong()).toInt()]
        return DIRECTIONS[idx]
    }

    /** The text block appended to the generation prompt. */
    fun brief(request: String, seed: Long): String {
        val d = pick(request, seed)
        val owner = ownerChoseLook(request)
        return buildString {
            append("\n\nDESIGN DIRECTION (make THIS site look unique; do not fall back to the usual dark-navy + cyan/gold template):\n")
            if (owner) {
                append("The owner already named colours or a theme in the request: use exactly those colours/theme everywhere. ")
                append("From the direction below take ONLY layout, typography feel and motion.\n")
            } else {
                append("Use this look unless the owner's words say otherwise.\n")
                append("- Palette: ").append(d.palette).append('\n')
            }
            append("- Direction name: ").append(d.name).append('\n')
            append("- Typography: ").append(d.type).append('\n')
            append("- Layout: ").append(d.layout).append('\n')
            append("- Motion: ").append(d.motion).append('\n')
        }
    }

    /** Capability + quality bar for the model. Honest about what runs inside one HTML file. */
    const val CRAFT = "QUALITY BAR (world-class, not a template): real sections for THIS subject with specific, believable copy in the owner's language; " +
        "strong hero with a real animated or 3D focal element (CSS 3D, canvas, WebGL or inline SVG), scroll-reveal (IntersectionObserver), hover/tilt micro-interactions, " +
        "custom inline SVG illustrations/icons/graphics (never emoji as the main art), gradients, patterns, sticky responsive nav with mobile menu, smooth anchors, " +
        "working front-end features: form validation with visible success state, booking/enquiry that opens WhatsApp or mailto links (no hidden server calls), " +
        "tabs/accordion/slider/lightbox where they fit, prefers-reduced-motion support, mobile-first layout that never scrolls sideways. " +
        "If the owner says 3D use real 3D (CSS perspective/preserve-3d or three.js). If the owner says 4D use 3D plus time/scroll-driven change (objects that morph, orbit or react to scroll and pointer). " +
        "If the owner asks for a video, build an animated hero/showreel from canvas/SVG/CSS animation unless an uploaded video token is supplied. " +
        "Optional libraries, ONLY when truly needed and always with a plain-CSS fallback if they fail to load: " +
        "three.js https://cdnjs.cloudflare.com/ajax/libs/three.js/r128/three.min.js and GSAP https://cdnjs.cloudflare.com/ajax/libs/gsap/3.12.2/gsap.min.js (+ScrollTrigger.min.js same folder). No other external files. " +
        "Use only phone numbers, addresses, prices and medical/legal claims that the owner supplied; otherwise clearly marked editable placeholders."

    /** v7.4 premium finish + phone performance rules, appended after CRAFT. */
    const val LUXURY = "LUXURY / PROFESSIONAL FINISH (this is what makes it look expensive): " +
        "a strict design system: 8px spacing scale, one container max-width 1200px, fluid type with clamp() (hero 44-88px, body 16-18px, line-height 1.6), section padding 96-140px desktop and 56-72px mobile, one consistent radius, layered soft shadows, hairline borders, restrained palette (one accent colour), plenty of white space. " +
        "Typography: load ONE display font and ONE text font from Google Fonts via <link rel=\"preconnect\"> + <link href=\"https://fonts.googleapis.com/css2?family=...&display=swap\" rel=\"stylesheet\"> " +
        "(for example Cormorant Garamond / Playfair Display / DM Serif Display with Inter / Manrope / Outfit) and always a system fallback stack so it still looks fine offline. " +
        "Structure: sticky translucent glass navbar with backdrop-filter, hero with strong headline + one-line promise + primary and secondary buttons + trust row (ratings, years, numbers as clearly marked placeholders), then at least 7 sections " +
        "(services/menu/work, why choose us, process/steps, gallery or showcase, testimonials slider with placeholder names marked as samples, pricing or packages if it fits, FAQ accordion, final call-to-action band, multi-column footer). " +
        "Polish: easing cubic-bezier(.2,.8,.2,1) 300-500ms transitions, hover lift + glow on cards and buttons, image overlays/duotone, subtle gradient and grain overlays, thin scroll-progress bar, active-section highlight in the nav, smooth anchors, focus-visible rings, 44px minimum touch targets. " +
        "PHONE PERFORMANCE (the page must never hang or stutter): one requestAnimationFrame loop at most, pause every animation and canvas/WebGL loop when the section is off-screen (IntersectionObserver) or the tab is hidden, cap devicePixelRatio at 2, keep any three.js scene under about 2000 triangles and 1 light, animate only transform and opacity, no more than about 60 animated elements, no huge blur radii, use loading=\"lazy\" and decoding=\"async\" on images, respect prefers-reduced-motion and also run a lighter mode when navigator.hardwareConcurrency <= 4."
}
