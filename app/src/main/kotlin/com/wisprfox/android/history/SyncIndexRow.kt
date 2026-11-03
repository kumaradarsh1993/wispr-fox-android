package com.wisprfox.android.history

/** One local row's sync identity, read by the Refresh consistency check. */
data class SyncIndexRow(
    val id: String,
    val remote: Boolean,
    val status: String,
    val dirty: Boolean,
    val updatedAt: Long,
)
