package com.example.idlemining.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HudOverlay(cash: Double, gems: Int, netPerSec: Double, onUpgradeClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Black.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Cash: $${"%.2f".format(cash)}", color = Color.Yellow, fontSize = 20.sp)
                    Text("Gems: $gems", color = Color.Cyan, fontSize = 14.sp)
                    Text("Rate: $${"%.2f".format(netPerSec)}/sec", color = Color.White, fontSize = 12.sp)
                }
                Button(onClick = onUpgradeClick) {
                    Text("Upgrades")
                }
            }
        }
    }
}
