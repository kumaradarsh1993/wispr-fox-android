package com.wisprfox.android.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.wisprfox.android.BuildConfig
import com.wisprfox.android.WisprFoxApp
import com.wisprfox.android.delivery.WisprFoxAccessibilityService
import com.wisprfox.android.settings.AppSettings
import com.wisprfox.android.sync.SupabaseConfig
import com.wisprfox.android.ui.AccountSection
import com.wisprfox.android.ui.HoldToConfirmButton
import com.wisprfox.android.ui.PermissionCard
import com.wisprfox.android.ui.SectionCard
import com.wisprfox.android.ui.Space
import com.wisprfox.android.ui.SpokeScaffold
import com.wisprfox.android.ui.ToggleRow
import com.wisprfox.android.ui.UsageMeterRow
import com.wisprfox.android.ui.rememberUsageSnapshot
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/* ── 4 · Delivery ────────────────────────────────────────────────────────── */

/**
 * How the text gets from Foxy into your app.
 *
 * The three permission cards replace naked `OutlinedButton`s that said "Open
 * Accessibility settings" without telling you whether it was already on. The
 * battery card is new here: it previously existed *only* in onboarding, and was
 * never re-surfaced in Settings despite Home's setup banner counting it — so a
 * user who skipped it during onboarding had no path back to it at all.
 */
@Composable
fun DeliverySpoke(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val container = remember { WisprFoxApp.container(ctx) }
    val scope = rememberCoroutineScope()
    val settings by container.settingsStore.settings.collectAsState(initial = AppSettings())

    val permTick = rememberResumeTick()
    val a11yOn = remember(permTick) { WisprFoxAccessibilityService.isConnected() }
    val canOverlay = remember(permTick) { Settings.canDrawOverlays(ctx) }
    val battOk = remember(permTick) { isIgnoringBattery(ctx) }

    SpokeScaffold("Delivery", onBack) {
        SectionCard {
            ToggleRow(
                label = "Auto-paste",
                summary = "Drop the text straight into whatever field you're in.",
                checked = settings.autoPasteEnabled,
                onChange = { scope.launch { container.settingsStore.setAutoPaste(it) } },
            )
        }

        PermissionCard(
            title = "Auto-paste access",
            subtitle = if (android.os.Build.MANUFACTURER.equals("samsung", ignoreCase = true))
                "On Samsung: Installed apps → wispr-fox → turn it on → Allow. Android does not permit apps to open the final service switch directly."
            else "Open Downloaded apps / Installed services, choose wispr-fox, turn it on, then Allow.",
            granted = a11yOn,
            cta = if (a11yOn) "Accessibility is on" else "Open Accessibility",
            onClick = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
        )
        PermissionCard(
            title = "Floating Foxy",
            subtitle = "Lets Foxy hover over any app as your tap-to-talk button.",
            granted = canOverlay,
            cta = "Allow overlay",
            onClick = {
                ctx.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")),
                )
            },
        )
        PermissionCard(
            title = "Keep Foxy alive",
            subtitle = "A battery exemption so Android doesn't kill the mic mid-sentence.",
            granted = battOk,
            cta = "Allow",
            onClick = {
                if (!isIgnoringBattery(ctx)) {
                    ctx.startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${ctx.packageName}"),
                        ),
                    )
                }
            },
        )

        Text(
            "Text is always copied to the clipboard as a fallback.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Space.xs),
        )
    }
}

/* ── 5 · Usage ───────────────────────────────────────────────────────────── */

