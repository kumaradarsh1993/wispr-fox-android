package com.wisprfox.android.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.wisprfox.android.BuildConfig
import com.wisprfox.android.R
import com.wisprfox.android.WisprFoxApp
import com.wisprfox.android.delivery.WisprFoxAccessibilityService
import com.wisprfox.android.provider.ProviderCatalog
import com.wisprfox.android.settings.FamilyUnlock
import com.wisprfox.android.settings.SecureKeyStore
import com.wisprfox.android.sync.SupabaseConfig
import kotlinx.coroutines.launch

/**
 * Desktop-style multi-step first-run, ported from the sibling's
 * `/onboarding/+page.svelte` (Welcome → Setup → Try it) and re-pointed for
 * Android:
 *
 *   1. Welcome  — "Hi, I'm Foxy." + the three mode cards (Raw/Clean/Draft)
 *                 with say→get examples, and the Draft "superpower" note.
 *   2. Setup    — the friends-&-family **setup code** as the prominent easy
 *                 path (when a blob is bundled), with a manual Groq-key path
 *                 below + the honest "how is this free?" note + optional Gemini.
 *   3. Grant    — the Android equivalent of the desktop demo: wire up the
 *                 floating fox (overlay), auto-paste (accessibility) and the
 *                 battery exemption, each with a live ✓ as it's granted, then
 *                 "Start dictating".
 *
 * The Groq key (step 2) is the one hard gate — nothing transcribes without it,
 * so step 3 can't be reached until a key is saved or a setup code is unlocked.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val container = remember { WisprFoxApp.container(ctx) }
    var step by rememberSaveable { mutableIntStateOf(0) }
    var sttSaved by remember { mutableStateOf(hasAnySttKey(container.secrets)) }

    // Permission states are read live; recheck whenever we return from a
    // system settings page (ON_RESUME bumps the tick the readers key off).
    var permTick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) permTick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    // Accounts + cross-device sync (v2.0): the sync step only exists in builds
    // that actually have a Supabase project baked in — an unconfigured build
    // skips straight from Grant to onDone, so onboarding for those builds is
    // byte-for-byte what it was before this feature.
    val totalSteps = if (SupabaseConfig.isConfigured()) 4 else 3

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Audit P1-2: this screen had *zero* inset handling — no Scaffold, no
            // WindowInsets — while targetSdk 35 on Android 15+ enforces
            // edge-to-edge. The wordmark rendered under the status bar and "Get
            // started" sat under the gesture bar, on the very first screen a new
            // user sees. safeDrawingPadding covers status bar, gesture bar, and
            // the IME (which step 2's key fields raise).
            .safeDrawingPadding(),
    ) {
        OnboardingHeader(step, totalSteps)

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val forward = targetState > initialState
                val dir = if (forward) 1 else -1
                (slideInHorizontally(tween(280)) { it * dir } + fadeIn(tween(280)))
                    .togetherWith(slideOutHorizontally(tween(280)) { -it * dir } + fadeOut(tween(180)))
            },
            modifier = Modifier.weight(1f),
            label = "onboarding-step",
        ) { s ->
            when (s) {
                0 -> WelcomeStep(
                    canSkipSetup = sttSaved,
                    onNext = { step = 1 },
                    onSkip = { step = 2 },
                )
                1 -> SetupStep(
                    container = container,
                    groqSaved = sttSaved,
                    onGroqChange = { sttSaved = it },
                    onBack = { step = 0 },
                    onNext = { step = 2 },
                )
                2 -> GrantStep(
                    ctx = ctx,
                    permTick = permTick,
                    onBack = { step = 1 },
                    onFinish = { if (SupabaseConfig.isConfigured()) step = 3 else onDone() },
                )
                else -> SyncStep(
                    container = container,
                    onBack = { step = 2 },
                    onDone = onDone,
                )
            }
        }
    }
}

/* ── Header: brand wordmark + progress dots ──────────────────────────────── */

