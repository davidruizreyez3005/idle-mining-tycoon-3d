package com.idlemining.tycoon3d.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.idlemining.tycoon3d.core.economy.EconomyRules
import com.idlemining.tycoon3d.core.economy.formatMoney
import com.idlemining.tycoon3d.game.GameState

/**
 * Bottom action bar: SELL (sends the worker to the depot) and UPGRADES (opens
 * the panel). The SELL button previews the current inventory value; the
 * UPGRADES button shows a badge whenever something is affordable.
 */
@Composable
fun BottomBar(
    state: GameState,
    onSell: () -> Unit,
    onUpgrades: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val value = EconomyRules.inventoryValue(state.content, state.inventory, state.upgrades)

    val anyAffordable = state.content.upgradeOrder.any { id ->
        val def = state.content.upgrade(id)
        val level = state.upgradeLevel(id)
        !EconomyRules.isMaxed(def, level) && state.money >= EconomyRules.upgradeCost(def, level)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onSell,
            modifier = Modifier
                .weight(1f)
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        ) {
            Text(
                text = if (value > 0.0) "SELL ${formatMoney(value)}" else "SELL",
                fontWeight = FontWeight.Bold,
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            Button(
                onClick = onUpgrades,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(text = "UPGRADES", fontWeight = FontWeight.Bold)
            }
            if (anyAffordable) {
                Badge(
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                    containerColor = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
