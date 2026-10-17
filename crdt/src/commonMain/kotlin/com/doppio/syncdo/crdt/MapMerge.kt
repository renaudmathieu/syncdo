package com.doppio.syncdo.crdt

/**
 * Merge two maps whose values are themselves [CrdtState]s: keys present on one side
 * are kept as is, colliding keys are merged with their own [CrdtState.merge].
 *
 * This is the "map of CRDTs is a CRDT" operator, the backbone of composite aggregates.
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
