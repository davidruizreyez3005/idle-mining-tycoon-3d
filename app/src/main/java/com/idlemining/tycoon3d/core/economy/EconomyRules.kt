package com.idlemining.tycoon3d.core.economy

import com.idlemining.tycoon3d.core.content.GameContent
import com.idlemining.tycoon3d.core.content.MarketTuning
import com.idlemining.tycoon3d.core.content.ResourceDef
import com.idlemining.tycoon3d.core.content.UpgradeDef
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Pure economy math — every formula in one place, all of it unit-tested.
 * Nothing here touches Android, state mutation or the renderer.
 */
object EconomyRules {

    private const val TAU = 2.0 * Math.PI
    /** Golden-angle phase spread (radians) — keeps resource waves out of sync. */
    private const val GOLDEN_ANGLE = 2.399963229728653

    // ------------------------------------------------------------- upgrades

    /** Cost of buying level `currentLevel + 1`. */
    fun upgradeCost(def: UpgradeDef, currentLevel: Int): Long {
        val factor = Math.pow(def.costGrowth.toDouble(), currentLevel.toDouble())
        return (def.baseCost * factor).roundToInt().toLong()
    }

    fun isMaxed(def: UpgradeDef, currentLevel: Int): Boolean = currentLevel >= def.maxLevel

    /** Level keyed by upgrade id — the single source of upgrade state. */
    fun level(upgrades: Map<String, Int>, id: String): Int = upgrades[id] ?: 0

    // ------------------------------------------------------------ worker

    fun moveSpeedPerSecond(content: GameContent, upgrades: Map<String, Int>): Float {
        val def = content.upgrade("boots")
        val lvl = level(upgrades, "boots")
        val mult = (1.0 + def.effect.perLevel * lvl).toFloat()
        return content.economy.worker.moveSpeed * mult
    }

    fun mineDamagePerSecond(content: GameContent, upgrades: Map<String, Int>): Float {
        val def = content.upgrade("pickaxe")
        val lvl = level(upgrades, "pickaxe")
        val mult = (1.0 + def.effect.perLevel * lvl).toFloat()
        return content.economy.worker.mineDps * mult
    }

    fun backpackCapacity(content: GameContent, upgrades: Map<String, Int>): Int {
        val def = content.upgrade("backpack")
        val lvl = level(upgrades, "backpack")
        return content.economy.start.backpack + (def.effect.perLevel * lvl).toInt()
    }

    // ------------------------------------------------------------- selling

    /**
     * Phase 3 market: the live multiplier applied to a resource's base price.
     *
     * Each resource rides a slow sine wave whose period and phase are derived
     * deterministically from its index in `resourceOrder` (golden-angle phase
     * spread), so the waves never sync — there is always a best and a worst
     * resource to sell at any moment. Amplitude and periods come from
     * economy.json ([MarketTuning]).
     */
    fun marketMultiplier(content: GameContent, resourceId: String, timeSec: Double): Double {
        val m = content.economy.market
        if (m.amplitude <= 0f) return 1.0
        val order = content.resourceOrder
        val index = order.indexOf(resourceId).coerceAtLeast(0)
        val spread = if (order.size > 1) index.toDouble() / (order.size - 1) else 0.0
        val period = (m.basePeriodSec + m.periodSpreadSec * spread).toDouble()
        val phase = index * GOLDEN_ANGLE
        return 1.0 + m.amplitude * sin(TAU * timeSec / period + phase)
    }

    /**
     * Price of one unit right now: base value x Trade-Contracts margin x live
     * market multiplier. `timeSec` is the market clock (GameState.marketTimeSec).
     */
    fun sellPricePerUnit(
        content: GameContent,
        resource: ResourceDef,
        upgrades: Map<String, Int>,
        timeSec: Double = 0.0,
    ): Double {
        val def = content.upgrade("market")
        val lvl = level(upgrades, "market")
        val margin = 1.0 + def.effect.perLevel * lvl
        return resource.baseValue * margin * marketMultiplier(content, resource.id, timeSec)
    }

    /** Total value of an inventory payload at current market level and time. */
    fun inventoryValue(
        content: GameContent,
        inventory: Map<String, Int>,
        upgrades: Map<String, Int>,
        timeSec: Double = 0.0,
    ): Double {
        return inventory.entries.sumOf { (id, count) ->
            if (count <= 0) 0.0 else sellPricePerUnit(content, content.resource(id), upgrades, timeSec) * count
        }
    }

