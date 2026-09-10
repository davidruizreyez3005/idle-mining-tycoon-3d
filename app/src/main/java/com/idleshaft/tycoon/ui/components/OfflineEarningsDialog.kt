package com.idleshaft.tycoon.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.idleshaft.tycoon.domain.Economy
import com.idleshaft.tycoon.domain.GameIntent
import com.idleshaft.tycoon.domain.GameState

/**
 * "Welcome back" dialog: reports offline earnings, time away, the applied rate and
 * the offline storage cap so the player understands exactly how the number arose.
 */
@Composable
fun OfflineEarningsDialog(
    state: GameState,
    dispatch: (GameIntent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { dispatch(GameIntent.DismissOfflineEarnings) },
        icon = {
            Icon(
                Icons.Filled.Bedtime,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(32.dp),
            )
        },
        title = { Text("Welcome back, boss!") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Paid,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        "  " + Economy.money(state.pendingOfflineEarnings),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text("Your automated crews kept working while you were away.")
                Text(
                    "Away for ${Economy.duration(state.pendingOfflineSeconds)} · " +
                        "paid at ${Economy.rate(state.pendingOfflineEarnings / state.pendingOfflineSeconds.coerceAtLeast(1))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Offline storage holds up to ${state.logistics.offlineCapHours}h of earnings — " +
                        "upgrade Warehouse Logistics in the Upgrades panel to extend it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = { dispatch(GameIntent.DismissOfflineEarnings) }) { Text("Collect") }
        },
        dismissButton = {
            TextButton(onClick = { dispatch(GameIntent.DismissOfflineEarnings) }) { Text("Nice") }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
