package com.css.protobuf.crdt.resolver.local

import com.css.protobuf.crdt.resolver.RepeatedResolver
import com.css.protobuf.crdt.resolver.ResolutionDeltaContext
import com.css.protobuf.crdt.resolver.SingleValueResolver
import com.css.protobuf.crdt.resolver.TestVersionTreeResolver
import com.css.protobuf.crdt.resolver.Version
import com.css.protobuf.crdt.resolver.VersionNode
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RepeatedCrdtLocalResolverTest {
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
    fun `fast path - lists are equal`() {
        // Given
        val currentVersion = Version(1L, 1000L, 1000L)
        val newVersion = Version(1L, 1100L, 1100L)
        val currentList = listOf("item1", "item2", "item3")
        val newList = listOf("item1", "item2", "item3") // Same content
        val currentNode =
            VersionNode(
                version = currentVersion,
                repeated =
                (listOf(
                    VersionNode(version = currentVersion),
                    VersionNode(version = currentVersion),
                    VersionNode(version = currentVersion),
                )),
            )

        // When
        val result =
            resolver.applyLocalWrite(
                currentValue = currentList,
                currentNode = currentNode,
                currentVersion = currentVersion,
                newValue = newList,
                newVersion = newVersion,
                context = context,
            )

        // Then
        assertFalse(result.resolution, "Should not resolve when lists are equal")
        assertEquals(currentList, result.value, "Should return current list")
        assertEquals(currentNode, result.node, "Should return current node")
    }

    @Test
    fun `fast path - both lists null`() {
        // Given
        val currentVersion = Version(1L, 1000L, 1000L)
        val newVersion = Version(1L, 1100L, 1100L)

        // When
        val result =
            resolver.applyLocalWrite(
                currentValue = null,
                currentNode = null,
                currentVersion = currentVersion,
                newValue = null,
                newVersion = newVersion,
                context = context,
            )

        // Then
        assertFalse(result.resolution, "Should not resolve when both lists are null")
        assertEquals(emptyList<String>(), result.value, "Should return empty list")
        assertEquals(currentVersion, result.node?.version, "Should create node with current version")
    }

    @Test
    fun `add elements to empty list`() {
        // Given
        val currentVersion = Version(1L, 1000L, 1000L)
        val newVersion = Version(1L, 1100L, 1100L)
        val currentList = emptyList<String>()
        val newList = listOf("item1", "item2")

        // When
        val result =
            resolver.applyLocalWrite(
                currentValue = currentList,
                currentNode = null,
                currentVersion = currentVersion,
                newValue = newList,
                newVersion = newVersion,
                context = context,
            )

        // Then
        assertTrue(result.resolution, "Should resolve when adding elements")
        assertEquals(newList, result.value, "Should return new list")
        assertEquals(newVersion, result.node?.version, "Should set list version")
        assertNotNull(result.node?.repeated, "Should create repeated structure")
        assertEquals(2, result.node.repeated.size, "Should track all elements")
        assertEquals(newVersion, result.node.repeated[0].version, "Should set element versions")
        assertEquals(newVersion, result.node.repeated[1].version, "Should set element versions")
    }

    @Test
    fun `modify existing element`() {
        // Given
        val currentVersion = Version(1L, 1000L, 1000L)
        val newVersion = Version(1L, 1100L, 1100L)
        val currentList = listOf("old_value", "unchanged")
        val newList = listOf("new_value", "unchanged")
        val currentNode =
            VersionNode(
                version = currentVersion,
                repeated =
                (listOf(
                    VersionNode(version = currentVersion),
                    VersionNode(version = currentVersion),
                )),
            )

        // When
        val result =
            resolver.applyLocalWrite(
                currentValue = currentList,
                currentNode = currentNode,
                currentVersion = currentVersion,
                newValue = newList,
                newVersion = newVersion,
                context = context,
            )

        // Then
        assertTrue(result.resolution, "Should resolve when modifying element")
        assertEquals(newList, result.value, "Should return updated list")
        assertEquals(newVersion, result.node?.version, "Should set list version")
        assertEquals(
            newVersion,
            result.node?.repeated?.get(0)?.version,
            "Should update changed element version",
        )
        assertEquals(
            currentVersion,
            result.node?.repeated?.get(1)?.version,
            "Should preserve unchanged element version",
        )
    }

    @Test
    fun `shrink list by removing elements`() {
        // Given
        val currentVersion = Version(1L, 1000L, 1000L)
        val newVersion = Version(1L, 1100L, 1100L)
        val currentList = listOf("keep1", "keep2", "remove1", "remove2")
        val newList = listOf("keep1", "keep2") // Remove last 2 elements
        val currentNode =
            VersionNode(
                version = currentVersion,
                repeated =
                (listOf(
                    VersionNode(version = currentVersion),
                    VersionNode(version = currentVersion),
                    VersionNode(version = currentVersion),
                    VersionNode(version = currentVersion),
                )),
            )

        // When
        val result =
            resolver.applyLocalWrite(
                currentValue = currentList,
                currentNode = currentNode,
                currentVersion = currentVersion,
                newValue = newList,
                newVersion = newVersion,
                context = context,
            )

        // Then
        assertTrue(result.resolution, "Should resolve when shrinking list")
        assertEquals(newList, result.value, "Should return shortened list")
        assertEquals(newVersion, result.node?.version, "Should set list version")
        // Should track all 4 positions including tombstones for removed elements
        assertEquals(4, result.node?.repeated?.size, "Should preserve all position tracking")
        assertEquals(
            currentVersion,
            result.node?.repeated?.get(0)?.version,
            "Should preserve kept element versions",
        )
        assertEquals(
            currentVersion,
            result.node?.repeated?.get(1)?.version,
            "Should preserve kept element versions",
        )
        assertEquals(newVersion, result.node?.repeated?.get(2)?.version, "Should set removal versions")
        assertEquals(newVersion, result.node?.repeated?.get(3)?.version, "Should set removal versions")
    }

    @Test
    fun `grow list by adding elements`() {
        // Given
        val currentVersion = Version(1L, 1000L, 1000L)
        val newVersion = Version(1L, 1100L, 1100L)
        val currentList = listOf("existing1", "existing2")
        val newList = listOf("existing1", "existing2", "new1", "new2")

        // When
        val result =
            resolver.applyLocalWrite(
                currentValue = currentList,
                currentNode = null,
                currentVersion = currentVersion,
                newValue = newList,
                newVersion = newVersion,
                context = context,
            )

        // Then
        assertTrue(result.resolution, "Should resolve when growing list")
        assertEquals(newList, result.value, "Should return expanded list")
        assertEquals(newVersion, result.node?.version, "Should set list version")
        assertEquals(4, result.node?.repeated?.size, "Should track all elements")
    }

    @Test
    fun `mixed operations - modify and resize`() {
        // Given
        val currentVersion = Version(1L, 1000L, 1000L)
        val newVersion = Version(1L, 1100L, 1100L)
        val currentList = listOf("keep", "modify_old", "remove")
        val newList = listOf("keep", "modify_new", "add1", "add2")
        val currentNode =
            VersionNode(
                version = currentVersion,
                repeated =
                (listOf(
                    VersionNode(version = currentVersion),
                    VersionNode(version = currentVersion),
                    VersionNode(version = currentVersion),
                )),
            )

        // When
        val result =
            resolver.applyLocalWrite(
                currentValue = currentList,
                currentNode = currentNode,
                currentVersion = currentVersion,
                newValue = newList,
                newVersion = newVersion,
                context = context,
            )

        // Then
        assertTrue(result.resolution, "Should resolve with mixed operations")
        assertEquals(newList, result.value, "Should return modified list")
        assertEquals(newVersion, result.node?.version, "Should set list version")
        assertEquals(4, result.node?.repeated?.size, "Should track max size")

        val entries = result.node?.repeated!!
        assertEquals(currentVersion, entries[0].version, "Should preserve unchanged element") // "keep"
        assertEquals(newVersion, entries[1].version, "Should update modified element") // "modify_new"
        assertEquals(newVersion, entries[2].version, "Should set removal version") // "remove" -> tombstone
        assertEquals(newVersion, entries[3].version, "Should set new element version") // "add1"
    }

    @Test
    fun `handles version progression in list elements`() {
        // Given - existing element has newer version, should apply ensureAfter
        val currentVersion = Version(1L, 1L, 1000L)
        val elementVersion = Version(1L, 2L, 1200L) // Newer than current
        val newVersion = Version(1L, 3L, 1100L) // Older than element
        val expectedVersion = Version(1L, 3L, 1201L) // ensureAfter result

        val currentList = listOf("old_value")
        val newList = listOf("new_value")
        val currentNode =
            VersionNode(
                version = currentVersion,
                repeated = (listOf(VersionNode(version = elementVersion))),
            )

        // When
        val result =
            resolver.applyLocalWrite(
                currentValue = currentList,
                currentNode = currentNode,
                currentVersion = currentVersion,
                newValue = newList,
                newVersion = newVersion,
                context = context,
            )

        // Then
        assertTrue(result.resolution, "Should resolve")
        assertEquals(expectedVersion, result.node?.repeated?.get(0)?.version, "Should apply ensureAfter to element")
    }

    @Test
    fun `empty to empty list creates minimal structure`() {
        // Given
        val currentVersion = Version(1L, 1000L, 1000L)
        val newVersion = Version(1L, 1100L, 1100L)
        val currentList = emptyList<String>()
        val newList = emptyList<String>()

        // When
        val result =
            resolver.applyLocalWrite(
                currentValue = currentList,
                currentNode = null,
                currentVersion = currentVersion,
                newValue = newList,
                newVersion = newVersion,
                context = context,
            )

        // Then
        assertFalse(result.resolution, "Should not resolve when both lists empty")
        assertEquals(emptyList<String>(), result.value, "Should return empty list")
        assertEquals(currentVersion, result.node?.version, "Should create node with current version")
    }
}
