package com.css.protobuf.crdt.protoc

import com.css.protobuf.crdt.test.TestMessage
import com.css.protobuf.crdt.data.Version
import com.css.protobuf.crdt.resolver.MessageFieldResolverProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ProtocMessageResolverTest {
    private val provider = CrdtMessageResolverProvider()
    private val resolver =
        ProtocMessageResolver(
            decoder = { TestMessage.parseFrom(it) },
            encoder = { it.toByteArray() },
            builderFactory = { TestMessage.newBuilder() },
            fieldDescriptors = TestMessage.getDescriptor().fields,
            fieldResolverProvider = MessageFieldResolverProvider,
            resolverProvider = provider,
            versionTreeResolver = ProtocVersionTreeResolver,
        )

    @Test
    fun `toVersionVector - empty list produces empty map`() {
        // Given
        val versions = emptyList<Version>()

        // When
        val result = with(resolver) { versions.toVersionVector() }

        // Then
        assertEquals(emptyMap<Long, Long>(), result, "Empty version list should produce empty version vector")
    }

    @Test
    fun `toVersionVector - single version`() {
        // Given
        val versions =
            listOf(
                Version.newBuilder()
                    .setTimestamp(1000L)
                    .setActorId(100L)
                    .setActorVersion(5L)
                    .build(),
            )

        // When
        val result = with(resolver) { versions.toVersionVector() }

        // Then
        assertEquals(
            mapOf(100L to 5L),
            result,
            "Should create version vector with single actor",
        )
    }

    @Test
    fun `toVersionVector - multiple versions from same actor uses max`() {
        // Given - actor 100 has versions 3, 7, 5 (out of order)
        val versions =
            listOf(
                Version.newBuilder().setTimestamp(1000L).setActorId(100L).setActorVersion(3L).build(),
                Version.newBuilder().setTimestamp(1001L).setActorId(100L).setActorVersion(7L).build(),
                Version.newBuilder().setTimestamp(1002L).setActorId(100L).setActorVersion(5L).build(),
            )

        // When
        val result = with(resolver) { versions.toVersionVector() }

        // Then - should use max version (7) for actor 100
        assertEquals(
            mapOf(100L to 7L),
            result,
            "Should use maximum actor version when multiple versions from same actor",
        )
    }

    @Test
    fun `toVersionVector - multiple actors`() {
        // Given
        val versions =
            listOf(
                Version.newBuilder().setTimestamp(1000L).setActorId(100L).setActorVersion(5L).build(),
                Version.newBuilder().setTimestamp(2000L).setActorId(200L).setActorVersion(3L).build(),
                Version.newBuilder().setTimestamp(3000L).setActorId(300L).setActorVersion(8L).build(),
            )

        // When
        val result = with(resolver) { versions.toVersionVector() }

        // Then
        assertEquals(
            mapOf(100L to 5L, 200L to 3L, 300L to 8L),
            result,
            "Should create version vector with all actors",
        )
    }

    @Test
    fun `toVersionVector - multiple versions from multiple actors uses max per actor`() {
        // Given
        val versions =
            listOf(
                Version.newBuilder().setTimestamp(1000L).setActorId(100L).setActorVersion(3L).build(),
                Version.newBuilder().setTimestamp(1001L).setActorId(200L).setActorVersion(2L).build(),
                Version.newBuilder().setTimestamp(1002L).setActorId(100L).setActorVersion(7L).build(), // Max for 100
                Version.newBuilder().setTimestamp(1003L).setActorId(200L).setActorVersion(5L).build(), // Max for 200
                Version.newBuilder().setTimestamp(1004L).setActorId(100L).setActorVersion(4L).build(),
            )

        // When
        val result = with(resolver) { versions.toVersionVector() }

        // Then
        assertEquals(
            mapOf(100L to 7L, 200L to 5L),
            result,
            "Should use maximum version per actor",
        )
    }

    @Test
    fun `toVersionVector - version 0 included`() {
        // Given
        val versions =
            listOf(
                Version.newBuilder().setTimestamp(1000L).setActorId(100L).setActorVersion(0L).build(),
                Version.newBuilder().setTimestamp(1001L).setActorId(200L).setActorVersion(5L).build(),
            )

        // When
        val result = with(resolver) { versions.toVersionVector() }

        // Then
        assertEquals(
            mapOf(100L to 0L, 200L to 5L),
            result,
            "Should include version 0",
        )
    }

    @Test
    fun `toVersionVector - timestamp does not affect version vector`() {
        // Given - same actor/version, different timestamps
        val versions =
            listOf(
                Version.newBuilder().setTimestamp(999L).setActorId(100L).setActorVersion(5L).build(),
                Version.newBuilder().setTimestamp(1000L).setActorId(100L).setActorVersion(5L).build(),
                Version.newBuilder().setTimestamp(1001L).setActorId(100L).setActorVersion(5L).build(),
            )

        // When
        val result = with(resolver) { versions.toVersionVector() }

        // Then - all collapse to same entry
        assertEquals(
            mapOf(100L to 5L),
            result,
            "Timestamp should not affect version vector (only actor_id and actor_version matter)",
        )
    }

    @Test
    fun `toVersionVector - large number of actors`() {
        // Given - 10 different actors
        val versions =
            (1L..10L).map { actorId ->
                Version.newBuilder()
                    .setTimestamp(actorId * 1000)
                    .setActorId(actorId)
                    .setActorVersion(actorId * 2)
                    .build()
            }

        // When
        val result = with(resolver) { versions.toVersionVector() }

        // Then
        val expected = (1L..10L).associate { it to it * 2 }
        assertEquals(expected, result, "Should handle many actors")
    }
}
