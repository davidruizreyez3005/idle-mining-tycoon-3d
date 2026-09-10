package com.idleshaft.tycoon.ui.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.idleshaft.tycoon.domain.Economy
import com.idleshaft.tycoon.domain.GameIntent
import com.idleshaft.tycoon.domain.GameState
import com.idleshaft.tycoon.game.engine.Simulation
import com.idleshaft.tycoon.ui.components.BuyButton
import com.idleshaft.tycoon.ui.components.PanelCard
import com.idleshaft.tycoon.ui.components.PanelHeader
import com.idleshaft.tycoon.ui.components.UpgradeRow

/** "Upgrades" panel: global logistics upgrades and the gem boost. */
@Composable
fun UpgradesPanel(
    state: GameState,
    nowMs: Long,
    dispatch: (GameIntent) -> Unit,
) {
    val logistics = state.logistics
    val truckCapLevel = logistics.truckCapacityLevel
    val truckSpeedLevel = logistics.truckSpeedLevel
    val offlineLevel = logistics.offlineCapHours - Economy.BASE_OFFLINE_CAP_HOURS
    val boostRemaining = (state.boostUntilMs - nowMs).coerceAtLeast(0L) / 1000L

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PanelHeader(
                title = "Upgrades",
                subtitle = "Logistics, delivery and storage — they apply to the whole operation.",
            )
        }

        item {
            PanelCard {
                UpgradeRow(
                    icon = Icons.Filled.LocalShipping,
                    title = "Truck Capacity",
                    level = truckCapLevel,
                    effect = "%d → %d bars per run".format(
                        Economy.truckCapacity(truckCapLevel),
                        Economy.truckCapacity(truckCapLevel + 1),
                    ),
                    cost = Economy.upgradeCost(Economy.UpgradeKind.TRUCK_CAPACITY, truckCapLevel),
                    affordable = state.cash >= Economy.upgradeCost(Economy.UpgradeKind.TRUCK_CAPACITY, truckCapLevel),
                    onBuy = { dispatch(GameIntent.BuyGlobalUpgrade(Economy.UpgradeKind.TRUCK_CAPACITY)) },
                )
                UpgradeRow(
                    icon = Icons.Filled.Speed,
                    title = "Truck Speed",
                    level = truckSpeedLevel,
                    effect = "%.1fs → %.1fs one-way".format(
                        Economy.truckTravelSeconds(truckSpeedLevel),
                        Economy.truckTravelSeconds(truckSpeedLevel + 1),
                    ),
                    cost = Economy.upgradeCost(Economy.UpgradeKind.TRUCK_SPEED, truckSpeedLevel),
                    affordable = state.cash >= Economy.upgradeCost(Economy.UpgradeKind.TRUCK_SPEED, truckSpeedLevel),
                    onBuy = { dispatch(GameIntent.BuyGlobalUpgrade(Economy.UpgradeKind.TRUCK_SPEED)) },
                )
                UpgradeRow(
                    icon = Icons.Filled.Warehouse,
                    title = "Warehouse Logistics",
                    level = offlineLevel,
                    effect = "%dh → %dh offline storage".format(
                        Economy.BASE_OFFLINE_CAP_HOURS + offlineLevel,
                        Economy.BASE_OFFLINE_CAP_HOURS + offlineLevel + 1,
                    ),
                    cost = Economy.upgradeCost(Economy.UpgradeKind.OFFLINE_CAP, offlineLevel),
                    affordable = state.cash >= Economy.upgradeCost(Economy.UpgradeKind.OFFLINE_CAP, offlineLevel),
                    maxedOut = offlineLevel >= Economy.MAX_OFFLINE_LEVEL,
                    onBuy = { dispatch(GameIntent.BuyGlobalUpgrade(Economy.UpgradeKind.OFFLINE_CAP)) },
                )
            }
        }

        item {
            PanelCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                    ) {}
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Gem Boost", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (boostRemaining > 0) "2x income active — ${boostRemaining}s left"
                            else "Double all income for ${Economy.BOOST_DURATION_MS / 1000}s",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    androidx.compose.material3.Icon(
                        Icons.Filled.Bolt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    BuyButton(
                        text = "${Economy.BOOST_GEM_COST} gems",
                        enabled = state.gems >= Economy.BOOST_GEM_COST,
                        onClick = { dispatch(GameIntent.ActivateBoost) },
                    )
                }
            }
        }
    }
}
