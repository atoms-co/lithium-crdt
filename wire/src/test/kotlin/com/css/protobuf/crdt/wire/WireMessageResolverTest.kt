package com.css.protobuf.crdt.wire

import com.css.protobuf.crdt.test.TestMessage
import com.css.protobuf.crdt.data.Version
import com.css.protobuf.crdt.wire.internal.WireMessageResolver
import com.css.protobuf.crdt.wire.internal.WireVersionTreeResolver
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

class WireMessageResolverTest {
    private val resolver =
        WireMessageResolver(
            decoder = TestMessage.ADAPTER::decode,
            encoder = TestMessage.ADAPTER::encode,
            messageType = TestMessage::class.java,
            protoAdapter = TestMessage.ADAPTER,
            resolverProvider = mockk(relaxed = true),
            versionTreeResolver = WireVersionTreeResolver,
        )

    @Test
    fun `toVersionVector - empty list produces empty map`() {
        // Given
        val versions = emptyList<Version>()

        // When
        val result = with(resolver) { versions.toVersionVector() }

        // Then
        assertEquals(emptyMap(), result, "Empty version list should produce empty version vector")
    }

    @Test
    fun `toVersionVector - single version`() {
        // Given
        val versions =
            listOf(
                Version(timestamp = 1000L, actor_id = 100L, actor_version = 5L),
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
                Version(timestamp = 1000L, actor_id = 100L, actor_version = 3L),
                Version(timestamp = 1001L, actor_id = 100L, actor_version = 7L),
                Version(timestamp = 1002L, actor_id = 100L, actor_version = 5L),
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
                Version(timestamp = 1000L, actor_id = 100L, actor_version = 5L),
                Version(timestamp = 2000L, actor_id = 200L, actor_version = 3L),
                Version(timestamp = 3000L, actor_id = 300L, actor_version = 8L),
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
                Version(timestamp = 1000L, actor_id = 100L, actor_version = 3L),
                Version(timestamp = 1001L, actor_id = 200L, actor_version = 2L),
                Version(timestamp = 1002L, actor_id = 100L, actor_version = 7L), // Max for 100
                Version(timestamp = 1003L, actor_id = 200L, actor_version = 5L), // Max for 200
                Version(timestamp = 1004L, actor_id = 100L, actor_version = 4L),
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
                Version(timestamp = 1000L, actor_id = 100L, actor_version = 0L),
                Version(timestamp = 1001L, actor_id = 200L, actor_version = 5L),
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
                Version(timestamp = 999L, actor_id = 100L, actor_version = 5L),
                Version(timestamp = 1000L, actor_id = 100L, actor_version = 5L),
                Version(timestamp = 1001L, actor_id = 100L, actor_version = 5L),
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
                Version(timestamp = actorId * 1000, actor_id = actorId, actor_version = actorId * 2)
            }

        // When
        val result = with(resolver) { versions.toVersionVector() }

        // Then
        val expected = (1L..10L).associate { it to it * 2 }
        assertEquals(expected, result, "Should handle many actors")
    }
}
