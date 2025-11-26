package com.css.protobuf.crdt.protoc

import com.css.protobuf.crdt.data.Version
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProtocVersionResolverTest {
    private val resolver = ProtocVersionTreeResolver

    @Test
    fun testComparableVersionSequenceCompare() {
        val version1 = Version.newBuilder().setTimestamp(1).setActorId(3).setActorVersion(1).build()
        val version2 = Version.newBuilder().setTimestamp(1).setActorId(2).setActorVersion(2).build()

        assertTrue(version1 > version2)
        assertTrue(version2 < version1)
        assertTrue(Version.newBuilder().build() < version1)
        assertTrue(version1 > Version.newBuilder().build())
        assertTrue(
            Version.newBuilder().setTimestamp(1).setActorId(3).setActorVersion(1).build().compareTo(version1) == 0
        )
        assertTrue(
            Version.newBuilder().setTimestamp(1).setActorId(2).setActorVersion(2).build().compareTo(version2) == 0
        )
        assertTrue(version1 <= Version.newBuilder().setTimestamp(1).setActorId(3).setActorVersion(1).build())
        assertTrue(version1 >= Version.newBuilder().setTimestamp(1).setActorId(3).setActorVersion(1).build())
        assertTrue(version2 >= Version.newBuilder().setTimestamp(1).setActorId(2).setActorVersion(2).build())
        assertTrue(version2 >= Version.newBuilder().setTimestamp(1).setActorId(2).setActorVersion(2).build())
        assertEquals(
            Version.newBuilder().setTimestamp(1).setActorId(3).setActorVersion(1).build(),
            maxOf(version2, version1, resolver)
        )
        assertEquals(
            Version.newBuilder().setTimestamp(1).setActorId(2).setActorVersion(2).build(),
            minOf(version2, version1, resolver)
        )
    }
}
