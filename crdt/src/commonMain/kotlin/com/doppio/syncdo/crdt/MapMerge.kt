package com.doppio.syncdo.crdt

/**
 * Merge two maps whose values are CRDTs: a key present on one side only is kept as is,
 * and the values of a key present on both sides are merged with their own
 * [CrdtState.merge].
 *
 * A map of CRDTs merged this way is itself a CRDT. This is the building block of
 * composite aggregates, such as "a map from item id to item":
 *
 * ```kotlin
 * data class TodoList(val items: Map<String, TodoItem>) : CrdtState<TodoList> {
 *     override fun merge(other: TodoList) = TodoList(items.mergeStates(other.items))
 * }
 * ```
 */
fun <K, V : CrdtState<V>> Map<K, V>.mergeStates(other: Map<K, V>): Map<K, V> =
    mergeValues(other) { a, b -> a.merge(b) }

/** Merge two maps of sets by taking the union at each key. */
internal fun <K, T> Map<K, Set<T>>.unionValues(other: Map<K, Set<T>>): Map<K, Set<T>> =
    mergeValues(other) { a, b -> a + b }

/** Combine two maps, folding the values of colliding keys through [merge]. */
internal fun <K, V : Any> Map<K, V>.mergeValues(other: Map<K, V>, merge: (V, V) -> V): Map<K, V> {
    val result = toMutableMap()
    for ((key, value) in other) {
        result[key] = result[key]?.let { merge(it, value) } ?: value
    }
    return result
}
