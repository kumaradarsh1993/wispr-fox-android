package com.wisprfox.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.wisprfox.android.settings.SecureKeyStore

/**
 * Reusable masked API-key entry (Settings + Onboarding). Writes to the Keystore.
 * [onChange] fires with the new key-presence (true after Save, false after Clear)
 * so parents like Onboarding can recompose their "continue" gate.
 */
@Composable
fun KeyField(
    label: String,
    key: SecureKeyStore.Key,
    secrets: SecureKeyStore,
    onChange: (hasKey: Boolean) -> Unit = {},
) {
    var value by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(secrets.has(key)) }
    var status by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            label = { Text(if (saved) "$label (saved — enter to replace)" else label) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            Button(
                enabled = value.isNotBlank(),
                onClick = {
                    secrets.put(key, value.trim())
                    saved = true
                    value = ""
                    status = "Saved"
                    onChange(true)
                },
            ) { Text("Save") }
            if (saved) {
                OutlinedButton(onClick = {
                    secrets.clear(key); saved = false; status = "Cleared"; onChange(false)
                }) { Text("Clear") }
            }
        }
        status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
