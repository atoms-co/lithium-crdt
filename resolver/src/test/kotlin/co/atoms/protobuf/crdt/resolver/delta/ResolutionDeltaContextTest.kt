package co.atoms.protobuf.crdt.resolver.delta

import co.atoms.protobuf.crdt.resolver.ResolutionDeltaContext
import co.atoms.protobuf.crdt.resolver.Version
import co.atoms.protobuf.crdt.resolver.VersionNode
import co.atoms.protobuf.crdt.resolver.withPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ResolutionDeltaContextTest {
    @Test
    fun `pushPath and popPath manage depth correctly`() {
        // Given
        val context = ResolutionDeltaContext<VersionNode, String>()

        // When
        context.pushPath("field1")
        context.pushPath("field2")

        // Then
        assertEquals(2, context.pathDepth, "Depth should be 2 after two pushes")

        // When
        context.popPath()

        // Then
        assertEquals(1, context.pathDepth, "Depth should be 1 after one pop")

        // When
        context.popPath()

        // Then
        assertEquals(0, context.pathDepth, "Depth should be 0 after popping all")
    }

    @Test
    fun `popPath below zero is safe`() {
        // Given
        val context = ResolutionDeltaContext<VersionNode, String>()

        // When - pop when depth is already 0
        context.popPath()

        // Then - no exception, depth stays at 0
        assertEquals(0, context.pathDepth)
    }

    @Test
    fun `addChange captures current path snapshot`() {
        // Given
        val context = ResolutionDeltaContext<VersionNode, String>()
        val encoder: (String) -> ByteArray = { it.toByteArray() }

        // When - add change at depth 0
        context.addChange(newValue = "root", versionNode = VersionNode(Version(1L, 1L, 1L)), encoder = encoder)

        // Then
        assertEquals(1, context.changes.size, "Should have 1 change")
        assertEquals(emptyList(), context.changes[0].pathComponents, "Root change should have empty path")
        assertEquals("root", context.changes[0].value)

        // When - add change at depth 2
        context.pushPath("field1")
        context.pushPath("field2")
        context.addChange(newValue = "nested", versionNode = VersionNode(Version(1L, 2L, 2L)), encoder = encoder)

        // Then
        assertEquals(2, context.changes.size, "Should have 2 changes")
        assertEquals(listOf("field1", "field2"), context.changes[1].pathComponents, "Should capture nested path")
        assertEquals("nested", context.changes[1].value)
    }

    @Test
    fun `path snapshot is immutable after capture`() {
        // Given
        val context = ResolutionDeltaContext<VersionNode, String>()
        val encoder: (String) -> ByteArray = { it.toByteArray() }

        // When - capture path at depth 2
        context.pushPath("field1")
        context.pushPath("field2")
        context.addChange(newValue = "value", versionNode = VersionNode(Version(1L, 1L, 1L)), encoder = encoder)

        // Then - verify captured path
        val capturedPath = context.changes[0].pathComponents
        assertEquals(listOf("field1", "field2"), capturedPath)

        // When - modify the stack
        context.pushPath("field3")
        context.popPath()
        context.popPath()
        context.popPath()

        // Then - captured path remains unchanged
        assertEquals(listOf("field1", "field2"), capturedPath, "Captured path should not change")
    }

    @Test
    fun `withPath provides exception-safe push and pop`() {
        // Given
        val context = ResolutionDeltaContext<VersionNode, String>()

        // When - use withPath
        val result =
            context.withPath("field1") {
                assertEquals(1, context.pathDepth, "Depth should be 1 inside withPath")
                "success"
            }

        // Then
        assertEquals("success", result)
        assertEquals(0, context.pathDepth, "Depth should be 0 after withPath")
    }

    @Test
    fun `withPath pops on exception`() {
        // Given
        val context = ResolutionDeltaContext<VersionNode, String>()

        // When - exception thrown inside withPath
        assertFailsWith<IllegalStateException> {
            context.withPath("field1") {
                assertEquals(1, context.pathDepth)
                throw IllegalStateException("test exception")
            }
        }

        // Then - path is still popped
        assertEquals(0, context.pathDepth, "Depth should be 0 even after exception")
    }

    @Test
    fun `stack grows dynamically`() {
        // Given
        val context = ResolutionDeltaContext<VersionNode, String>()

        // When - push beyond initial size of 32
        repeat(50) { i -> context.pushPath("field$i") }

        // Then
        assertEquals(50, context.pathDepth, "Should grow to depth 50")

        // When - capture path
        val encoder: (String) -> ByteArray = { it.toByteArray() }
        context.addChange(newValue = "deep", versionNode = VersionNode(Version(1L, 1L, 1L)), encoder = encoder)

        // Then
        assertEquals(50, context.changes[0].pathComponents.size, "Should capture all 50 components")
        assertEquals("field0", context.changes[0].pathComponents[0])
        assertEquals("field49", context.changes[0].pathComponents[49])
    }

    @Test
    fun `maximum depth protection`() {
        // Given
        val context = ResolutionDeltaContext<VersionNode, String>()

        // When - push to max depth (1024)
        repeat(1024) { i -> context.pushPath("field$i") }

        // Then - should succeed
        assertEquals(1024, context.pathDepth)

        // When - try to exceed max depth
        val exception = assertFailsWith<IllegalStateException> { context.pushPath("tooDeep") }

        // Then
        assertTrue(exception.message?.contains("1024") == true, "Exception should mention max depth")
    }

    @Test
    fun `clear resets context`() {
        // Given
        val context = ResolutionDeltaContext<VersionNode, String>()
        val encoder: (String) -> ByteArray = { it.toByteArray() }

        // When - add some state
        context.pushPath("field1")
        context.pushPath("field2")
        context.addChange(newValue = "value1", versionNode = VersionNode(Version(1L, 1L, 1L)), encoder = encoder)
        context.addChange(newValue = "value2", versionNode = VersionNode(Version(1L, 2L, 2L)), encoder = encoder)

        // Then - verify state exists
        assertEquals(2, context.pathDepth)
        assertEquals(2, context.changes.size)

        // When - clear
        context.clear()

        // Then - everything is reset
        assertEquals(0, context.pathDepth, "Depth should be reset")
        assertEquals(0, context.changes.size, "Changes should be cleared")
    }

    @Test
    fun `addChange with null value`() {
        // Given
        val context = ResolutionDeltaContext<VersionNode, String>()
        val encoder: (String) -> ByteArray = { it.toByteArray() }

        // When
        context.pushPath("field1")
        context.addChange(newValue = null, versionNode = VersionNode(Version(1L, 1L, 1L)), encoder = encoder)

        // Then
        assertEquals(1, context.changes.size)
        assertEquals(null, context.changes[0].value)
        assertEquals(listOf("field1"), context.changes[0].pathComponents)
    }

    @Test
    fun `multiple changes at different paths`() {
        // Given
        val context = ResolutionDeltaContext<VersionNode, String>()
        val encoder: (String) -> ByteArray = { it.toByteArray() }

        // When - simulate nested message field changes
        context.withPath("1") { // field tag 1
            context.addChange(
                newValue = "field1Value",
                versionNode = VersionNode(Version(1L, 1L, 1L)),
                encoder = encoder,
            )
        }

        context.withPath("2") { // field tag 2
            context.withPath("mapKey") { // map key
                context.addChange(
                    newValue = "mapValue",
                    versionNode = VersionNode(Version(1L, 1L, 1L)),
                    encoder = encoder,
                )
            }
        }

        context.withPath("3") { // field tag 3
            context.withPath("0") { // repeated index 0
                context.addChange(
                    newValue = "repeatedValue",
                    versionNode = VersionNode(Version(1L, 1L, 1L)),
                    encoder = encoder,
                )
            }
        }

        // Then
        assertEquals(3, context.changes.size)

        // Verify first change
        assertEquals(listOf("1"), context.changes[0].pathComponents)
        assertEquals("field1Value", context.changes[0].value)

        // Verify second change (nested in map)
        assertEquals(listOf("2", "mapKey"), context.changes[1].pathComponents)
        assertEquals("mapValue", context.changes[1].value)

        // Verify third change (nested in repeated field)
        assertEquals(listOf("3", "0"), context.changes[2].pathComponents)
        assertEquals("repeatedValue", context.changes[2].value)
    }
}
