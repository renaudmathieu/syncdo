package com.doppio.syncdo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doppio.syncdo.sync.SyncStatus
import com.doppio.syncdo.ui.theme.SyncDoStatusColors

@Composable
fun PeersScreen(
    modifier: Modifier = Modifier,
    syncStatus: SyncStatus
) {
    val statusColor = when (syncStatus) {
        SyncStatus.Synced -> SyncDoStatusColors.Synced
        SyncStatus.Syncing, SyncStatus.PendingChanges -> SyncDoStatusColors.Syncing
        SyncStatus.Offline -> SyncDoStatusColors.Offline
        SyncStatus.Error -> MaterialTheme.colorScheme.error
    }
    val statusLabel = when (syncStatus) {
        SyncStatus.Synced -> "Connected"
        SyncStatus.Syncing -> "Syncing..."
        SyncStatus.PendingChanges -> "Pending changes"
        SyncStatus.Offline -> "Offline"
        SyncStatus.Error -> "Error"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Peers",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Sync status card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = statusLabel,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = statusLabel,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        // Add a Peer section
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Add a Peer",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { /* TODO: Enter code flow */ },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text("# Enter Code", fontSize = 14.sp)
                }
                OutlinedButton(
                    onClick = { /* TODO: QR code flow */ },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text("Scan QR", fontSize = 14.sp)
                }
            }
        }

        // Connected Devices placeholder
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Connected Devices",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "No devices connected yet",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
