package com.wisprfox.android.sync

/**
 * Single source of truth for the Supabase project this build talks to
 * (`../wispr-fox-web/docs/SYNC_DESIGN.md` "Client config"). The anon key is
 * publishable — safe to ship in the APK; RLS on the Supabase side is what
 * actually protects user data.
 *
 * The orchestrator bakes real values before tagging a release. Until then (or
 * in any build that never got them baked in), [isConfigured] is false and
 * every sync/account code path must behave exactly as signed-out: no crashes,
 * no network calls, a quiet "Sync not configured in this build" note in the UI.
 */
object SupabaseConfig {
    const val SUPABASE_URL = "https://hvaljemiwuhnohrndyyh.supabase.co"
    const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imh2YWxqZW1pd3Vobm9ocm5keXloIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODQxMjI1MDAsImV4cCI6MjA5OTY5ODUwMH0.kOu8wVU1XqGOooAShVCQtkr6IIxcdeBujuyUiajMOBc"

    /** Deep-link target Google/OAuth redirects back into the app on. */
    const val AUTH_REDIRECT_URL = "wisprfox://auth-callback"

    fun isConfigured(): Boolean =
        SUPABASE_URL.isNotBlank() &&
            !SUPABASE_URL.endsWith("_PLACEHOLDER") &&
            SUPABASE_ANON_KEY.isNotBlank() &&
            !SUPABASE_ANON_KEY.endsWith("_PLACEHOLDER")
}
