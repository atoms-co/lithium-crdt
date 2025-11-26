package com.css.protobuf.crdt.resolver.delta

import com.css.protobuf.crdt.resolver.ResolutionDeltaContext
import com.css.protobuf.crdt.resolver.SingleValueResolver
import com.css.protobuf.crdt.resolver.TestVersionTreeResolver
import com.css.protobuf.crdt.resolver.Version
import com.css.protobuf.crdt.resolver.VersionNode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for SingleValueDeltaResolver verifying version vector-based delta computation.
 *
 * The delta resolver determines which changes need to be sent based on a version vector
 * representing what the recipient already has. If a version is included in the version
 * vector, it means the recipient already has that change and it should not be sent.
 */
class SingleValueDeltaResolverTest {
    private val decoder: (ByteArray) -> String = { it.decodeToString() }
    private val encoder: (String) -> ByteArray = { it.toByteArray() }
    private val resolver = SingleValueResolver(
        decoder = decoder,
        encoder = encoder,
        versionTreeResolver = TestVersionTreeResolver
    )

    @Test
    fun `changeDelta adds change when version not in version vector`() {
        // Given
        val context = ResolutionDeltaContext<VersionNode, String>()
        val version = Version(1L, 100L, 100L)
        val versionVector = mapOf(1L to 50L, 2L to 75L)

        // When
        resolver.changeDelta(
            value = "test_value",
            node = VersionNode(version = version),
            version = version,
            versionVector = versionVector,
            context = context
        )

        // Then
        assertEquals(1, context.changes.size, "Should add change when version not in vector")
        assertEquals("test_value", context.changes[0].value)
        assertEquals(version, context.changes[0].versionNode.version)
    }

    @Test
    fun `changeDelta skips change when version in version vector`() {
        // Given
        val context = ResolutionDeltaContext<VersionNode, String>()
        val version = Version(1L, 50L, 50L)
        val versionVector = mapOf(1L to 100L)

        // When
        resolver.changeDelta(
            value = "test_value",
            node = VersionNode(version = version),
            version = version,
            versionVector = versionVector,
            context = context
        )

        // Then
        assertEquals(0, context.changes.size, "Should skip change when version in vector")
    }

    @Test
    fun `changeDelta uses node version when available`() {
        // Given
        val context = ResolutionDeltaContext<VersionNode, String>()
        val nodeVersion = Version(1L, 200L, 200L)
        val fallbackVersion = Version(1L, 100L, 100L)
        val versionVector = mapOf<Long, Long>()

        // When
        resolver.changeDelta(
            value = "test_value",
            node = VersionNode(version = nodeVersion),
            version = fallbackVersion,
            versionVector = versionVector,
            context = context
        )

        // Then
        assertEquals(1, context.changes.size)
        assertEquals(nodeVersion, context.changes[0].versionNode.version, "Should use node version, not fallback")
    }

    @Test
    fun `changeDelta uses fallback version when node is null`() {
        // Given
        val context = ResolutionDeltaContext<VersionNode, String>()
        val fallbackVersion = Version(1L, 100L, 100L)
        val versionVector = mapOf<Long, Long>()

        // When
        resolver.changeDelta(
            value = "test_value",
            node = null,
            version = fallbackVersion,
            versionVector = versionVector,
            context = context
        )

        // Then
        assertEquals(1, context.changes.size)
        assertEquals(fallbackVersion, context.changes[0].versionNode.version, "Should use fallback when node is null")
    }

    @Test
    fun `changeDelta handles null value`() {
        // Given
        val context = ResolutionDeltaContext<VersionNode, String>()
        val version = Version(1L, 100L, 100L)
        val versionVector = mapOf<Long, Long>()

        // When
        resolver.changeDelta(
            value = null,
            node = VersionNode(version = version),
            version = version,
            versionVector = versionVector,
            context = context
        )

        // Then
        assertEquals(1, context.changes.size)
        assertEquals(null, context.changes[0].value, "Should handle null value correctly")
        assertEquals(version, context.changes[0].versionNode.version)
    }

    @Test
    fun `changeDelta with multiple version vector entries`() {
        // Given
        val context = ResolutionDeltaContext<VersionNode, String>()
        val version = Version(2L, 75L, 75L)
        val version2 = Version(1L, 50L, 50L)
        val versionVector = mapOf(
            1L to 50L,
            2L to 100L
        )

        // When
        resolver.changeDelta(
            value = "test_value",
            node = VersionNode(version = version),
            version = version,
            versionVector = versionVector,
            context = context
        )
        resolver.changeDelta(
            value = "test_value",
            node = VersionNode(version = version2),
            version = version2,
            versionVector = versionVector,
            context = context
        )

        // Then
        assertEquals(0, context.changes.size, "Should skip when the actor vector entry includes the version")
    }

    @Test
    fun `changeDelta with empty version vector adds all changes`() {
        // Given
        val context = ResolutionDeltaContext<VersionNode, String>()
        val version = Version(1L, 100L, 100L)
        val versionVector = emptyMap<Long, Long>()

        // When
        resolver.changeDelta(
            value = "test_value",
            node = VersionNode(version = version),
            version = version,
            versionVector = versionVector,
            context = context
        )

        // Then
        assertEquals(1, context.changes.size, "Empty version vector should accept all changes")
    }

    @Test
    fun `changeDelta uses node version of 0 when node has no explicit version`() {
        // Given
        val context = ResolutionDeltaContext<VersionNode, String>()
        val fallbackVersion = Version(1L, 100L, 100L)
        val versionVector = mapOf<Long, Long>()

        // When - node null
        resolver.changeDelta(
            value = "test_value",
            node = null,
            version = fallbackVersion,
            versionVector = versionVector,
            context = context
        )

        // Then - should use fallback since node version is 0
        assertEquals(1, context.changes.size)
        assertEquals(fallbackVersion, context.changes[0].versionNode.version)
    }
}
