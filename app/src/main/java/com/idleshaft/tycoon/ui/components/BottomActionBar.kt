package com.idleshaft.tycoon.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** The four panels reachable from the bottom bar. */
enum class GamePanel { SHAFTS, REFINERY, UPGRADES, MANAGERS }

/**
 * Bottom navigation bar of the HUD — opens bottom-sheet panels without pausing
 * the 3D rendering behind them.
 */
@Composable
fun BottomActionBar(
    onPanelSelected: (GamePanel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            BottomItem(Icons.Filled.Terrain, "Shafts") { onPanelSelected(GamePanel.SHAFTS) }
            BottomItem(Icons.Filled.Factory, "Refinery") { onPanelSelected(GamePanel.REFINERY) }
            BottomItem(Icons.Filled.Upgrade, "Upgrades") { onPanelSelected(GamePanel.UPGRADES) }
            BottomItem(Icons.Filled.Engineering, "Managers") { onPanelSelected(GamePanel.MANAGERS) }
        }
    }
}

@Composable
private fun BottomItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
