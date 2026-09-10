package com.idleshaft.tycoon.ui.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.idleshaft.tycoon.domain.Economy
import com.idleshaft.tycoon.domain.GameIntent
import com.idleshaft.tycoon.domain.GameState
import com.idleshaft.tycoon.domain.OreType
import com.idleshaft.tycoon.ui.components.BuyButton
import com.idleshaft.tycoon.ui.components.OreChip
import com.idleshaft.tycoon.ui.components.PanelCard
import com.idleshaft.tycoon.ui.components.PanelHeader

private data class ManagerEntry(
    val name: String,
    val role: String,
    val description: String,
    val cost: Double,
    val hired: Boolean,
    val action: GameIntent,
    val ore: OreType? = null,
)

/** "Managers" panel: hire the crew that keeps every stage running while you're gone. */
@Composable
fun ManagersPanel(
    state: GameState,
    dispatch: (GameIntent) -> Unit,
) {
    val entries = state.shafts.mapIndexed { i, shaft ->
        ManagerEntry(
            name = shaftManagerNames[i],
            role = "${shaft.oreType.displayName} shaft manager",
            description = "Miners keep digging and the cart keeps hauling — even offline.",
            cost = Economy.shaftManagerCost(i),
            hired = shaft.managerHired,
            action = GameIntent.HireShaftManager(i),
            ore = shaft.oreType,
        )
    } + listOf(
        ManagerEntry(
            name = "Crusher Kate",
            role = "Refinery foreman",
            description = "The crusher eats raw ore around the clock.",
            cost = Economy.REFINERY_MANAGER_COST,
            hired = state.refinery.managerHired,
            action = GameIntent.HireRefineryManager,
        ),
        ManagerEntry(
            name = "Turbo Marta",
            role = "Dispatch lead",
            description = "Trucks load, deliver and sell without a single tap.",
            cost = Economy.LOGISTICS_MANAGER_COST,
            hired = state.logistics.managerHired,
            action = GameIntent.HireLogisticsManager,
        ),
    )

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PanelHeader(
                title = "Managers",
                subtitle = "Automate a stage and it keeps producing forever — the idle core of the game.",
            )
        }
        items(entries) { entry -> ManagerCard(entry, state, dispatch) }
    }
}

@Composable
private fun ManagerCard(entry: ManagerEntry, state: GameState, dispatch: (GameIntent) -> Unit) {
    PanelCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                Icons.Filled.Engineering,
                contentDescription = null,
                tint = entry.ore?.let { com.idleshaft.tycoon.ui.components.oreChipColor(it) }
                    ?: MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(entry.name, style = MaterialTheme.typography.titleMedium)
                    if (entry.hired) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = "Hired",
                            tint = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
                Text(entry.role, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(entry.description, style = MaterialTheme.typography.bodySmall)
            }
            if (!entry.hired) {
                BuyButton(
                    text = Economy.money(entry.cost),
                    enabled = state.cash >= entry.cost,
                    onClick = { dispatch(entry.action) },
                )
            } else {
                Text(
                    "HIRED",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

private val shaftManagerNames = listOf(
    "Old Pete",
    "Iron Rosa",
    "Digger Dan",
    "Deep Vera",
)
