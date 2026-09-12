package com.idlemining.tycoon3d.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.idlemining.tycoon3d.game.PopupKind
import kotlinx.coroutines.delay

/**
 * First-launch onboarding card — teaches the three verbs of the vertical slice
 * (mine, sell, upgrade) in one glance.
 */
@Composable
fun HintOverlay(
    zoneName: String,
    onGotIt: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Welcome to $zoneName",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                HintLine("TAP A ROCK", "Your worker walks over and mines it.")
                HintLine("TAP THE GROUND", "Walk anywhere — the camera follows you automatically.")
                HintLine("SELL AT THE DEPOT", "The market stall with the spinning coin.")
                HintLine("BUY UPGRADES", "Mine faster, carry more, automate everything.")

                Button(
                    onClick = onGotIt,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(text = "GOT IT", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun HintLine(title: String, body: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One floating HUD popup entry. */
data class PopupEntry(val id: Int, val text: String, val kind: PopupKind)

/**
 * A small stack of transient floating texts near the top-center — sale results,
 * pickups, warnings. Entries expire after ~2.4 s.
 */
@Composable
fun PopupsOverlay(
    popups: List<PopupEntry>,
    onExpire: (PopupEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Expire the oldest entry on a timer (re-armed whenever the stack changes).
    LaunchedEffect(popups.size) {
        if (popups.isNotEmpty()) {
            delay(2400)
            onExpire(popups.first())
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 92.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        popups.takeLast(4).forEach { entry ->
            val (bg, fg) = when (entry.kind) {
                PopupKind.MONEY -> MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.onSecondary
                PopupKind.WARN -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError
                PopupKind.INFO -> MaterialTheme.colorScheme.surface to MaterialTheme.colorScheme.onSurface
            }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = bg.copy(alpha = 0.92f),
                contentColor = fg,
            ) {
                Text(
                    text = entry.text,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
    }
}
