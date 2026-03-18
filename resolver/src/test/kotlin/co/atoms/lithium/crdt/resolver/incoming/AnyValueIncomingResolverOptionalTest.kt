package co.atoms.lithium.crdt.resolver.incoming

import co.atoms.lithium.crdt.resolver.OptionalAnyValueResolver
import co.atoms.lithium.crdt.resolver.ResolutionDeltaContext
import co.atoms.lithium.crdt.resolver.SingleValueResolver
import co.atoms.lithium.crdt.resolver.TestVersionTreeResolver
import co.atoms.lithium.crdt.resolver.Version
import co.atoms.lithium.crdt.resolver.VersionNode
import co.atoms.lithium.crdt.resolver.version.ResolutionStrategy
import kotlin.test.Test
import kotlin.test.assertEquals

class AnyValueIncomingResolverOptionalTest {

    private val context = ResolutionDeltaContext<VersionNode, String>()
    private val stringDecoder: (ByteArray) -> String = { it.decodeToString() }
    private val stringEncoder: (String) -> ByteArray = { it.toByteArray() }
    private val stringResolver = SingleValueResolver(
        decoder = stringDecoder,
        encoder = stringEncoder,
        versionTreeResolver = TestVersionTreeResolver
    )
    private val resolver = OptionalAnyValueResolver(
        decoder = stringDecoder,
        encoder = stringEncoder,
        valueResolver = stringResolver,
        versionTreeResolver = TestVersionTreeResolver
    )

    @Test
    fun `incoming null value - always use local`() {
        // Given
        val localVersion = Version(1L, 1000L, 1000L)
        val incomingVersion = Version(1L, 1100L, 1100L)
        val localNode = VersionNode(version = localVersion)
        val incomingNode = VersionNode(version = incomingVersion)

        // When
        val result =
            resolver.resolveConflict(
                localValue = "local_value",
                localNode = localNode,
                localVersion = localVersion,
                incomingValue = null, // Null incoming
                incomingNode = incomingNode,
                incomingVersion = incomingVersion,
                context = context,
            )

        // Then
        assertEquals(context.changes.size, 0, "Should have no change.")
        assertEquals("local_value", result.value, "Should use local value despite higher incoming version")
        assertEquals(localNode, result.node, "Should use local node")
        assertEquals(ResolutionStrategy.LOCAL, result.resolution, "Should resolve as local")
    }

    @Test
    fun `both values null - no change`() {
        // Given
        val localVersion = Version(1L, 1000L, 1000L)
        val incomingVersion = Version(1L, 1100L, 1100L)
        val localNode = VersionNode(version = localVersion)

        // When
        val result =
            resolver.resolveConflict(
                localValue = null,
                localNode = localNode,
                localVersion = localVersion,
                incomingValue = null,
                incomingNode = null,
                incomingVersion = incomingVersion,
                context = context,
            )

        // Then
        assertEquals(context.changes.size, 0, "Should have no change.")
        assertEquals(null, result.value, "Should preserve null value")
        assertEquals(localNode, result.node, "Should use local node")
        assertEquals(ResolutionStrategy.NO_CHANGE, result.resolution, "Should not change")
    }

    @Test
    fun `non-null incoming value - delegates to main resolver`() {
        // Given
        val localVersion = Version(1L, 1000L, 1000L)
        val incomingVersion = Version(1L, 1100L, 1100L) // Higher
        val localNode = VersionNode(version = localVersion)
        val incomingNode = VersionNode(version = incomingVersion)

        // When
        val result =
            resolver.resolveConflict(
                localValue = "local_value",
                localNode = localNode,
                localVersion = localVersion,
                incomingValue = "incoming_value", // Non-null
                incomingNode = incomingNode,
                incomingVersion = incomingVersion,
                context = context,
            )

        // Then
        assertEquals(context.changes.size, 1, "Should have 1 change.")
        assertEquals("incoming_value", result.value, "Should use incoming value (higher version)")
        assertEquals(incomingNode, result.node, "Should use incoming node")
        assertEquals(ResolutionStrategy.INCOMING, result.resolution, "Should resolve as incoming")
    }
}
