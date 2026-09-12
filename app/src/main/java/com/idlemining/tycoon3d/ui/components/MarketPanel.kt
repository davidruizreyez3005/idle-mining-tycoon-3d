package com.idlemining.tycoon3d.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.idlemining.tycoon3d.core.content.ContentLoader
import com.idlemining.tycoon3d.core.economy.EconomyRules
import com.idlemining.tycoon3d.core.economy.formatMoney
import com.idlemining.tycoon3d.game.GameState
import com.idlemining.tycoon3d.ui.theme.MoneyGreen
import com.idlemining.tycoon3d.ui.theme.WarnRed

/**
 * Phase 3 market sheet — live prices per resource. Every resource rides its
 * own slow sine wave ([EconomyRules.marketMultiplier]), so the right time to
 * sell becomes a real decision: hold a full backpack while the arrow points
 * up, dump it when your resource peaks. Rows update on every state emission
 * (10 Hz) straight from the simulation's market clock.
 */
@Composable
fun MarketPanel(
    state: GameState,
    modifier: Modifier = Modifier,
) {
    val time = state.marketTimeSec

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 620.dp)
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Market",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "prices drift live — sell on the upswing",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.content.resourceOrder) { id ->
                MarketRow(state = state, resourceId = id, time = time)
            }
        }
    }
}

@Composable
private fun MarketRow(state: GameState, resourceId: String, time: Double) {
    val res = state.content.resource(resourceId)
    val dot = Color(ContentLoader.parseColor(res.color, "resource").toInt())
    val price = EconomyRules.sellPricePerUnit(state.content, res, state.upgrades, time)
    val trend = EconomyRules.priceTrend(state.content, resourceId, time)
    val pct = (EconomyRules.marketMultiplier(state.content, resourceId, time) - 1.0) * 100.0

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = CircleShape, color = dot) {
                Box(Modifier.padding(8.dp))
            }

            Column(
                modifier = Modifier.padding(start = 12.dp).weight(1f),
            ) {
                Text(
                    text = res.name,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
                val units = state.inventory[resourceId] ?: 0
                Text(
                    text = if (units > 0) "holding $units" else "not held",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (units > 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val trendColor = when {
                trend > 0 -> MoneyGreen
                trend < 0 -> WarnRed
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            val trendSymbol = when {
                trend > 0 -> "▲"
                trend < 0 -> "▼"
                else -> "—"
            }
            val sign = if (pct >= 0) "+" else ""
            Text(
                text = "$trendSymbol $sign${String.format(java.util.Locale.US, "%.0f", pct)}%",
                color = trendColor,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(end = 14.dp),
            )

            Text(
                text = formatMoney(price),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
