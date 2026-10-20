package com.doppio.syncdo.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doppio.syncdo.sync.SyncStatus
import com.doppio.syncdo.ui.theme.SyncDoStatusColors

@Composable
fun SyncStatusIndicator(modifier: Modifier = Modifier, status: SyncStatus) {
    val target = when (status) {
        SyncStatus.Synced -> SyncDoStatusColors.Synced
        SyncStatus.Syncing, SyncStatus.PendingChanges -> SyncDoStatusColors.Syncing
        SyncStatus.Offline -> SyncDoStatusColors.Offline
        SyncStatus.Error -> SyncDoStatusColors.Error
    }
    val color by animateColorAsState(target, tween(300))
    val label = when (status) {
        SyncStatus.Synced -> "Synced"
        SyncStatus.Syncing -> "Syncing"
        SyncStatus.PendingChanges -> "Pending"
        SyncStatus.Offline -> "Offline"
        SyncStatus.Error -> "Error"
    }
    // The dot breathes while a push is in flight
    val breathing = status == SyncStatus.Syncing
    val pulse by rememberInfiniteTransition().animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .graphicsLayer { alpha = if (breathing) pulse else 1f }
                .background(color, CircleShape)
        )
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, letterSpacing = 0.sp),
        )
    }
}
