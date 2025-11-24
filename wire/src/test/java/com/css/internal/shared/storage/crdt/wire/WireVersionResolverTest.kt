package com.css.internal.shared.storage.crdt.wire

import com.css.internal.shared.storage.crdt.data.Version
import com.css.internal.shared.storage.crdt.wire.internal.WireVersionTreeResolver
import com.css.internal.shared.storage.crdt.wire.internal.compareTo
import kotlin.test.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

class WireVersionResolverTest {
    private val resolver = WireVersionTreeResolver

    @Test
    fun testComparableVersionSequenceCompare() {
        val version1 = Version(timestamp = 1, actor_id = 3, actor_version = 1)
        val version2 = Version(timestamp = 1, actor_id = 2, actor_version = 2)

        assertTrue(version1.compareTo(version2) == 1)
        assertTrue(version2 < version1)
        assertTrue(Version() < version1)
        assertTrue(version1 > Version())
        assertTrue(Version(timestamp = 1, actor_id = 3, actor_version = 1).compareTo(version1) == 0)
        assertTrue(Version(timestamp = 1, actor_id = 2, actor_version = 2).compareTo(version2) == 0)
        assertTrue(version1 <= Version(timestamp = 1, actor_id = 3, actor_version = 1))
        assertTrue(version1 >= Version(timestamp = 1, actor_id = 3, actor_version = 1))
        assertTrue(version2 >= Version(timestamp = 1, actor_id = 2, actor_version = 2))
        assertTrue(version2 >= Version(timestamp = 1, actor_id = 2, actor_version = 2))
        assertEquals(Version(timestamp = 1, actor_id = 3, actor_version = 1), maxOf(version2, version1, resolver))
        assertEquals(Version(timestamp = 1, actor_id = 2, actor_version = 2), minOf(version2, version1, resolver))
    }

    // ========== Version Vector Tests ==========

    @Test
    fun `isIncluded - version is included when actor version in vector is equal`() {
        // Given
        val version = Version(timestamp = 100, actor_id = 1, actor_version = 5)
        val versionVector = mapOf(
            1L to 5L // Actor 1 at version 5
        )

        // When/Then - version 5 from actor 1 is included
        assertTrue(resolver.isIncluded(versionVector, version))
    }

    @Test
    fun `isIncluded - version is included when actor version in vector is greater`() {
        // Given
        val version = Version(timestamp = 100, actor_id = 1, actor_version = 3)
        val versionVector = mapOf(
            1L to 5L // Actor 1 at version 5 (> 3)
        )

        // When/Then - version 3 from actor 1 is included (we have up to version 5)
        assertTrue(resolver.isIncluded(versionVector, version))
    }

    @Test
    fun `isIncluded - version is not included when actor version in vector is less`() {
        // Given
        val version = Version(timestamp = 100, actor_id = 1, actor_version = 10)
        val versionVector = mapOf(
            1L to 5L // Actor 1 only at version 5 (< 10)
        )

        // When/Then - version 10 from actor 1 is NOT included (we only have up to version 5)
        assertFalse(resolver.isIncluded(versionVector, version))
    }

    @Test
    fun `isIncluded - version is not included when actor not in version vector`() {
        // Given
        val version = Version(timestamp = 100, actor_id = 2, actor_version = 3)
        val versionVector = mapOf(
            1L to 5L // Only actor 1, not actor 2
        )

        // When/Then - version from actor 2 is NOT included (actor 2 not in vector)
        assertFalse(resolver.isIncluded(versionVector, version))
    }

    @Test
    fun `isIncluded - version is not included when version vector is empty`() {
        // Given
        val version = Version(timestamp = 100, actor_id = 1, actor_version = 5)
        val versionVector = emptyMap<Long, Long>()

        // When/Then - no versions included in empty vector
        assertFalse(resolver.isIncluded(versionVector, version))
    }

    @Test
    fun `isIncluded - timestamp does not affect inclusion`() {
        // Given - same actor and version, different timestamps
        val version1 = Version(timestamp = 100, actor_id = 1, actor_version = 5)
        val version2 = Version(timestamp = 999, actor_id = 1, actor_version = 5)
        val versionVector = mapOf(
            1L to 5L // Actor 1 at version 5
        )

        // When/Then - both versions included regardless of timestamp
        assertTrue(resolver.isIncluded(versionVector, version1))
        assertTrue(resolver.isIncluded(versionVector, version2))
    }

