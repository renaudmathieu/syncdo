package com.doppio.syncdo.model

import kotlin.time.Instant

data class PeerInfo(
    val id: String,
    val name: String,
    val deviceType: DeviceType,
    val lastSync: Instant?,
    val isOnline: Boolean
)

enum class DeviceType {
    Phone, Tablet, Desktop
}
