package com.idlemining.tycoon3d.ui.components

import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import com.idlemining.tycoon3d.core.economy.EconomyRules
import com.idlemining.tycoon3d.core.economy.formatMoney
import com.idlemining.tycoon3d.game.GameState
import com.idlemining.tycoon3d.game.WorkerAction
import kotlin.math.floor

/**
 * Phase 3.5 — HUD recomposition budget.
 *
 * The simulation emits a new [GameState] ten times a second. The pre-tuning
 * HUD read that state directly in composition, so every chip, button and
 * progress bar recomposed at 10 Hz — re-running market sine math, upgrade
 * cost loops and string formatting on the main thread between frames.
 *
 * Each HUD component now derives a small [snapshot][snapshotOf] through
 * `derivedStateOf`: the lambda still evaluates on every emission (it is
 * microseconds of pure math), but Compose only *recomposes* when the derived
 * snapshot actually differs. Values that ride the market clock are quantized
 * to whole seconds, so market-driven text ticks at 1 Hz instead of 10 Hz;
 * money, backpack and upgrade values change only when the player acts.
 */

// ----------------------------------------------------------------- top HUD

data class TopHudSnapshot(
    val moneyText: String,
    val carried: Int,
    val capacity: Int,
    val fill: Float,
    val nearFull: Boolean,
    /** null hides the passive-rate chip (no extractor or zero output). */
    val perSecondText: String?,
)

fun topHudSnapshot(state: GameState): TopHudSnapshot {
    val capacity = EconomyRules.backpackCapacity(state.content, state.upgrades).coerceAtLeast(1)
    val carried = state.totalCarried
    // Market prices are quantized to whole seconds — the passive-rate chip
    // ticks at 1 Hz instead of tracking the raw clock at 10 Hz.
    val marketTime = floor(state.marketTimeSec)
    val extractorLevel = state.upgradeLevel("extractor")
    val perSecond = if (extractorLevel > 0) {
        EconomyRules.idleRatesPerSecond(state.content, extractorLevel).entries.sumOf { (id, rate) ->
            EconomyRules.sellPricePerUnit(state.content, state.content.resource(id), state.upgrades, marketTime) * rate
        }
    } else 0.0
    return TopHudSnapshot(
        moneyText = formatMoney(state.money),
        carried = carried,
        capacity = capacity,
        fill = (carried.toFloat() / capacity).coerceIn(0f, 1f),
        nearFull = carried >= capacity,
        perSecondText = if (perSecond > 0.0) "+" + formatMoney(perSecond) + "/s" else null,
    )
}

@androidx.compose.runtime.Composable
fun rememberTopHudSnapshot(state: State<GameState>): TopHudSnapshot =
    remember { derivedStateOf { topHudSnapshot(state.value) } }.value

// -------------------------------------------------------------- bottom bar

data class BottomBarSnapshot(
    val sellText: String,
    val anyAffordable: Boolean,
)

fun bottomBarSnapshot(state: GameState): BottomBarSnapshot {
    val value = EconomyRules.inventoryValue(
        state.content, state.inventory, state.upgrades, floor(state.marketTimeSec),
    )
    val anyAffordable = state.content.upgradeOrder.any { id ->
        val def = state.content.upgrade(id)
        val level = state.upgradeLevel(id)
        !EconomyRules.isMaxed(def, level) && state.money >= EconomyRules.upgradeCost(def, level)
    }
    return BottomBarSnapshot(
        sellText = if (value > 0.0) "SELL ${formatMoney(value)}" else "SELL",
        anyAffordable = anyAffordable,
    )
}

@androidx.compose.runtime.Composable
fun rememberBottomBarSnapshot(state: State<GameState>): BottomBarSnapshot =
    remember { derivedStateOf { bottomBarSnapshot(state.value) } }.value

// ----------------------------------------------------------- mining card

data class MiningSnapshot(
    val visible: Boolean,
    val label: String,
    val progress: Float,
    val pctText: String,
)

fun miningSnapshot(state: GameState): MiningSnapshot {
    val w = state.worker
    if (w.action != WorkerAction.MINING || w.miningNodeIndex < 0) {
        return HiddenMining
    }
    val node = state.nodes.getOrNull(w.miningNodeIndex) ?: return HiddenMining
    val def = state.content.nodeType(node.typeId)
    val progress = (1f - node.hp / node.maxHp).coerceIn(0f, 1f)
    return MiningSnapshot(
        visible = true,
        label = "Mining ${def.name}",
        progress = progress,
        pctText = "${(progress * 100).toInt()}%",
    )
}

private val HiddenMining = MiningSnapshot(false, "", 0f, "")

@androidx.compose.runtime.Composable
fun rememberMiningSnapshot(state: State<GameState>): MiningSnapshot =
    remember { derivedStateOf { miningSnapshot(state.value) } }.value

// -------------------------------------------------------------- market rows

data class MarketRowData(
    val id: String,
    val name: String,
    val colorArgb: Long,
    val units: Int,
    val priceText: String,
    val pctText: String,
    /** null = flat; true = rising; false = falling. */
    val trendUp: Boolean?,
)

fun marketRows(state: GameState): List<MarketRowData> {
    // Quantized clock — rows refresh at 1 Hz while the sheet is open.
    val time = floor(state.marketTimeSec)
    return state.content.resourceOrder.map { id ->
        val res = state.content.resource(id)
        val price = EconomyRules.sellPricePerUnit(state.content, res, state.upgrades, time)
        val trend = EconomyRules.priceTrend(state.content, id, time)
        val pct = (EconomyRules.marketMultiplier(state.content, id, time) - 1.0) * 100.0
        val sign = if (pct >= 0) "+" else ""
        MarketRowData(
            id = id,
            name = res.name,
            colorArgb = com.idlemining.tycoon3d.core.content.ContentLoader.parseColor(res.color, "resource '${res.id}'"),
            units = state.inventory[id] ?: 0,
            priceText = formatMoney(price),
            pctText = "$sign${String.format(java.util.Locale.US, "%.0f", pct)}%",
            trendUp = when {
                trend > 0 -> true
                trend < 0 -> false
                else -> null
            },
        )
    }
}

@androidx.compose.runtime.Composable
fun rememberMarketRows(state: State<GameState>): List<MarketRowData> =
    remember { derivedStateOf { marketRows(state.value) } }.value
