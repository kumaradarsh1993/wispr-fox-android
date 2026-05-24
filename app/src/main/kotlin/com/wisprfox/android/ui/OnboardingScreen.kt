package com.wisprfox.android.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wisprfox.android.R
import com.wisprfox.android.WisprFoxApp
import com.wisprfox.android.settings.SecureKeyStore

/**
 * First-run setup. Cannot be skipped past the one hard requirement — a Groq
 * key — because nothing works without it. The OS-permission steps deep-link to
 * the relevant settings pages (overlay + accessibility have no runtime dialog).
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val container = remember { WisprFoxApp.container(ctx) }
    var keySaved by remember { mutableStateOf(container.secrets.has(SecureKeyStore.Key.GroqStt)) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(painterResource(R.drawable.fox_sitting), contentDescription = null, modifier = Modifier.size(120.dp))
        Text("Welcome to wispr-fox", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Tap the floating fox, speak, and your words land in whatever you're typing. Long-press the fox to pick Raw, Cleaned, or Reformatted.",
            style = MaterialTheme.typography.bodyMedium,
        )

        HorizontalDivider()
        Text("1. Add your Groq API key", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("Free at console.groq.com. Used for Whisper transcription (and Llama cleanup unless you pick Gemini in Settings).", style = MaterialTheme.typography.bodySmall)
        KeyField("Groq API key", SecureKeyStore.Key.GroqStt, container.secrets)

        HorizontalDivider()
        Text("2. Grant access", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        OutlinedButton(
            onClick = {
                if (!Settings.canDrawOverlays(ctx)) {
                    ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Allow the floating fox (overlay)") }
        OutlinedButton(
            onClick = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Enable auto-paste (Accessibility) — optional") }
        OutlinedButton(
            onClick = {
                val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
                if (pm?.isIgnoringBatteryOptimizations(ctx.packageName) != true) {
                    ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Keep wispr-fox alive (battery exemption)") }

        HorizontalDivider()
        keySaved = container.secrets.has(SecureKeyStore.Key.GroqStt)
        Button(onClick = onDone, enabled = keySaved, modifier = Modifier.fillMaxWidth()) {
            Text(if (keySaved) "Start dictating" else "Add your Groq key to continue")
        }
    }
}
