package co.atoms.lithium.crdt.protoc

import co.atoms.lithium.crdt.data.Actors
import co.atoms.lithium.crdt.data.PathComponent
import co.atoms.lithium.crdt.data.Version
import co.atoms.lithium.crdt.data.VersionNode
import co.atoms.lithium.crdt.resolver.ChangeEvent
import co.atoms.lithium.crdt.resolver.NodeMergeChangeProvider
import co.atoms.lithium.crdt.resolver.version.ApplyChangesResult
import co.atoms.lithium.crdt.resolver.version.ResolutionStrategy
import co.atoms.lithium.crdt.test.NestedMessage
import co.atoms.lithium.crdt.test.TestMessage
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer

/**
 * Tests demonstrating why baseline actors validation is critical for delta sync.
 *
 * These tests illustrate the "maxVersion problem" with nested fields: when a parent message
 * has been deleted locally, incoming delta changes to child fields cannot be safely applied
 * because we lack the sibling data needed for correct conflict resolution.
 *
 * ## Baseline Validation Logic
 *
 * The `applyChanges` method compares `localActors.versionVector` against `incomingBaselineActors`:
 *
 * - **LOCAL/NO_CHANGE**: Local is at or ahead of baseline → safe to apply changes
 * - **INCOMING**: Baseline has changes local doesn't have → reject, return LOCAL unchanged
 * - **MERGED_VALUES**: Diverged states → reject, return LOCAL unchanged
 *
 * The key insight: changes can only be safely applied when the local state is at or ahead
 * of the baseline from which changes were computed. If baseline has actors/versions that
 * local doesn't have, the sender computed changes from a state that includes data we've
 * never seen - applying would skip conflict resolution for that data.
 *
 * ## The Tests
 *
 * 1. What goes wrong with nested fields (data loss from missing siblings)
 * 2. How baseline validation prevents incorrect merges when baseline is ahead
 * 3. How full-state resolution produces correct results
 *
 * @see CrdtMessageResolver.applyChanges for baseline validation logic
 */
class ProtocBaselineActorsDivergenceTest {

    companion object {
        // Field numbers from test_message.proto
        private const val NESTED_VALUE_FIELD = 16 // nestedValue field in TestMessage
        private const val STRING_VALUE_FIELD = 42 // stringValue field in NestedMessage
        private const val INT_VALUE_FIELD = 43    // intValue field in NestedMessage
    }

    private val resolver = CrdtMessageResolverProvider().getOrCreateResolverFor(TestMessage.getDefaultInstance())

    /**
     * Creates a Version with the given parameters.
     * Version comparison is lexicographic: timestamp first, then actor_id for tie-breaking.
     */
    private fun version(timestamp: Long, actorId: Long, actorVersion: Long) =
        Version.newBuilder()
            .setTimestamp(timestamp)
            .setActorId(actorId)
            .setActorVersion(actorVersion)
            .build()

    /**
     * Creates a VersionNode with the given version (leaf node, no children).
     */
    private fun versionNode(version: Version) =
        VersionNode.newBuilder().setVersion(version).build()

    /**
     * Creates a VersionNode with child field versions (for message types).
     * Fields are stored in the Struct.fields map, keyed by field number.
     */
    private fun versionNode(version: Version, fields: Map<Int, VersionNode>): VersionNode {
        val struct = VersionNode.Struct.newBuilder()
            .putAllFields(fields)
            .build()
        return VersionNode.newBuilder()
            .setVersion(version)
            .setStruct(struct)
            .build()
    }

