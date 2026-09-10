package com.idleshaft.tycoon.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.idleshaft.tycoon.domain.GameIntent
import com.idleshaft.tycoon.domain.GameState

/**
 * Right-edge manual work buttons, visible only for stages that have no manager yet.
 * One tap = one work cycle (or a run-until-starved session for refinery / trucks).
 */
@Composable
fun WorkButtons(
    state: GameState,
    dispatch: (GameIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val showMine = state.shafts.any { it.unlocked && !it.managerHired }
    val showRefine = !state.refinery.managerHired
    val showSell = !state.logistics.managerHired
    if (!showMine && !showRefine && !showSell) return

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showMine) WorkButton(Icons.Filled.Construction, "DIG", GameIntent.TapMine, dispatch)
        if (showRefine) WorkButton(Icons.Filled.Factory, "REFINE", GameIntent.TapRefine, dispatch)
        if (showSell) WorkButton(Icons.Filled.LocalShipping, "SELL", GameIntent.TapDeliver, dispatch)
    }
}

@Composable
private fun WorkButton(
    icon: ImageVector,
    label: String,
    intent: GameIntent,
    dispatch: (GameIntent) -> Unit,
) {
    val pulse by rememberInfiniteTransition(label = "work-$label").animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "scale-$label",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .graphicsLayer {
                scaleX = pulse
                scaleY = pulse
            },
    ) {
        androidx.compose.material3.Surface(
            onClick = { dispatch(intent) },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(58.dp),
        ) {
            androidx.compose.foundation.layout.Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(58.dp),
            ) {
                Icon(icon, contentDescription = label, modifier = Modifier.size(26.dp))
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .padding(top = 4.dp)
                .background(
                    MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                    CircleShape,
                )
                .padding(horizontal = 10.dp, vertical = 2.dp),
        )
    }
}
