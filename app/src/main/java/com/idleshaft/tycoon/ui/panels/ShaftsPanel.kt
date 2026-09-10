package com.idleshaft.tycoon.ui.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.idleshaft.tycoon.domain.CartPhase
import com.idleshaft.tycoon.domain.Economy
import com.idleshaft.tycoon.domain.GameIntent
import com.idleshaft.tycoon.domain.GameState
import com.idleshaft.tycoon.domain.MinerPhase
import com.idleshaft.tycoon.domain.OreType
import com.idleshaft.tycoon.domain.Shaft
import com.idleshaft.tycoon.ui.components.BuyButton
import com.idleshaft.tycoon.ui.components.OreChip
import com.idleshaft.tycoon.ui.components.PanelCard
import com.idleshaft.tycoon.ui.components.PanelHeader
import com.idleshaft.tycoon.ui.components.oreChipColor
import com.idleshaft.tycoon.ui.components.UpgradeRow

/** "Shafts" panel: unlock new shafts, upgrade miner crew / cart / depth per shaft. */
@Composable
fun ShaftsPanel(
    state: GameState,
    dispatch: (GameIntent) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PanelHeader(
                title = "Mine Shafts",
                subtitle = "Copper, iron, gold and diamond lie deeper and deeper down.",
            )
        }

        val nextLocked = state.shafts.indexOfFirst { !it.unlocked }
        if (nextLocked >= 0) {
            item { UnlockShaftCard(state, nextLocked, dispatch) }
        }

        items(state.shafts.withIndex().filter { it.value.unlocked }.toList()) { (index, shaft) ->
            ShaftCard(index, shaft, state, dispatch)
        }
    }
}

@Composable
private fun UnlockShaftCard(state: GameState, index: Int, dispatch: (GameIntent) -> Unit) {
    val ore = OreType.ordered[index]
    val cost = Economy.shaftUnlockCost(index)
    val affordable = state.cash >= cost
    PanelCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            androidx.compose.foundation.layout.Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(4.dp)
                    .size(40.dp)
                    .background(oreChipColor(ore).copy(alpha = 0.25f), androidx.compose.foundation.shape.CircleShape),
            ) {
                androidx.compose.material3.Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = oreChipColor(ore),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Unlock the ${ore.displayName} Shaft", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${ore.rawValuePerUnit}x richer ore · value ${Economy.money(Economy.barValue(ore, 1))}/bar",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        BuyButton(
            text = "Unlock · " + Economy.money(cost),
            enabled = affordable,
            onClick = { dispatch(GameIntent.UnlockShaft) },
            modifier = Modifier.align(Alignment.End),
        )
    }
}

@Composable
private fun ShaftCard(index: Int, shaft: Shaft, state: GameState, dispatch: (GameIntent) -> Unit) {
    val ore = shaft.oreType
    val storage = Economy.shaftStorage(shaft.cartCapacityLevel)
    val working = shaft.miners.any { it.phase != MinerPhase.IDLE } || shaft.cart.phase != CartPhase.IDLE

    PanelCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OreChip(ore, ore.displayName)
            if (shaft.managerHired) {
                ManagerBadge()
            } else {
                Text(
                    if (working) "digging…" else "crew idle",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "stock ${"%.0f".format(shaft.buffer)}/${"%.0f".format(storage)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }

        if (!shaft.managerHired) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Hire the crew manager to automate this shaft",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                BuyButton(
                    text = Economy.money(Economy.shaftManagerCost(index)),
                    enabled = state.cash >= Economy.shaftManagerCost(index),
                    onClick = { dispatch(GameIntent.HireShaftManager(index)) },
                )
            }
        }

        val speedLevel = shaft.minerSpeedLevel
        val capacityLevel = shaft.minerCapacityLevel
        val cartCapLevel = shaft.cartCapacityLevel
        val cartSpeedLevel = shaft.cartSpeedLevel
        val depthLevel = shaft.depthLevel

        UpgradeRow(
            icon = Icons.Filled.Speed,
            title = "Miner Speed",
            level = speedLevel,
            effect = "Dig time ${"%.1f".format(Economy.mineTimeSeconds(speedLevel))}s → ${"%.1f".format(Economy.mineTimeSeconds(speedLevel + 1))}s",
            cost = Economy.upgradeCost(Economy.UpgradeKind.MINER_SPEED, speedLevel),
            affordable = state.cash >= Economy.upgradeCost(Economy.UpgradeKind.MINER_SPEED, speedLevel),
            onBuy = { dispatch(GameIntent.BuyShaftUpgrade(index, Economy.UpgradeKind.MINER_SPEED)) },
        )
        UpgradeRow(
            icon = Icons.Filled.Construction,
            title = "Miner Capacity",
            level = capacityLevel,
            effect = "${"%.0f".format(Economy.minerCapacity(capacityLevel))} → ${"%.0f".format(Economy.minerCapacity(capacityLevel + 1))} ore/trip",
            cost = Economy.upgradeCost(Economy.UpgradeKind.MINER_CAPACITY, capacityLevel),
            affordable = state.cash >= Economy.upgradeCost(Economy.UpgradeKind.MINER_CAPACITY, capacityLevel),
            onBuy = { dispatch(GameIntent.BuyShaftUpgrade(index, Economy.UpgradeKind.MINER_CAPACITY)) },
        )
        UpgradeRow(
            icon = Icons.Filled.Person,
            title = "Cart Capacity",
            level = cartCapLevel,
            effect = "${"%.0f".format(Economy.cartCapacity(cartCapLevel))} → ${"%.0f".format(Economy.cartCapacity(cartCapLevel + 1))} ore/trip",
            cost = Economy.upgradeCost(Economy.UpgradeKind.CART_CAPACITY, cartCapLevel),
            affordable = state.cash >= Economy.upgradeCost(Economy.UpgradeKind.CART_CAPACITY, cartCapLevel),
            onBuy = { dispatch(GameIntent.BuyShaftUpgrade(index, Economy.UpgradeKind.CART_CAPACITY)) },
        )
        UpgradeRow(
            icon = Icons.Filled.Speed,
            title = "Cart Speed",
            level = cartSpeedLevel,
            effect = "Travel ${"%.1f".format(Economy.cartTravelSeconds(cartSpeedLevel))}s → ${"%.1f".format(Economy.cartTravelSeconds(cartSpeedLevel + 1))}s",
            cost = Economy.upgradeCost(Economy.UpgradeKind.CART_SPEED, cartSpeedLevel),
            affordable = state.cash >= Economy.upgradeCost(Economy.UpgradeKind.CART_SPEED, cartSpeedLevel),
            onBuy = { dispatch(GameIntent.BuyShaftUpgrade(index, Economy.UpgradeKind.CART_SPEED)) },
        )
        UpgradeRow(
            icon = Icons.Filled.ArrowDownward,
            title = "Shaft Depth",
            level = depthLevel,
            effect = "Vein richness ×${"%.2f".format(Economy.richness(depthLevel))} → ×${"%.2f".format(Economy.richness(depthLevel + 1))}",
            cost = Economy.upgradeCost(Economy.UpgradeKind.SHAFT_DEPTH, depthLevel),
            affordable = state.cash >= Economy.upgradeCost(Economy.UpgradeKind.SHAFT_DEPTH, depthLevel),
            onBuy = { dispatch(GameIntent.BuyShaftUpgrade(index, Economy.UpgradeKind.SHAFT_DEPTH)) },
        )
    }
}

@Composable
private fun ManagerBadge() {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
    ) {
        Text(
            "AUTO",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}
