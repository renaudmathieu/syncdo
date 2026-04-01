package com.doppio.syncdo.persistence

import com.doppio.syncdo.crdt.TodoListCrdt

interface LocalStorage {
    suspend fun load(): TodoListCrdt?
    suspend fun save(state: TodoListCrdt)
}
