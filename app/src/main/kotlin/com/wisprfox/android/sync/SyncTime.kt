package com.wisprfox.android.sync

import java.time.Instant
import java.time.format.DateTimeParseException

/** RFC3339/ISO-8601 UTC helpers — the wire format the whole sync protocol uses. */
object SyncTime {
    fun toIso(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).toString()

    fun nowIso(): String = Instant.now().toString()

    /** Returns null on an unparsable/blank timestamp rather than throwing —
     *  pull rows are attacker-adjacent-enough (server-controlled JSON) that a
     *  malformed timestamp should degrade to "skip this row", not crash sync. */
    fun toMillis(iso: String?): Long? {
        if (iso.isNullOrBlank()) return null
        return try {
            Instant.parse(iso).toEpochMilli()
        } catch (e: DateTimeParseException) {
            null
        }
    }
}
