package com.css.internal.shared.storage.crdt.wire

import com.css.internal.shared.storage.crdt.data.VersionNode.BoolMap
import com.css.internal.shared.storage.crdt.data.VersionNode.Int32Map
import com.css.internal.shared.storage.crdt.data.VersionNode.Int64Map
import com.css.internal.shared.storage.crdt.data.VersionNode.Repeated
import com.css.internal.shared.storage.crdt.data.VersionNode.StringMap
import com.css.internal.shared.storage.crdt.data.VersionNode.Struct
import com.css.internal.shared.storage.crdt.data.Version
import com.css.internal.shared.storage.crdt.data.VersionNode
import com.css.internal.shared.storage.crdt.wire.internal.WireVersionTreeResolver
import com.css.internal.shared.storage.crdt.wire.internal.WireVersionTreeResolver.isLeaf
import junit.framework.TestCase.assertFalse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VersionNodeTest {
    private val versionTreeResolver = WireVersionTreeResolver

    private val versions = mapOf(
        1 to VersionNode(
            version = Version(1, 1),
            struct = Struct(
                fields = mapOf(
                    1 to VersionNode(version = Version(2, 3))
                )
            )
        ),
        2 to VersionNode(
            version = Version(0, 3),
            struct = Struct(
                fields = mapOf(
                    1 to VersionNode(version = Version(5, 2))
                )
            )
        ),
        3 to VersionNode(
            version = Version(0, 1),
        ),
        4 to VersionNode(
            version = Version(1, 2),
        ),
        5 to VersionNode(
            version = Version(4, 3),
        ),
        6 to VersionNode(
            version = Version(5, 1),
        ),
    )

    @Test
    fun test_isLeaf() {
        assertTrue(VersionNode().isLeaf())
        assertTrue(VersionNode(version = Version()).isLeaf())
        assertTrue(VersionNode(version = Version(), struct = Struct()).isLeaf())
        assertTrue(VersionNode(version = Version(), bool_map = BoolMap()).isLeaf())
        assertTrue(VersionNode(version = Version(), string_map = StringMap()).isLeaf())
        assertTrue(VersionNode(version = Version(), int64_map = Int64Map()).isLeaf())
        assertTrue(VersionNode(version = Version(), int32_map = Int32Map()).isLeaf())
        assertTrue(VersionNode(version = Version(), repeated = Repeated()).isLeaf())

        assertFalse(
            VersionNode(
                version = Version(),
                struct = Struct(
                    fields = mapOf(
                        1 to VersionNode()
                    )
                )
            ).isLeaf()
        )
        assertFalse(
            VersionNode(
                version = Version(),
                bool_map = BoolMap(
                    entries = mapOf(
                        false to
                            VersionNode()
                    )
                )
            ).isLeaf()
        )
        assertFalse(
            VersionNode(
                version = Version(),
                string_map = StringMap(
                    entries = mapOf(
                        "string"
                            to VersionNode()
                    )
                )
            ).isLeaf()
        )

        assertFalse(
            VersionNode(
                version = Version(),
                int64_map = Int64Map(
                    entries = mapOf(
                        1L to VersionNode()
                    )
                )
            ).isLeaf()
        )

        assertFalse(
            VersionNode(
                version = Version(),
                int32_map = Int32Map(
                    entries = mapOf(
                        1 to VersionNode()
                    )
                )
            ).isLeaf()
        )

        assertFalse(
            VersionNode(
                version = Version(),
                repeated = Repeated(
                    entries = listOf(VersionNode()),
                )
            ).isLeaf()
        )
    }

    @Test
    fun test_minVersion() = with(versionTreeResolver) {
        assertEquals(
            versions.values.minVersion(Version(4L, 5L)),
            Version(0L, 1L)
        )
        assertEquals(
            listOf<VersionNode>().minVersion(Version(4L, 5L)),
            Version(4L, 5L)
        )
        assertEquals(
            VersionNode().struct?.fields?.values.minVersion(Version(4L, 5L)),
            Version(4L, 5L)
        )
    }

    @Test
    fun test_maxVersion() = with(versionTreeResolver) {
        assertEquals(
            VersionNode(
                struct = Struct(fields = versions)
            ).maxVersion(Version(10L, 1L)),
            Version(5L, 2L)
        )
        assertEquals(
            VersionNode(
                struct = Struct()
            ).maxVersion(Version(10L, 1L)),
            Version(10L, 1L)
        )
        assertEquals(
            VersionNode().struct?.fields?.values?.firstOrNull().maxVersion(Version(10L, 1L)),
            Version(10L, 1L)
        )
    }
}
