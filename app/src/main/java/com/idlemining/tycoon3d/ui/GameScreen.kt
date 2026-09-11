package com.idlemining.tycoon3d.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.idlemining.tycoon3d.core.economy.formatMoney
import com.idlemining.tycoon3d.game.GameEvent
import com.idlemining.tycoon3d.game.GameIntent
import com.idlemining.tycoon3d.game.PopupKind
import com.idlemining.tycoon3d.game.scene.GameScene
import com.idlemining.tycoon3d.ui.components.BottomBar
import com.idlemining.tycoon3d.ui.components.HintOverlay
import com.idlemining.tycoon3d.ui.components.MiningProgress
import com.idlemining.tycoon3d.ui.components.OfflineDialog
import com.idlemining.tycoon3d.ui.components.PopupEntry
import com.idlemining.tycoon3d.ui.components.PopupsOverlay
import com.idlemining.tycoon3d.ui.components.TopHud
import com.idlemining.tycoon3d.ui.components.UpgradePanel
import com.idlemining.tycoon3d.ui.theme.MoneyGreen
import com.idlemining.tycoon3d.ui.theme.WarnRed
import com.idlemining.tycoon3d.viewmodel.GameViewModel
import kotlinx.coroutines.delay

/**
 * The whole game screen: the 3D Filament scene renders full-bleed underneath a
 * Compose HUD (top stats, bottom action bar, mining progress, floating popups)
 * and a modal bottom-sheet upgrade panel that slides up without interrupting
 * the render loop.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(viewModel: GameViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val gameStateForScene = rememberUpdatedState(state)
    var showUpgrades by remember { mutableStateOf(false) }

    val popups = remember { mutableStateListOf<PopupEntry>() }
    val dispatchLatest = rememberUpdatedState(viewModel::dispatch)
    var nextPopupId by remember { mutableStateOf(0) }

    fun pushPopup(text: String, kind: PopupKind) {
        popups.add(PopupEntry(nextPopupId, text, kind))
        if (popups.size > 4) popups.removeAt(0)
        nextPopupId++
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is GameEvent.Sold ->
                    pushPopup("Sold ${event.units} units for +${formatMoney(event.amount)}", PopupKind.MONEY)

                is GameEvent.NodeBroken -> {
                    val summary = event.yields.entries.joinToString(" + ") { "${it.value} ${it.key}" }
                    if (summary.isNotBlank()) pushPopup(summary, PopupKind.INFO)
                }

                is GameEvent.Popup -> pushPopup(event.text, event.kind)

                is GameEvent.UpgradeBought -> {
                    val name = state.content.upgrade(event.id)?.name ?: event.id
                    pushPopup("$name - level $event.level", PopupKind.MONEY)
                }

                is GameEvent.BackpackFull -> Unit // already surfaced as a WARN popup
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // 3D world (never recomposes during play).
        GameScene(
            gameState = gameStateForScene,
            dispatch = { intent -> dispatchLatest.value(intent) },
            modifier = Modifier.fillMaxSize(),
        )

        TopHud(state = state, modifier = Modifier.align(Alignment.TopCenter))

        PopupsOverlay(
            popups = popups,
            onExpire = { entry -> popups.removeAll { it.id == entry.id } },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        MiningProgress(
            state = state,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        BottomBar(
            state = state,
            onSell = { dispatchLatest.value(GameIntent.TapDepot) },
            onUpgrades = { showUpgrades = true },
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        state.offlineReport?.let { report ->
            OfflineDialog(
                report = report,
                content = state.content,
                upgrades = state.upgrades,
                onCollect = { dispatchLatest.value(GameIntent.DismissOffline) },
            )
        }

        if (state.flags["hint_shown"] != true) {
            HintOverlay(
                zoneName = state.content.world.name,
                onGotIt = { dispatchLatest.value(GameIntent.MarkHintShown) },
            )
        }

        if (showUpgrades) {
            ModalBottomSheet(
                onDismissRequest = { showUpgrades = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = MaterialTheme.colorScheme.background,
            ) {
                UpgradePanel(
                    state = state,
                    onBuy = { id -> dispatchLatest.value(GameIntent.BuyUpgrade(id)) },
                )
            }
        }
    }
}

// Re-exported palette aliases used by popups.
internal val moneyColor = MoneyGreen
internal val warnColor = WarnRed
