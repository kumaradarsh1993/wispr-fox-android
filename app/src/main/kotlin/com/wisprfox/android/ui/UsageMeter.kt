package com.wisprfox.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wisprfox.android.WisprFoxApp
import com.wisprfox.android.history.UsageLine
import com.wisprfox.android.history.UsageMath
import com.wisprfox.android.history.UsageSnapshot
import com.wisprfox.android.provider.ProviderCatalog
import com.wisprfox.android.settings.AppSettings
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalContext
import java.util.TimeZone

/**
 * Usage meters (P-2). ok/warn/danger colours mirror the desktop pctClass bands.
 * Bars only render for providers with a real cap (groq/deepgram) — everyone
 * else shows the number only (empty track), the desktop rule.
 */

private val METER_OK = Color(0xFF6CB16D)
private val METER_WARN = Color(0xFFE0A63A)
private val METER_DANGER = Color(0xFFCF5B4E)

private fun bandColor(band: UsageMath.Band): Color = when (band) {
    UsageMath.Band.OK -> METER_OK
    UsageMath.Band.WARN -> METER_WARN
    UsageMath.Band.DANGER -> METER_DANGER
}

/**
 * Collects a live usage snapshot for the active providers. Recomputes the day
 * key + reset countdown on a 60s ticker so the label stays current and the
 * bucket rolls over at UTC midnight without the screen being reopened.
 */
@Composable
fun rememberUsageSnapshot(settings: AppSettings): UsageSnapshot? {
    val ctx = LocalContext.current
    val container = remember { WisprFoxApp.container(ctx) }
    // A minute ticker drives resnapshot so resetLabel counts down live.
    val minuteTick by produceState(0) {
        while (true) { delay(60_000); value += 1 }
    }
    val snapshotFlow = remember(
        settings.sttProvider, settings.sttModel, settings.llmProvider, settings.activeLlmModel, minuteTick,
    ) {
        container.usage.snapshot(
            sttProvider = settings.sttProvider,
            sttModel = settings.sttModel,
            llmProvider = settings.llmProvider,
            llmModel = settings.activeLlmModel,
            localZone = TimeZone.getDefault(),
            nowMillisProvider = { System.currentTimeMillis() },
        )
    }
    return snapshotFlow.collectAsState(initial = null).value
}

/** Compact single-column usage strip for the Home screen. */
@Composable
fun UsageStrip(snapshot: UsageSnapshot, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        UsageMeterRow("Speech", snapshot.stt)
        UsageMeterRow("Cleanup", snapshot.llm)
        Text(
            snapshot.resetLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One meter row: caption + provider/model + value, and a bar when metered. */
@Composable
fun UsageMeterRow(caption: String, line: UsageLine) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                caption,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(
                "${ProviderCatalog.label(line.provider)} · ${ProviderCatalog.shortModel(line.model)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Text(
                line.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (line.hasMeter) bandColor(line.band) else MaterialTheme.colorScheme.onSurface,
            )
        }
        // Bar only for capped providers; others show the number only (desktop rule).
        if (line.hasMeter) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction = (line.percent / 100f).coerceIn(0f, 1f))
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(bandColor(line.band)),
                )
            }
        }
    }
}
