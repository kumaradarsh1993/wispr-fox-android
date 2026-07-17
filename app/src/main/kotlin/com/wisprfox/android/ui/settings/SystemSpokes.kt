package com.wisprfox.android.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.wisprfox.android.BuildConfig
import com.wisprfox.android.WisprFoxApp
import com.wisprfox.android.delivery.WisprFoxAccessibilityService
import com.wisprfox.android.settings.AppSettings
import com.wisprfox.android.ui.AccountSection
import com.wisprfox.android.ui.PermissionCard
import com.wisprfox.android.ui.SectionCard
import com.wisprfox.android.ui.Space
import com.wisprfox.android.ui.SpokeScaffold
import com.wisprfox.android.ui.ToggleRow
import com.wisprfox.android.ui.UsageMeterRow
import com.wisprfox.android.ui.rememberUsageSnapshot
import kotlinx.coroutines.launch

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
            subtitle = "Lets Foxy place text in the focused field. Without it, text is copied for you to paste yourself.",
            granted = a11yOn,
            cta = "Open Accessibility settings",
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

    SpokeScaffold("Storage", onBack) {
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
}

/* ── 7 · Account ─────────────────────────────────────────────────────────── */

@Composable
fun AccountSpoke(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val container = remember { WisprFoxApp.container(ctx) }
    SpokeScaffold("Account", onBack) {
        SectionCard {
            AccountSection(container)
        }
    }
}

/* ── 8 · About ───────────────────────────────────────────────────────────── */

@Composable
fun AboutSpoke(onBack: () -> Unit, onReplayOnboarding: () -> Unit) {
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
        OutlinedButton(onClick = onReplayOnboarding, modifier = Modifier.fillMaxWidth()) {
            Text("Replay setup guide")
        }
    }
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
