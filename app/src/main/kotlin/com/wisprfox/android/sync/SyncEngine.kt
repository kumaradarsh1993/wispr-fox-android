package com.wisprfox.android.sync

import com.wisprfox.android.history.RecordingRepository
import com.wisprfox.android.history.RemotePulledNote
import com.wisprfox.android.history.SyncMetaDao
import com.wisprfox.android.history.SyncMetaEntity
import com.wisprfox.android.history.SyncMetaKeys
import com.wisprfox.android.settings.SecureKeyStore
import com.wisprfox.android.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Push/pull sync loop against Supabase PostgREST, implementing
 * `../wispr-fox-web/docs/SYNC_DESIGN.md` "Sync protocol". OkHttp +
 * kotlinx.serialization only (house style — no Supabase SDK). Every entry
 * point no-ops instantly when unconfigured or signed out; failures are
 * swallowed into a [Result] so callers (WorkManager, a 60s ticker, foreground
 * hooks) can fire-and-forget without risking the dictation hot path.
 *
 * A single [Mutex] serialises overlapping triggers (foreground + ticker +
 * post-recording can all fire close together) so two sync passes never race
 * each other's dirty-flag bookkeeping.
 */
class SyncEngine(
    baseClient: OkHttpClient,
    private val authManager: AuthManager,
    private val settingsStore: SettingsStore,
    private val recordings: RecordingRepository,
    private val syncMetaDao: SyncMetaDao,
    private val secrets: SecureKeyStore,
) {
    private val client: OkHttpClient = baseClient.newBuilder()
        .callTimeout(30, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private val mutex = Mutex()

    /**
     * Run one full push/pull round. [initial] additionally marks every
     * already-`done` local row dirty before pushing (first sign-in bootstrap —
     * pre-existing history becomes push-worthy). Safe to call from anywhere,
     * anytime; it's a no-op when unconfigured/signed-out, and overlapping
     * calls collapse onto the mutex rather than racing.
     */
    suspend fun syncNow(initial: Boolean = false): Result<Unit> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured() || !authManager.isSignedIn()) return@withContext Result.success(Unit)
        mutex.withLock {
            runCatching {
                val token = authManager.getValidAccessToken()
                    ?: return@runCatching // couldn't refresh — try again next trigger, don't surface an error toast
                val userId = authManager.state.value.userId ?: return@runCatching
                val deviceId = settingsStore.deviceId()
                val deviceName = settingsStore.settings.first().deviceName

                registerDevice(token, userId, deviceId, deviceName)
                // Apply a pending account-purge BEFORE trusting any local or
                // pulled note (SYNC_DESIGN.md Purge "Apply purge on sync"). This
                // covers login too: sign-in kicks syncNow(initial=true), so the
                // marker is checked the first time a signed-in session syncs.
                applyPurgeIfNeeded(token, userId)
                if (initial) recordings.markAllDoneDirtyForInitialSync()
                pushNotes(token, userId, deviceId)
                pullNotes(token, userId)
                pushProviderKeys(token, userId)
                pullProviderKeys(token, userId)
                settingsStore.setLastSyncedAt(System.currentTimeMillis())
            }
        }
    }

    /** What the Refresh consistency check found and fixed. */
    data class ReconcileReport(
        val fetched: Int = 0,
        val updated: Int = 0,
        val removed: Int = 0,
        val repushed: Int = 0,
        val reclaimed: Int = 0,
        val waiting: Int = 0,
        val error: String? = null,
    ) {
        fun summary(): String {
            if (error != null) return "Sync check failed: $error"
            val parts = buildList {
                if (fetched > 0) add("$fetched new from other devices")
                if (updated > 0) add("$updated updated")
                if (removed > 0) add("$removed removed")
                if (repushed > 0) add("$repushed re-sent from this phone")
                if (reclaimed > 0) add("$reclaimed repaired")
                if (waiting > 0) add("$waiting still waiting to send")
            }
            return if (parts.isEmpty()) "Everything is in sync." else "Synced: ${parts.joinToString(", ")}."
        }
    }

    @kotlinx.serialization.Serializable
    private data class ServerIndexRow(
        val id: String,
        val device_id: String? = null,
        val updated_at: String? = null,
        val deleted_at: String? = null,
    )

    /**
     * The History Refresh button: a normal sync, then a full comparison of the
     * server's row list with this phone's. Fetches other devices' rows that are
     * missing or newer here, removes rows deleted on their own device, takes
     * back ownership of this phone's rows an older build marked as pulled, and
     * re-sends this phone's rows the server lacks.
     */
    suspend fun reconcile(): ReconcileReport = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured() || !authManager.isSignedIn()) {
            return@withContext ReconcileReport(error = "Sign in to sync with your other devices.")
        }
        val first = syncNow()
        if (first.isFailure) {
            return@withContext ReconcileReport(error = first.exceptionOrNull()?.message ?: "Couldn't reach your account.")
        }
        mutex.withLock {
            runCatching {
                val token = authManager.getValidAccessToken() ?: error("Couldn't reach your account. Try again.")
                val userId = authManager.state.value.userId ?: error("Couldn't reach your account. Try again.")
                val deviceId = settingsStore.deviceId()

                val server = mutableListOf<ServerIndexRow>()
                while (true) {
                    val url = "${SupabaseConfig.SUPABASE_URL}/rest/v1/notes" +
                        "?user_id=eq.$userId&select=id,device_id,updated_at,deleted_at&order=id.asc&limit=$INDEX_PAGE&offset=${server.size}"
                    val page = getJson(url, token, ListSerializer(ServerIndexRow.serializer()))
                    server += page
                    if (page.size < INDEX_PAGE) break
                }
                val local = recordings.syncIndex().associateBy { it.id }
                var removed = 0
                var reclaimed = 0
                val toFetch = mutableListOf<String>()
                val onServer = HashSet<String>()
                for (row in server) {
                    onServer += row.id
                    val here = local[row.id]
                    if (row.device_id == deviceId) {
                        // Ours, but an older build stored the echo as a pulled row.
                        if (row.deleted_at == null && here != null && here.remote) {
                            recordings.reclaimOwnership(row.id)
                            reclaimed++
                        }
                        continue
                    }
                    if (row.deleted_at != null) {
                        if (here != null) { recordings.applyTombstone(row.id); removed++ }
                        continue
                    }
                    val serverMs = SyncTime.toMillis(row.updated_at) ?: 0L
                    if (here == null || (here.remote && serverMs > here.updatedAt)) toFetch += row.id
                }

                val missingMine = local.values
                    .filter { !it.remote && it.status == "done" && !it.dirty && it.id !in onServer }
                    .map { it.id }
                if (missingMine.isNotEmpty()) {
                    recordings.markDirtyIds(missingMine)
                    pushNotes(token, userId, deviceId)
                }

                var fetched = 0
                var updated = 0
                for (chunk in toFetch.chunked(FETCH_CHUNK)) {
                    val url = "${SupabaseConfig.SUPABASE_URL}/rest/v1/notes?user_id=eq.$userId&id=in.(${chunk.joinToString(",")})"
                    for (row in getJson(url, token, ListSerializer(RemoteNote.serializer()))) {
                        if (applyPulled(row)) { if (local.containsKey(row.id)) updated++ else fetched++ }
                    }
                }
                val waiting = recordings.syncIndex().count { !it.remote && (it.dirty || it.status !in setOf("done", "error")) }
                settingsStore.setLastSyncedAt(System.currentTimeMillis())
                ReconcileReport(fetched, updated, removed, missingMine.size, reclaimed, waiting)
            }.getOrElse { ReconcileReport(error = it.message ?: "Couldn't finish the check.") }
        }
    }

    private fun <T> getJson(url: String, token: String, serializer: kotlinx.serialization.KSerializer<T>): T {
        val request = Request.Builder()
            .url(url)
            .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer $token")
            .build()
        return client.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("HTTP ${resp.code}: ${body.take(200)}")
            json.decodeFromString(serializer, body)
        }
    }

    /** Apply one row another device owns. Returns whether it was applied. */
    private suspend fun applyPulled(row: RemoteNote): Boolean {
        val createdMs = SyncTime.toMillis(row.created_at) ?: return false
        val updatedMs = SyncTime.toMillis(row.updated_at) ?: createdMs
        val before = recordings.syncIndex().firstOrNull { it.id == row.id }?.updatedAt
        recordings.applyRemoteNote(row.toPulled(createdMs, updatedMs))
        return before == null || updatedMs > before
    }

    private fun RemoteNote.toPulled(createdMs: Long, updatedMs: Long) = RemotePulledNote(
        id = id,
        platform = platform,
        origin = origin,
        title = title,
        transcript = transcript,
        cleanedText = cleaned_text,
        draftedText = drafted_text,
        meetingNotesText = meeting_notes_text,
        speakerTurns = speaker_turns,
        speakerNames = speaker_names,
        isMeeting = is_meeting,
        diarizationEnabled = diarization_enabled,
        durationMs = duration_ms,
        sttProvider = stt_provider,
        llmProvider = llm_provider,
        createdAtMillis = createdMs,
        updatedAtMillis = updatedMs,
        deletedAtMillis = SyncTime.toMillis(deleted_at),
    )

    /** Best-effort cloud tombstone for "delete everywhere" — wired into
     *  [RecordingRepository] as a lambda so that package stays sync-agnostic.
     *  Scoped to this device with `&device_id=eq.<deviceId>` (SYNC_DESIGN.md
     *  "Delete — ownership-scoped": the device_id equality check is what
     *  carries the delete policy server-side, mirrored from desktop's
     *  `tombstone_remote` in `../wispr-fox/src-tauri/src/sync/engine.rs`) —
     *  [ids] is already filtered to owned rows by [RecordingRepository], but
     *  the PATCH itself must not be *capable* of reaching another device's
     *  rows either. A single batched PATCH (`id=in.(...)`) replaces the old
     *  per-id loop, same as desktop. Deliberately swallows its own errors: a
     *  failed tombstone just means the next sync round retries (the row is
     *  already gone locally by then, so a transient failure here doesn't
     *  leave the user's phone in a weird state, only delays the other
     *  devices seeing the deletion). */
    suspend fun tombstoneNotes(ids: List<String>): Unit = withContext(Dispatchers.IO) {
        if (ids.isEmpty() || !SupabaseConfig.isConfigured() || !authManager.isSignedIn()) return@withContext
        runCatching {
            val token = authManager.getValidAccessToken() ?: return@runCatching
            val deviceId = settingsStore.deviceId()
            val nowIso = SyncTime.nowIso()
            val idList = ids.joinToString(",")
            val body = """{"deleted_at":"$nowIso","transcript":null,"cleaned_text":null,"drafted_text":null,"meeting_notes_text":null,"speaker_turns":null,"speaker_names":null,"title":null,"updated_at":"$nowIso"}"""
                .toRequestBody(JSON_MEDIA)
            val request = Request.Builder()
                .url("${SupabaseConfig.SUPABASE_URL}/rest/v1/notes?id=in.($idList)&device_id=eq.$deviceId")
                .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .header("Prefer", "return=minimal")
                .patch(body)
                .build()
            runCatching { client.newCall(request).execute().close() }
        }
    }

    // ─── purge (SYNC_DESIGN.md "Purge — protocol v1") ────────────────────────

    /**
     * Initiate a purge: the one operation allowed to cross device ownership —
     * a deliberate "reset my whole account history everywhere" that also clears
     * undeletable orphans (rows whose originating device is gone). Destructive
     * and irreversible; the UI gates it behind press-and-hold + a confirm dialog.
     *
     * Order matters: the marker is written BEFORE the hard delete so that even
     * if the bulk delete half-fails, other devices still see `purged_at` and
     * wipe themselves on their next sync (the marker, not the empty table, is
     * what propagates). Then this device wipes locally and records the marker as
     * already-applied so its own next sync doesn't re-wipe.
     */
    suspend fun purgeEverywhere(): Result<Unit> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured() || !authManager.isSignedIn()) {
            return@withContext Result.failure(IllegalStateException("Sign in to purge across devices."))
        }
        mutex.withLock {
            runCatching {
                val token = authManager.getValidAccessToken()
                    ?: throw IllegalStateException("Couldn't reach your account — try again.")
                val userId = authManager.state.value.userId
                    ?: throw IllegalStateException("Couldn't reach your account — try again.")
                val t = SyncTime.nowIso()

                // 1) Marker first (upsert user_settings['purged_at'] = t).
                upsertPurgedAt(token, userId, t)
                // 2) Best-effort hard DELETE of every note the user owns. RLS
                //    scopes to the user, so this clears this device's rows, other
                //    devices' rows, and orphans in one shot. A failure here is
                //    swallowed — the marker already went up, so propagation still
                //    happens and the next sync round can retry the delete.
                runCatching { hardDeleteAllNotes(token, userId) }
                // 3) Wipe locally + record the marker as applied, and advance the
                //    notes cursor to t so the post-purge pull only sees genuinely
                //    new activity.
                recordings.deleteAll()
                syncMetaDao.set(SyncMetaEntity(SyncMetaKeys.APPLIED_PURGE_AT, t))
                syncMetaDao.setLong(SyncMetaKeys.NOTES_CURSOR, SyncTime.toMillis(t) ?: System.currentTimeMillis())
            }
        }
    }

    /**
     * If the server's `purged_at` is newer than the marker we've already applied
     * (RFC3339 string compare, all UTC — the shared-contract comparison), wipe
     * every local recording + its audio and advance both the applied marker and
     * the notes cursor to it. A fresh install (empty applied marker) seeing a
     * `purged_at` just advances the markers — wiping empty state is a harmless
     * no-op, not a special case.
     */
    private suspend fun applyPurgeIfNeeded(token: String, userId: String) {
        val serverPurgedAt = fetchPurgedAt(token, userId)?.takeIf { it.isNotBlank() } ?: return
        val applied = syncMetaDao.get(SyncMetaKeys.APPLIED_PURGE_AT).orEmpty()
        if (serverPurgedAt <= applied) return
        recordings.deleteAll()
        syncMetaDao.set(SyncMetaEntity(SyncMetaKeys.APPLIED_PURGE_AT, serverPurgedAt))
        syncMetaDao.setLong(SyncMetaKeys.NOTES_CURSOR, SyncTime.toMillis(serverPurgedAt) ?: System.currentTimeMillis())
    }

    /** Read `user_settings['purged_at']` for this user (null if never set). */
    private fun fetchPurgedAt(token: String, userId: String): String? {
        val request = Request.Builder()
            .url("${SupabaseConfig.SUPABASE_URL}/rest/v1/user_settings?user_id=eq.$userId&key=eq.purged_at")
            .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer $token")
            .build()
        return client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val bodyStr = resp.body?.string().orEmpty()
            val rows = runCatching { json.decodeFromString(ListSerializer(RemoteSetting.serializer()), bodyStr) }
                .getOrDefault(emptyList())
            rows.firstOrNull { it.key == "purged_at" }?.value
        }
    }

    private fun upsertPurgedAt(token: String, userId: String, iso: String) {
        val setting = RemoteSetting(user_id = userId, key = "purged_at", value = iso, updated_at = iso)
        val body = json.encodeToString(ListSerializer(RemoteSetting.serializer()), listOf(setting)).toRequestBody(JSON_MEDIA)
        val request = Request.Builder()
            .url("${SupabaseConfig.SUPABASE_URL}/rest/v1/user_settings")
            .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .header("Prefer", "resolution=merge-duplicates,return=minimal")
            .post(body)
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("Couldn't set the purge marker (HTTP ${resp.code}).")
        }
    }

    private fun hardDeleteAllNotes(token: String, userId: String) {
        // PostgREST refuses an unfiltered DELETE; user_id=eq.<me> is both the
        // required filter and (with RLS) exactly the user's own rows.
        val request = Request.Builder()
            .url("${SupabaseConfig.SUPABASE_URL}/rest/v1/notes?user_id=eq.$userId")
            .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer $token")
            .header("Prefer", "return=minimal")
            .delete()
            .build()
        client.newCall(request).execute().close()
    }

    // ─── devices ─────────────────────────────────────────────────────────────

    private fun registerDevice(token: String, userId: String, deviceId: String, deviceName: String) {
        val device = RemoteDevice(
            id = deviceId,
            user_id = userId,
            name = deviceName,
            platform = "mobile",
            last_seen_at = SyncTime.nowIso(),
        )
        val body = json.encodeToString(RemoteDevice.serializer(), device).toRequestBody(JSON_MEDIA)
        val request = Request.Builder()
            .url("${SupabaseConfig.SUPABASE_URL}/rest/v1/devices")
            .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .header("Prefer", "resolution=merge-duplicates,return=minimal")
            .post(body)
            .build()
        client.newCall(request).execute().close()
    }

    // ─── notes (transcripts) ─────────────────────────────────────────────────

    /**
     * Notes are pushed as one JSON array. PostgREST rejects a bulk insert
     * whose objects do not all carry the same keys (HTTP 400, PGRST102 "All
     * object keys must match") — and the shared [json] omits null fields, so
     * any batch mixing, say, a row with a title and one without failed, every
     * time, silently. Every dirty row then stayed stuck and phone transcripts
     * never reached other devices. Emit nulls explicitly so every object has
     * the same keys.
     */
    private val pushJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = true }

    private suspend fun pushNotes(token: String, userId: String, deviceId: String) {
        val dirty = recordings.dirtyDoneRows()
        if (dirty.isEmpty()) return
        // Stamp the push time, not the row's local edit time (same as desktop).
        // Other devices pull with `updated_at > cursor`; a row pushed late with
        // an old timestamp would land below a cursor that already moved past it
        // and never be pulled.
        val pushedAt = SyncTime.nowIso()
        val payload = dirty.map { row ->
            RemoteNote(
                id = row.id,
                user_id = userId,
                device_id = deviceId,
                platform = "mobile",
                origin = if (row.imported) "upload" else "mic",
                title = row.title ?: (row.meetingNotesText ?: row.draftedText ?: row.cleanedText ?: row.transcript)?.take(80),
                transcript = row.transcript,
                cleaned_text = row.cleanedText,
                drafted_text = row.draftedText,
                meeting_notes_text = row.meetingNotesText,
                speaker_turns = row.speakerTurns,
                speaker_names = row.speakerNames,
                is_meeting = row.isMeeting,
                diarization_enabled = row.diarizationEnabled,
                duration_ms = row.durationMs,
                stt_provider = row.sttProvider,
                llm_provider = row.llmProvider,
                created_at = SyncTime.toIso(row.createdAt),
                updated_at = pushedAt,
                deleted_at = null,
            )
        }
        val body = pushJson.encodeToString(ListSerializer(RemoteNote.serializer()), payload).toRequestBody(JSON_MEDIA)
        val request = Request.Builder()
            .url("${SupabaseConfig.SUPABASE_URL}/rest/v1/notes")
            .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .header("Prefer", "resolution=merge-duplicates,return=minimal")
            .post(body)
            .build()
        client.newCall(request).execute().use { resp ->
            if (resp.isSuccessful) {
                recordings.clearDirty(dirty.map { it.id })
            } else {
                // Was silent before, which is how this bug hid for months.
                android.util.Log.w(
                    "WisprSync",
                    "notes push failed: HTTP ${resp.code} ${resp.body?.string()?.take(300)}",
                )
            }
        }
    }

    private suspend fun pullNotes(token: String, userId: String) {
        val cursorMillis = syncMetaDao.getLong(SyncMetaKeys.NOTES_CURSOR) ?: 0L
        // Every client stamps updated_at with its own clock, so a row from a
        // device whose clock runs behind can land below our cursor. Re-read a
        // 15-minute overlap; applyRemoteNote skips rows already applied.
        val cursorIso = SyncTime.toIso((cursorMillis - PULL_OVERLAP_MS).coerceAtLeast(0L))
        val url = "${SupabaseConfig.SUPABASE_URL}/rest/v1/notes" +
            "?user_id=eq.$userId&updated_at=gt.$cursorIso&order=updated_at.asc"
        val request = Request.Builder()
            .url(url)
            .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer $token")
            .build()
        val rows: List<RemoteNote> = client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return
            val bodyStr = resp.body?.string().orEmpty()
            runCatching { json.decodeFromString(ListSerializer(RemoteNote.serializer()), bodyStr) }.getOrDefault(emptyList())
        }
        var maxSeen = cursorMillis
        val myDeviceId = settingsStore.deviceId()
        for (row in rows) {
            val createdMs = SyncTime.toMillis(row.created_at) ?: continue
            val updatedMs = SyncTime.toMillis(row.updated_at) ?: createdMs
            if (updatedMs > maxSeen) maxSeen = updatedMs
            // Our own row echoing back from our push. This phone's copy is the
            // original: it has the audio and it owns the row. Applying the echo
            // used to overwrite it as a pulled row (remote, no audio), which
            // stopped every later edit from being pushed and greyed out Rerun.
            // Only speaker names can legitimately change elsewhere.
            if (row.device_id == myDeviceId) {
                if (row.deleted_at == null) recordings.applyRemoteSpeakerNames(row.id, row.speaker_names)
                continue
            }
            recordings.applyRemoteNote(
                RemotePulledNote(
                    id = row.id,
                    platform = row.platform,
                    origin = row.origin,
                    title = row.title,
                    transcript = row.transcript,
                    cleanedText = row.cleaned_text,
                    draftedText = row.drafted_text,
                    meetingNotesText = row.meeting_notes_text,
                    speakerTurns = row.speaker_turns,
                    speakerNames = row.speaker_names,
                    isMeeting = row.is_meeting,
                    diarizationEnabled = row.diarization_enabled,
                    durationMs = row.duration_ms,
                    sttProvider = row.stt_provider,
                    llmProvider = row.llm_provider,
                    createdAtMillis = createdMs,
                    updatedAtMillis = updatedMs,
                    deletedAtMillis = SyncTime.toMillis(row.deleted_at),
                )
            )
        }
        if (maxSeen > cursorMillis) syncMetaDao.setLong(SyncMetaKeys.NOTES_CURSOR, maxSeen)
    }

    // ─── provider keys (user_settings) ───────────────────────────────────────

    private data class KeyMapping(
        val settingKey: String,
        val pushSource: SecureKeyStore.Key,
        val pullTargets: List<SecureKeyStore.Key>,
    )

    private val keyMappings = listOf(
        KeyMapping("key_groq", SecureKeyStore.Key.GroqStt, listOf(SecureKeyStore.Key.GroqStt, SecureKeyStore.Key.GroqLlm)),
        KeyMapping("key_openai", SecureKeyStore.Key.OpenAiStt, listOf(SecureKeyStore.Key.OpenAiStt, SecureKeyStore.Key.OpenAiLlm)),
        KeyMapping("key_deepgram", SecureKeyStore.Key.DeepgramStt, listOf(SecureKeyStore.Key.DeepgramStt)),
        KeyMapping("key_elevenlabs", SecureKeyStore.Key.ElevenLabsStt, listOf(SecureKeyStore.Key.ElevenLabsStt)),
        KeyMapping("key_gemini", SecureKeyStore.Key.GeminiLlm, listOf(SecureKeyStore.Key.GeminiLlm)),
    )

    /** Push any locally-held key whose value has changed since we last pushed
     *  it (tracked via a content hash in `sync_meta`, not a timestamp — we
     *  don't otherwise know when a key was edited). This makes "push on key
     *  change" fall out of calling [syncNow] again after a key save, with no
     *  separate code path, and avoids re-pushing an unchanged key every tick
     *  (which would fight with another device's pull). */
    private suspend fun pushProviderKeys(token: String, userId: String) {
        for (m in keyMappings) {
            val value = secrets.get(m.pushSource)?.takeIf { it.isNotBlank() } ?: continue
            val hash = value.hashCode().toString()
            val lastPushedHash = syncMetaDao.get(pushHashKey(m.settingKey))
            if (lastPushedHash == hash) continue
            val setting = RemoteSetting(user_id = userId, key = m.settingKey, value = value, updated_at = SyncTime.nowIso())
            val body = json.encodeToString(ListSerializer(RemoteSetting.serializer()), listOf(setting)).toRequestBody(JSON_MEDIA)
            val request = Request.Builder()
                .url("${SupabaseConfig.SUPABASE_URL}/rest/v1/user_settings")
                .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .header("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(body)
                .build()
            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) syncMetaDao.set(SyncMetaEntity(pushHashKey(m.settingKey), hash))
            }
        }
    }

    private suspend fun pullProviderKeys(token: String, userId: String) {
        val request = Request.Builder()
            .url("${SupabaseConfig.SUPABASE_URL}/rest/v1/user_settings?user_id=eq.$userId")
            .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer $token")
            .build()
        val rows: List<RemoteSetting> = client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return
            val bodyStr = resp.body?.string().orEmpty()
            runCatching { json.decodeFromString(ListSerializer(RemoteSetting.serializer()), bodyStr) }.getOrDefault(emptyList())
        }
        for (row in rows) {
            // Only provider-key rows are adopted. Any other user_settings row —
            // notably the purge marker `purged_at` — falls through this `continue`
            // and is never written to the keystore or pushed back up: it's
            // read-on-sync by applyPurgeIfNeeded / write-on-purge only, never an
            // API key.
            val mapping = keyMappings.firstOrNull { it.settingKey == row.key } ?: continue
            val value = row.value?.takeIf { it.isNotBlank() } ?: continue
            val remoteMs = SyncTime.toMillis(row.updated_at) ?: continue
            val trackedMs = syncMetaDao.getLong(pullTrackKey(mapping.settingKey)) ?: 0L
            if (remoteMs <= trackedMs) continue
            for (target in mapping.pullTargets) secrets.put(target, value)
            syncMetaDao.setLong(pullTrackKey(mapping.settingKey), remoteMs)
            // Adopt the value as "already pushed" too, so we don't immediately
            // turn around and push it straight back up next round.
            syncMetaDao.set(SyncMetaEntity(pushHashKey(mapping.settingKey), value.hashCode().toString()))
        }
    }

    private fun pushHashKey(settingKey: String) = "keypush_hash_$settingKey"
    private fun pullTrackKey(settingKey: String) = "keypull_at_$settingKey"

    companion object {
        private val JSON_MEDIA = "application/json".toMediaType()
        /** How far behind the pull cursor each pull re-reads (see pullNotes). */
        private const val PULL_OVERLAP_MS = 15 * 60 * 1000L
        /** Ids per `id=in.(...)` fetch, small enough to keep the URL short. */
        private const val FETCH_CHUNK = 40
        /** PostgREST's default page size on Supabase. */
        private const val INDEX_PAGE = 1000
    }
}
