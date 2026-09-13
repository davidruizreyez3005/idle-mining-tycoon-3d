package com.idlemining.tycoon3d.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.idlemining.tycoon3d.core.content.GameContent
import com.idlemining.tycoon3d.core.economy.EconomyRules
import com.idlemining.tycoon3d.core.economy.formatMoney
import com.idlemining.tycoon3d.core.economy.formatRate
import com.idlemining.tycoon3d.game.GameState

/**
 * The upgrade shop — fully data-driven from upgrades.json. Every card shows the
 * current effect, the next-level effect and the cost.
 *
 * Cards are derived per displayed value (level, cost, affordability) so the
 * sheet recomposes only when money or upgrade levels actually change — not on
 * every 10 Hz simulation emission.
 */
@Composable
fun UpgradePanel(
    state: State<GameState>,
    onBuy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // No composition-time state reads — the derived lambda reads .value only
    // when the state actually changes, so this panel is recomposition-quiet.
    val cards by remember { derivedStateOf { upgradeCards(state.value) } }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 620.dp)
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
    ) {
        Text(
            text = "Upgrades",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(cards, key = { it.id }) { card ->
                UpgradeCard(card, onBuy = { onBuy(card.id) })
            }
        }
    }
}

/** Fully resolved display state for one upgrade card. */
private data class UpgradeCardData(
    val id: String,
    val name: String,
    val desc: String,
    val levelText: String,
    val effectText: String,
    val buyText: String,
    val affordable: Boolean,
)

private fun upgradeCards(state: GameState): List<UpgradeCardData> {
    val content = state.content
    return content.upgradeOrder.map { id ->
        val def = content.upgrade(id)
        val level = state.upgradeLevel(id)
        val maxed = EconomyRules.isMaxed(def, level)
        val cost = EconomyRules.upgradeCost(def, level)
        UpgradeCardData(
            id = id,
            name = def.name,
            desc = def.desc,
            levelText = if (maxed) "MAX" else "Lv $level / ${def.maxLevel}",
            effectText = effectSummary(def, level, content),
            buyText = if (maxed) "Fully upgraded" else "Buy - ${formatMoney(cost.toDouble())}",
            affordable = !maxed && state.money >= cost,
        )
    }
}

@Composable
private fun UpgradeCard(card: UpgradeCardData, onBuy: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = card.name,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = card.levelText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = card.desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            Text(
                text = card.effectText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 6.dp),
            )

            Spacer(Modifier.height(10.dp))

            Button(
                onClick = onBuy,
                enabled = card.affordable,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(
                    text = card.buyText,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** Human-readable current-effect line per upgrade type. */
private fun effectSummary(def: com.idlemining.tycoon3d.core.content.UpgradeDef, level: Int, content: GameContent): String {
    val per = def.effect.perLevel
    return when (def.effect.type) {
        "miningSpeed" -> "Current: +${(per * level * 100).toInt()}% mining speed"
        "moveSpeed" -> "Current: +${(per * level * 100).toInt()}% move speed"
        "backpack" -> "Current: +${(per * level).toInt()} capacity"
        "sellMargin" -> "Current: +${(per * level * 100).toInt()}% sell prices"
        "luckyStrike" -> "Current: +${(per * level * 100).toInt()}% double-loot chance"
        "offlineCap" -> "Current: +${(per * level).toInt()}h offline earnings cap"
        "idleExtraction" -> {
            val rates = EconomyRules.idleRatesPerSecond(content, level)
            if (rates.isEmpty()) {
                "Locked - buy level 1 to start passive mining"
            } else {
                rates.entries.joinToString("  ") { (id, rate) ->
                    "${formatRate(rate)} ${content.resource(id).name.lowercase()}"
                }
            }
        }
        else -> ""
    }
}
