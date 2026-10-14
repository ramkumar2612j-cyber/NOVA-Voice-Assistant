package com.nova.assistant

/** Shared outcome keeps provider errors distinct from a genuine empty contact list. */
sealed class ContactLookupResult {
    data class Success(val rows: List<ContactPhoneRow>) : ContactLookupResult()
    data class Failure(val reason: String, val permissionDenied: Boolean = false) : ContactLookupResult()
}

/** Injectable query seam: fakes are for unit tests only; production supplies ContactLookup. */
interface ContactRowSource {
    fun queryAll(): ContactLookupResult
}

sealed class ContactResolutionOutcome {
    data class ResolvedCandidates(val result: ContactMatchResult) : ContactResolutionOutcome()
    data class LookupFailed(val failure: ContactLookupResult.Failure) : ContactResolutionOutcome()
}

/** Stages 2–4: obtain actual rows, match and report candidates without executing an action. */
object ContactResolutionPipeline {
    fun resolve(query: String, source: ContactRowSource): ContactResolutionOutcome = when (val result = source.queryAll()) {
        is ContactLookupResult.Success -> ContactResolutionOutcome.ResolvedCandidates(ContactMatcher.match(query, result.rows))
        is ContactLookupResult.Failure -> ContactResolutionOutcome.LookupFailed(result)
    }
}
