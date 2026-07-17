package com.wisprfox.android.ui

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.wisprfox.android.R
import com.wisprfox.android.WisprFoxApp
import com.wisprfox.android.core.AppState
import com.wisprfox.android.core.ImportConfig
import com.wisprfox.android.core.PipelineState
import com.wisprfox.android.history.Recording
import com.wisprfox.android.overlay.AvatarView
import com.wisprfox.android.provider.DictationMode
import com.wisprfox.android.provider.ProviderCatalog
import com.wisprfox.android.settings.AppSettings
import com.wisprfox.android.settings.SecureKeyStore
import com.wisprfox.android.ui.settings.ModeSegmented
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private const val STT_ACCURATE = "whisper-large-v3"

/**
 * Home — the activation surface.
 *
 * What this replaced (audit "Home — cut the hero, promote the content"): up to
 * two banners saying overlapping things (~180dp), a `StatusPill` reading
 * "Recording" directly above a hero title reading "Listening…", a 180dp box
 * holding a 160dp avatar (~22% of the viewport for a decorative element), an
 * avatar-skin picker sitting next to the record button as though skin choice and
 * dictation were related, and then *three separate settings cards* duplicating
 * the Settings screen inline. Home was ~60% settings by area, and Recent — the
 * only thing on the screen with actual content — got whatever was left.
 *
 * The rule now: **Home holds the fox, the one setting that changes per
 * utterance, and your content.** Everything else is one tap away in Settings.
 *
 * Mode is the sole inline setting and it earns that: it's chosen per-utterance,
 * not configured once. Provider/model collapse to a single read-only summary
 * line that deep-links to the Transcription spoke.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenHistory: () -> Unit, onOpenSettings: () -> Unit) {
    val ctx = LocalContext.current
    val container = remember { WisprFoxApp.container(ctx) }
    val scope = rememberCoroutineScope()
    val state by AppState.state.collectAsState()
    val settings by container.settingsStore.settings.collectAsState(initial = AppSettings())
    val recordings by container.recordings.observeRecent(limit = 4).collectAsState(initial = emptyList())
    val recording = state.pipeline == PipelineState.RECORDING

    // Overlay + battery can only change out in system settings, so re-read on
    // ON_RESUME. Accessibility does NOT go through this tick: AppState exposes
    // it as a live flow (`a11yConnected`), which Home used to ignore in favour
    // of polling the service singleton — stale between resumes for no reason.
    var permTick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) permTick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val missing = remember(permTick, state.a11yConnected) { missingPermissions(ctx, state.a11yConnected) }

    // ── Audio-file import ────────────────────────────────────────────────────
    // Defaults per the product ask: Whisper Large v3 on Groq for transcription,
    // Gemini for cleanup. These are the import sheet's own selections and don't
    // disturb the live-dictation model chosen in Settings.
    var showImport by rememberSaveable { mutableStateOf(false) }
    var importConfig by remember {
        mutableStateOf(
            ImportConfig(
                sttProvider = ProviderCatalog.STT_GROQ,
                sttModel = STT_ACCURATE,
                mode = DictationMode.CLEANED,
                llmProvider = ProviderCatalog.LLM_GEMINI,
                llmModel = ProviderCatalog.defaultLlmModel(ProviderCatalog.LLM_GEMINI),
            )
        )
    }
    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) container.importController.import(uris, importConfig)
        showImport = false
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.fox_favicon), null, Modifier.size(26.dp))
                        Spacer(Modifier.width(Space.sm))
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)) { append("wispr") }
                                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)) { append("fox") }
                            },
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        bottomBar = { WisprBottomBar(NavTab.HOME, onSelect = { if (it == NavTab.HISTORY) onOpenHistory() }) },
        // The fox is the primary control, but on a 6.8" display it sits well
        // above the thumb arc. The FAB is the same action at a reachable
        // position — not a second, competing one.
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { container.controller.toggle(settings.defaultMode) },
                containerColor = if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                contentColor = if (recording) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(if (recording) Icons.Filled.Stop else Icons.Filled.Mic, contentDescription = null) },
                text = { Text(if (recording) "Stop" else "Speak") },
            )
        },
    ) { inner ->
        ScreenColumn(
            Modifier.padding(inner),
            gap = Space.md,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ONE banner. Previously two fired together saying overlapping
            // things: missingPermissions() already counts "auto-paste", so the
            // setup banner said "2 permissions still need to be granted" while a
            // three-line accessibility paragraph rendered immediately below it —
            // ~180dp of duplication that pushed the hero below the fold.
            if (missing.isNotEmpty()) {
                SetupBanner(missing, onClick = onOpenSettings)
            }

            // Hero. 120dp, not a 160dp avatar inside a 180dp box.
            Spacer(Modifier.height(Space.xs))
            Box(
                modifier = Modifier
                    .size(Sizes.hero)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = if (recording) "Stop recording" else "Start recording",
                    ) { container.controller.toggle(settings.defaultMode) },
                contentAlignment = Alignment.Center,
            ) {
                AvatarView(settings.avatar, state.pipeline, Modifier.size(Sizes.hero))
            }

            // ONE label. StatusPill ("Recording") used to sit directly above
            // heroTitle ("Listening…") — two labels, same information, stacked.
            AnimatedContent(
                targetState = heroTitle(state),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "hero-title",
            ) { title ->
                Text(title, style = MaterialTheme.typography.headlineSmall)
            }
            Text(
                heroSubtitle(state),
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.messageIsError) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(Space.xs))

            // The one setting that belongs on Home: it's per-utterance.
            ModeSegmented(
                selected = settings.defaultMode,
                onSelect = { scope.launch { container.settingsStore.setDefaultMode(it) } },
            )

            // Replaces the "Speech-to-text" + "Smart cleanup" cards wholesale —
            // both were settings duplicated onto Home. Read-only summary,
            // deep-links to where they're actually configured.
            EngineSummaryRow(settings = settings, onClick = onOpenSettings)

            val usage = rememberUsageSnapshot(settings)
            if (usage != null) {
                SectionCard { UsageStrip(usage) }
            }

            // Recent gets the room the hero was wasting.
            RecentsCard(recordings = recordings, onOpenAll = onOpenHistory)

            ImportRow(onClick = { showImport = true })

            // Clear the FAB + bottom bar.
            Spacer(Modifier.height(Space.xxl))
        }
    }

    if (showImport) {
        ImportSheet(
            config = importConfig,
            onConfigChange = { importConfig = it },
            geminiReady = container.secrets.has(SecureKeyStore.Key.GeminiLlm),
            onImport = { importPicker.launch(IMPORT_MIME_TYPES) },
            onDismiss = { showImport = false },
        )
    }
}

// SAF filter for the import picker. The audio wildcard covers every codec the
// platform can decode (m4a/AAC, mp3, amr, 3gp, ogg/opus, flac, wav) across
// Samsung and iPhone recordings.
private val IMPORT_MIME_TYPES = arrayOf("audio/*")

/* ── Engine summary ──────────────────────────────────────────────────────── */

