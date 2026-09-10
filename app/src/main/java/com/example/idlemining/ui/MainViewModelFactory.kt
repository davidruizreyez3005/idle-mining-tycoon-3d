package com.example.idlemining.ui

import androidx.compose.ui.Modifier
import com.example.idlemining.data.local.AppDatabase

@Composable
fun HudOverlay(
    cash: Double,
    gems: Int,
    netPerSec: Double,
    onUpgradeClick: () -> Unit,
    modifier: Modifier
) {
    // Implementation delegated to composable in same file
    com.example.idlemining.ui.HudOverlay(cash, gems, netPerSec, onUpgradeClick, modifier)
}
