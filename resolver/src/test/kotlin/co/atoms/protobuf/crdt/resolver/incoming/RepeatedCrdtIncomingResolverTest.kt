package co.atoms.protobuf.crdt.resolver.incoming

import co.atoms.protobuf.crdt.resolver.RepeatedResolver
import co.atoms.protobuf.crdt.resolver.ResolutionDeltaContext
import co.atoms.protobuf.crdt.resolver.SingleValueResolver
import co.atoms.protobuf.crdt.resolver.TestVersionTreeResolver
import co.atoms.protobuf.crdt.resolver.Version
import co.atoms.protobuf.crdt.resolver.VersionNode
import co.atoms.protobuf.crdt.resolver.version.ResolutionStrategy
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

class RepeatedCrdtIncomingResolverTest {
    private val context = ResolutionDeltaContext<VersionNode, String>()
    private val decoder: (ByteArray) -> String = { it.decodeToString() }
    private val encoder: (String) -> ByteArray = { it.toByteArray() }
    private val valueResolver = SingleValueResolver(
        decoder = decoder,
        encoder = encoder,
        versionTreeResolver = TestVersionTreeResolver
    )
    private val mockListDecoder: (ByteArray) -> List<String> = mockk()
    private val mockListEncoder: (List<String>) -> ByteArray = mockk()
    private val resolver =
        RepeatedResolver(
            decoder = mockListDecoder,
            encoder = mockListEncoder,
            valueResolver = valueResolver,
            versionTreeResolver = TestVersionTreeResolver,
        )

    @Test
    fun `identical lists - no change`() {
        // Given
        val version = Version(1L, 1000L, 1000L)
        val list = listOf("item1", "item2", "item3")
        val entries =
            listOf(
                VersionNode(version = version),
                VersionNode(version = version),
                VersionNode(version = version),
            )
        val node = VersionNode(version = version, repeated = (entries))

        // When
        val result =
            resolver.resolveConflict(
                localValue = list,
                localNode = node,
                localVersion = version,
                incomingValue = list, // Same list
                incomingNode = node, // Same node
                incomingVersion = version,
                context = context,
            )

        // Then
        assertEquals(ResolutionStrategy.NO_CHANGE, result.resolution, "Should not change when identical")
        assertEquals(list, result.value, "Should return same list")
        assertEquals(node, result.node, "Should return same node")
    }

    @Test
    fun `list size determined by higher version`() {
        // Given
        val localVersion = Version(1L, 1000L, 1000L)
        val incomingVersion = Version(1L, 1100L, 1100L) // Higher

        // Local list: [item1, item2, item3] (size 3)
        val localList = listOf("item1", "item2", "item3")
        val localNode =
            VersionNode(
                version = localVersion,
                repeated =
                (listOf(
                    VersionNode(version = localVersion),
                    VersionNode(version = localVersion),
                    VersionNode(version = localVersion),
                )),
            )

        // Incoming list: [item1, item2] (size 2, higher version)
        val incomingList = listOf("item1", "item2")
        val incomingNode =
            VersionNode(
                version = incomingVersion,
                repeated =
                (listOf(
                    VersionNode(version = incomingVersion),
                    VersionNode(version = incomingVersion),
                )),
            )

        // When
        val result =
            resolver.resolveConflict(
                localValue = localList,
                localNode = localNode,
                localVersion = localVersion,
                incomingValue = incomingList,
                incomingNode = incomingNode,
                incomingVersion = incomingVersion,
                context = context,
            )

        // Then
        assertEquals(ResolutionStrategy.INCOMING, result.resolution, "Should adopt incoming")
        assertEquals(2, result.value?.size, "Should use incoming size (2)")
        assertEquals(incomingList, result.value, "Should use incoming list")
    }

