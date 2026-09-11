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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.idlemining.tycoon3d.game.GameState
import com.idlemining.tycoon3d.game.WorkerAction

/**
 * Compact mining progress card — visible only while the worker actively mines,
 * floating above the bottom bar.
 */
@Composable
fun MiningProgress(state: GameState, modifier: Modifier = Modifier) {
    if (state.worker.action != WorkerAction.MINING) return
    val node = state.nodes.getOrNull(state.worker.miningNodeIndex) ?: return
    val def = state.content.nodeType(node.typeId)
    val progress = (1f - node.hp / node.maxHp).coerceIn(0f, 1f)

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
                text = "Mining ${def.name}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}
