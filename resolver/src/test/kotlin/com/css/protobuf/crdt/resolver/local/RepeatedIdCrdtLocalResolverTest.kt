package com.css.protobuf.crdt.resolver.local

import com.css.protobuf.crdt.resolver.CrdtResolver
import com.css.protobuf.crdt.resolver.NodeMergeResult
import com.css.protobuf.crdt.resolver.RepeatedIdResolver
import com.css.protobuf.crdt.resolver.ResolutionDeltaContext
import com.css.protobuf.crdt.resolver.TestMessage
import com.css.protobuf.crdt.resolver.TestVersionTreeResolver
import com.css.protobuf.crdt.resolver.Version
import com.css.protobuf.crdt.resolver.VersionNode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals

class RepeatedIdCrdtLocalResolverTest {
    private val currentVersion = Version(actorId = 1L, actorVersion = 1L, timestamp = 1L)
    private val newVersion = Version(actorId = 1L, actorVersion = 3L, timestamp = 3L)
    private val returnedMapResult =
        NodeMergeResult(
            resolution = true,
            value =
            mapOf(
                "key1" to TestMessage(stringValue = "key1", int32Value = 2),
                "key2" to TestMessage(stringValue = "key2", int32Value = 3),
                "key3" to TestMessage(stringValue = "key3", int32Value = 4),
            ),
            node = mockk<VersionNode>(),
        )
    private val context = ResolutionDeltaContext<VersionNode, String>()
    private val decoder: (ByteArray) -> List<TestMessage> = mockk()
    private val encoder: (List<TestMessage>) -> ByteArray = mockk()
    private val mapResolver =
        mockk<CrdtResolver<Map<String, TestMessage>, VersionNode, Version, String>> {
            every {
                applyLocalWrite(
                    currentValue = any(),
                    currentNode = any(),
                    currentVersion = any(),
                    newValue = any(),
                    newVersion = any(),
                    context = context,
                )
            } returns returnedMapResult
        }
    private val currentNode = mockk<VersionNode>()

    private val resolver = RepeatedIdResolver(
        decoder = decoder,
        encoder = encoder,
        keyTransformer = { it.stringValue },
        mapResolver = mapResolver,
        versionTreeResolver = TestVersionTreeResolver,
    )

    @Test
    fun testTransformList() {
        val result =
            resolver.applyLocalWrite(
                currentValue =
                listOf(
                    TestMessage(stringValue = "key1", int32Value = 1),
                    TestMessage(stringValue = "key1", int32Value = 2),
                    TestMessage(stringValue = "key2", int32Value = 3),
                    TestMessage(stringValue = "key3", int32Value = 4),
                ),
                currentNode = currentNode,
                currentVersion = currentVersion,
                newValue =
                listOf(
                    TestMessage(stringValue = "key2", int32Value = 1),
                    TestMessage(stringValue = "key2", int32Value = 2),
                    TestMessage(stringValue = "key3", int32Value = 3),
                    TestMessage(stringValue = "key4", int32Value = 4),
                ),
                newVersion = newVersion,
                context = context,
            )

        assertEquals(returnedMapResult.value?.values?.toList(), result.value)
        assertEquals(returnedMapResult.resolution, result.resolution)
        assertEquals(returnedMapResult.node, result.node)

        verify {
            mapResolver.applyLocalWrite(
                currentValue =
                mapOf(
                    "key1" to TestMessage(stringValue = "key1", int32Value = 2),
                    "key2" to TestMessage(stringValue = "key2", int32Value = 3),
                    "key3" to TestMessage(stringValue = "key3", int32Value = 4),
                ),
                currentNode = currentNode,
                currentVersion = currentVersion,
                newValue =
                mapOf(
                    "key2" to TestMessage(stringValue = "key2", int32Value = 2),
                    "key3" to TestMessage(stringValue = "key3", int32Value = 3),
                    "key4" to TestMessage(stringValue = "key4", int32Value = 4),
                ),
                newVersion = newVersion,
                context = context,
            )
        }
    }
}
