package com.idlemining.tycoon3d

import com.idlemining.tycoon3d.core.content.ContentLoader
import com.idlemining.tycoon3d.core.economy.EconomyRules
import com.idlemining.tycoon3d.core.economy.formatDuration
import com.idlemining.tycoon3d.core.economy.formatMoney
import com.idlemining.tycoon3d.core.economy.formatRate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
class EconomyRulesTest {

    private val content = TestContent.build()

    @Test
    fun `upgrade cost follows the growth curve`() {
        val pickaxe = content.upgrade("pickaxe")
        assertEquals(60L, EconomyRules.upgradeCost(pickaxe, 0))
        // 60 * 1.9 = 114
        assertEquals(114L, EconomyRules.upgradeCost(pickaxe, 1))
        // 60 * 1.9^2 = 216.6 -> 217
        assertEquals(217L, EconomyRules.upgradeCost(pickaxe, 2))
    }

    @Test
    fun `maxed upgrades are detected`() {
        val def = content.upgrade("pickaxe")
        assertEquals(10, def.maxLevel)
        assertTrue(EconomyRules.isMaxed(def, 10))
        assertTrue(!EconomyRules.isMaxed(def, 9))
    }

    @Test
    fun `sell margin applies per market level`() {
        val stone = content.resource("stone")
        // Stone is index 0 (phase 0) — at t=0 the market multiplier is exactly 1.
        assertEquals(2.0, EconomyRules.sellPricePerUnit(content, stone, emptyMap(), 0.0), 1e-9)
        val withMarket = mapOf("market" to 2) // +30%
        assertEquals(2.6, EconomyRules.sellPricePerUnit(content, stone, withMarket, 0.0), 1e-9)
    }

    @Test
    fun `inventory value sums across resources at the live market`() {
        val inv = mapOf("stone" to 3, "gold" to 1)
        val expected = 3 * EconomyRules.sellPricePerUnit(content, content.resource("stone"), emptyMap(), 0.0) +
                1 * EconomyRules.sellPricePerUnit(content, content.resource("gold"), emptyMap(), 0.0)
        assertEquals(expected, EconomyRules.inventoryValue(content, inv, emptyMap(), 0.0), 1e-9)
    }

    // ------------------------------------------------------ phase 3: market

    @Test
    fun `market multiplier stays within the amplitude bounds`() {
        val amp = content.economy.market.amplitude.toDouble()
        for (res in content.resourceOrder) {
            for (t in 0..1000 step 7) {
                val m = EconomyRules.marketMultiplier(content, res, t.toDouble())
                assertTrue("multiplier $m out of bounds for $res", m >= 1.0 - amp - 1e-9)
                assertTrue("multiplier $m out of bounds for $res", m <= 1.0 + amp + 1e-9)
            }
        }
    }

    @Test
    fun `market multiplier hits authored peaks deterministically`() {
        // Stone is index 0: phase 0, period 300s. Quarter period = peak.
        // (1e-6 tolerance: amplitude is authored as a Float.)
        assertEquals(1.0, EconomyRules.marketMultiplier(content, "stone", 0.0), 1e-9)
        assertEquals(1.22, EconomyRules.marketMultiplier(content, "stone", 75.0), 1e-6)
        assertEquals(0.78, EconomyRules.marketMultiplier(content, "stone", 225.0), 1e-6)
    }

    @Test
    fun `resource waves are out of sync`() {
        // When stone peaks, at least one other resource must not be at its peak.
        val t = 75.0
        val values = content.resourceOrder.map { EconomyRules.marketMultiplier(content, it, t) }
        assertTrue("all resources synchronized at t=$t", values.toSet().size > 1)
    }

    @Test
    fun `price trend points along the wave`() {
        // Stone (period 300s, phase 0): rising into the t=75 peak, falling after.
        assertEquals(1, EconomyRules.priceTrend(content, "stone", 75.0))
        assertEquals(-1, EconomyRules.priceTrend(content, "stone", 225.0))
    }

    // ------------------------------------------------- phase 3: abilities

    @Test
    fun `lucky strike chance scales with level and caps at one`() {
        assertEquals(0.0, EconomyRules.luckyStrikeChance(content, emptyMap()), 1e-9)
        assertEquals(0.06, EconomyRules.luckyStrikeChance(content, mapOf("lucky" to 1)), 1e-9)
        assertEquals(0.48, EconomyRules.luckyStrikeChance(content, mapOf("lucky" to 8)), 1e-9)
    }

