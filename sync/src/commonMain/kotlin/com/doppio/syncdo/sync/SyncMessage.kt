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
public sealed interface SyncMessage<D> {

    /**
     * Client to server: a local change to merge into the server state and forward to peers.
     *
     * @property delta the change, a small state.
     * @property nodeId the client that made the change.
     */
    @Serializable
    @SerialName("push_delta")
    public data class PushDelta<D>(
        public val delta: D,
        public val nodeId: NodeId,
    ) : SyncMessage<D>

    /**
     * Client to server, on connect: "here is what I have seen, send me what I miss".
     *
     * @property clock the clock of the client's local state.
     * @property nodeId the client asking.
     */
    @Serializable
    @SerialName("pull_request")
    public data class PullRequest<D>(
        public val clock: VectorClock,
        public val nodeId: NodeId,
    ) : SyncMessage<D>

    /**
     * Server to client: something the client is missing, to merge into its local state.
     *
     * @property delta missed deltas merged into one, the full state, or a peer's push.
     */
    @Serializable
    @SerialName("pull_response")
    public data class PullResponse<D>(
        public val delta: D,
    ) : SyncMessage<D>
}