    @Nested
    @DisplayName("The maxVersion Problem - Demonstrating Data Loss Without Baseline Validation")
    inner class MaxVersionProblemTests {

        /**
         * This test demonstrates the maxVersion problem with nested fields.
         *
         * Scenario showing WHY full-state resolution is needed:
         * - Baseline: customer { name: "Alice", email: "a@x.com", phone: "555" } with various versions
         * - Local: customer deleted at t=1008 (Device D)
         * - Incoming delta: only name="Bob"@t=1020 (Device B)
         *
         * Problem: The delta has name@t=1020 > deletion@t=1008, so the message should win.
         * But the delta only has `name` - we'd lose email and phone!
         *
         * This test shows that when we manually construct a "bad" delta result (simulating
         * what would happen if validation failed), we lose sibling data.
         */
        @Test
        fun `demonstrates data loss when applying delta to deleted parent without sibling data`() {
            // === DEMONSTRATE THE PROBLEM ===
            // If we were to apply a delta directly without full sibling context,
            // we'd only have the changed field - other fields would be lost!

            // What a "bad" delta application would produce - only the changed field:
            val badMergeResult = TestMessage.newBuilder()
                .setNestedValue(
                    NestedMessage.newBuilder()
                        .setStringValue("bob@example.com") // Only this field from delta
                        // intValue is MISSING - lost because delta didn't include it!
                        .build()
                )
                .build()

            // The bad merge loses the phone number!
            assertThat(badMergeResult.nestedValue.intValue).isEqualTo(0) // Lost!
            assertThat(badMergeResult.nestedValue.stringValue).isEqualTo("bob@example.com")

            // === CORRECT BEHAVIOR: Full state resolution preserves all data ===
            // When baseline doesn't match (which triggers fallback to resolveConflict),
            // we have all sibling data and can resolve correctly.
            // See the `full state resolution correctly handles message vs deletion` test below.
        }

        /**
         * Demonstrates the correct resolution using full-state conflict resolution.
         *
         * When baseline validation fails, the caller should fall back to resolveConflict
         * which has access to the complete state on both sides.
         */
        @Test
        fun `full state resolution correctly handles message vs deletion with all sibling data`() {
            // === LOCAL STATE: Nested message was deleted at t=1008 ===
            val localValue = TestMessage.newBuilder()
                .clearNestedValue()
                .build()

            val deletionVersion = version(timestamp = 1008, actorId = 400, actorVersion = 1)
            val localNode = versionNode(
                version = version(timestamp = 1000, actorId = 100, actorVersion = 1),
                fields = mapOf(
                    NESTED_VALUE_FIELD to versionNode(deletionVersion)
                )
            )
            val localActors = Actors.newBuilder()
                .setLocalActor(400)
                .putAllVersionVector(mapOf(100L to 1L, 200L to 1L, 300L to 1L, 400L to 1L))
                .build()

            // === INCOMING STATE: Full nested message with updated name at t=1020 ===
            // In full resolution, the sender provides the COMPLETE state
            val incomingNestedValue = NestedMessage.newBuilder()
                .setStringValue("bob@example.com")  // Updated at t=1020
                .setIntValue(5551234)               // Preserved from t=1005
                .build()

            val incomingValue = TestMessage.newBuilder()
                .setNestedValue(incomingNestedValue)
                .build()

            // Full version tree includes ALL child versions
            val incomingNestedNode = versionNode(
                version = version(timestamp = 1000, actorId = 100, actorVersion = 1),
                fields = mapOf(
                    STRING_VALUE_FIELD to versionNode(version(timestamp = 1020, actorId = 200, actorVersion = 2)),
                    INT_VALUE_FIELD to versionNode(version(timestamp = 1005, actorId = 300, actorVersion = 1))
                )
            )
            val incomingNode = versionNode(
                version = version(timestamp = 1000, actorId = 100, actorVersion = 1),
                fields = mapOf(NESTED_VALUE_FIELD to incomingNestedNode)
            )
            val incomingVersionVector = mapOf(100L to 1L, 200L to 2L, 300L to 1L)

            // === FULL RESOLUTION ===
            val result = resolver.resolveConflict(
                localValue = localValue,
                localNode = localNode,
                localActors = localActors,
                incomingValue = incomingValue,
                incomingNode = incomingNode,
                incomingVersionVector = incomingVersionVector
            )

            // The message wins because maxVersion(t=1020) > deletion(t=1008)
            // AND we have all the sibling data preserved!
            val resolvedMessage = result.mergeResult.value as TestMessage
            assertThat(resolvedMessage.hasNestedValue()).isTrue()
            assertThat(resolvedMessage.nestedValue.stringValue).isEqualTo("bob@example.com")
            assertThat(resolvedMessage.nestedValue.intValue).isEqualTo(5551234) // Preserved!

            // Resolution strategy indicates values were merged
            assertThat(result.mergeResult.resolution).isIn(
                listOf(ResolutionStrategy.INCOMING, ResolutionStrategy.MERGED_VALUES)
            )
        }

        /**
         * Shows that when the deletion is newer than all children, deletion wins in full resolution.
         */
        @Test
        fun `full state resolution - deletion wins when newer than all children`() {
            // === LOCAL STATE: Nested message deleted at t=1050 (after all children) ===
            val localValue = TestMessage.newBuilder()
                .clearNestedValue()
                .build()

            val deletionVersion = version(timestamp = 1050, actorId = 400, actorVersion = 1)
            val localNode = versionNode(
                version = version(timestamp = 1000, actorId = 100, actorVersion = 1),
                fields = mapOf(
                    NESTED_VALUE_FIELD to versionNode(deletionVersion)
                )
            )
            val localActors = Actors.newBuilder()
                .setLocalActor(400)
                .putAllVersionVector(mapOf(100L to 1L, 200L to 1L, 300L to 1L, 400L to 1L))
                .build()

            // === INCOMING STATE: Message with maxVersion at t=1020 ===
            val incomingNestedValue = NestedMessage.newBuilder()
                .setStringValue("bob@example.com")
                .setIntValue(5551234)
                .build()

            val incomingValue = TestMessage.newBuilder()
                .setNestedValue(incomingNestedValue)
                .build()

            val incomingNestedNode = versionNode(
                version = version(timestamp = 1000, actorId = 100, actorVersion = 1),
                fields = mapOf(
                    STRING_VALUE_FIELD to versionNode(version(timestamp = 1020, actorId = 200, actorVersion = 2)),
                    INT_VALUE_FIELD to versionNode(version(timestamp = 1005, actorId = 300, actorVersion = 1))
                )
            )
            val incomingNode = versionNode(
                version = version(timestamp = 1000, actorId = 100, actorVersion = 1),
                fields = mapOf(NESTED_VALUE_FIELD to incomingNestedNode)
            )
            val incomingVersionVector = mapOf(100L to 1L, 200L to 2L, 300L to 1L)

            // === FULL RESOLUTION ===
            val result = resolver.resolveConflict(
                localValue = localValue,
                localNode = localNode,
                localActors = localActors,
                incomingValue = incomingValue,
                incomingNode = incomingNode,
                incomingVersionVector = incomingVersionVector
            )

            // Deletion wins because deletion(t=1050) > maxVersion(t=1020)
            val resolvedMessage = result.mergeResult.value as TestMessage
            assertThat(resolvedMessage.hasNestedValue()).isFalse()
        }
    }

