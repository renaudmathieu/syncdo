package com.doppio.syncdo.sync

import com.doppio.syncdo.crdt.NodeId
import com.doppio.syncdo.crdt.TodoListCrdt
import com.doppio.syncdo.crdt.TodoListDelta
import com.doppio.syncdo.crdt.VectorClock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface SyncMessage {

    @Serializable
    @SerialName("push_delta")
    data class PushDelta(
        val delta: TodoListDelta,
        val nodeId: NodeId
    ) : SyncMessage

    @Serializable
    @SerialName("pull_request")
    data class PullRequest(
        val clock: VectorClock,
        val nodeId: NodeId
    ) : SyncMessage

    @Serializable
    @SerialName("pull_response")
    data class PullResponse(
        val delta: TodoListDelta
    ) : SyncMessage

    @Serializable
    @SerialName("full_sync")
    data class FullSync(
        val state: TodoListCrdt
    ) : SyncMessage
}
