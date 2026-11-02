package com.nova.assistant

/**
 * v6.87: "connect by voice / chat". The service starts a flow (after the owner's spoken or typed yes), the provider's token page opens,
 * and when NOVA comes back to the front the activity picks the token from the clipboard (Android only lets the focused app read it).
 * Holds no token itself, only which connector is waiting and until when. Pure JVM, unit tested.
 */
object ConnectorFlow {
    const val WAIT_MS = 10L * 60L * 1000L

    @Volatile private var id = ""
    @Volatile private var until = 0L

    fun start(connector: String, now: Long = System.currentTimeMillis()) {
        id = connector
        until = now + WAIT_MS
    }

    /** The connector waiting for a token, or "" (none, or the wait ran out). */
    fun active(now: Long = System.currentTimeMillis()): String = if (id.isNotEmpty() && now < until) id else ""

    fun clear() {
        id = ""
        until = 0L
    }
}
