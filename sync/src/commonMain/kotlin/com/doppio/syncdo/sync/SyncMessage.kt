package com.doppio.syncdo.sync

import com.doppio.syncdo.crdt.NodeId
import com.doppio.syncdo.crdt.VectorClock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire protocol for incremental CRDT sync over a single full-duplex channel (e.g. WebSocket).
 *
 * Parameterized on the application's [ClockedState] type, used for deltas and full states
 * alike. Serialize with `SyncMessage.serializer(deltaSerializer)`.
 *
 * Exchange:
 *   client → server: [PullRequest] on connect, then zero or more [PushDelta].
 *   server → client: [PullResponse] carrying what the client is missing (sent only when
 *                    something is missing), and to broadcast every peer's pushed delta.
 */
@Serializable
sealed interface SyncMessage<D> {

    @Serializable
    @SerialName("push_delta")
    data class PushDelta<D>(
        val delta: D,
        val nodeId: NodeId,
    ) : SyncMessage<D>

    @Serializable
    @SerialName("pull_request")
    data class PullRequest<D>(
        val clock: VectorClock,
        val nodeId: NodeId,
    ) : SyncMessage<D>

    @Serializable
    @SerialName("pull_response")
    data class PullResponse<D>(
        val delta: D,
    ) : SyncMessage<D>
}
