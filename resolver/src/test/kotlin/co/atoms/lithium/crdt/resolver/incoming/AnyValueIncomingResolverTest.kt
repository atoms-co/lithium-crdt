package co.atoms.lithium.crdt.resolver.incoming

import co.atoms.lithium.crdt.resolver.ResolutionDeltaContext
import co.atoms.lithium.crdt.resolver.SingleValueResolver
import co.atoms.lithium.crdt.resolver.TestVersionTreeResolver
import co.atoms.lithium.crdt.resolver.Version
import co.atoms.lithium.crdt.resolver.VersionNode
import co.atoms.lithium.crdt.resolver.version.ResolutionStrategy
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


    @Test
    fun `incoming version higher, same value - no change entry`() {
        val ctx = ResolutionDeltaContext<VersionNode, String>()
        val localVersion = Version(1L, 1000L, 1000L)
        val incomingVersion = Version(1L, 1100L, 1100L)

        val result = resolver.resolveConflict(
            localValue = "same",
            localNode = VersionNode(version = localVersion),
            localVersion = localVersion,
            incomingValue = "same",
            incomingNode = VersionNode(version = incomingVersion),
            incomingVersion = incomingVersion,
            context = ctx,
        )

        assertEquals(ResolutionStrategy.INCOMING, result.resolution)
        assertEquals(0, ctx.result.size, "No change entry when values are identical")
    }

    @Test
    fun `incoming version higher, different value - change entry added`() {
        val ctx = ResolutionDeltaContext<VersionNode, String>()
        val localVersion = Version(1L, 1000L, 1000L)
        val incomingVersion = Version(1L, 1100L, 1100L)

        val result = resolver.resolveConflict(
            localValue = "old",
            localNode = VersionNode(version = localVersion),
            localVersion = localVersion,
            incomingValue = "new",
            incomingNode = VersionNode(version = incomingVersion),
            incomingVersion = incomingVersion,
            context = ctx,
        )

        assertEquals(ResolutionStrategy.INCOMING, result.resolution)
        assertEquals(1, ctx.result.size, "Change entry added when values differ")
    }

    @Test
    fun `incoming version higher, both null - no change entry`() {
        val ctx = ResolutionDeltaContext<VersionNode, String>()
        val localVersion = Version(1L, 1000L, 1000L)
        val incomingVersion = Version(1L, 1100L, 1100L)

        val result = resolver.resolveConflict(
            localValue = null,
            localNode = VersionNode(version = localVersion),
            localVersion = localVersion,
            incomingValue = null,
            incomingNode = VersionNode(version = incomingVersion),
            incomingVersion = incomingVersion,
            context = ctx,
        )

        assertEquals(ResolutionStrategy.INCOMING, result.resolution)
        assertEquals(0, ctx.result.size, "No change entry when both values are null")
    }

    @Test
    fun `incoming version higher, null to non-null - change entry added`() {
        val ctx = ResolutionDeltaContext<VersionNode, String>()
        val localVersion = Version(1L, 1000L, 1000L)
        val incomingVersion = Version(1L, 1100L, 1100L)

        val result = resolver.resolveConflict(
            localValue = null,
            localNode = VersionNode(version = localVersion),
            localVersion = localVersion,
            incomingValue = "new",
            incomingNode = VersionNode(version = incomingVersion),
            incomingVersion = incomingVersion,
            context = ctx,
        )

        assertEquals(ResolutionStrategy.INCOMING, result.resolution)
        assertEquals(1, ctx.result.size, "Change entry added for null to non-null")
    }

    @Test
    fun `incoming version higher, non-null to null - change entry added`() {
        val ctx = ResolutionDeltaContext<VersionNode, String>()
        val localVersion = Version(1L, 1000L, 1000L)
        val incomingVersion = Version(1L, 1100L, 1100L)

        val result = resolver.resolveConflict(
            localValue = "old",
            localNode = VersionNode(version = localVersion),
            localVersion = localVersion,
            incomingValue = null,
            incomingNode = VersionNode(version = incomingVersion),
            incomingVersion = incomingVersion,
            context = ctx,
        )

        assertEquals(ResolutionStrategy.INCOMING, result.resolution)
        assertEquals(1, ctx.result.size, "Change entry added for non-null to null")
    }
}
