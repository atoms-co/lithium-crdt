package co.atoms.protobuf.crdt.resolver.local

import co.atoms.protobuf.crdt.resolver.ResolutionDeltaContext
import co.atoms.protobuf.crdt.resolver.SingleValueResolver
import co.atoms.protobuf.crdt.resolver.TestVersionTreeResolver
import co.atoms.protobuf.crdt.resolver.Version
import co.atoms.protobuf.crdt.resolver.VersionNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnyValueLocalResolverTest {
    private val context = ResolutionDeltaContext<VersionNode, String>()
    private val decoder: (ByteArray) -> String = { it.decodeToString() }
    private val encoder: (String) -> ByteArray = { it.toByteArray() }
    private val resolver = SingleValueResolver(
        decoder = decoder,
        encoder = encoder,
        versionTreeResolver = TestVersionTreeResolver
    )

    @Test
    fun `fast path - values are equal with existing node`() {
        // Given
        val currentVersion = Version(1L, 1000L, 1000L)
        val newVersion = Version(1L, 1100L, 1100L)
        val currentNode = VersionNode(version = currentVersion)

        // When
        val result =
            resolver.applyLocalWrite(
                currentValue = "same_value",
                currentNode = currentNode,
                currentVersion = currentVersion,
                newValue = "same_value",
                newVersion = newVersion,
                context = context,
            )

        // Then
        assertFalse(result.resolution, "Should not resolve when values are equal")
        assertEquals("same_value", result.value, "Should return current value")
        assertEquals(currentNode, result.node, "Should return existing node")
    }

    @Test
    fun `fast path - values are equal with null node`() {
        // Given
        val currentVersion = Version(1L, 1000L, 1000L)
        val newVersion = Version(1L, 1100L, 1100L)

        // When
        val result =
            resolver.applyLocalWrite(
                currentValue = "same_value",
                currentNode = null,
                currentVersion = currentVersion,
                newValue = "same_value",
                newVersion = newVersion,
                context = context,
            )

        // Then
        assertFalse(result.resolution, "Should not resolve when values are equal")
        assertEquals("same_value", result.value, "Should return current value")
        assertEquals(currentVersion, result.node?.version, "Should create node with current version")
    }

    @Test
    fun `value changed - new version already greater than current`() {
        // Given - newVersion (1100) > currentVersion (1000), no ensureAfter needed
        val currentVersion = Version(1L, 1000L, 1000L)
        val newVersion = Version(1L, 1100L, 1100L)
        val currentNode = VersionNode(version = currentVersion)

        // When
        val result =
            resolver.applyLocalWrite(
                currentValue = "old_value",
                currentNode = currentNode,
                currentVersion = currentVersion,
                newValue = "new_value",
                newVersion = newVersion,
                context = context,
            )

        // Then
        assertTrue(result.resolution, "Should resolve when values differ")
        assertEquals("new_value", result.value, "Should return new value")
        assertEquals(newVersion, result.node?.version, "Should keep original new version")
    }

    @Test
    fun `value changed - new version needs ensureAfter increment`() {
        // Given - newVersion (900) < currentVersion (1000), ensureAfter should make it 1001
        val currentVersion = Version(actorId = 1L, actorVersion = 1L, timestamp = 1000L)
        val newVersion = Version(actorId = 1L, actorVersion = 2L, timestamp = 900L) // Older timestamp
        val currentNode = VersionNode(version = currentVersion)
        val expectedVersion = Version(actorId = 1L, actorVersion = 2L, timestamp = 1001L) // ensureAfter result

        // When
        val result =
            resolver.applyLocalWrite(
                currentValue = "old_value",
                currentNode = currentNode,
                currentVersion = currentVersion,
                newValue = "new_value",
                newVersion = newVersion,
                context = context,
            )

        // Then
        assertTrue(result.resolution, "Should resolve when values differ")
        assertEquals("new_value", result.value, "Should return new value")
        assertEquals(expectedVersion, result.node?.version, "Should increment to 1001")
    }

    @Test
    fun `value changed - with null node uses currentVersion for ensureAfter`() {
        // Given - currentNode is null, should use currentVersion (1000) for ensureAfter
        val currentVersion = Version(1L, 1L, 1000L)
        val newVersion = Version(1L, 2L, 950L) // Older timestamp
        val expectedVersion = Version(1L, 2L, 1001L) // ensureAfter result

        // When
        val result =
            resolver.applyLocalWrite(
                currentValue = "old_value",
                currentNode = null,
                currentVersion = currentVersion,
                newValue = "new_value",
                newVersion = newVersion,
                context = context,
            )

        // Then
        assertTrue(result.resolution, "Should resolve when values differ")
        assertEquals("new_value", result.value, "Should return new value")
        assertEquals(expectedVersion, result.node?.version, "Should increment based on currentVersion")
    }

    @Test
    fun `value changed - uses currentNode version when higher than currentVersion`() {
        // Given - currentNode has newer version than currentVersion parameter
        val currentVersion = Version(1L, 1L, 1000L)
        val nodeVersion = Version(1L, 2L, 1200L) // Newer
        val newVersion = Version(1L, 3L, 1100L) // Older than node
        val currentNode = VersionNode(version = nodeVersion)
        val expectedVersion = Version(1L, 3L, 1201L) // ensureAfter nodeVersion

        // When
        val result =
            resolver.applyLocalWrite(
                currentValue = "old_value",
                currentNode = currentNode,
                currentVersion = currentVersion,
                newValue = "new_value",
                newVersion = newVersion,
                context = context,
            )

        // Then
        assertTrue(result.resolution, "Should resolve when values differ")
        assertEquals("new_value", result.value, "Should return new value")
        assertEquals(expectedVersion, result.node?.version, "Should increment nodeVersion to 1201")
        assertEquals(expectedVersion, result.node?.version, "Should increment based on node version")
    }

    @Test
    fun `monotonic progression when new version goes backwards`() {
        // Given - simulate clock skew where new version has older timestamp
        val currentVersion = Version(1L, 1L, 2000L)
        val newVersion = Version(1L, 2L, 1500L) // Went backwards!
        val expectedVersion = Version(1L, 2L, 2001L) // Forced forward

        // When
        val result =
            resolver.applyLocalWrite(
                currentValue = "old",
                currentNode = VersionNode(version = currentVersion),
                currentVersion = currentVersion,
                newValue = "new",
                newVersion = newVersion,
                context = context,
            )

        // Then
        assertTrue(result.resolution, "Should resolve")
        assertEquals(expectedVersion, result.node?.version, "Should enforce monotonic progression")
    }

    @Test
    fun `no modification when new version already ahead`() {
        // Given - new version is already newer
        val currentVersion = Version(1L, 1000L, 1000L)
        val newVersion = Version(1L, 1500L, 1500L) // Already ahead

        // When
        val result =
            resolver.applyLocalWrite(
                currentValue = "old",
                currentNode = VersionNode(version = currentVersion),
                currentVersion = currentVersion,
                newValue = "new",
                newVersion = newVersion,
                context = context,
            )

        // Then
        assertTrue(result.resolution, "Should resolve")
        assertEquals(newVersion, result.node?.version, "Should keep original version unchanged")
    }

    @Test
    fun `uses most recent version between currentNode and currentVersion`() {
        // Given - currentNode has more recent version than currentVersion parameter
        val currentVersion = Version(1L, 1L, 1000L)
        val nodeVersion = Version(1L, 2L, 1800L) // More recent
        val newVersion = Version(1L, 3L, 1500L)
        val expectedVersion = Version(1L, 3L, 1801L) // Based on node version

        // When
        val result =
            resolver.applyLocalWrite(
                currentValue = "old",
                currentNode = VersionNode(version = nodeVersion),
                currentVersion = currentVersion,
                newValue = "new",
                newVersion = newVersion,
                context = context,
            )

        // Then
        assertTrue(result.resolution, "Should resolve")
        assertEquals(expectedVersion, result.node?.version, "Should use node version for ensureAfter")
    }
}
