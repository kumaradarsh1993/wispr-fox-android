package com.wisprfox.android.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.wisprfox.android.provider.ProviderCatalog
import com.wisprfox.android.settings.SecureKeyStore
import com.wisprfox.android.ui.KeyField
import com.wisprfox.android.ui.SectionCard
import com.wisprfox.android.ui.Sizes
import com.wisprfox.android.ui.Space
import com.wisprfox.android.ui.foxColors

/**
 * API-key entry for a provider group (audit P0-3, "the key move").
 *
 * Settings used to render **all seven key fields unconditionally** — a user on
 * Groq scrolled past empty OpenAI, Deepgram, ElevenLabs and Gemini fields for
 * providers they will never use. That's ~450dp of dead space, more than a full
 * S23 Ultra screen height, sitting directly between the two controls people
 * actually change.
 *
 * So: show **one** field, for the currently selected provider. The rest go
 * behind an expander with a ✓/— status dot each, so "did I ever save my OpenAI
 * key?" is still answerable at a glance without unfolding anything.
 */
@Composable
fun KeySection(
    selectedProvider: String,
    providers: List<ProviderCatalog.ProviderOption>,
    keyFor: (String) -> SecureKeyStore.Key,
    secrets: SecureKeyStore,
    labelFor: (String) -> String,
    footer: String,
) {
    // Bumped after any save/clear so the status dots below re-read the store.
    // KeyField owns its own field state; presence is what we mirror here.
    var keyTick by remember { mutableIntStateOf(0) }
    var showOthers by rememberSaveable { mutableStateOf(false) }

    val others = providers.filter { it.id != selectedProvider }

    SectionCard {
        KeyField(
            label = labelFor(selectedProvider),
            key = keyFor(selectedProvider),
            secrets = secrets,
            onChange = { keyTick++ },
        )
        Text(
            footer,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (others.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Sizes.touch)
                    .clickable(onClick = { showOthers = !showOthers }),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                Text(
                    "Other provider keys",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                // Status at a glance, so the expander doesn't hide the answer to
                // "have I set this one up?"
                key(keyTick) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                        others.forEach { StatusDot(secrets.has(keyFor(it.id))) }
                    }
                }
                Icon(
                    if (showOthers) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (showOthers) "Collapse other provider keys" else "Expand other provider keys",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(showOthers) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                    others.forEach { option ->
                        KeyField(
                            label = labelFor(option.id),
                            key = keyFor(option.id),
                            secrets = secrets,
                            onChange = { keyTick++ },
                        )
                    }
                }
            }
        }
    }
}

/** ✓/— presence indicator. Decorative: the field's own label says "saved". */
@Composable
private fun StatusDot(present: Boolean) {
    Box(
        Modifier
            .size(8.dp)
            .clearAndSetSemantics { }
            .background(
                if (present) MaterialTheme.foxColors.success else MaterialTheme.colorScheme.outlineVariant,
                CircleShape,
            ),
    )
}
