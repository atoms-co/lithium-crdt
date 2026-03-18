package co.atoms.protobuf.crdt.resolver

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IsLeafTest {
    private val resolver = TestVersionTreeResolver

    @Test
    fun `simple version node with no data is a leaf`() {
        // Given - just a version, no counter, no maps, no children
        val node = VersionNode(
            version = Version(1L, 1L, 1000L)
        )

        // When/Then - should be a leaf (no child structures)
        with(resolver) {
            assertTrue(node.isLeaf(), "Simple version node should be a leaf")
        }
    }

    @Test
    fun `counter node with single actor is a leaf`() {
        // Given - version + counter with single actor, no maps/children
        val node = VersionNode(
            version = Version(1L, 1L, 1000L),
            counter = Counter(
                mapOf(
                    1L to VersionCount(version = 1L, value = 10L)
                )
            )
        )

        // When/Then - should be a leaf (single actor counter is unnecessary, version represents value)
        with(resolver) {
            assertTrue(node.isLeaf(), "Counter node with single actor should be a leaf")
        }
    }

    @Test
    fun `counter node with multiple actors is not a leaf`() {
        // Given - version + counter with multiple actors, no maps/children
        val node = VersionNode(
            version = Version(1L, 5L, 1000L),
            counter = Counter(
                mapOf(
                    1L to VersionCount(version = 5L, value = 10L),
                    2L to VersionCount(version = 3L, value = 7L),
                    3L to VersionCount(version = 2L, value = 5L)
                )
            )
        )

        // When/Then - should NOT be a leaf (counter with 2+ actors requires traversal)
        with(resolver) {
            assertFalse(node.isLeaf(), "Counter node with multiple actors should not be a leaf")
        }
    }

    @Test
    fun `node with child map is not a leaf`() {
        // Given - has a string map with children
        val node = VersionNode(
            version = Version(1L, 1L, 1000L),
            string_map = mapOf(
                "key" to VersionNode(version = Version(2L, 1L, 1100L))
            )
        )

        // When/Then - should NOT be a leaf (has child structures)
        with(resolver) {
            assertFalse(node.isLeaf(), "Node with child map should not be a leaf")
        }
    }

    @Test
    fun `node with repeated entries is not a leaf`() {
        // Given - has repeated entries
        val node = VersionNode(
            version = Version(1L, 1L, 1000L),
            repeated = listOf(
                VersionNode(version = Version(2L, 1L, 1100L))
            )
        )

        // When/Then - should NOT be a leaf (has child structures)
        with(resolver) {
            assertFalse(node.isLeaf(), "Node with repeated entries should not be a leaf")
        }
    }

    @Test
    fun `node with int_map fields is not a leaf`() {
        // Given - has fields (int_map)
        val node = VersionNode(
            version = Version(1L, 1L, 1000L),
            int_map = mapOf(
                1 to VersionNode(version = Version(2L, 1L, 1100L))
            )
        )

        // When/Then - should NOT be a leaf (has child structures)
        with(resolver) {
            assertFalse(node.isLeaf(), "Node with fields should not be a leaf")
        }
    }

    @Test
    fun `counter node with child map is not a leaf`() {
        // Given - has both counter AND a child map
        val node = VersionNode(
            version = Version(1L, 1L, 1000L),
            counter = Counter(
                mapOf(
                    1L to VersionCount(version = 1L, value = 10L)
                )
            ),
            string_map = mapOf(
                "key" to VersionNode(version = Version(2L, 1L, 1100L))
            )
        )

        // When/Then - should NOT be a leaf (has child structures)
        with(resolver) {
            assertFalse(node.isLeaf(), "Counter node with child map should not be a leaf")
        }
    }
}
