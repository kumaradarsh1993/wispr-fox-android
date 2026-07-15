package com.wisprfox.android.sync

import android.util.Base64
import com.wisprfox.android.settings.SecureKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * Supabase Auth (GoTrue) client — OkHttp + kotlinx.serialization only, no
 * Supabase SDK (house style, `../wispr-fox-web/docs/SYNC_DESIGN.md` "Auth
 * flows per platform" / android). Handles email/password sign-in + sign-up and
 * the Google PKCE flow driven through a Custom Tab (no native Google SDK, no
 * Android SHA-1 registration — Supabase's `/authorize` does all of it).
 *
 * Token storage: refresh token + user id/email go to [SecureKeyStore]
 * (hardware-backed). The access token lives in memory only, matching the
 * desktop keyring-first rule for the same class of secret; it's re-derived
 * from the refresh token on cold start via [getValidAccessToken].
 *
 * Every public entry point no-ops (or fails softly) when
 * [SupabaseConfig.isConfigured] is false — signed-out behaviour must be
 * identical to a build that never linked this file in.
 */
class AuthManager(
    baseClient: OkHttpClient,
    private val secrets: SecureKeyStore,
) {
    data class AuthUiState(
        val signedIn: Boolean,
        val email: String?,
        val userId: String?,
    )

    private val client: OkHttpClient = baseClient.newBuilder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private val refreshMutex = Mutex()

    @Volatile private var accessToken: String? = null
    @Volatile private var accessTokenExpiresAtMs: Long = 0L
    /** Held only for the lifetime of one PKCE round-trip (Custom Tab → redirect). */
    @Volatile private var pendingCodeVerifier: String? = null

    private val _state = MutableStateFlow(loadInitialState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    private fun loadInitialState(): AuthUiState {
        val signedIn = SupabaseConfig.isConfigured() && secrets.has(SecureKeyStore.Key.SupabaseRefreshToken)
        return AuthUiState(
            signedIn = signedIn,
            email = secrets.get(SecureKeyStore.Key.SupabaseUserEmail),
            userId = secrets.get(SecureKeyStore.Key.SupabaseUserId),
        )
    }

    fun isSignedIn(): Boolean = _state.value.signedIn

    // ─── Email / password ───────────────────────────────────────────────────

    suspend fun signInWithPassword(email: String, password: String): Result<Unit> =
        tokenRequest("grant_type=password", """{"email":${jsonStr(email)},"password":${jsonStr(password)}}""")

    suspend fun signUpWithPassword(email: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured()) return@withContext Result.failure(notConfigured())
        runCatching {
            val body = """{"email":${jsonStr(email)},"password":${jsonStr(password)}}"""
                .toRequestBody(JSON_MEDIA)
            val request = Request.Builder()
                .url("${SupabaseConfig.SUPABASE_URL}/auth/v1/signup")
                .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                .header("Content-Type", "application/json")
                .post(body)
                .build()
            val resp = execute(request)
            // Signup with email confirmation OFF returns a session immediately
            // (same shape as sign-in); with confirmation ON it returns just the
            // user and no tokens — surface that as a friendly Result.failure so
            // the UI can say "check your email" instead of silently no-op'ing.
            if (resp.access_token == null || resp.refresh_token == null) {
                throw IllegalStateException(
                    resp.msg ?: resp.error_description ?: resp.message
                        ?: "Account created — check your email to confirm, then sign in."
                )
            }
            applyTokenResponse(resp)
        }
    }

    // ─── Google via Supabase-managed PKCE ───────────────────────────────────

    /** Builds the `/authorize` URL to open in a Custom Tab and stashes the
     *  verifier for [completeGoogleSignIn]. Returns null if unconfigured. */
    fun beginGoogleSignIn(): String? {
        if (!SupabaseConfig.isConfigured()) return null
        val verifier = randomUrlSafe(64)
        val challenge = s256(verifier)
        pendingCodeVerifier = verifier
        val redirect = java.net.URLEncoder.encode(SupabaseConfig.AUTH_REDIRECT_URL, "UTF-8")
        return "${SupabaseConfig.SUPABASE_URL}/auth/v1/authorize" +
            "?provider=google" +
            "&redirect_to=$redirect" +
            "&code_challenge=$challenge" +
            "&code_challenge_method=s256"
    }

    /** Call from `MainActivity.onNewIntent` when the redirect Uri's host is
     *  `auth-callback`. Exchanges the `?code=` param using the stashed verifier. */
    suspend fun completeGoogleSignIn(code: String): Result<Unit> {
        val verifier = pendingCodeVerifier
            ?: return Result.failure(IllegalStateException("No sign-in in progress — try again."))
        val result = tokenRequest(
            "grant_type=pkce",
            """{"auth_code":${jsonStr(code)},"code_verifier":${jsonStr(verifier)}}""",
        )
        if (result.isSuccess) pendingCodeVerifier = null
        return result
    }

    // ─── Session ─────────────────────────────────────────────────────────────

    /** In-memory access token, refreshing via the stored refresh token if
     *  expired/absent. Returns null when signed out or refresh fails (in which
     *  case the caller should treat sync as unavailable this round — a failed
     *  refresh does NOT sign the user out here; a transient network blip
     *  shouldn't nuke a saved session. Only an explicit Sign out clears it.) */
    suspend fun getValidAccessToken(): String? {
        if (!SupabaseConfig.isConfigured()) return null
        val refreshToken = secrets.get(SecureKeyStore.Key.SupabaseRefreshToken) ?: return null
        refreshMutex.withLock {
            val now = System.currentTimeMillis()
            accessToken?.let { if (now < accessTokenExpiresAtMs - REFRESH_SKEW_MS) return it }
            val refreshed = tokenRequest("grant_type=refresh_token", """{"refresh_token":${jsonStr(refreshToken)}}""")
            return if (refreshed.isSuccess) accessToken else null
        }
    }

    suspend fun signOut() {
        val token = accessToken
        if (SupabaseConfig.isConfigured() && token != null) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val request = Request.Builder()
                        .url("${SupabaseConfig.SUPABASE_URL}/auth/v1/logout")
                        .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                        .header("Authorization", "Bearer $token")
                        .post("".toRequestBody(JSON_MEDIA))
                        .build()
                    client.newCall(request).execute().close()
                }
            }
        }
        accessToken = null
        accessTokenExpiresAtMs = 0L
        pendingCodeVerifier = null
        secrets.clear(SecureKeyStore.Key.SupabaseRefreshToken)
        secrets.clear(SecureKeyStore.Key.SupabaseUserEmail)
        secrets.clear(SecureKeyStore.Key.SupabaseUserId)
        _state.value = AuthUiState(signedIn = false, email = null, userId = null)
    }

    // ─── internals ───────────────────────────────────────────────────────────

    private suspend fun tokenRequest(grantQuery: String, jsonBody: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured()) return@withContext Result.failure(notConfigured())
        runCatching {
            val body = jsonBody.toRequestBody(JSON_MEDIA)
            val request = Request.Builder()
                .url("${SupabaseConfig.SUPABASE_URL}/auth/v1/token?$grantQuery")
                .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                .header("Content-Type", "application/json")
                .post(body)
                .build()
            val resp = execute(request)
            applyTokenResponse(resp)
        }
    }

    private fun execute(request: Request): TokenResponse {
        client.newCall(request).execute().use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            val parsed = runCatching { json.decodeFromString(TokenResponse.serializer(), bodyStr) }.getOrNull()
            if (!resp.isSuccessful) {
                val msg = parsed?.error_description ?: parsed?.msg ?: parsed?.message ?: parsed?.error
                    ?: "Sign-in failed (HTTP ${resp.code})."
                throw IllegalStateException(msg)
            }
            return parsed ?: throw IllegalStateException("Unexpected empty response from Supabase.")
        }
    }

    private fun applyTokenResponse(resp: TokenResponse) {
        val access = resp.access_token ?: throw IllegalStateException("No access token in response.")
        val refresh = resp.refresh_token ?: throw IllegalStateException("No refresh token in response.")
        accessToken = access
        accessTokenExpiresAtMs = System.currentTimeMillis() + (resp.expires_in ?: 3600L) * 1000L
        secrets.put(SecureKeyStore.Key.SupabaseRefreshToken, refresh)
        resp.user?.email?.let { secrets.put(SecureKeyStore.Key.SupabaseUserEmail, it) }
        resp.user?.id?.let { secrets.put(SecureKeyStore.Key.SupabaseUserId, it) }
        _state.value = AuthUiState(
            signedIn = true,
            email = resp.user?.email ?: secrets.get(SecureKeyStore.Key.SupabaseUserEmail),
            userId = resp.user?.id ?: secrets.get(SecureKeyStore.Key.SupabaseUserId),
        )
    }

    private fun notConfigured() = IllegalStateException("Sync not configured in this build.")

    private fun jsonStr(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun randomUrlSafe(bytes: Int): String {
        val buf = ByteArray(bytes)
        SecureRandom().nextBytes(buf)
        return Base64.encodeToString(buf, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun s256(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    companion object {
        private val JSON_MEDIA = "application/json".toMediaType()
        private const val REFRESH_SKEW_MS = 30_000L
    }
}