    @Test
    fun `lucky and warehouse are optional in older content`() {
        val minimal = ContentLoader.load(TestContent.bundleOverrides())
        assertEquals(0.0, EconomyRules.luckyStrikeChance(minimal, mapOf("lucky" to 5)), 1e-9)
        assertEquals(4.0, EconomyRules.offlineCapHours(minimal, mapOf("warehouse" to 5)), 1e-9)
    }

    @Test
    fun `warehouse extends the offline cap`() {
        assertEquals(4.0, EconomyRules.offlineCapHours(content, emptyMap()), 1e-9)
        assertEquals(10.0, EconomyRules.offlineCapHours(content, mapOf("warehouse" to 3)), 1e-9)
    }

    @Test
    fun `backpack capacity grows with upgrades`() {
        assertEquals(12, EconomyRules.backpackCapacity(content, emptyMap()))
        assertEquals(18, EconomyRules.backpackCapacity(content, mapOf("backpack" to 1)))
        assertEquals(72, EconomyRules.backpackCapacity(content, mapOf("backpack" to 10)))
    }

    @Test
    fun `worker speed and damage scale with upgrades`() {
        assertEquals(2.4f, EconomyRules.moveSpeedPerSecond(content, emptyMap()), 1e-6f)
        // boots +18%/level: 2.4 * 1.18 = 2.832
        assertEquals(2.832f, EconomyRules.moveSpeedPerSecond(content, mapOf("boots" to 1)), 1e-4f)

        assertEquals(22f, EconomyRules.mineDamagePerSecond(content, emptyMap()), 1e-6f)
        // pickaxe +35%: 22 * 1.35 = 29.7
        assertEquals(29.7f, EconomyRules.mineDamagePerSecond(content, mapOf("pickaxe" to 1)), 1e-4f)
    }

    @Test
    fun `idle rates unlock by level`() {
        assertTrue(EconomyRules.idleRatesPerSecond(content, 0).isEmpty())

        val level1 = EconomyRules.idleRatesPerSecond(content, 1)
        assertEquals(0.4, level1["stone"]!!, 1e-9)
        assertTrue(!level1.containsKey("coal"))

        val level4 = EconomyRules.idleRatesPerSecond(content, 4)
        assertEquals(1.6, level4["stone"]!!, 1e-9)
        assertEquals(0.6, level4["coal"]!!, 1e-9)
    }

    @Test
    fun `offline earnings are capped at configured hours`() {
        // 0.4/s * 4h (cap) = 5760 stone
        val capped = EconomyRules.offlineEarnings(content, mapOf("extractor" to 1), 100 * 3600L)
        assertEquals(5760, capped.resources["stone"])
        assertEquals(4 * 3600L, capped.effectiveSeconds)

        // 1 hour away: 0.4 * 3600 = 1440
        val oneHour = EconomyRules.offlineEarnings(content, mapOf("extractor" to 1), 3600L)
        assertEquals(1440, oneHour.resources["stone"])
        assertEquals(3600L, oneHour.effectiveSeconds)

        // No extractor: nothing.
        assertTrue(EconomyRules.offlineEarnings(content, emptyMap<String, Int>(), 3600L).resources.isEmpty())
    }

    @Test
    fun `warehouse lifts the offline earnings cap`() {
        // 4h base + 2h per level -> 6h cap: a 5h absence now fully counts.
        val lifted = EconomyRules.offlineEarnings(
            content, mapOf("extractor" to 1, "warehouse" to 1), 5 * 3600L,
        )
        assertEquals(5 * 3600L, lifted.effectiveSeconds)
        assertEquals((0.4 * 5 * 3600).toInt(), lifted.resources["stone"])
    }

    @Test
    fun `money formatting groups thousands`() {
        assertEquals("$0", formatMoney(0.0))
        assertEquals("$51", formatMoney(51.0))
        assertEquals("$1,234", formatMoney(1234.0))
        assertEquals("$1,234,567", formatMoney(1234567.0))
    }

    @Test
    fun `duration formatting is compact`() {
        assertEquals("45s", formatDuration(45))
        assertEquals("3m 12s", formatDuration(192))
        assertEquals("2h 5m", formatDuration(7500))
    }

    @Test
    fun `rate formatting keeps one decimal`() {
        assertEquals("0.4/s", formatRate(0.4))
        assertEquals("12.5/s", formatRate(12.5))
    }
}
