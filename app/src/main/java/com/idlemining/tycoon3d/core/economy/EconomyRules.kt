package com.idlemining.tycoon3d.core.economy

import com.idlemining.tycoon3d.core.content.GameContent
import com.idlemining.tycoon3d.core.content.ResourceDef
import com.idlemining.tycoon3d.core.content.UpgradeDef
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pure economy math — every formula in one place, all of it unit-tested.
 * Nothing here touches Android, state mutation or the renderer.
 */
object EconomyRules {

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

    fun sellPricePerUnit(content: GameContent, resource: ResourceDef, upgrades: Map<String, Int>): Double {
        val def = content.upgrade("market")
        val lvl = level(upgrades, "market")
        val margin = 1.0 + def.effect.perLevel * lvl
        return resource.baseValue * margin
    }

    /** Total value of an inventory payload at current market level. */
    fun inventoryValue(content: GameContent, inventory: Map<String, Int>, upgrades: Map<String, Int>): Double {
        return inventory.entries.sumOf { (id, count) ->
            if (count <= 0) 0.0 else sellPricePerUnit(content, content.resource(id), upgrades) * count
        }
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
     * `offlineCapHours`; a level-0 extractor earns nothing.
     */
    fun offlineEarnings(
        content: GameContent,
        extractorLevel: Int,
        elapsedSeconds: Long,
    ): OfflineEarnings {
        val capSeconds = content.economy.idle.offlineCapHours * 3600L
        val effective = elapsedSeconds.coerceIn(0L, capSeconds)
        val rates = idleRatesPerSecond(content, extractorLevel)
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