    /**
     * Trend of the live price over the look-back window: +1 rising, -1 falling,
     * 0 flat (within a small epsilon). Drives the arrows in the market panel.
     */
    fun priceTrend(content: GameContent, resourceId: String, timeSec: Double): Int {
        val window = content.economy.market.trendWindowSec.toDouble()
        val now = marketMultiplier(content, resourceId, timeSec)
        val before = marketMultiplier(content, resourceId, timeSec - window)
        val delta = now - before
        return when {
            delta > 1e-4 -> 1
            delta < -1e-4 -> -1
            else -> 0
        }
    }

    // ----------------------------------------------------- Phase 3 abilities

    /**
     * Chance that a broken vein drops double loot ("Lucky Strikes"). Safe map
     * access — older content without the upgrade simply yields 0 chance.
     */
    fun luckyStrikeChance(content: GameContent, upgrades: Map<String, Int>): Double {
        val def = content.upgrades["lucky"] ?: return 0.0
        val lvl = level(upgrades, "lucky")
        return (def.effect.perLevel * lvl).coerceIn(0.0, 1.0)
    }

    /**
     * Offline cap in hours: base from economy.json plus 2h per Warehouse level.
     * Safe map access — older content without the upgrade uses the base cap.
     */
    fun offlineCapHours(content: GameContent, upgrades: Map<String, Int>): Double {
        val base = content.economy.idle.offlineCapHours.toDouble()
        val def = content.upgrades["warehouse"] ?: return base
        val lvl = level(upgrades, "warehouse")
        return base + def.effect.perLevel * lvl
    }

    // ------------------------------------------------------------- idle

    /**
     * Passive production per second, per resource, at the given extractor level.
     * Each rate only counts once the level reaches its `unlockLevel`.
     */
    fun idleRatesPerSecond(content: GameContent, extractorLevel: Int): Map<String, Double> {
        if (extractorLevel <= 0) return emptyMap()
        val def = content.upgrade("extractor")
        val rates = mutableMapOf<String, Double>()
        for (rate in def.effect.rates) {
            if (extractorLevel >= rate.unlockLevel) {
                rates[rate.resource] = (rates[rate.resource] ?: 0.0) + rate.perLevel * extractorLevel
            }
        }
        return rates
    }

    // ---------------------------------------------------------- offline

    /**
     * Resources earned while the game was closed. Elapsed time is capped at
     * the Warehouse-extended offline cap; a level-0 extractor earns nothing.
     */
    fun offlineEarnings(
        content: GameContent,
        upgrades: Map<String, Int>,
        elapsedSeconds: Long,
    ): OfflineEarnings {
        val capSeconds = (offlineCapHours(content, upgrades) * 3600.0).toLong()
        val effective = elapsedSeconds.coerceIn(0L, capSeconds)
        val rates = idleRatesPerSecond(content, level(upgrades, "extractor"))
        if (rates.isEmpty() || effective <= 0L) return OfflineEarnings(0L, emptyMap())

        val earned = rates.mapValues { (_, perSecond) ->
            floor(perSecond * effective).toInt()
        }.filterValues { it > 0 }
        return OfflineEarnings(effective, earned)
    }
}

/** Result of the away-time calculation. */
data class OfflineEarnings(
    /** Seconds of away time that actually counted (after the cap). */
    val effectiveSeconds: Long,
    /** resource id -> units earned. */
    val resources: Map<String, Int>,
)

/** Formats money the way the HUD shows it: $1,234. */
fun formatMoney(value: Double): String {
    val whole = value.toLong()
    return "$${formatThousands(whole)}"
}

fun formatThousands(value: Long): String {
    val s = value.toString()
    return buildString {
        var counter = 0
        for (i in s.indices.reversed()) {
            append(s[i])
            counter++
            if (counter % 3 == 0 && i > 0) append(',')
        }
    }.reversed()
}

/** Compact duration for the offline dialog: "3h 12m", "45s". */
fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${s}s"
        else -> "${s}s"
    }
}

/** Short resource/sec label, e.g. "1.2/s". */
fun formatRate(perSecond: Double): String =
    String.format(java.util.Locale.US, "%.1f/s", perSecond)

/** Caps an addition of [want] units so [current] never exceeds [cap]. */
fun capacityClamp(current: Int, want: Int, cap: Int): Int =
    min(want, (cap - current).coerceAtLeast(0))
