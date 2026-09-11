package com.idleshaft.tycoon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.idleshaft.tycoon.domain.Economy
import com.idleshaft.tycoon.domain.GameState
import com.idleshaft.tycoon.game.threed.MiningScene
import com.idleshaft.tycoon.ui.components.BottomActionBar
import com.idleshaft.tycoon.ui.components.CashPopupsOverlay
import com.idleshaft.tycoon.ui.components.GamePanel
import com.idleshaft.tycoon.ui.components.OfflineEarningsDialog
import com.idleshaft.tycoon.ui.components.TopHudBar
import com.idleshaft.tycoon.ui.components.TopScrim
import com.idleshaft.tycoon.ui.components.WorkButtons
import com.idleshaft.tycoon.ui.panels.ManagersPanel
import com.idleshaft.tycoon.ui.panels.RefineryPanel
import com.idleshaft.tycoon.ui.panels.ShaftsPanel
import com.idleshaft.tycoon.ui.panels.UpgradesPanel
import com.idleshaft.tycoon.viewmodel.GameViewModel
import kotlinx.coroutines.delay

/**
 * The whole game screen: the 3D Filament scene renders full-bleed underneath a Compose
 * HUD (top stats, manual work buttons, bottom navigation) and modal bottom-sheet panels
 * that slide up without interrupting the render loop.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(viewModel: GameViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var activePanel by remember { mutableStateOf<GamePanel?>(null) }

    // Half-second clock so the boost countdown ticks smoothly.
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.boostUntilMs) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(500)
        }
    }

    val incomePerSecond = Economy.incomePerSecond(state)
    val boostSecondsRemaining = ((state.boostUntilMs - nowMs).coerceAtLeast(0L)) / 1000L

    // Live state handle for the frame loop — the 3D scene reads it inside onFrame only,
    // so simulation ticks (10 Hz) never recompose the scene tree.
    val gameStateForScene = rememberUpdatedState(state)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        MiningScene(
            gameState = gameStateForScene,
            modifier = Modifier.fillMaxSize(),
        )

        TopScrim(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .height(150.dp),
        )
        TopHudBar(
            state = state,
            incomePerSecond = incomePerSecond,
            boostSecondsRemaining = boostSecondsRemaining,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        // Floating sale/gem popups, anchored near the market corner of the view.
        CashPopupsOverlay(
            events = viewModel.events,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 52.dp)
                .width(220.dp)
                .height(360.dp),
        )

        WorkButtons(
            state = state,
            dispatch = viewModel::dispatch,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
        )

        BottomActionBar(
            onPanelSelected = { activePanel = it },
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        if (state.pendingOfflineEarnings > 0.0) {
            OfflineEarningsDialog(state = state, dispatch = viewModel::dispatch)
        }

        activePanel?.let { panel ->
            ModalBottomSheet(
                onDismissRequest = { activePanel = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = MaterialTheme.colorScheme.background,
            ) {
                when (panel) {
                    GamePanel.SHAFTS -> ShaftsPanel(state, viewModel::dispatch)
                    GamePanel.REFINERY -> RefineryPanel(state, viewModel::dispatch)
                    GamePanel.UPGRADES -> UpgradesPanel(state, nowMs, viewModel::dispatch)
                    GamePanel.MANAGERS -> ManagersPanel(state, viewModel::dispatch)
                }
            }
        }
    }
}
