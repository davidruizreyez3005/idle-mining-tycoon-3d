package com.idleshaft.tycoon.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.idleshaft.tycoon.domain.Economy
import com.idleshaft.tycoon.domain.GameState
import androidx.compose.foundation.layout.Box

/**
 * The top HUD: cash balance, gem count, net income rate and (while active) the
 * 2x boost countdown. Rendered as translucent chips floating over the 3D scene.
 */
@Composable
fun TopHudBar(
    state: GameState,
    incomePerSecond: Double,
    boostSecondsRemaining: Long,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HudChip(
                icon = { Icon(Icons.Filled.Paid, null, tint = Color(0xFF9BE564)) },
                value = Economy.money(state.cash),
                label = "Cash",
                emphasized = true,
            )
            HudChip(
                icon = { Icon(Icons.Filled.Diamond, null, tint = Color(0xFF6FDCFF)) },
                value = state.gems.toString(),
                label = "Gems",
            )
            HudChip(
                icon = null,
                value = Economy.rate(incomePerSecond),
                label = "Auto income",
            )
        }
        if (boostSecondsRemaining > 0) {
            val pulse by rememberInfiniteTransition(label = "boost").animateFloat(
                initialValue = 0.75f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
                label = "boost-alpha",
            )
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f * pulse + 0.35f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Filled.Bolt, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                    Text(
                        "2x income — ${boostSecondsRemaining}s",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun HudChip(
    icon: (@Composable () -> Unit)?,
    value: String,
    label: String,
    emphasized: Boolean = false,
) {
    val bg by animateColorAsState(
        targetValue = if (emphasized) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        label = "chip-bg",
    )
    Surface(shape = RoundedCornerShape(16.dp), color = bg) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { icon() }
            }
            Column {
                Text(
                    value,
                    style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
                    color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Soft dark gradient behind the top HUD so it reads over bright sky pixels. */
@Composable
fun TopScrim(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background.copy(alpha = 0.75f),
                        Color.Transparent,
                    ),
                ),
            ),
    )
}