@Composable
fun UsageSpoke(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val container = remember { WisprFoxApp.container(ctx) }
    val settings by container.settingsStore.settings.collectAsState(initial = AppSettings())
    val usage = rememberUsageSnapshot(settings)

    SpokeScaffold("Usage", onBack) {
        SectionCard("Today") {
            if (usage == null) {
                Text(
                    "Working it out…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                UsageMeterRow("Speech-to-text", usage.stt)
                UsageMeterRow("Cleanup", usage.llm)
                Text(
                    usage.resetLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            "Free-tier meters for the providers you're using. Groq and Deepgram publish a daily cap, so they get a bar; others show the raw count.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Space.xs),
        )
    }
}

/* ── 6 · Storage ─────────────────────────────────────────────────────────── */

@Composable
fun StorageSpoke(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val container = remember { WisprFoxApp.container(ctx) }
    val scope = rememberCoroutineScope()
    val settings by container.settingsStore.settings.collectAsState(initial = AppSettings())
    var storage by remember { mutableStateOf(com.wisprfox.android.history.RecordingRepository.AudioStorage(0, 0)) }
    var confirmClearAudio by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val recorded = container.recordings.audioStorage()
        val staged = container.importController.stagedStorage()
        storage = com.wisprfox.android.history.RecordingRepository.AudioStorage(recorded.files + staged.first, recorded.bytes + staged.second)
    }

    SpokeScaffold("Storage", onBack) {
        SectionCard("On this phone") {
            Text("${storage.files} audio file${if (storage.files == 1) "" else "s"} · ${formatStorage(storage.bytes)}",
                style = MaterialTheme.typography.bodyLarge)
            Text("Transcripts stay when local audio is cleared.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { confirmClearAudio = true }, enabled = storage.files > 0, modifier = Modifier.fillMaxWidth()) {
                Text("Clear local audio")
            }
        }
        SectionCard("Keep audio for") {
            var days by remember(settings.retentionDays) { mutableStateOf(settings.retentionDays.toFloat()) }
            Text(
                if (days.toInt() == 0) "Delete audio right after transcribing"
                else "${days.toInt()} days",
                style = MaterialTheme.typography.bodyLarge,
            )
            Slider(
                value = days,
                onValueChange = { days = it },
                onValueChangeFinished = { scope.launch { container.settingsStore.setRetentionDays(days.toInt()) } },
                valueRange = 0f..30f,
                steps = 29,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SectionCard("Storage cap") {
            var cap by remember(settings.retentionMaxMb) { mutableStateOf(settings.retentionMaxMb.toFloat()) }
            Text("${cap.toInt()} MB", style = MaterialTheme.typography.bodyLarge)
            Slider(
                value = cap,
                onValueChange = { cap = it },
                onValueChangeFinished = { scope.launch { container.settingsStore.setRetentionMaxMb(cap.toInt()) } },
                valueRange = 100f..1000f,
                steps = 17,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Oldest recordings are removed first once you go over.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (confirmClearAudio) AlertDialog(
        onDismissRequest = { confirmClearAudio = false },
        title = { Text("Clear ${storage.files} audio files?") },
        text = { Text("Raw, Clean, Draft, Meeting Notes, and synced transcripts stay. Playback and re-transcription will no longer be available for these rows.") },
        confirmButton = { Button(onClick = {
            confirmClearAudio = false
            scope.launch {
                container.recordings.clearAllAudio()
                container.importController.clearStaged()
                storage = container.recordings.audioStorage()
            }
        }) { Text("Clear audio") } },
        dismissButton = { TextButton(onClick = { confirmClearAudio = false }) { Text("Cancel") } },
    )
}

private fun formatStorage(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.2f GB".format(bytes / 1024.0 / 1024.0 / 1024.0)
    bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

/* ── 7 · Account ─────────────────────────────────────────────────────────── */

@Composable
fun AccountSpoke(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val container = remember { WisprFoxApp.container(ctx) }
    val authState by container.authManager.state.collectAsState()
    SpokeScaffold("Account", onBack) {
        SectionCard {
            AccountSection(container)
        }
        // Purge sits at the very bottom of Account (SYNC_DESIGN.md), and only
        // when signed in — it's an account-wide, cross-device reset, meaningless
        // without a session.
        if (SupabaseConfig.isConfigured() && authState.signedIn) {
            PurgeSection(container.syncEngine)
        }
    }
}

/**
 * Purge — the one operation allowed to cross device ownership (SYNC_DESIGN.md
 * "Purge"). Wipes every transcript on the account, on this phone and on every
 * other device (including ones the user no longer has), plus orphaned rows whose
 * originating device is gone. Irreversible, so it's gated twice: press-and-hold
 * to arm, then an explicit confirm dialog whose copy states the blast radius
 * plainly. Audio-only local files are wiped too (they die with their rows).
 */
@Composable
private fun PurgeSection(syncEngine: com.wisprfox.android.sync.SyncEngine) {
    val scope = rememberCoroutineScope()
    var confirming by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    SectionCard("Danger zone") {
        Text(
            "Purge account history",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            "Permanently deletes every transcript on your account — this phone and every other signed-in device, including devices you no longer have. This cannot be undone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HoldToConfirmButton(
            text = if (busy) "Purging…" else "Hold to purge everything",
            enabled = !busy,
            onHoldComplete = { confirming = true },
        )
        status?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Purge everything, everywhere?") },
            text = {
                Text(
                    "This permanently deletes every transcript on your account across ALL devices — including devices you no longer have — and clears orphaned recordings. It cannot be undone. Audio on this phone is deleted too.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirming = false
                        busy = true
                        status = null
                        scope.launch {
                            val result = syncEngine.purgeEverywhere()
                            busy = false
                            status = result.fold(
                                onSuccess = { "History purged everywhere. Other devices will catch up on their next sync." },
                                onFailure = { it.message ?: "Purge failed — nothing was changed. Try again." },
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Purge everything") }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel") } },
        )
    }
}

/* ── 8 · About ───────────────────────────────────────────────────────────── */

@Composable
fun AboutSpoke(onBack: () -> Unit, onReplayOnboarding: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var releases by remember { mutableStateOf<ReleaseSnapshot?>(null) }
    var updateError by remember { mutableStateOf<String?>(null) }
    SpokeScaffold("About", onBack) {
        SectionCard("wispr-fox for Android") {
            Text(
                "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        SectionCard("Privacy") {
            Text(
                "Bring-your-own-key dictation. Audio is sent only to the provider you choose for that request, and your keys stay encrypted on this phone. No analytics, no telemetry.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SectionCard("Updates") {
            Text(
                releases?.let {
                    "Stable ${it.stableTag ?: "not found"}${if (it.stableNewer) " · update available" else ""}\n" +
                        "Nightly ${it.nightlyTag ?: "not found"}${if (it.nightlyNewer) " · update available" else ""}"
                }
                    ?: "Check GitHub for the newest stable and nightly builds.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            updateError?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    checking = true; updateError = null
                    scope.launch {
                        runCatching { fetchReleases() }
                            .onSuccess { releases = it }
                            .onFailure { updateError = "Couldn't check right now. You can still open Releases." }
                        checking = false
                    }
                },
                enabled = !checking,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (checking) "Checking…" else "Check for updates") }
            OutlinedButton(
                onClick = {
                    val url = releases?.nightlyApk ?: releases?.nightlyUrl ?: "https://github.com/kumaradarsh1993/wispr-fox-android/releases"
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Download latest nightly") }
            OutlinedButton(
                onClick = {
                    val url = releases?.stableApk ?: releases?.stableUrl ?: "https://github.com/kumaradarsh1993/wispr-fox-android/releases/latest"
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Download latest stable") }
        }
        OutlinedButton(onClick = onReplayOnboarding, modifier = Modifier.fillMaxWidth()) {
            Text("Replay setup guide")
        }
    }
}

private data class ReleaseSnapshot(
    val stableTag: String?,
    val stableUrl: String?,
    val stableApk: String?,
    val stableNewer: Boolean,
    val nightlyTag: String?,
    val nightlyUrl: String?,
    val nightlyApk: String?,
    val nightlyNewer: Boolean,
)

private suspend fun fetchReleases(): ReleaseSnapshot = withContext(Dispatchers.IO) {
    val request = okhttp3.Request.Builder()
        .url("https://api.github.com/repos/kumaradarsh1993/wispr-fox-android/releases?per_page=20")
        .header("Accept", "application/vnd.github+json")
        .build()
    okhttp3.OkHttpClient().newCall(request).execute().use { response ->
        if (!response.isSuccessful) error("HTTP ${response.code}")
        val rows = Json.parseToJsonElement(response.body?.string().orEmpty()).jsonArray
        val stable = rows.firstOrNull { it.jsonObject["prerelease"]?.jsonPrimitive?.content == "false" }
        val nightly = rows.firstOrNull { it.jsonObject["prerelease"]?.jsonPrimitive?.content == "true" }
        fun apk(row: kotlinx.serialization.json.JsonElement?): String? = row?.jsonObject?.get("assets")?.jsonArray
            ?.firstOrNull { it.jsonObject["name"]?.jsonPrimitive?.content?.endsWith(".apk", ignoreCase = true) == true }
            ?.jsonObject?.get("browser_download_url")?.jsonPrimitive?.content
        val stableTag = stable?.jsonObject?.get("tag_name")?.jsonPrimitive?.content
        val nightlyTag = nightly?.jsonObject?.get("tag_name")?.jsonPrimitive?.content
        ReleaseSnapshot(
            stableTag,
            stable?.jsonObject?.get("html_url")?.jsonPrimitive?.content,
            apk(stable),
            isNewer(stableTag, BuildConfig.VERSION_NAME),
            nightlyTag,
            nightly?.jsonObject?.get("html_url")?.jsonPrimitive?.content,
            apk(nightly),
            isNewer(nightlyTag, BuildConfig.VERSION_NAME),
        )
    }
}

private fun isNewer(tag: String?, current: String): Boolean {
    fun parts(value: String): List<Int> = value.removePrefix("v").substringBefore('-')
        .split('.').map { it.toIntOrNull() ?: 0 }
    val candidate = tag?.let(::parts) ?: return false
    val installed = parts(current)
    val count = maxOf(candidate.size, installed.size)
    return (0 until count).firstNotNullOfOrNull { index ->
        val difference = candidate.getOrElse(index) { 0 } - installed.getOrElse(index) { 0 }
        difference.takeIf { it != 0 }
    }?.let { it > 0 } ?: false
}

/* ── helpers ─────────────────────────────────────────────────────────────── */

/**
 * Bumps on every ON_RESUME. Permission state can only change out in the system
 * settings app, so returning from it is exactly when we need to re-read.
 */
@Composable
private fun rememberResumeTick(): Int {
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return tick
}

private fun isIgnoringBattery(ctx: Context): Boolean {
    val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return pm.isIgnoringBatteryOptimizations(ctx.packageName)
}
