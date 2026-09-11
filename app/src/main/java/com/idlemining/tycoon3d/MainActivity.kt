package com.idlemining.tycoon3d

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.idlemining.tycoon3d.ui.GameScreen
import com.idlemining.tycoon3d.ui.theme.MiningTycoonTheme
import com.idlemining.tycoon3d.viewmodel.GameViewModel

/**
 * Single-activity game. Immersive fullscreen (system bars hidden, swipe to
 * reveal), edge-to-edge Compose UI with the 3D Filament scene layered
 * underneath the HUD.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: GameViewModel by viewModels { GameViewModel.Factory(application) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()
        setContent {
            MiningTycoonTheme {
                GameScreen(viewModel)
            }
        }
    }

    override fun onPause() {
        viewModel.onAppPause()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}
