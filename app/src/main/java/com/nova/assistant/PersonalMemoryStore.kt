package com.nova.assistant

import android.content.Context

/**
 * Where the personal facts live: one private SharedPreferences file "nova_memory" on this phone only.
 * allowBackup is false in the manifest, so the file is not copied to any cloud backup either.
 * Facts are never logged and never leave the phone from here (see PersonalMemory for the only two ways a few relevant
 * facts reach a model: LocalBrains.chat on the device itself, and the optional online chain system prompt).
 */
object PersonalMemoryStore {
    private const val PREF = "nova_memory"
    private const val KEY = "facts"
    private val lock = Any()

    fun load(ctx: Context): List<String> = synchronized(lock) {
        try {
            PersonalMemory.decode(ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, null))
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** true = written. commit() so a crash right after "yaad rakh liya" cannot lose it. */
    fun save(ctx: Context, list: List<String>): Boolean = synchronized(lock) {
        try {
            ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putString(KEY, PersonalMemory.encode(list)).commit()
        } catch (e: Exception) {
            false
        }
    }

    fun clear(ctx: Context): Boolean = synchronized(lock) {
        try {
            ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(KEY).commit()
        } catch (e: Exception) {
            false
        }
    }

    fun count(ctx: Context): Int = load(ctx).size

    /** PART 7: load + change + save under ONE lock, so the Memory screen and a voice command cannot overwrite each other. persisted=false: the phone did not save. */
    class Updated(val result: PersonalMemory.AddResult, val persisted: Boolean)

    fun update(ctx: Context, change: (List<String>) -> PersonalMemory.AddResult): Updated = synchronized(lock) {
        val r = change(load(ctx))
        Updated(r, if (r.status == "saved") save(ctx, r.list) else true)
    }

    /** PART 7: merge an imported text file (every line is checked by PersonalMemory.add). null = unusable file or the phone did not save. */
    fun importFrom(ctx: Context, text: String): PersonalMemory.ImportResult? = synchronized(lock) {
        val r = PersonalMemory.importText(load(ctx), text) ?: return@synchronized null
        if (r.added > 0 && !save(ctx, r.list)) return@synchronized null
        r
    }
}