@Composable
private fun OnboardingHeader(step: Int, totalSteps: Int = 3) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.screen)
            .padding(top = Space.lg, bottom = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            Image(painterResource(R.drawable.fox_logo), contentDescription = null, modifier = Modifier.size(26.dp))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onBackground)) { append("wispr") }
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("-fox") }
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
            for (i in 0 until totalSteps) {
                val color = when {
                    i == step -> MaterialTheme.colorScheme.primary
                    i < step -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.outlineVariant
                }
                Box(Modifier.size(if (i == step) 11.dp else 9.dp).background(color, CircleShape))
            }
        }
    }
}

/* ── Screen 1: Welcome ───────────────────────────────────────────────────── */

@Composable
private fun WelcomeStep(canSkipSetup: Boolean, onNext: () -> Unit, onSkip: () -> Unit) {
    StepScaffold {
        Image(
            painterResource(R.drawable.fox_sitting),
            contentDescription = null,
            modifier = Modifier.size(128.dp).align(Alignment.CenterHorizontally),
        )
        Text(
            "Hi, I'm Foxy.",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Text(
            "Tap me, say what you mean, and the words land in whatever you're typing — any app on your phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(Space.xs))
        // Three cards, not four: the "Draft is the hidden superpower" card was a
        // fourth thing to read before the only button on the screen, and it was
        // talking about the card directly above it. Folded into Draft's own note.
        ModeCard(
            tag = "Raw",
            said = "the meeting is at 4 pm tomorrow",
            got = "the meeting is at 4 pm tomorrow",
        )
        ModeCard(
            tag = "Clean",
            said = "uhh so the meeting tomorrow at 4 i think",
            got = "The meeting tomorrow is at 4.",
        )
        ModeCard(
            tag = "Draft",
            said = "tell saurabh i'll be late tomorrow",
            got = "Hi Saurabh, just letting you know I'll be running late tomorrow…",
            note = "The hidden superpower — say the gist and Foxy writes the whole thing.",
            highlight = true,
        )

        Spacer(Modifier.height(Space.xs))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Get started") }
        if (canSkipSetup) {
            TextButton(onClick = onSkip, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Skip setup — my keys are saved")
            }
        }
    }
}

@Composable
private fun ModeCard(
    tag: String,
    said: String,
    got: String,
    note: String? = null,
    highlight: Boolean = false,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (highlight) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(Radius.lg),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Space.card), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Box(
                Modifier
                    .background(
                        if (highlight) MaterialTheme.colorScheme.surface
                        else MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(Radius.sm),
                    )
                    .padding(horizontal = Space.sm, vertical = Space.xs),
            ) {
                Text(
                    tag,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (highlight) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            ExampleLine("You say", said, highlight)
            ExampleLine("You get", got, highlight)
            if (note != null) {
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ExampleLine(label: String, text: String, highlight: Boolean = false) {
    val onCard = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val muted = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = muted,
            fontWeight = FontWeight.SemiBold,
            // widthIn, not a fixed size(): a hard-sized Text clips its own label
            // once the user raises the font scale, and One UI's font-size slider
            // is heavily used (audit P1-9).
            modifier = Modifier.widthIn(min = 56.dp),
        )
        Text(
            "\"$text\"",
            style = MaterialTheme.typography.bodySmall,
            color = onCard,
            modifier = Modifier.weight(1f),
        )
    }
}

/* ── Screen 2: Setup (setup code + Groq key) ─────────────────────────────── */

@Composable
private fun SetupStep(
    container: com.wisprfox.android.core.AppContainer,
    groqSaved: Boolean,
    onGroqChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    StepScaffold {
        Text("Get set up", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "One speech-to-text key and you're ready. If someone shared a setup code with you, use that - no key needed.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // ── Easy path: friends-&-family setup code ──────────────────────────
        if (BuildConfig.FAMILY_BLOB.isNotBlank()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(Space.card), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Text(
                        "Have a setup code?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        "Enter the code you were given and Foxy sets itself up automatically — no API key, no signup.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    var code by remember { mutableStateOf("") }
                    var status by remember { mutableStateOf<String?>(null) }
                    var ok by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it; status = null },
                        label = { Text("Setup code") },
                        singleLine = true,
                        enabled = !ok,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        enabled = code.isNotBlank() && !ok,
                        onClick = {
                            val keys = FamilyUnlock.decrypt(BuildConfig.FAMILY_BLOB, code.trim())
                            if (keys != null && FamilyUnlock.apply(keys, container.secrets)) {
                                onGroqChange(true)
                                ok = true
                                status = "Unlocked — you're all set"
                            } else {
                                status = "That code didn't work — check it and try again"
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (ok) "Unlocked ✓" else "Unlock") }
                    status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer) }
                }
            }

            Text(
                "— or set up your own key —",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        // ── "How is this free?" ──────────────────────────────────────────────
        HowIsThisFree()

        // ── Manual path: Groq key ────────────────────────────────────────────
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(Space.card), verticalArrangement = Arrangement.spacedBy(Space.md)) {
                Text("Add a speech-to-text key", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                var clicks by remember { mutableIntStateOf(0) }
                Text(
                    if (clicks == 0)
                        "Groq is still the easiest free start, but OpenAI, Deepgram, and ElevenLabs are supported too."
                    else
                        "Opened Groq. Signed up? Tap again to create a key, or paste another provider key below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = {
                        clicks++
                        runCatching {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://console.groq.com/keys")))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (clicks == 0) "Get my Groq key" else "Take me to my keys page") }

                // ONE field — Groq, the recommended path. The other three used to
                // render unconditionally right here, on the step where a new user
                // is most likely to bail, turning it into ~1,400dp of scroll
                // (audit "Onboarding — fix insets, then cut").
                KeyField("Paste your Groq key (gsk_…)", SecureKeyStore.Key.GroqStt, container.secrets) {
                    if (it) scope.launch { container.settingsStore.setSttProvider(ProviderCatalog.STT_GROQ) }
                    onGroqChange(hasAnySttKey(container.secrets))
                }

                var showOthers by rememberSaveable { mutableStateOf(false) }
                TextButton(onClick = { showOthers = !showOthers }) {
                    Text(if (showOthers) "Use Groq instead ▴" else "Use a different provider ▾")
                }
                if (showOthers) {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                        OtherSttKey("OpenAI key", SecureKeyStore.Key.OpenAiStt, ProviderCatalog.STT_OPENAI, container, onGroqChange)
                        OtherSttKey("Deepgram key", SecureKeyStore.Key.DeepgramStt, ProviderCatalog.STT_DEEPGRAM, container, onGroqChange)
                        OtherSttKey("ElevenLabs key", SecureKeyStore.Key.ElevenLabsStt, ProviderCatalog.STT_ELEVENLABS, container, onGroqChange)
                    }
                }

                Text(
                    "Keys stay encrypted on this phone. Audio is sent only to the provider you select.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ── Optional Gemini ──────────────────────────────────────────────────
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(Space.card), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text("Gemini key (optional)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Only needed if you switch the cleanup provider to Gemini in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                KeyField("Gemini API key", SecureKeyStore.Key.GeminiLlm, container.secrets)
            }
        }

        Spacer(Modifier.height(Space.xs))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
            Button(onClick = onNext, enabled = groqSaved, modifier = Modifier.weight(1f)) {
                Text(if (groqSaved) "Next" else "Add a key")
            }
        }
    }
}

/**
 * A non-Groq STT key. Saving one also makes it the active provider — same
 * behaviour the four always-visible fields had, just not always visible.
 */
@Composable
private fun OtherSttKey(
    label: String,
    key: SecureKeyStore.Key,
    provider: String,
    container: com.wisprfox.android.core.AppContainer,
    onGroqChange: (Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    KeyField(label, key, container.secrets) {
        if (it) scope.launch { container.settingsStore.setSttProvider(provider) }
        onGroqChange(hasAnySttKey(container.secrets))
    }
}

@Composable
private fun HowIsThisFree() {
    // Collapsed by default: it answers a question the user hasn't asked yet, and
    // expanded it pushed the actual key field further down the bail-out step.
    var open by rememberSaveable { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().clickable { open = !open }.padding(Space.card), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text(if (open) "▾" else "▸", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("How is this free?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            if (open) {
                Text(
                    "No catch — we ride the generous free tier that Groq gives every account: about 2,000 transcriptions a day plus ~1,000 cleanups, no credit card, resets each midnight. You can switch to a paid or different provider any time in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/* ── Screen 3: Grant access & finish ─────────────────────────────────────── */

@Composable
private fun GrantStep(ctx: Context, permTick: Int, onBack: () -> Unit, onFinish: () -> Unit) {
    val canOverlay = remember(permTick) { Settings.canDrawOverlays(ctx) }
    val a11yOn = remember(permTick) { isAccessibilityEnabled(ctx) }
    val battOk = remember(permTick) { isIgnoringBattery(ctx) }

    StepScaffold {
        Text("Almost there", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Two quick taps so Foxy can float over your apps and drop text into them. The first is required; the others make it smoother.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        PermissionCard(
            title = "Show the floating fox",
            subtitle = "Lets Foxy hover over any app as your tap-to-talk button.",
            granted = canOverlay,
            cta = "Allow overlay",
            onClick = {
                if (!Settings.canDrawOverlays(ctx)) {
                    ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
                }
            },
        )
        PermissionCard(
            title = "Auto-paste into the box",
            subtitle = "Drops your words straight into the focused field. Without it, text is copied to the clipboard to paste yourself.",
            granted = a11yOn,
            cta = "Enable auto-paste",
            onClick = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
        )
        PermissionCard(
            title = "Keep Foxy alive",
            subtitle = "A battery exemption so Android doesn't kill the mic mid-sentence.",
            granted = battOk,
            cta = "Allow",
            onClick = {
                if (!isIgnoringBattery(ctx)) {
                    ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
                }
            },
        )

        Spacer(Modifier.height(Space.xs))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
            Button(
                onClick = onFinish,
                enabled = canOverlay,
                modifier = Modifier.weight(1f),
            ) { Text("Start dictating") }
        }
        if (!canOverlay) {
            Text(
                "Allow the overlay to finish — the rest you can turn on later.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

// PermissionCard lives in ui/Components.kt — Settings → Delivery shows the same
// three permissions, and this screen's private copy was the original.

/* ── Screen 4: Sync across devices (accounts, v2.0 — only when configured) ── */

@Composable
private fun SyncStep(
    container: com.wisprfox.android.core.AppContainer,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val authState by container.authManager.state.collectAsState()
    StepScaffold {
        Text("Sync across your devices", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Sign in once and your transcripts, API keys, and settings follow you to desktop and the browser too. Totally optional — everything above already works without it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Space.card)) {
                AccountSection(container, showDeviceName = false)
            }
        }

        Spacer(Modifier.height(Space.xs))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
            Button(onClick = onDone, modifier = Modifier.weight(1f)) {
                Text(if (authState.signedIn) "Start dictating" else "Continue without an account")
            }
        }
    }
}

/* ── Shared scaffold for a step's scrollable body ────────────────────────── */

@Composable
private fun StepScaffold(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            // Same horizontal inset as every other screen (was 20dp here, 16 in
            // Settings, 12 in History, 20 on Home — audit P0-1).
            .padding(horizontal = Space.screen)
            .padding(top = Space.md, bottom = Space.xl),
        verticalArrangement = Arrangement.spacedBy(Space.md),
        content = content,
    )
}

/* ── Permission helpers ──────────────────────────────────────────────────── */

private fun isAccessibilityEnabled(ctx: Context): Boolean {
    val expected = ComponentName(ctx, WisprFoxAccessibilityService::class.java).flattenToString()
    val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
}

private fun isIgnoringBattery(ctx: Context): Boolean {
    val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return pm.isIgnoringBatteryOptimizations(ctx.packageName)
}

private fun hasAnySttKey(secrets: SecureKeyStore): Boolean =
    secrets.has(SecureKeyStore.Key.GroqStt) ||
        secrets.has(SecureKeyStore.Key.OpenAiStt) ||
        secrets.has(SecureKeyStore.Key.DeepgramStt) ||
        secrets.has(SecureKeyStore.Key.ElevenLabsStt)
