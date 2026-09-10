package com.idleshaft.tycoon.ui.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.idleshaft.tycoon.domain.Economy
import com.idleshaft.tycoon.domain.GameIntent
import com.idleshaft.tycoon.domain.GameState
import com.idleshaft.tycoon.domain.OreType
import com.idleshaft.tycoon.game.engine.Simulation
import com.idleshaft.tycoon.ui.components.BuyButton
import com.idleshaft.tycoon.ui.components.OreChip
import com.idleshaft.tycoon.ui.components.PanelCard
import com.idleshaft.tycoon.ui.components.PanelHeader
import com.idleshaft.tycoon.ui.components.StatPill
import com.idleshaft.tycoon.ui.components.UpgradeRow

/** "Refinery" panel: ore silo, bar silo, processing speed / value upgrades, manager. */
@Composable
fun RefineryPanel(
    state: GameState,
    dispatch: (GameIntent) -> Unit,
) {
    val refinery = state.refinery
    val speedLevel = refinery.speedLevel
    val efficiencyLevel = refinery.efficiencyLevel

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PanelHeader(
                title = "Crusher & Refinery",
                subtitle = "10 units of raw ore melt into one sellable bar.",
            )
        }

        item {
            PanelCard {
                Text("Surface silos", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OreChip(OreType.COPPER, "%.0f".format(state.rawOreSilo[OreType.COPPER] ?: 0.0))
                    OreChip(OreType.IRON, "%.0f".format(state.rawOreSilo[OreType.IRON] ?: 0.0))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OreChip(OreType.GOLD, "%.0f".format(state.rawOreSilo[OreType.GOLD] ?: 0.0))
                    OreChip(OreType.DIAMOND, "%.0f".format(state.rawOreSilo[OreType.DIAMOND] ?: 0.0))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatPill("Bars in stock", "${Economy.totalBars(state)}")
                    StatPill("Bar value (Cu)", Economy.money(Economy.barValue(OreType.COPPER, efficiencyLevel)))
                    StatPill("Batch time", "%.1fs".format(Economy.refineTimeSeconds(speedLevel)))
                }
            }
        }

        item {
            PanelCard {
                UpgradeRow(
                    icon = Icons.Filled.Speed,
                    title = "Processing Speed",
                    level = speedLevel,
                    effect = "%.1fs → %.1fs per bar".format(
                        Economy.refineTimeSeconds(speedLevel),
                        Economy.refineTimeSeconds(speedLevel + 1),
                    ),
                    cost = Economy.upgradeCost(Economy.UpgradeKind.REFINERY_SPEED, speedLevel),
                    affordable = state.cash >= Economy.upgradeCost(Economy.UpgradeKind.REFINERY_SPEED, speedLevel),
                    onBuy = { dispatch(GameIntent.BuyGlobalUpgrade(Economy.UpgradeKind.REFINERY_SPEED)) },
                )
                UpgradeRow(
                    icon = Icons.Filled.Paid,
                    title = "Refinery Efficiency",
                    level = efficiencyLevel,
                    effect = "Bar value ×%.2f → ×%.2f".format(
                        Economy.barValueMultiplier(efficiencyLevel),
                        Economy.barValueMultiplier(efficiencyLevel + 1),
                    ),
                    cost = Economy.upgradeCost(Economy.UpgradeKind.REFINERY_EFFICIENCY, efficiencyLevel),
                    affordable = state.cash >= Economy.upgradeCost(Economy.UpgradeKind.REFINERY_EFFICIENCY, efficiencyLevel),
                    onBuy = { dispatch(GameIntent.BuyGlobalUpgrade(Economy.UpgradeKind.REFINERY_EFFICIENCY)) },
                )
            }
        }

        if (!refinery.managerHired) {
            item {
                PanelCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Hire Crusher Kate to run the refinery non-stop — " +
                                "the crusher keeps eating while you are away.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        BuyButton(
                            text = Economy.money(Economy.REFINERY_MANAGER_COST),
                            enabled = state.cash >= Economy.REFINERY_MANAGER_COST,
                            onClick = { dispatch(GameIntent.HireRefineryManager) },
                        )
                    }
                }
            }
        }
    }
}