    @Test
    fun `isIncluded - multiple actors in version vector`() {
        // Given
        val versionVector = mapOf(
            1L to 10L, // Actor 1 at version 10
            2L to 5L, // Actor 2 at version 5
            3L to 8L // Actor 3 at version 8
        )

        // When/Then - check various versions
        // Actor 1
        assertTrue(resolver.isIncluded(versionVector, Version(timestamp = 100, actor_id = 1, actor_version = 10)))
        assertTrue(resolver.isIncluded(versionVector, Version(timestamp = 100, actor_id = 1, actor_version = 5)))
        assertFalse(resolver.isIncluded(versionVector, Version(timestamp = 100, actor_id = 1, actor_version = 11)))

        // Actor 2
        assertTrue(resolver.isIncluded(versionVector, Version(timestamp = 100, actor_id = 2, actor_version = 5)))
        assertTrue(resolver.isIncluded(versionVector, Version(timestamp = 100, actor_id = 2, actor_version = 3)))
        assertFalse(resolver.isIncluded(versionVector, Version(timestamp = 100, actor_id = 2, actor_version = 6)))

        // Actor 3
        assertTrue(resolver.isIncluded(versionVector, Version(timestamp = 100, actor_id = 3, actor_version = 8)))
        assertFalse(resolver.isIncluded(versionVector, Version(timestamp = 100, actor_id = 3, actor_version = 9)))

        // Actor 4 (not in vector)
        assertFalse(resolver.isIncluded(versionVector, Version(timestamp = 100, actor_id = 4, actor_version = 1)))
    }

    @Test
    fun `isIncluded - version 0 handling`() {
        // Given
        val versionVector = mapOf(
            1L to 5L
        )

        // When/Then - version 0 is included if actor is in vector
        assertTrue(resolver.isIncluded(versionVector, Version(timestamp = 100, actor_id = 1, actor_version = 0)))
    }

    @Test
    fun `isIncluded - real world scenario with multiple actors and versions`() {
        // Given - simulating a real distributed system scenario
        // Device 1 (actor_id=100) made changes up to version 15
        // Device 2 (actor_id=200) made changes up to version 8
        // Device 3 (actor_id=300) made changes up to version 3
        val versionVector = mapOf(
            100L to 15L,
            200L to 8L,
            300L to 3L
        )

        // When/Then - test various change versions

        // Changes from Device 1
        assertTrue(resolver.isIncluded(versionVector, Version(timestamp = 1000, actor_id = 100, actor_version = 1)))
        assertTrue(resolver.isIncluded(versionVector, Version(timestamp = 1001, actor_id = 100, actor_version = 10)))
        assertTrue(resolver.isIncluded(versionVector, Version(timestamp = 1002, actor_id = 100, actor_version = 15)))
        assertFalse(resolver.isIncluded(versionVector, Version(timestamp = 1003, actor_id = 100, actor_version = 16)))
        assertFalse(resolver.isIncluded(versionVector, Version(timestamp = 1004, actor_id = 100, actor_version = 20)))

        // Changes from Device 2
        assertTrue(resolver.isIncluded(versionVector, Version(timestamp = 2000, actor_id = 200, actor_version = 5)))
        assertTrue(resolver.isIncluded(versionVector, Version(timestamp = 2001, actor_id = 200, actor_version = 8)))
        assertFalse(resolver.isIncluded(versionVector, Version(timestamp = 2002, actor_id = 200, actor_version = 9)))

        // Changes from Device 3
        assertTrue(resolver.isIncluded(versionVector, Version(timestamp = 3000, actor_id = 300, actor_version = 1)))
        assertTrue(resolver.isIncluded(versionVector, Version(timestamp = 3001, actor_id = 300, actor_version = 3)))
        assertFalse(resolver.isIncluded(versionVector, Version(timestamp = 3002, actor_id = 300, actor_version = 4)))

        // Changes from unknown Device 4 (not in version vector)
        assertFalse(resolver.isIncluded(versionVector, Version(timestamp = 4000, actor_id = 400, actor_version = 1)))
    }
}
