package com.nova.assistant

/** One real phone row returned by Android ContactsContract. CONTACT_ID groups multiple numbers for one person. */
data class ContactPhoneRow(
    val contactId: String,
    val displayName: String,
    val number: String,
    val type: Int = 0,
    val isSuperPrimary: Boolean = false
)

data class ContactCandidate(
    val contactId: String,
    val displayName: String,
    val number: String,
    val score: Double,
    val phoneRows: List<ContactPhoneRow>
)

enum class ContactMatchStatus {
    MATCH, AMBIGUOUS_DUPLICATE, AMBIGUOUS_SIMILAR, LOW_CONFIDENCE, NO_MATCH, NEED_NAME
}

data class ContactMatchResult(val status: ContactMatchStatus, val candidates: List<ContactCandidate>) {
    val selected: ContactCandidate? get() = if (status == ContactMatchStatus.MATCH) candidates.firstOrNull() else null
}

/** Stages 3+4: score candidates and expose ambiguity; never picks among multiple plausible people. */
object ContactMatcher {
    private data class Scored(val id: String, val name: String, val rows: List<ContactPhoneRow>, val score: Double, val exact: Boolean)

    fun match(query: String, rows: List<ContactPhoneRow>): ContactMatchResult {
        val q = basic(query)
        if (q.isBlank()) return ContactMatchResult(ContactMatchStatus.NEED_NAME, emptyList())
        if (rows.isEmpty()) return ContactMatchResult(ContactMatchStatus.NO_MATCH, emptyList())
        val grouped = rows.filter { it.displayName.isNotBlank() && it.number.isNotBlank() }
            .groupBy { if (it.contactId.isNotBlank()) it.contactId else "row:${it.displayName.lowercase()}:${it.number}" }
        // The same person saved twice (two CONTACT_IDs, e.g. phone + SIM/Google copy): same name AND same number set is not
        // ambiguity. Distinct numbers under one name still ARE ambiguous and stay separate.
        val merged = LinkedHashMap<String, Pair<String, List<ContactPhoneRow>>>()
        for ((id, group) in grouped) {
            val numbers = group.map { it.number.filter { c -> c.isDigit() }.takeLast(10) }.toSortedSet().joinToString(",")
            val key = basic(group.first().displayName) + "|" + numbers
            val existing = merged[key]
            merged[key] = if (existing == null) id to group else existing.first to (existing.second + group)
        }
        val scored = merged.values.mapNotNull { (id, group) ->
            val name = group.first().displayName
            val nameBasic = basic(name)
            val qFold = fold(q)
            val nFold = fold(nameBasic)
            val qt = q.split(" ").filter { it.isNotBlank() }
            val nt = nameBasic.split(" ").filter { it.isNotBlank() }
            val exact = q == nameBasic
            val score = when {
                exact -> 1.0
                qFold == nFold -> 0.96
                qt.isNotEmpty() && qt == nt -> 0.94
                qt.isNotEmpty() && qt.all { it in nt } -> 0.89
                q in nameBasic || nameBasic in q -> 0.83
                qt.any { token -> nt.any { it.startsWith(token) || token.startsWith(it) } } -> 0.78
                else -> {
                    val compactQ = qFold.replace(" ", "")
                    val compactN = nFold.replace(" ", "")
                    val similarity = 1.0 - levenshtein(compactQ, compactN).toDouble() / maxOf(compactQ.length, compactN.length, 1)
                    if (similarity >= 0.50) 0.50 + similarity * 0.35 else similarity * 0.5
                }
            }
            Scored(id, name, group, score, exact)
        }.sortedWith(compareByDescending<Scored> { it.score }.thenBy { it.name.lowercase() })

        val plausible = scored.filter { it.score >= 0.76 }
        if (plausible.isEmpty()) {
            return ContactMatchResult(if (scored.firstOrNull()?.score ?: 0.0 >= 0.50) ContactMatchStatus.LOW_CONFIDENCE else ContactMatchStatus.NO_MATCH,
                scored.take(5).map { it.toCandidate() })
        }
        val exactCandidates = plausible.filter { it.exact }
        if (exactCandidates.size > 1) {
            return ContactMatchResult(ContactMatchStatus.AMBIGUOUS_DUPLICATE, exactCandidates.map { it.toCandidate() })
        }
        val top = plausible.first()
        val others = plausible.drop(1)
        // Safety rule: exactness does not override another plausible person. Even when one
        // candidate scores higher, do not auto-select if more than one distinct contact is plausible.
        if (top.exact && plausible.size == 1) {
            return ContactMatchResult(ContactMatchStatus.MATCH, listOf(top.toCandidate()))
        }
        if (others.isNotEmpty()) {
            val similar = plausible.filter { top.score - it.score <= 0.16 }
            return ContactMatchResult(ContactMatchStatus.AMBIGUOUS_SIMILAR, (if (similar.isNotEmpty()) similar else plausible.take(5)).map { it.toCandidate() })
        }
        if (top.score < 0.84) return ContactMatchResult(ContactMatchStatus.LOW_CONFIDENCE, listOf(top.toCandidate()))
        return ContactMatchResult(ContactMatchStatus.MATCH, listOf(top.toCandidate()))
    }

    fun basic(raw: String): String = HindiRoman.toRoman(raw).lowercase()
        .replace(Regex("[^a-z0-9+ ]"), " ")
        .replace(Regex("\\s+"), " ").trim()

    private fun fold(raw: String): String = basic(raw)
        .replace("aa", "a").replace("ee", "i").replace("ii", "i")
        .replace("oo", "u").replace("uu", "u").replace("sh", "s")
        .replace("v", "w").replace("ph", "f")
        .replace(Regex("(.)\\1+"), "$1")

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            val cur = IntArray(b.length + 1)
            cur[0] = i + 1
            for (j in b.indices) cur[j + 1] = minOf(cur[j] + 1, prev[j + 1] + 1, prev[j] + if (a[i] == b[j]) 0 else 1)
            prev = cur
        }
        return prev[b.length]
    }

    private fun Scored.toCandidate(): ContactCandidate {
        val ordered = rows.sortedWith(compareByDescending<ContactPhoneRow> { it.isSuperPrimary }
            .thenByDescending { it.type == 2 /* Phone.TYPE_MOBILE */ })
        return ContactCandidate(id, name, ordered.firstOrNull()?.number.orEmpty(), score, ordered)
    }
}
