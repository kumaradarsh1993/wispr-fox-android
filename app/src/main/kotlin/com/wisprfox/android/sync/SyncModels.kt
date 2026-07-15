package com.wisprfox.android.sync

import kotlinx.serialization.Serializable

/**
 * Wire shapes for the Supabase REST surface (PostgREST `/rest/v1/...` + GoTrue
 * `/auth/v1/...`), matching `../wispr-fox-web/supabase/schema.sql` exactly.
 * Plain data classes + kotlinx.serialization, no Retrofit (house style).
 * `created_at`/`updated_at`/`deleted_at`/`last_seen_at` travel as RFC3339
 * strings on the wire; [SyncTime] converts to/from local epoch millis.
 */

@Serializable
data class RemoteDevice(
    val id: String,
    val user_id: String? = null,
    val name: String = "",
    val platform: String = "mobile",
    val last_seen_at: String? = null,
)

@Serializable
data class RemoteNote(
    val id: String,
    val user_id: String? = null,
    val device_id: String? = null,
    val platform: String,
    val origin: String = "mic",
    val title: String? = null,
    val transcript: String? = null,
    val cleaned_text: String? = null,
    val drafted_text: String? = null,
    val duration_ms: Long = 0,
    val stt_provider: String? = null,
    val llm_provider: String? = null,
    val created_at: String,
    val updated_at: String? = null,
    val deleted_at: String? = null,
)

@Serializable
data class RemoteSetting(
    val user_id: String? = null,
    val key: String,
    val value: String? = null,
    val updated_at: String? = null,
)

// ─── Auth (GoTrue) ──────────────────────────────────────────────────────────

@Serializable
data class SupabaseAuthUser(
    val id: String,
    val email: String? = null,
)

@Serializable
data class TokenResponse(
    val access_token: String? = null,
    val refresh_token: String? = null,
    val expires_in: Long? = null,
    val token_type: String? = null,
    val user: SupabaseAuthUser? = null,
    // GoTrue error shapes vary by endpoint; capture both common forms.
    val error: String? = null,
    val error_description: String? = null,
    val msg: String? = null,
    val message: String? = null,
)
