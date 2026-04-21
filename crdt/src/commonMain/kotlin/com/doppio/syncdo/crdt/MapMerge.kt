package com.doppio.syncdo.crdt

/**
 * Combine two maps by folding colliding values through [merge].
 * Keys unique to one side are carried through unchanged.
 *
 * The result forms a join-semilattice whenever [merge] does, which lets CRDT
 * aggregates delegate their field-by-field merge to this helper instead of
 * open-coding the union/zip dance.
 */
inline fun <K, V : Any> Map<K, V>.mergeValues(
    other: Map<K, V>,
    merge: (V, V) -> V,
): Map<K, V> {
    if (isEmpty()) return other
    if (other.isEmpty()) return this
    val result = LinkedHashMap<K, V>(size + other.size)
    result.putAll(this)
    for ((key, value) in other) {
        result[key] = result[key]?.let { merge(it, value) } ?: value
    }
    return result
}

/**
 * Merge two maps whose values are themselves [CrdtState]s, using their own
 * [CrdtState.merge] at colliding keys. This is the "map of CRDTs is a CRDT"
 * operator — the backbone of most composite domain aggregates.
 */
fun <K, V : CrdtState<V>> Map<K, V>.mergeStates(other: Map<K, V>): Map<K, V> =
    mergeValues(other) { a, b -> a.merge(b) }

/**
 * Merge two maps of sets by taking the union at each key.
 * Useful for accumulating tombstones, tag sets, or any grow-only per-key collection.
 */
fun <K, T> Map<K, Set<T>>.unionValues(other: Map<K, Set<T>>): Map<K, Set<T>> =
    mergeValues(other) { a, b -> a + b }
