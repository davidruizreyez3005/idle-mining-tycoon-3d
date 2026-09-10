package com.idleshaft.tycoon.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.idleshaft.tycoon.domain.Economy
import com.idleshaft.tycoon.domain.GameEvent
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.random.Random

/** A single floating popup ("+$12.00" or "+1 💎"). */
data class PopupItem(val id: Long, val text: String, val tint: Color)

private var popupId = 0L

/**
 * Screen-space cash popups anchored near the market area of the 3D view.
 * Each popup rises and fades over ~1.2s, then removes itself.
 */
@Composable
fun CashPopupsOverlay(
    events: kotlinx.coroutines.flow.SharedFlow<GameEvent>,
    modifier: Modifier = Modifier,
) {
    val items = remember { mutableStateListOf<PopupItem>() }

    LaunchedEffect(Unit) {
        events.collect { event ->
            when (event) {
                is GameEvent.CashPopup -> {
                    val amount = event.amount
                    val jitter = Random.nextFloat()
                    items.add(
                        PopupItem(
                            id = ++popupId,
                            text = "+" + Economy.money(amount),
                            tint = if (amount >= 100.0) Color(0xFFFFD766) else Color(0xFFB9F18D),
                        ),
                    )
                    if (items.size > 6) items.removeAt(0)
                    // Retire after the animation window.
                    val id = popupId
                    delay(1250)
                    items.removeAll { it.id == id }
                }
                is GameEvent.GemMilestone -> {
                    items.add(PopupItem(++popupId, "+1 GEM", Color(0xFF6FDCFF)))
                    val id = popupId
                    delay(1600)
                    items.removeAll { it.id == id }
                }
                else -> Unit
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        items.forEachIndexed { index, item ->
            key(item.id) {
                FloatingPopup(item, column = index)
            }
        }
    }
}

@Composable
private fun FloatingPopup(item: PopupItem, column: Int) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(item.id) {
        progress.animateTo(1f, tween(1200))
    }
    val rise = (progress.value * 90).dp
    val driftPx = ((column % 3) - 1) * 46
    AnimatedVisibility(
        visible = progress.value < 1f,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(300)),
    ) {
        Text(
            text = item.text,
            style = MaterialTheme.typography.titleMedium,
            color = item.tint,
            modifier = Modifier
                .offset { IntOffset(driftPx, -rise.roundToPx()) }
                .alpha(1f - progress.value * 0.85f),
        )
    }
}