/** "Groq · Whisper Turbo → Llama 70B" — two former cards, one line. */
@Composable
private fun EngineSummaryRow(settings: AppSettings, onClick: () -> Unit) {
    val summary = buildString {
        append(ProviderCatalog.label(settings.sttProvider))
        append(" · ")
        append(ProviderCatalog.shortModel(settings.sttModel))
        if (settings.defaultMode.usesLlm) {
            append(" → ")
            append(ProviderCatalog.shortModel(settings.activeLlmModel))
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touch)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Icon(
            Icons.Filled.Tune,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            summary,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

/* ── Setup banner ────────────────────────────────────────────────────────── */

/**
 * The single merged banner. Names *what* is missing rather than counting it —
 * "2 permissions still need to be granted" told the user nothing actionable.
 */
@Composable
private fun SetupBanner(missing: List<String>, onClick: () -> Unit) {
    val what = when (missing.size) {
        1 -> missing.first()
        2 -> "${missing[0]} and ${missing[1]}"
        else -> missing.dropLast(1).joinToString(", ") + ", and " + missing.last()
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.lg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(
            Modifier.padding(Space.card),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(
                    "Finish setup",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    "Turn on $what so Foxy can float over your apps and drop text into them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/* ── Recents preview ─────────────────────────────────────────────────────── */

@Composable
private fun RecentsCard(recordings: List<Recording>, onOpenAll: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.lg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(vertical = Space.sm)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Sizes.touch)
                    .clickable(onClick = onOpenAll)
                    .padding(horizontal = Space.card),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Recent", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("See all", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            if (recordings.isEmpty()) {
                Text(
                    "Nothing yet — tap Foxy to dictate something.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Space.card, vertical = Space.sm),
                )
            } else {
                recordings.forEach { RecentRow(it, onClick = onOpenAll) }
            }
        }
    }
}

@Composable
private fun RecentRow(rec: Recording, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touch)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.card, vertical = Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Text(
            rec.primaryText()?.takeIf { it.isNotBlank() }?.take(80) ?: "(no transcript yet)",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "${recentTimeFmt.format(Date(rec.createdAt))} · ${rec.mode.label} · ${rec.durationMs / 1000}s",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val recentTimeFmt = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())

/* ── Import ──────────────────────────────────────────────────────────────── */

/** Demoted from a card to a row — it's a rare action, not a headline. */
@Composable
private fun ImportRow(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touch)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Icon(Icons.Filled.FileUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(
            "Import an audio file",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/* ── Permission probe ────────────────────────────────────────────────────── */

/**
 * Human-readable names, because they're rendered into the banner's prose now.
 * `a11yConnected` is passed in from the AppState flow rather than polled.
 */
private fun missingPermissions(ctx: Context, a11yConnected: Boolean): List<String> {
    val out = mutableListOf<String>()
    if (!Settings.canDrawOverlays(ctx)) out += "the floating fox"
    if (!a11yConnected) out += "auto-paste"
    val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
    if (pm?.isIgnoringBatteryOptimizations(ctx.packageName) != true) out += "the battery exemption"
    return out
}

private fun heroTitle(s: AppState.Snapshot): String = when (s.pipeline) {
    PipelineState.IDLE -> "Tap to speak"
    PipelineState.RECORDING -> "Listening…"
    PipelineState.TRANSCRIBING -> "Transcribing…"
    PipelineState.CLEANING -> "Polishing…"
    PipelineState.INJECTING -> "Delivering…"
    PipelineState.DONE -> "Done"
    PipelineState.ERROR -> "Something went wrong"
}

private fun heroSubtitle(s: AppState.Snapshot): String = when (s.pipeline) {
    PipelineState.IDLE -> s.message ?: "I'm here when you're ready."
    PipelineState.ERROR -> s.message ?: "Tap to try again."
    else -> s.message ?: "Hang tight…"
}
