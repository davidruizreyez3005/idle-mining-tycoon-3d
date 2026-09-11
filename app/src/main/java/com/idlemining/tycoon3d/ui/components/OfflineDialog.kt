package com.idlemining.tycoon3d.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.idlemining.tycoon3d.core.content.ContentLoader
import com.idlemining.tycoon3d.core.content.GameContent
import com.idlemining.tycoon3d.core.economy.formatDuration
import com.idlemining.tycoon3d.core.economy.formatMoney
import com.idlemining.tycoon3d.game.OfflineReport

/**
 * "While you were away..." — the idle-game welcome-back moment. Reports what
 * the Auto-Extractor produced while the game was closed (after the cap).
 */
@Composable
fun OfflineDialog(
    report: OfflineReport,
    content: GameContent,
    upgrades: Map<String, Int>,
    onCollect: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCollect,
        title = { Text("While you were away...") },
        text = {
            Column {
                Text(
                    text = "The Auto-Extractor kept working for ${formatDuration(report.awaySeconds)}.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                androidx.compose.foundation.layout.Spacer(Modifier.padding(6.dp))
                report.resources.forEach { (id, count) ->
                    val res = content.resource(id)
                    val dot = Color(ContentLoader.parseColor(res.color, "resource").toInt())
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(shape = CircleShape, color = dot, modifier = Modifier.padding(end = 10.dp)) {
                            androidx.compose.foundation.layout.Box(Modifier.padding(7.dp))
                        }
                        Text(
                            text = "$count ${res.name}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                androidx.compose.foundation.layout.Spacer(Modifier.padding(6.dp))
                Text(
                    text = "Total value: ${formatMoney(report.totalValue)}",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onCollect) {
                Text("COLLECT", fontWeight = FontWeight.Bold)
            }
        },
    )
}
