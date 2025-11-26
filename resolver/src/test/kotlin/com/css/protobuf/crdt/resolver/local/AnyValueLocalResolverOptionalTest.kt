package com.css.protobuf.crdt.resolver.local

import com.css.protobuf.crdt.resolver.OptionalAnyValueResolver
import com.css.protobuf.crdt.resolver.ResolutionDeltaContext
import com.css.protobuf.crdt.resolver.SingleValueResolver
import com.css.protobuf.crdt.resolver.TestVersionTreeResolver
import com.css.protobuf.crdt.resolver.Version
import com.css.protobuf.crdt.resolver.VersionNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnyValueLocalResolverOptionalTest {
    private val context = ResolutionDeltaContext<VersionNode, String>()
    private val decoder: (ByteArray) -> String = { it.decodeToString() }
    private val encoder: (String) -> ByteArray = { it.toByteArray() }
    private val singleResolver = SingleValueResolver(
        decoder = decoder,
        encoder = encoder,
        versionTreeResolver = TestVersionTreeResolver
    )
    private val resolver = OptionalAnyValueResolver(
        decoder = decoder,
        encoder = encoder,
        valueResolver = singleResolver,
        versionTreeResolver = TestVersionTreeResolver
    )

    @Test
    fun `null new value - with existing node`() {
        // Given
        val currentVersion = Version(1L, 1000L, 1000L)
        val newVersion = Version(1L, 1100L, 1100L)
        val currentNode = VersionNode(currentVersion)

        // When
        val result =
            resolver.applyLocalWrite(
                currentValue = "existing_value",
                currentNode = currentNode,
                currentVersion = currentVersion,
                newValue = null,
                newVersion = newVersion,
                context = context,
            )

        // Then
        assertFalse(result.resolution, "Should not resolve when new value is null")
        assertEquals("existing_value", result.value, "Should preserve current value")
        assertEquals(currentNode, result.node, "Should preserve existing node")
    }

    @Test
    fun `null new value - with null node`() {
        // Given
        val currentVersion = Version(1L, 1000L, 1000L)
        val newVersion = Version(1L, 1100L, 1100L)

        // When
        val result =
            resolver.applyLocalWrite(
                currentValue = "existing_value",
                currentNode = null,
                currentVersion = currentVersion,
                newValue = null,
                newVersion = newVersion,
                context = context,
            )

        // Then
        assertFalse(result.resolution, "Should not resolve when new value is null")
        assertEquals("existing_value", result.value, "Should preserve current value")
        assertEquals(currentVersion, result.node?.version, "Should create node with current version")
    }

    @Test
    fun `non-null new value - delegates to main resolver`() {
        // Given
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
        assertTrue(result.resolution, "Should resolve when new value is non-null")
        assertEquals("new_value", result.value, "Should return new value")
        assertEquals(newVersion, result.node?.version, "Should keep original version (already greater)")
    }
}
