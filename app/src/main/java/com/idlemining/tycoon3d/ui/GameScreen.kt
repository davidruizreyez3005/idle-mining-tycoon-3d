package com.idlemining.tycoon3d.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
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
import com.idlemining.tycoon3d.ui.components.MarketPanel
import com.idlemining.tycoon3d.ui.components.MiningProgress
import com.idlemining.tycoon3d.ui.components.OfflineDialog
import com.idlemining.tycoon3d.ui.components.PopupEntry
import com.idlemining.tycoon3d.ui.components.PopupsOverlay
import com.idlemining.tycoon3d.ui.components.TopHud
import com.idlemining.tycoon3d.ui.components.UpgradePanel
import com.idlemining.tycoon3d.ui.theme.MoneyGreen
import com.idlemining.tycoon3d.ui.theme.WarnRed
import com.idlemining.tycoon3d.viewmodel.GameViewModel

/**
 * The whole game screen: the 3D Filament scene renders full-bleed underneath a
 * Compose HUD (top stats, bottom action bar, mining progress, floating popups)
 * and a modal bottom-sheet upgrade panel that slides up without interrupting
 * the render loop.
 *
 * **Recomposition budget (Phase 3.5).** This screen holds the live state only
 * as a [State] *holder* — `gameState.value` is never read directly in this
 * body. Everything displayed derives either inside the leaf components
 * (see `ui/components/HudSnapshots.kt`) or behind a rare-change
 * `derivedStateOf` gate below, so a screen-level recomposition happens on
 * player actions and at ~1 Hz for market-driven text — never at the
 * simulation's 10 Hz. The 3D scene itself takes the immutable content object
 * and composes exactly once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(viewModel: GameViewModel) {
    val gameState: State<com.idlemining.tycoon3d.game.GameState> =
        viewModel.state.collectAsStateWithLifecycle()
    val content = viewModel.content
    var showUpgrades by remember { mutableStateOf(false) }
    var showMarket by remember { mutableStateOf(false) }

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
                    val name = content.upgrade(event.id)?.name ?: event.id
                    pushPopup("$name - level ${event.level}", PopupKind.MONEY)
                }

                is GameEvent.BackpackFull -> Unit // already surfaced as a WARN popup
            }
        }
    }

    // ── Rare-change gates: each derived value flips only when its underlying
    //    condition actually changes, so the screen body stays recomposition-quiet.
    val offlineGate by remember {
        derivedStateOf { gameState.value.offlineReport?.let { it to gameState.value.upgrades } }
    }
    val hintShown by remember {
        derivedStateOf { gameState.value.flags["hint_shown"] == true }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // 3D world — composes once; animation lives in the frame callback.
        GameScene(
            content = content,
            gameState = gameState,
            dispatch = { intent -> dispatchLatest.value(intent) },
            modifier = Modifier.fillMaxSize(),
        )

        TopHud(state = gameState, modifier = Modifier.align(Alignment.TopCenter))

        PopupsOverlay(
            popups = popups,
            onExpire = { entry -> popups.removeAll { it.id == entry.id } },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        MiningProgress(
            state = gameState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        BottomBar(
            state = gameState,
            onSell = { dispatchLatest.value(GameIntent.TapDepot) },
            onMarket = { showMarket = true },
            onUpgrades = { showUpgrades = true },
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        offlineGate?.let { (report, upgrades) ->
            OfflineDialog(
                report = report,
                content = content,
                upgrades = upgrades,
                onCollect = { dispatchLatest.value(GameIntent.DismissOffline) },
            )
        }

        if (!hintShown) {
            HintOverlay(
                zoneName = content.world.name,
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
                    state = gameState,
                    onBuy = { id -> dispatchLatest.value(GameIntent.BuyUpgrade(id)) },
                )
            }
        }

        if (showMarket) {
            ModalBottomSheet(
                onDismissRequest = { showMarket = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = MaterialTheme.colorScheme.background,
            ) {
                MarketPanel(state = gameState)
            }
        }
    }
}

// Re-exported palette aliases used by popups.
internal val moneyColor = MoneyGreen
internal val warnColor = WarnRed
