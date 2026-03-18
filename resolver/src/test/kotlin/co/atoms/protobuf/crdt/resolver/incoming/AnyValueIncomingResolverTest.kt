package co.atoms.protobuf.crdt.resolver.incoming

import co.atoms.protobuf.crdt.resolver.ResolutionDeltaContext
import co.atoms.protobuf.crdt.resolver.SingleValueResolver
import co.atoms.protobuf.crdt.resolver.TestVersionTreeResolver
import co.atoms.protobuf.crdt.resolver.Version
import co.atoms.protobuf.crdt.resolver.VersionNode
import co.atoms.protobuf.crdt.resolver.version.ResolutionStrategy
import kotlin.test.Test
import kotlin.test.assertEquals

class AnyValueIncomingResolverTest {

    private val context = ResolutionDeltaContext<VersionNode, String>()
    private val decoder: (ByteArray) -> String = { it.decodeToString() }
    private val encoder: (String) -> ByteArray = { it.toByteArray() }
    private val resolver = SingleValueResolver(
        decoder = decoder,
        encoder = encoder,
        versionTreeResolver = TestVersionTreeResolver
    )

    @Test
    fun `incoming version higher - use incoming value`() {
        // Given
        val localVersion = Version(1L, 1000L, 1000L)
        val incomingVersion = Version(1L, 1100L, 1100L) // Higher timestamp
        val localNode = VersionNode(version = localVersion)
        val incomingNode = VersionNode(version = incomingVersion)

        // When
        val result =
            resolver.resolveConflict(
                localValue = "local_value",
                localNode = localNode,
                localVersion = localVersion,
                incomingValue = "incoming_value",
                incomingNode = incomingNode,
                incomingVersion = incomingVersion,
                context = context,
            )

        // Then
        assertEquals("incoming_value", result.value, "Should use incoming value")
        assertEquals(incomingNode, result.node, "Should use incoming node")
        assertEquals(ResolutionStrategy.INCOMING, result.resolution, "Should resolve as incoming")
    }

    @Test
    fun `local version higher - use local value`() {
        // Given
        val localVersion = Version(1L, 1100L, 1100L) // Higher timestamp
        val incomingVersion = Version(1L, 1000L, 1000L)
        val localNode = VersionNode(version = localVersion)
        val incomingNode = VersionNode(version = incomingVersion)

        // When
        val result =
            resolver.resolveConflict(
                localValue = "local_value",
                localNode = localNode,
                localVersion = localVersion,
                incomingValue = "incoming_value",
                incomingNode = incomingNode,
                incomingVersion = incomingVersion,
                context = context,
            )

        // Then
        assertEquals("local_value", result.value, "Should use local value")
        assertEquals(localNode, result.node, "Should use local node")
        assertEquals(ResolutionStrategy.LOCAL, result.resolution, "Should resolve as local")
    }

    @Test
    fun `equal versions - no change`() {
        // Given
        val version = Version(1L, 1000L, 1000L)
        val localNode = VersionNode(version = version)
        val incomingNode = VersionNode(version = version)

        // When
        val result =
            resolver.resolveConflict(
                localValue = "local_value",
                localNode = localNode,
                localVersion = version,
                incomingValue = "incoming_value",
                incomingNode = incomingNode,
                incomingVersion = version,
                context = context,
            )

        // Then
        assertEquals("local_value", result.value, "Should use local value")
        assertEquals(localNode, result.node, "Should use local node")
        assertEquals(ResolutionStrategy.NO_CHANGE, result.resolution, "Should resolve as no change")
    }

    @Test
    fun `null local node - create node with local version`() {
        // Given
        val localVersion = Version(1L, 1100L, 1100L)
        val incomingVersion = Version(1L, 1000L, 1000L)
        val incomingNode = VersionNode(version = incomingVersion)

        // When
        val result =
            resolver.resolveConflict(
                localValue = "local_value",
                localNode = null,
                localVersion = localVersion,
                incomingValue = "incoming_value",
                incomingNode = incomingNode,
                incomingVersion = incomingVersion,
                context = context,
            )

        // Then
        assertEquals("local_value", result.value, "Should use local value")
        assertEquals(localVersion, result.node?.version, "Should create local node")
        assertEquals(ResolutionStrategy.LOCAL, result.resolution, "Should resolve as local")
    }

    @Test
    fun `null incoming node - create node with incoming version`() {
        // Given
        val localVersion = Version(1L, 1000L, 1000L)
        val incomingVersion = Version(1L, 1100L, 1100L) // Higher
        val localNode = VersionNode(version = localVersion)

        // When
        val result =
            resolver.resolveConflict(
                localValue = "local_value",
                localNode = localNode,
                localVersion = localVersion,
                incomingValue = "incoming_value",
                incomingNode = null,
                incomingVersion = incomingVersion,
                context = context,
            )

        // Then
        assertEquals("incoming_value", result.value, "Should use incoming value")
        assertEquals(incomingVersion, result.node?.version, "Should create incoming node")
        assertEquals(ResolutionStrategy.INCOMING, result.resolution, "Should resolve as incoming")
    }
}