    @Nested
    @DisplayName("Baseline Actor Divergence Detection")
    inner class BaselineDivergenceDetectionTests {

        /**
         * When baseline has an actor unknown to local, we've diverged.
         * The sender computed changes from a state that includes data we've never seen.
         * We must reject and fall back to full-state resolution.
         */
        @Test
        fun `detects divergence when baseline has actor unknown to local`() {
            val localValue = TestMessage.newBuilder()
                .setInt32Value(100)
                .build()
            val localNode = versionNode(version(timestamp = 1000, actorId = 100, actorVersion = 5))
            val localActors = Actors.newBuilder()
                .setLocalActor(100)
                .putAllVersionVector(mapOf(100L to 5L, 200L to 3L))
                .build()

            // Baseline has actor 999 that local doesn't know about!
            // This means the sender computed changes from a state that includes
            // data from actor 999 that we've never seen.
            val incomingBaselineActors = mapOf(100L to 5L, 200L to 3L, 999L to 2L)

            val incomingChanges = listOf(
                createInt32Change(value = 200, version = version(timestamp = 2000, actorId = 200, actorVersion = 4))
            )

            val result = resolver.applyChanges(
                localValue = localValue,
                localNode = localNode,
                localActors = localActors,
                incomingChanges = incomingChanges,
                incomingBaselineActors = incomingBaselineActors
            )

            // Should detect divergence and return REJECTED
            // Caller should fall back to full-state resolveConflict
            assertThat(result.mergeResult.resolution).isEqualTo(ApplyChangesResult.REJECTED)
        }

        /**
         * When baseline has a higher version for an actor than local has, we've diverged.
         * The sender's baseline state includes changes we haven't seen yet.
         */
        @Test
        fun `detects divergence when baseline has higher version than local for same actor`() {
            val localValue = TestMessage.newBuilder()
                .setInt32Value(100)
                .build()
            val localNode = versionNode(version(timestamp = 1000, actorId = 100, actorVersion = 5))
            val localActors = Actors.newBuilder()
                .setLocalActor(100)
                .putAllVersionVector(mapOf(100L to 5L)) // Local is at version 5
                .build()

            // Baseline thinks we're at version 10 - it has changes we don't have!
            val incomingBaselineActors = mapOf(100L to 10L)

            val incomingChanges = listOf(
                createInt32Change(value = 200, version = version(timestamp = 2000, actorId = 200, actorVersion = 1))
            )

            val result = resolver.applyChanges(
                localValue = localValue,
                localNode = localNode,
                localActors = localActors,
                incomingChanges = incomingChanges,
                incomingBaselineActors = incomingBaselineActors
            )

            // Should detect divergence (baseline has changes we don't have) and return REJECTED
            // Caller should fall back to full-state resolveConflict
            assertThat(result.mergeResult.resolution).isEqualTo(ApplyChangesResult.REJECTED)
        }

        /**
         * When local is ahead of baseline, changes CAN be safely applied.
         * Local has everything baseline had, plus more - no data will be lost.
         */
        @Test
        fun `applies changes when local is ahead of baseline`() {
            val localValue = TestMessage.newBuilder()
                .setInt32Value(100)
                .build()
            val localNode = versionNode(version(timestamp = 1000, actorId = 100, actorVersion = 10))
            val localActors = Actors.newBuilder()
                .setLocalActor(100)
                .putAllVersionVector(mapOf(100L to 10L, 200L to 5L, 999L to 3L))
                .build()

            // Baseline is behind local - this is safe!
            // Local has everything baseline knew about and more.
            val incomingBaselineActors = mapOf(100L to 5L, 200L to 3L)

            val incomingChanges = listOf(
                createInt32Change(value = 200, version = version(timestamp = 2000, actorId = 300, actorVersion = 1))
            )

            val result = resolver.applyChanges(
                localValue = localValue,
                localNode = localNode,
                localActors = localActors,
                incomingChanges = incomingChanges,
                incomingBaselineActors = incomingBaselineActors
            )

            // Changes should be applied since local is ahead of baseline
            assertThat(result.mergeResult.resolution).isEqualTo(ApplyChangesResult.APPLIED)
            assertThat((result.mergeResult.value as TestMessage).int32Value).isEqualTo(200)
        }

        /**
         * When baselines match exactly, changes can be safely applied.
         */
        @Test
        fun `applies changes when baseline actors match local state exactly`() {
            val localValue = TestMessage.newBuilder()
                .setInt32Value(100)
                .build()
            val localNode = versionNode(version(timestamp = 1000, actorId = 100, actorVersion = 5))
            val localActors = Actors.newBuilder()
                .setLocalActor(100)
                .putAllVersionVector(mapOf(100L to 5L, 200L to 3L))
                .build()

            // Baseline matches exactly
            val incomingBaselineActors = mapOf(100L to 5L, 200L to 3L)

            val incomingChanges = listOf(
                createInt32Change(value = 200, version = version(timestamp = 2000, actorId = 300, actorVersion = 1))
            )

            val result = resolver.applyChanges(
                localValue = localValue,
                localNode = localNode,
                localActors = localActors,
                incomingChanges = incomingChanges,
                incomingBaselineActors = incomingBaselineActors
            )

            // Changes should be applied since baseline matches exactly
            assertThat(result.mergeResult.resolution).isEqualTo(ApplyChangesResult.APPLIED)
            assertThat((result.mergeResult.value as TestMessage).int32Value).isEqualTo(200)
        }
    }

    // === Helper methods for creating changes ===

    private fun createInt32Change(value: Int, version: Version): ChangeEvent<Int, VersionNode, PathComponent> {
        return NodeMergeChangeProvider(
            encoder = { ByteBuffer.allocate(4).putInt(it).array() },
            pathComponents = listOf(PathComponent.newBuilder().setFieldNumber(3).build()), // int32Value field
            value = value,
            versionNode = versionNode(version)
        )
    }

    private fun createNestedStringChange(value: String, version: Version): ChangeEvent<String, VersionNode, PathComponent> {
        // Path: nestedValue (field 16) -> stringValue (field 42)
        return NodeMergeChangeProvider(
            encoder = { it.toByteArray(Charsets.UTF_8) },
            pathComponents = listOf(
                PathComponent.newBuilder().setFieldNumber(NESTED_VALUE_FIELD).build(),
                PathComponent.newBuilder().setFieldNumber(STRING_VALUE_FIELD).build()
            ),
            value = value,
            versionNode = versionNode(version)
        )
    }
}
