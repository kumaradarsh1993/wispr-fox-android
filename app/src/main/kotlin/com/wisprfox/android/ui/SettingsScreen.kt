package com.wisprfox.android.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.wisprfox.android.BuildConfig
import com.wisprfox.android.WisprFoxApp
import com.wisprfox.android.delivery.WisprFoxAccessibilityService
import com.wisprfox.android.provider.ProviderCatalog
import com.wisprfox.android.settings.AppSettings
import com.wisprfox.android.sync.SupabaseConfig

/**
 * Settings — a **hub**, not a scroll (audit P0-3).
 *
 * What this replaced: a single 130-line `Column(verticalScroll)` holding 16
 * groups separated by dividers — ~2,400dp of it — with seven API-key fields on
 * screen at once and an invented "Delivery & avatar" category merging four
 * unrelated concerns. It wasn't designed; it was appended to.
 *
 * Why hub-and-spoke and not tabs: M3 tabs are for switching between *peer
 * content* of the same kind, and Account and Avatar Size are not peers. One UI's
 * own Settings app uses zero tabs anywhere, and tabs would cap us around 4–5
 * groups when we have 8. A list of rows opening large-title sub-pages is the
 * convention the muscle memory already has.
 *
 * Each row carries a **summary of its current value**, so the hub answers
 * questions ("what model am I on?") rather than just being a menu. Eight rows at
 * ~76dp fits one screen with no scrolling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenSpoke: (SettingsSpoke) -> Unit,
) {
    val ctx = LocalContext.current
    val container = remember { WisprFoxApp.container(ctx) }
    val settings by container.settingsStore.settings.collectAsState(initial = AppSettings())
    val authState by container.authManager.state.collectAsState()

    // Accessibility state is read live and refreshed on ON_RESUME so the Delivery
    // summary is right the moment the user returns from the system page.
    var permTick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) permTick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val a11yOn = remember(permTick) { WisprFoxAccessibilityService.isConnected() }

    val usage = rememberUsageSnapshot(settings)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { inner ->
        ScreenColumn(Modifier.padding(inner), gap = Space.listGap) {
            SettingsRow(
                icon = Icons.Filled.Mic,
                title = "Transcription",
                summary = "${ProviderCatalog.label(settings.sttProvider)} · ${ProviderCatalog.shortModel(settings.sttModel)}",
                onClick = { onOpenSpoke(SettingsSpoke.TRANSCRIPTION) },
            )
            SettingsRow(
                icon = Icons.Filled.AutoAwesome,
                title = "Cleanup & modes",
                summary = "${settings.defaultMode.label} · ${ProviderCatalog.shortModel(settings.activeLlmModel)} on ${ProviderCatalog.label(settings.llmProvider)}",
                onClick = { onOpenSpoke(SettingsSpoke.CLEANUP) },
            )
            SettingsRow(
                icon = Icons.Filled.Pets,
                title = "Foxy",
                summary = buildString {
                    append(avatarLabel(settings.avatar))
                    append(" · ")
                    append(settings.avatarScale.label)
                    append(" · ")
                    append(if (settings.overlayBubbleEnabled) "Floating on" else "Floating off")
                },
                onClick = { onOpenSpoke(SettingsSpoke.FOXY) },
            )
            SettingsRow(
                icon = Icons.Filled.ContentPaste,
                title = "Delivery",
                summary = buildString {
                    append(if (settings.autoPasteEnabled) "Auto-paste on" else "Auto-paste off")
                    append(" · ")
                    append(if (a11yOn) "Accessibility on" else "Accessibility off")
                },
                onClick = { onOpenSpoke(SettingsSpoke.DELIVERY) },
            )
            SettingsRow(
                icon = Icons.Filled.Insights,
                title = "Usage",
                summary = usage?.let { "${it.stt.label} speech · ${it.llm.label} cleanup today" }
                    ?: "Today's free-tier meters",
                onClick = { onOpenSpoke(SettingsSpoke.USAGE) },
            )
            SettingsRow(
                icon = Icons.Filled.Storage,
                title = "Storage",
                summary = "Keep ${settings.retentionDays} days · ${settings.retentionMaxMb} MB cap",
                onClick = { onOpenSpoke(SettingsSpoke.STORAGE) },
            )
            // Mirrors onboarding's gate: an unconfigured build has no account
            // feature at all, so the row would open an empty page.
            if (SupabaseConfig.isConfigured()) {
                SettingsRow(
                    icon = Icons.Filled.CloudSync,
                    title = "Account",
                    summary = authState.email ?: "Not signed in · sync across devices",
                    onClick = { onOpenSpoke(SettingsSpoke.ACCOUNT) },
                )
            }
            SettingsRow(
                icon = Icons.Filled.Info,
                title = "About",
                summary = "Version ${BuildConfig.VERSION_NAME}",
                onClick = { onOpenSpoke(SettingsSpoke.ABOUT) },
            )
            Spacer(Modifier.height(Space.xl))
        }
    }
}

/** The spokes the hub can open. Routes live in `MainActivity`'s NavHost. */
enum class SettingsSpoke(val route: String) {
    TRANSCRIPTION("settings/transcription"),
    CLEANUP("settings/cleanup"),
    FOXY("settings/foxy"),
    DELIVERY("settings/delivery"),
    USAGE("settings/usage"),
    STORAGE("settings/storage"),
    ACCOUNT("settings/account"),
    ABOUT("settings/about"),
}
