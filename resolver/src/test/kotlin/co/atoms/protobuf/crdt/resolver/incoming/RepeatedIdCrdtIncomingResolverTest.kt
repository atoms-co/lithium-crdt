package co.atoms.protobuf.crdt.resolver.incoming

import co.atoms.protobuf.crdt.resolver.CrdtResolver
import co.atoms.protobuf.crdt.resolver.NodeMergeResult
import co.atoms.protobuf.crdt.resolver.RepeatedIdResolver
import co.atoms.protobuf.crdt.resolver.ResolutionDeltaContext
import co.atoms.protobuf.crdt.resolver.TestMessage
import co.atoms.protobuf.crdt.resolver.TestVersionTreeResolver
import co.atoms.protobuf.crdt.resolver.Version
import co.atoms.protobuf.crdt.resolver.VersionNode
import co.atoms.protobuf.crdt.resolver.version.ResolutionStrategy
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals

class RepeatedIdCrdtIncomingResolverTest {
    private val localVersion = Version(1L, 1L, 1L)
    private val incomingVersion = Version(1L, 3L, 3L)
    private val context = ResolutionDeltaContext<VersionNode, String>()
    private val decoder: (ByteArray) -> List<TestMessage> = mockk()
    private val encoder: (List<TestMessage>) -> ByteArray = mockk()
    private val returnedMapResult =
        NodeMergeResult(
            resolution = ResolutionStrategy.MERGED_VALUES,
            value =
            mapOf(
                "key1" to TestMessage(stringValue = "key1", int32Value = 2),
                "key2" to TestMessage(stringValue = "key2", int32Value = 3),
                "key3" to TestMessage(stringValue = "key3", int32Value = 4),
            ),
            node = mockk<VersionNode>(),
        )
    private val mapResolver =
        mockk<CrdtResolver<Map<String, TestMessage>, VersionNode, Version, String>> {
            every {
                resolveConflict(
                    localValue = any(),
                    localNode = any(),
                    localVersion = any(),
                    incomingValue = any(),
                    incomingNode = any(),
                    incomingVersion = any(),
                    context = context,
                )
            } returns returnedMapResult
        }
    private val localNode = mockk<VersionNode>()
    private val incomingNode = mockk<VersionNode>()

    private val resolver =
        RepeatedIdResolver(
            decoder = decoder,
            encoder = encoder,
            keyTransformer = { it.stringValue },
            mapResolver = mapResolver,
            versionTreeResolver = TestVersionTreeResolver,
        )

    @Test
    fun testTransformList() {
        val result =
            resolver.resolveConflict(
                localValue =
                listOf(
                    TestMessage(stringValue = "key1", int32Value = 1),
                    TestMessage(stringValue = "key1", int32Value = 2),
                    TestMessage(stringValue = "key2", int32Value = 3),
                    TestMessage(stringValue = "key3", int32Value = 4),
                ),
                localNode = localNode,
                localVersion = localVersion,
                incomingValue =
                listOf(
                    TestMessage(stringValue = "key2", int32Value = 1),
                    TestMessage(stringValue = "key2", int32Value = 2),
                    TestMessage(stringValue = "key3", int32Value = 3),
                    TestMessage(stringValue = "key4", int32Value = 4),
                ),
                incomingNode = incomingNode,
                incomingVersion = incomingVersion,
                context = context,
            )

        assertEquals(returnedMapResult.value?.values?.toList(), result.value)
        assertEquals(returnedMapResult.resolution, result.resolution)
        assertEquals(returnedMapResult.node, result.node)

        verify {
            mapResolver.resolveConflict(
                localValue =
                mapOf(
                    "key1" to TestMessage(stringValue = "key1", int32Value = 2),
                    "key2" to TestMessage(stringValue = "key2", int32Value = 3),
                    "key3" to TestMessage(stringValue = "key3", int32Value = 4),
                ),
                localNode = localNode,
                localVersion = localVersion,
                incomingValue =
                mapOf(
                    "key2" to TestMessage(stringValue = "key2", int32Value = 2),
                    "key3" to TestMessage(stringValue = "key3", int32Value = 3),
                    "key4" to TestMessage(stringValue = "key4", int32Value = 4),
                ),
                incomingVersion = incomingVersion,
                incomingNode = incomingNode,
                context = context,
            )
        }
    }
}
