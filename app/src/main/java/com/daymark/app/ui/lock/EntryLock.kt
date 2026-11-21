package com.daymark.app.ui.lock

import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.daymark.app.ui.components.DaymarkCard

/**
 * Optional lock for the private entries (slips, urges, "Not a slip" notes, journal).
 * Unlocked once per visit; [MainActivity] re-locks it after the same background grace as the app lock,
 * and unlocking the whole app (app lock) unlocks entries too.
 */
object EntryLock {
    var unlocked by mutableStateOf(false)

    fun authenticate(activity: FragmentActivity) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { unlocked = true }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Show your entries")
            .setSubtitle("Slips, urges and journal are private")
            .setAllowedAuthenticators(Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL)
            .build()
        runCatching { prompt.authenticate(info) }.onFailure { unlocked = true } // never trap the user if auth is unavailable
    }
}

/** Shown in place of the day's entries while [EntryLock] is locked. */
@Composable
fun LockedEntriesCard(count: Int) {
    val activity = LocalContext.current as? FragmentActivity
    DaymarkCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Fingerprint, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Entries are locked", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (count == 1) "1 entry on this day" else if (count > 1) "$count entries on this day" else "Unlock to see your entries",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(
            onClick = { activity?.let(EntryLock::authenticate) ?: run { EntryLock.unlocked = true } },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(16.dp),
        ) { Text("Unlock") }
    }
}
