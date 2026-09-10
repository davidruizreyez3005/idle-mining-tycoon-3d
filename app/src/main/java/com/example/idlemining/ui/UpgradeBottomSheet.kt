package com.example.idlemining.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun UpgradeBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Upgrade Mining Facility", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onDismiss) { Text("Upgrade Shaft Depth (+15%)") }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onDismiss) { Text("Upgrade Refinery (+10% Efficiency)") }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onDismiss) { Text("Upgrade Cart Speed (+20%)") }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onDismiss) { Text("Close") }
        }
    }
}
