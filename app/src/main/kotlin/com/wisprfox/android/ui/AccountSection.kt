package com.wisprfox.android.ui

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.wisprfox.android.core.AppContainer
import com.wisprfox.android.sync.SupabaseConfig
import com.wisprfox.android.sync.SyncWorker
import kotlinx.coroutines.launch

/**
 * Account/sync UI shared between Settings ("Account" section) and Onboarding
 * (the skippable sign-in step), per `SYNC_DESIGN.md` "UX touchpoints". Kept as
 * one composable so both surfaces stay in lock-step with zero duplication —
 * they differ only in the chrome around this (Settings wraps it in the usual
 * section title, Onboarding wraps it in the step scaffold + a skip button).
 */
@Composable
fun AccountSection(container: AppContainer, showDeviceName: Boolean = true) {
    val authState by container.authManager.state.collectAsState()

    if (!SupabaseConfig.isConfigured()) {
        Text(
            "Sync not configured in this build.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    if (authState.signedIn) {
        SignedInAccountCard(container, authState.email, showDeviceName)
    } else {
        SignedOutAccountCard(container)
    }
}

@Composable
private fun SignedOutAccountCard(container: AppContainer) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var creatingAccount by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Sign in to sync transcripts, API keys, and settings across your phone, desktop, and browser. Audio never leaves this phone either way.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = {
                val url = container.authManager.beginGoogleSignIn() ?: return@Button
                runCatching {
                    CustomTabsIntent.Builder().build().launchUrl(ctx, Uri.parse(url))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Sign in with Google") }

        Text(
            "— or use email —",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it; status = null },
            label = { Text("Email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; status = null },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterToggle("Sign in", !creatingAccount) { creatingAccount = false }
            FilterToggle("Create account", creatingAccount) { creatingAccount = true }
        }
        Button(
            enabled = !busy && email.isNotBlank() && password.isNotBlank(),
            onClick = {
                busy = true
                status = null
                scope.launch {
                    val result = if (creatingAccount) {
                        container.authManager.signUpWithPassword(email.trim(), password)
                    } else {
                        container.authManager.signInWithPassword(email.trim(), password)
                    }
                    busy = false
                    result.fold(
                        onSuccess = {
                            status = null
                            password = ""
                            SyncWorker.ensurePeriodic(ctx)
                            SyncWorker.enqueueOnce(ctx, initial = true)
                        },
                        onFailure = { status = it.message ?: "Something went wrong — try again." },
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (busy) "Working…" else if (creatingAccount) "Create account" else "Sign in") }
        status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun SignedInAccountCard(container: AppContainer, email: String?, showDeviceName: Boolean) {
    val scope = rememberCoroutineScope()
    val settings by container.settingsStore.settings.collectAsState(initial = com.wisprfox.android.settings.AppSettings())
    var deviceName by remember(settings.deviceName) { mutableStateOf(settings.deviceName) }
    var syncing by remember { mutableStateOf(false) }
    var lastSyncedLabel by remember { mutableStateOf(relativeTime(settings.lastSyncedAt)) }

    LaunchedEffect(settings.lastSyncedAt) { lastSyncedLabel = relativeTime(settings.lastSyncedAt) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Signed in as", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(email ?: "unknown", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)

        if (showDeviceName) {
            OutlinedTextField(
                value = deviceName,
                onValueChange = { deviceName = it },
                label = { Text("This device's name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { scope.launch { container.settingsStore.setDeviceName(deviceName.trim().ifBlank { settings.deviceName }) } }) {
                    Text("Save name")
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !syncing,
                onClick = {
                    syncing = true
                    scope.launch {
                        runCatching { container.syncEngine.syncNow() }
                        syncing = false
                    }
                },
            ) { Text(if (syncing) "Syncing…" else "Sync now") }
            Text(
                "Last synced: $lastSyncedLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedButton(
            onClick = { scope.launch { container.authManager.signOut() } },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Sign out") }
        Text(
            "Signing out keeps everything already on this phone — it just stops syncing.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FilterToggle(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) { Text(label) }
    } else {
        TextButton(onClick = onClick) { Text(label) }
    }
}

private fun relativeTime(epochMillis: Long?): String {
    if (epochMillis == null) return "never"
    val deltaMs = System.currentTimeMillis() - epochMillis
    val minutes = deltaMs / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 24 * 60 -> "${minutes / 60}h ago"
        else -> "${minutes / (24 * 60)}d ago"
    }
}
