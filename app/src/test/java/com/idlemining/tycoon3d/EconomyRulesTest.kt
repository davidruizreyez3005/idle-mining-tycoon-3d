package com.idlemining.tycoon3d

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
        assertEquals(2.0, EconomyRules.sellPricePerUnit(content, stone, emptyMap()), 1e-9)
        val withMarket = mapOf("market" to 2) // +30%
        assertEquals(2.6, EconomyRules.sellPricePerUnit(content, stone, withMarket), 1e-9)
    }

    @Test
    fun `inventory value sums across resources`() {
        val inv = mapOf("stone" to 3, "gold" to 1)
        // 3*2 + 1*45 = 51
        assertEquals(51.0, EconomyRules.inventoryValue(content, inv, emptyMap()), 1e-9)
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
        val capped = EconomyRules.offlineEarnings(content, 1, 100 * 3600L)
        assertEquals(5760, capped.resources["stone"])
        assertEquals(4 * 3600L, capped.effectiveSeconds)

        // 1 hour away: 0.4 * 3600 = 1440
        val oneHour = EconomyRules.offlineEarnings(content, 1, 3600L)
        assertEquals(1440, oneHour.resources["stone"])
        assertEquals(3600L, oneHour.effectiveSeconds)

        // No extractor: nothing.
        assertTrue(EconomyRules.offlineEarnings(content, 0, 3600L).resources.isEmpty())
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
