package com.css.internal.shared.storage.crdt.resolver

interface MessageActorAdapter<A, V> {
    val A.versionVector: Map<Long, Long>
    fun merge(localActors: A?, versionVector: Map<Long, Long>): A
    fun incrementLocalActor(actors: A?): A
    fun localVersion(actors: A, timestamp: Long): V
    fun List<V>.toVersionVector(): Map<Long, Long>
}