    @Test
    fun `per-element conflict resolution`() {
        // Given
        val localVersion = Version(1L, 1000L, 1000L)
        val incomingVersion = Version(1L, 1100L, 1100L) // Higher list version

        // Local list: [local1, local2] with per-element versions
        val localList = listOf("local1", "local2")
        val localNode =
            VersionNode(
                version = localVersion,
                repeated =
                (listOf(
                    VersionNode(version = Version(1L, 1200L, 1200L)), // Element 0 newer locally
                    VersionNode(version = Version(1L, 900L, 900L)), // Element 1 older locally
                )),
            )

        // Incoming list: [incoming1, incoming2] with per-element versions
        val incomingList = listOf("incoming1", "incoming2")
        val incomingNode =
            VersionNode(
                version = incomingVersion,
                repeated =
                (listOf(
                    VersionNode(version = Version(1L, 1100L, 1100L)), // Element 0 older than local
                    VersionNode(version = Version(1L, 1000L, 1000L)), // Element 1 newer than local
                )),
            )

        // When
        val result =
            resolver.resolveConflict(
                localValue = localList,
                localNode = localNode,
                localVersion = localVersion,
                incomingValue = incomingList,
                incomingNode = incomingNode,
                incomingVersion = incomingVersion,
                context = context,
            )

        // Then
        assertEquals(ResolutionStrategy.MERGED_VALUES, result.resolution, "Should merge values")
        assertEquals(2, result.value?.size, "Should have 2 elements")
        assertEquals("local1", result.value?.get(0), "Element 0 should use local (newer version)")
        assertEquals("incoming2", result.value?.get(1), "Element 1 should use incoming (newer version)")
    }

    @Test
    fun `growing list with new elements`() {
        // Given
        val localVersion = Version(1L, 1000L, 1000L)
        val incomingVersion = Version(1L, 1100L, 1100L) // Higher

        // Local: [item1]
        val localList = listOf("item1")
        val localNode =
            VersionNode(
                version = localVersion,
                repeated = (listOf(VersionNode(version = localVersion))),
            )

        // Incoming: [item1, item2, item3] (longer)
        val incomingList = listOf("item1", "item2", "item3")
        val incomingNode =
            VersionNode(
                version = incomingVersion,
                repeated =
                (listOf(
                    VersionNode(version = incomingVersion),
                    VersionNode(version = incomingVersion),
                    VersionNode(version = incomingVersion),
                )),
            )

        // When
        val result =
            resolver.resolveConflict(
                localValue = localList,
                localNode = localNode,
                localVersion = localVersion,
                incomingValue = incomingList,
                incomingNode = incomingNode,
                incomingVersion = incomingVersion,
                context = context,
            )

        // Then
        assertEquals(ResolutionStrategy.INCOMING, result.resolution, "Should use incoming")
        assertEquals(3, result.value?.size, "Should use incoming size (3)")
        assertEquals(incomingList, result.value, "Should use incoming list")
    }

    @Test
    fun `shrinking list removes elements`() {
        // Given
        val localVersion = Version(1L, 1000L, 1000L)
        val incomingVersion = Version(1L, 1100L, 1100L) // Higher

        // Local: [item1, item2, item3]
        val localList = listOf("item1", "item2", "item3")
        val localNode =
            VersionNode(
                version = localVersion,
                repeated =
                (listOf(
                    VersionNode(version = localVersion),
                    VersionNode(version = localVersion),
                    VersionNode(version = localVersion),
                )),
            )

        // Incoming: [item1] (shorter, but higher version)
        val incomingList = listOf("item1")
        val incomingNode =
            VersionNode(
                version = incomingVersion,
                repeated = (listOf(VersionNode(version = incomingVersion))),
            )

        // When
        val result =
            resolver.resolveConflict(
                localValue = localList,
                localNode = localNode,
                localVersion = localVersion,
                incomingValue = incomingList,
                incomingNode = incomingNode,
                incomingVersion = incomingVersion,
                context = context,
            )

        // Then
        assertEquals(ResolutionStrategy.INCOMING, result.resolution, "Should use incoming")
        assertEquals(1, result.value?.size, "Should use incoming size (1)")
        assertEquals(incomingList, result.value, "Should use incoming list")
    }

    @Test
    fun `empty lists`() {
        // Given
        val localVersion = Version(1L, 1000L, 1000L)
        val incomingVersion = Version(1L, 1100L, 1100L)

        // When
        val result =
            resolver.resolveConflict(
                localValue = emptyList(),
                localNode = null,
                localVersion = localVersion,
                incomingValue = emptyList(),
                incomingNode = null,
                incomingVersion = incomingVersion,
                context = context,
            )

        // Then
        assertEquals(ResolutionStrategy.NO_CHANGE, result.resolution, "Should not change empty lists")
        assertEquals(emptyList<String>(), result.value, "Should return empty list")
    }
}
