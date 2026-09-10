package com.example.idlemining

import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.idlemining.game.threed.MiningScene
import com.example.idlemining.ui.HudOverlay
import com.example.idlemining.ui.MainViewModel
import com.example.idlemining.ui.UpgradeBottomSheet

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels { MainViewModelFactory() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scene = MiningScene(this)

        setContent {
            MaterialTheme {
                var showUpgradeSheet by remember { mutableStateOf(false) }
                val state by viewModel.gameState.collectAsState()

                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { scene.sceneView },
                        modifier = Modifier.fillMaxSize()
                    )
                    // HUD Overlay drawn via Compose over the 3D view
                    HudOverlay(
                        cash = state.cash,
                        gems = state.gems,
                        netPerSec = state.netIncomePerSec,
                        onUpgradeClick = { showUpgradeSheet = true },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (showUpgradeSheet) {
                    UpgradeBottomSheet(onDismiss = { showUpgradeSheet = false })
                }
            }
        }
    }
}
