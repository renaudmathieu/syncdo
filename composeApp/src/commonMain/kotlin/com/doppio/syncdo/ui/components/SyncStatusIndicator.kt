package com.doppio.syncdo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doppio.syncdo.model.SyncStatus
import com.doppio.syncdo.ui.theme.SyncDoStatusColors

@Composable
fun SyncStatusIndicator(modifier: Modifier = Modifier, status: SyncStatus) {
    val color = when (status) {
        SyncStatus.Synced -> SyncDoStatusColors.Synced
        SyncStatus.Syncing, SyncStatus.PendingChanges -> SyncDoStatusColors.Syncing
        SyncStatus.Offline -> SyncDoStatusColors.Offline
        SyncStatus.Error -> MaterialTheme.colorScheme.error
    }
    val label = when (status) {
        SyncStatus.Synced -> "Synced"
        SyncStatus.Syncing -> "Syncing"
        SyncStatus.PendingChanges -> "Pending"
        SyncStatus.Offline -> "Offline"
        SyncStatus.Error -> "Error"
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
