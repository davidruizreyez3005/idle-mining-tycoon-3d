package com.idlemining.tycoon3d.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.idlemining.tycoon3d.game.GameState
import com.idlemining.tycoon3d.ui.theme.Gold

/**
 * Top HUD: money, passive income rate and the backpack meter. Chips are translucent
 * so the 3D world shows through.
 *
 * Takes the live state as a [State] holder and derives a [TopHudSnapshot]
 * (1 Hz-quantized market values) — recomposition happens only when displayed
 * values actually change, not on every 10 Hz simulation emission.
 */
@Composable
fun TopHud(state: State<GameState>, modifier: Modifier = Modifier) {
    val snap = rememberTopHudSnapshot(state)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Chip {
                Text(
                    text = snap.moneyText,
                    color = Gold,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            if (snap.perSecondText != null) {
                Chip {
                    Text(
                        text = snap.perSecondText,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Chip {
                Text(
                    text = "${snap.carried} / ${snap.capacity}",
                    color = if (snap.nearFull) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        // Backpack meter, right-aligned under the chips.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
            ) {
                LinearProgressIndicator(
                    progress = { snap.fill },
                    modifier = Modifier.width(140.dp).height(5.dp).padding(2.dp),
                    color = if (snap.nearFull) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent,
                )
            }
        }
    }
}

@Composable
private fun Chip(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        androidx.compose.foundation.layout.Box(Modifier.padding(horizontal = 14.dp, vertical = 7.dp)) {
            content()
        }
    }
}
