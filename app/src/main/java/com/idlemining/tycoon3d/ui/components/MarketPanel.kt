package com.idlemining.tycoon3d.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.heightIn
import com.idlemining.tycoon3d.game.GameState
import com.idlemining.tycoon3d.ui.theme.MoneyGreen
import com.idlemining.tycoon3d.ui.theme.WarnRed

/**
 * Phase 3 market sheet — live prices per resource. Every resource rides its
 * own slow sine wave ([com.idlemining.tycoon3d.core.economy.EconomyRules.marketMultiplier]),
 * so the right time to sell becomes a real decision: hold a full backpack
 * while the arrow points up, dump it when your resource peaks.
 *
 * Rows come from a 1 Hz-quantized derived snapshot ([rememberMarketRows]) —
 * the sheet recomposes once per second while open instead of ten times.
 */
@Composable
fun MarketPanel(
    state: State<GameState>,
    modifier: Modifier = Modifier,
) {
    val rows = rememberMarketRows(state)

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
            items(rows, key = { it.id }) { row ->
                MarketRow(row)
            }
        }
    }
}

@Composable
private fun MarketRow(row: MarketRowData) {
    val dot = Color(row.colorArgb.toInt())

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
                    text = row.name,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = if (row.units > 0) "holding ${row.units}" else "not held",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (row.units > 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val trendColor = when (row.trendUp) {
                true -> MoneyGreen
                false -> WarnRed
                null -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            val trendSymbol = when (row.trendUp) {
                true -> "▲"
                false -> "▼"
                null -> "—"
            }
            Text(
                text = "$trendSymbol ${row.pctText}",
                color = trendColor,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(end = 14.dp),
            )

            Text(
                text = row.priceText,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
