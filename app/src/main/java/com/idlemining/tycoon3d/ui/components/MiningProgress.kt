package com.idlemining.tycoon3d.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.idlemining.tycoon3d.game.GameState

/**
 * Compact mining progress card — visible only while the worker actively mines,
 * floating above the bottom bar.
 *
 * The snapshot changes at 10 Hz *only while mining* (the progress bar must
 * track the pickaxe damage) and is fully static — zero recompositions — while
 * the worker walks or idles.
 */
@Composable
fun MiningProgress(state: State<GameState>, modifier: Modifier = Modifier) {
    val snap = rememberMiningSnapshot(state)
    if (!snap.visible) return

    Surface(
        modifier = modifier
            .padding(bottom = 92.dp)
            .width(220.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = snap.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                LinearProgressIndicator(
                    progress = { snap.progress },
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Text(
                    text = snap.pctText,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}
