package com.css.protobuf.crdt.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class ActorsTest {
    @Test
    fun `incrementLocalActor - null actors creates new with version 1`() {
        // Given
        val actorId = 12345L

        // When
        val result = null.incrementLocalActor { actorId }

        // Then
        assertEquals(12345L, result.local_actor, "Should use generated actor ID")
        assertEquals(mapOf(12345L to 1L), result.version_vector, "Should initialize version to 1")
    }

    @Test
    fun `incrementLocalActor - null actors uses random generator by default`() {
        // Given/When
        val result = null.incrementLocalActor()

        // Then
        assertNotEquals(0L, result.local_actor, "Should generate non-zero actor ID")
        assertEquals(1L, result.version_vector[result.local_actor], "Should initialize version to 1")
        assertEquals(1, result.version_vector.size, "Should have exactly one actor")
    }

    @Test
    fun `incrementLocalActor - existing actors increments local actor version`() {
        // Given
        val actors =
            Actors(
                local_actor = 100L,
                version_vector = mapOf(100L to 5L, 200L to 3L),
            )

        // When
        val result = actors.incrementLocalActor()

        // Then
        assertEquals(100L, result.local_actor, "Should preserve local actor ID")
        assertEquals(
            mapOf(100L to 6L, 200L to 3L),
            result.version_vector,
            "Should increment local actor from 5 to 6",
        )
    }

    @Test
    fun `incrementLocalActor - multiple increments`() {
        // Given
        var actors =
            Actors(
                local_actor = 100L,
                version_vector = mapOf(100L to 1L, 200L to 5L),
            )

        // When - increment 3 times
        actors = actors.incrementLocalActor()
        actors = actors.incrementLocalActor()
        actors = actors.incrementLocalActor()

        // Then
        assertEquals(100L, actors.local_actor, "Should preserve local actor ID")
        assertEquals(
            mapOf(100L to 4L, 200L to 5L),
            actors.version_vector,
            "Should increment from 1 to 4",
        )
    }

    @Test
    fun `incrementLocalActor - actor with ID 0 generates new ID`() {
        // Given
        val actors =
            Actors(
                local_actor = 0L,
                version_vector = mapOf(100L to 5L),
            )

        // When
        val result = actors.incrementLocalActor { 999L }

        // Then
        assertEquals(999L, result.local_actor, "Should generate new actor ID when current is 0")
        assertEquals(
            mapOf(100L to 5L, 999L to 1L),
            result.version_vector,
            "Should initialize new actor at version 1",
        )
    }

    @Test
    fun `merge - null local actors with version vector`() {
        // Given
        val versionVector = mapOf(100L to 5L, 200L to 3L)

        // When
        val result = null.merge(incoming = versionVector)

        // Then
        assertEquals(versionVector, result.version_vector, "Should use incoming version vector")
        assertEquals(0L, result.local_actor, "Default local actor should be 0")
    }

    @Test
    fun `merge - existing local actors with new version vector merges correctly`() {
        // Given
        val localActors =
            Actors(
                local_actor = 100L,
                version_vector = mapOf(100L to 5L, 200L to 3L),
            )
        val incomingVersionVector = mapOf(100L to 7L, 300L to 2L) // Actor 100 higher, actor 300 new

        // When
        val result = localActors.merge(incoming = incomingVersionVector)

        // Then - should take max of each actor
        assertEquals(100L, result.local_actor, "Should preserve local actor")
        assertEquals(
            mapOf(100L to 7L, 200L to 3L, 300L to 2L),
            result.version_vector,
            "Should merge version vectors taking max per actor",
        )
    }

    @Test
    fun `merge - local actor version higher than incoming`() {
        // Given
        val localActors =
            Actors(
                local_actor = 100L,
                version_vector = mapOf(100L to 10L, 200L to 3L),
            )
        val incomingVersionVector = mapOf(100L to 5L, 200L to 8L)

        // When
        val result = localActors.merge(incoming = incomingVersionVector)

        // Then - should take max of each actor
        assertEquals(
            mapOf(100L to 10L, 200L to 8L),
            result.version_vector,
            "Should keep higher local version for actor 100, use higher incoming for actor 200",
        )
    }

    @Test
    fun `merge - empty incoming version vector`() {
        // Given
        val localActors =
            Actors(
                local_actor = 100L,
                version_vector = mapOf(100L to 10L, 200L to 3L),
            )

        // When
        val result = localActors.merge(incoming = emptyMap())

        // Then
        assertEquals(
            mapOf(100L to 10L, 200L to 3L),
            result.version_vector,
            "Should preserve local version vector when incoming is empty",
        )
    }

    @Test
    fun `merge - disjoint actor sets`() {
        // Given
        val localActors =
            Actors(
                local_actor = 100L,
                version_vector = mapOf(100L to 5L, 200L to 3L),
            )
        val incomingVersionVector = mapOf(300L to 7L, 400L to 2L) // No overlap

        // When
        val result = localActors.merge(incoming = incomingVersionVector)

        // Then
        assertEquals(
            mapOf(100L to 5L, 200L to 3L, 300L to 7L, 400L to 2L),
            result.version_vector,
            "Should combine all actors from both vectors",
        )
    }

    @Test
    fun `Map increment - adds new key with value 1`() {
        // Given
        val map = mapOf(100L to 5L, 200L to 3L)

        // When
        val result = map.increment(key = 300L)

        // Then
        assertEquals(
            mapOf(100L to 5L, 200L to 3L, 300L to 1L),
            result,
            "Should add new key with value 1",
        )
    }

    @Test
    fun `Map increment - increments existing key`() {
        // Given
        val map = mapOf(100L to 5L, 200L to 3L)

        // When
        val result = map.increment(key = 100L)

        // Then
        assertEquals(
            mapOf(100L to 6L, 200L to 3L),
            result,
            "Should increment existing key from 5 to 6",
        )
    }

    @Test
    fun `Map increment - preserves immutability`() {
        // Given
        val original = mapOf(100L to 5L)

        // When
        val incremented = original.increment(key = 100L)

        // Then
        assertEquals(mapOf(100L to 5L), original, "Original map should be unchanged")
        assertEquals(mapOf(100L to 6L), incremented, "New map should have incremented value")
    }

    @Test
    fun `MutableMap incrementAndGet - returns new value`() {
        // Given
        val map = mutableMapOf(100L to 5L)

        // When
        val newValue = map.incrementAndGet(key = 100L)

        // Then
        assertEquals(6L, newValue, "Should return incremented value")
        assertEquals(mapOf(100L to 6L), map, "Map should be mutated")
    }

    @Test
    fun `MutableMap incrementAndGet - initializes new key to 1`() {
        // Given
        val map = mutableMapOf(100L to 5L)

        // When
        val newValue = map.incrementAndGet(key = 200L)

        // Then
        assertEquals(1L, newValue, "Should return 1 for new key")
        assertEquals(mapOf(100L to 5L, 200L to 1L), map, "Map should have new entry")
    }

    @Test
    fun `Map merge - combines with maxOf strategy`() {
        // Given
        val local = mapOf(100L to 10L, 200L to 3L)
        val incoming = mapOf(100L to 5L, 200L to 8L, 300L to 2L)

        // When
        val result = local.merge(other = incoming, combine = ::maxOf)

        // Then
        assertEquals(
            mapOf(100L to 10L, 200L to 8L, 300L to 2L),
            result,
            "Should take max of each key",
        )
    }

    @Test
    fun `Map merge - empty local map returns other`() {
        // Given
        val local = emptyMap<Long, Long>()
        val incoming = mapOf(100L to 5L, 200L to 3L)

        // When
        val result = local.merge(other = incoming, combine = ::maxOf)

        // Then
        assertEquals(incoming, result, "Should return other when local is empty")
    }

    @Test
    fun `full workflow - increment then merge`() {
        // Given - two devices with initial state
        var device1 =
            Actors(
                local_actor = 100L,
                version_vector = mapOf(100L to 5L, 200L to 3L),
            )
        var device2 =
            Actors(
                local_actor = 200L,
                version_vector = mapOf(100L to 4L, 200L to 6L),
            )

        // When - each device makes local changes
        device1 = device1.incrementLocalActor() // Device 1: 100->6
        device2 = device2.incrementLocalActor() // Device 2: 200->7

        // Then - verify increments
        assertEquals(mapOf(100L to 6L, 200L to 3L), device1.version_vector)
        assertEquals(mapOf(100L to 4L, 200L to 7L), device2.version_vector)

        // When - devices sync with each other
        device1 = device1.merge(incoming = device2.version_vector)
        device2 = device2.merge(incoming = device1.version_vector)

        // Then - both should have same merged state
        assertEquals(
            mapOf(100L to 6L, 200L to 7L),
            device1.version_vector,
            "Device 1 should have merged state",
        )
        assertEquals(
            mapOf(100L to 6L, 200L to 7L),
            device2.version_vector,
            "Device 2 should have same merged state",
        )
    }
}
