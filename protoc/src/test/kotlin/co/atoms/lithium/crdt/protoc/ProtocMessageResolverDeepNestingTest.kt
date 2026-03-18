package co.atoms.lithium.crdt.protoc

import co.atoms.lithium.crdt.test.NestedMessage
import co.atoms.lithium.crdt.test.NestedRepeatedMessage
import co.atoms.lithium.crdt.test.TestMessage
import co.atoms.lithium.crdt.data.Actors
import co.atoms.lithium.crdt.data.Version
import co.atoms.lithium.crdt.data.VersionNode
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Tests for deep nesting (depth > 4) through repeated fields, maps, and nested messages.
 *
 * This tests the change detection and path component parsing for complex structures:
 * - Message -> Repeated -> Map -> Nested Message -> Field (depth 5)
 * - Map -> Nested Message -> Repeated -> Map -> Field (depth 5)
 * - Multiple levels of nesting with various field types
// */
class ProtocMessageResolverDeepNestingTest {
    private val resolver = CrdtMessageResolverProvider().getOrCreateResolverFor(TestMessage.getDefaultInstance())

    @Test
    fun `applyChanges - depth 5 through nestedValue-repeatedMessage-map-nestedMessage-field`() {
        // Build a deep structure: TestMessage.nestedValue.nestedRepeatedMessage[0].nestedMessageMap["key1"].intValue
        // Path depth: 1(nestedValue) -> 2(nestedRepeatedMessage) -> 3(array[0]) -> 4(map["key1"]) -> 5(intValue)

        // Given - local state with deep nesting
        val localValue = TestMessage.newBuilder()
            .setNestedValue(
                NestedMessage.newBuilder()
                    .addNestedRepeatedMessage(
                        NestedRepeatedMessage.newBuilder()
                            .putNestedMessageMap(
                                "key1",
                                NestedMessage.newBuilder()
                                    .setIntValue(100)
                                    .setStringValue("original")
                                    .build()
                            )
                            .setIntValue(50)
                            .build()
                    )
                    .build()
            )
            .setInt32Value(42)
            .build()

        val localActors = Actors.newBuilder()
            .setLocalActor(1L)
            .putAllVersionVector(mapOf(1L to 5L))
            .build()
        val localNode = VersionNode.newBuilder()
            .setVersion(
                Version.newBuilder()
                    .setTimestamp(1000L)
                    .setActorId(1L)
                    .setActorVersion(5L)
                    .build()
            )
            .build()

        // Incoming baseline matches local
        val incomingBaselineActors = mapOf(1L to 5L)

        // Generate change from actor 2 that modifies the deeply nested intValue
        val incomingValue = TestMessage.newBuilder()
            .setNestedValue(
                NestedMessage.newBuilder()
                    .addNestedRepeatedMessage(
                        NestedRepeatedMessage.newBuilder()
                            .putNestedMessageMap(
                                "key1",
                                NestedMessage.newBuilder()
                                    .setIntValue(999) // Changed value
                                    .setStringValue("original")
                                    .build()
                            )
                            .setIntValue(50)
                            .build()
                    )
                    .build()
            )
            .setInt32Value(42)
            .build()

        val incomingBaselineActorsObj = Actors.newBuilder()
            .setLocalActor(2L)
            .putAllVersionVector(mapOf(1L to 5L))
            .build()

        val writeResult = resolver.applyLocalWrite(
            currentValue = localValue,
            currentNode = localNode,
            currentActors = incomingBaselineActorsObj,
            newValue = incomingValue,
            timestamp = 2000L
        )

        // When - apply the deep changes
        val result = resolver.applyChanges(
            localValue = localValue,
            localNode = localNode,
            localActors = localActors,
            incomingChanges = writeResult.changes,
            incomingBaselineActors = incomingBaselineActors
        )

        // Then - the deeply nested value should be updated
        val resultMessage = result.mergeResult.value as TestMessage
        assertThat(resultMessage.int32Value).isEqualTo(42) // Unchanged
        assertThat(resultMessage.nestedValue.nestedRepeatedMessageList).hasSize(1)

        val repeatedMsg = resultMessage.nestedValue.getNestedRepeatedMessage(0)
        assertThat(repeatedMsg.intValue).isEqualTo(50) // Unchanged
        assertThat(repeatedMsg.nestedMessageMapMap).containsKey("key1")

        val nestedInMap = requireNotNull(repeatedMsg.nestedMessageMapMap["key1"])
        assertThat(nestedInMap.intValue).isEqualTo(999) // Changed!
        assertThat(nestedInMap.stringValue).isEqualTo("original") // Unchanged
    }

    @Test
    fun `applyChanges - depth 5 through nestedMapValue-nestedMessage-repeatedMessage-map-field`() {
        // Build structure: TestMessage.nestedMapValue["outer"].nestedRepeatedMessage[0].nestedMessageMap["inner"].stringValue
        // Path depth: 1(nestedMapValue) -> 2(map["outer"]) -> 3(nestedRepeatedMessage) -> 4(array[0]) -> 5(map["inner"]) -> 6(stringValue)

        val localValue = TestMessage.newBuilder()
            .putNestedMapValue(
                "outer",
                NestedMessage.newBuilder()
                    .addNestedRepeatedMessage(
                        NestedRepeatedMessage.newBuilder()
                            .putNestedMessageMap(
                                "inner",
                                NestedMessage.newBuilder()
                                    .setStringValue("deep-original")
                                    .setIntValue(123)
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .setStringValue("root-value")
            .build()

        val localActors = Actors.newBuilder()
            .setLocalActor(1L)
            .putAllVersionVector(mapOf(1L to 10L))
            .build()
        val localNode = VersionNode.newBuilder()
            .setVersion(
                Version.newBuilder()
                    .setTimestamp(5000L)
                    .setActorId(1L)
                    .setActorVersion(10L)
                    .build()
            )
            .build()

        val incomingBaselineActors = mapOf(1L to 10L)

        // Create incoming change that modifies the deeply nested stringValue
        val incomingValue = TestMessage.newBuilder()
            .putNestedMapValue(
                "outer",
                NestedMessage.newBuilder()
                    .addNestedRepeatedMessage(
                        NestedRepeatedMessage.newBuilder()
                            .putNestedMessageMap(
                                "inner",
                                NestedMessage.newBuilder()
                                    .setStringValue("deep-modified") // Changed
                                    .setIntValue(123)
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .setStringValue("root-value")
            .build()

        val incomingBaselineActorsObj = Actors.newBuilder()
            .setLocalActor(3L)
            .putAllVersionVector(mapOf(1L to 10L))
            .build()

        val writeResult = resolver.applyLocalWrite(
            currentValue = localValue,
            currentNode = localNode,
            currentActors = incomingBaselineActorsObj,
            newValue = incomingValue,
            timestamp = 6000L
        )

        // When
        val result = resolver.applyChanges(
            localValue = localValue,
            localNode = localNode,
            localActors = localActors,
            incomingChanges = writeResult.changes,
            incomingBaselineActors = incomingBaselineActors
        )

        // Then - deeply nested stringValue should be updated
        val resultMessage = result.mergeResult.value as TestMessage
        assertThat(resultMessage.stringValue).isEqualTo("root-value") // Unchanged
        assertThat(resultMessage.nestedMapValueMap).containsKey("outer")

        val outerNested = requireNotNull(resultMessage.nestedMapValueMap["outer"])
        assertThat(outerNested.nestedRepeatedMessageList).hasSize(1)

        val repeatedMsg = outerNested.getNestedRepeatedMessage(0)
        assertThat(repeatedMsg.nestedMessageMapMap).containsKey("inner")

        val innerNested = requireNotNull(repeatedMsg.nestedMessageMapMap["inner"])
        assertThat(innerNested.stringValue).isEqualTo("deep-modified") // Changed!
        assertThat(innerNested.intValue).isEqualTo(123) // Unchanged
    }

    @Test
    fun `applyChanges - multiple deep changes in same message`() {
        // Test multiple changes at different depths in the same message

        val localValue = TestMessage.newBuilder()
            .setInt32Value(100)
            .setNestedValue(
                NestedMessage.newBuilder()
                    .setIntValue(200)
                    .addNestedRepeatedMessage(
                        NestedRepeatedMessage.newBuilder()
                            .setIntValue(300)
                            .putNestedMessageMap(
                                "key1",
                                NestedMessage.newBuilder()
                                    .setIntValue(400)
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .putNestedMapValue(
                "mapKey1",
                NestedMessage.newBuilder()
                    .setIntValue(500)
                    .build()
            )
            .build()

        val localActors = Actors.newBuilder()
            .setLocalActor(1L)
            .putAllVersionVector(mapOf(1L to 20L))
            .build()
        val localNode = VersionNode.newBuilder()
            .setVersion(
                Version.newBuilder()
                    .setTimestamp(10000L)
                    .setActorId(1L)
                    .setActorVersion(20L)
                    .build()
            )
            .build()

        val incomingBaselineActors = mapOf(1L to 20L)

        // Change values at multiple depths
        val incomingValue = TestMessage.newBuilder()
            .setInt32Value(111) // Depth 1
            .setNestedValue(
                NestedMessage.newBuilder()
                    .setIntValue(222) // Depth 2
                    .addNestedRepeatedMessage(
                        NestedRepeatedMessage.newBuilder()
                            .setIntValue(333) // Depth 3
                            .putNestedMessageMap(
                                "key1",
                                NestedMessage.newBuilder()
                                    .setIntValue(444) // Depth 5
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .putNestedMapValue(
                "mapKey1",
                NestedMessage.newBuilder()
                    .setIntValue(555) // Depth 3
                    .build()
            )
            .build()

        val incomingBaselineActorsObj = Actors.newBuilder()
            .setLocalActor(2L)
            .putAllVersionVector(mapOf(1L to 20L))
            .build()

        val writeResult = resolver.applyLocalWrite(
            currentValue = localValue,
            currentNode = localNode,
            currentActors = incomingBaselineActorsObj,
            newValue = incomingValue,
            timestamp = 11000L
        )

        // When
        val result = resolver.applyChanges(
            localValue = localValue,
            localNode = localNode,
            localActors = localActors,
            incomingChanges = writeResult.changes,
            incomingBaselineActors = incomingBaselineActors
        )

        // Then - all changes at different depths should be applied
        val resultMessage = result.mergeResult.value as TestMessage
        assertThat(resultMessage.int32Value).isEqualTo(111) // Depth 1
        assertThat(resultMessage.nestedValue.intValue).isEqualTo(222) // Depth 2

        val repeatedMsg = resultMessage.nestedValue.getNestedRepeatedMessage(0)
        assertThat(repeatedMsg.intValue).isEqualTo(333) // Depth 3
        assertThat(requireNotNull(repeatedMsg.nestedMessageMapMap["key1"]).intValue).isEqualTo(444) // Depth 5

        assertThat(requireNotNull(resultMessage.nestedMapValueMap["mapKey1"]).intValue).isEqualTo(555) // Depth 3
    }

    @Test
    fun `applyChanges - conflicting changes at different depths are resolved independently`() {
        // Test that changes at different depths are resolved independently using LWW

        val baseValue = TestMessage.newBuilder()
            .setNestedValue(
                NestedMessage.newBuilder()
                    .setIntValue(100)
                    .addNestedRepeatedMessage(
                        NestedRepeatedMessage.newBuilder()
                            .putNestedMessageMap(
                                "key",
                                NestedMessage.newBuilder()
                                    .setIntValue(200)
                                    .setStringValue("base")
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()

        // Local makes change at depth 2 (timestamp 2000)
        val localValue = TestMessage.newBuilder()
            .setNestedValue(
                NestedMessage.newBuilder()
                    .setIntValue(111) // Local change at depth 2
                    .addNestedRepeatedMessage(
                        NestedRepeatedMessage.newBuilder()
                            .putNestedMessageMap(
                                "key",
                                NestedMessage.newBuilder()
                                    .setIntValue(200)
                                    .setStringValue("base")
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()

        val localActors = Actors.newBuilder()
            .setLocalActor(1L)
            .putAllVersionVector(mapOf(1L to 10L))
            .build()
        val localNode = VersionNode.newBuilder()
            .setVersion(
                Version.newBuilder()
                    .setTimestamp(2000L)
                    .setActorId(1L)
                    .setActorVersion(10L)
                    .build()
            )
            .build()

        // Incoming makes change at depth 5 (timestamp 3000) - should win at that depth
        val incomingValue = TestMessage.newBuilder()
            .setNestedValue(
                NestedMessage.newBuilder()
                    .setIntValue(100) // No change at depth 2
                    .addNestedRepeatedMessage(
                        NestedRepeatedMessage.newBuilder()
                            .putNestedMessageMap(
                                "key",
                                NestedMessage.newBuilder()
                                    .setIntValue(999) // Incoming change at depth 5
                                    .setStringValue("base")
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()

        val incomingBaselineActors = mapOf(1L to 9L)
        val incomingBaselineActorsObj = Actors.newBuilder()
            .setLocalActor(2L)
            .putAllVersionVector(mapOf(1L to 9L))
            .build()

        // Create base state for incoming
        val baseNode = VersionNode.newBuilder()
            .setVersion(
                Version.newBuilder()
                    .setTimestamp(1000L)
                    .setActorId(1L)
                    .setActorVersion(9L)
                    .build()
            )
            .build()

        val writeResult = resolver.applyLocalWrite(
            currentValue = baseValue,
            currentNode = baseNode,
            currentActors = incomingBaselineActorsObj,
            newValue = incomingValue,
            timestamp = 3000L
        )

        // When - apply incoming changes to local
        val result = resolver.applyChanges(
            localValue = localValue,
            localNode = localNode,
            localActors = localActors,
            incomingChanges = writeResult.changes,
            incomingBaselineActors = incomingBaselineActors
        )

        // Then - local change at depth 2 should be kept, incoming change at depth 5 should be applied
        val resultMessage = result.mergeResult.value as TestMessage
        assertThat(resultMessage.nestedValue.intValue).isEqualTo(111) // Local kept (depth 2)

        val nestedInMap = requireNotNull(resultMessage.nestedValue.getNestedRepeatedMessage(0).nestedMessageMapMap["key"])
        assertThat(nestedInMap.intValue).isEqualTo(999) // Incoming applied (depth 5)
    }

    // ========== Priority Test Cases for Coverage Gaps ==========

    @Test
    fun `applyChanges - unset nested message creates structure for deep change`() {
        // Test that unset nested message at intermediate depth initializes path for deep changes
        // This tests the ability of CRDT to create missing intermediate structures

        // Given - local state without nestedValue set
        val localValue = TestMessage.newBuilder()
            .setInt32Value(42)
            .setStringValue("root")
            .build()

        val localActors = Actors.newBuilder()
            .setLocalActor(1L)
            .putAllVersionVector(mapOf(1L to 5L))
            .build()
        val localNode = VersionNode.newBuilder()
            .setVersion(
                Version.newBuilder()
                    .setTimestamp(1000L)
                    .setActorId(1L)
                    .setActorVersion(5L)
                    .build()
            )
            .build()

        val incomingBaselineActors = mapOf(1L to 5L)

        // Incoming - creates entire structure from scratch including deep nested changes
        val incomingValue = TestMessage.newBuilder()
            .setInt32Value(42)
            .setStringValue("root")
            .setNestedValue(
                NestedMessage.newBuilder()
                    .setIntValue(100)
                    .addNestedRepeatedMessage(
                        NestedRepeatedMessage.newBuilder()
                            .putNestedMessageMap(
                                "key1",
                                NestedMessage.newBuilder()
                                    .setIntValue(999)
                                    .setStringValue("new")
                                    .build()
                            )
                            .setIntValue(50)
                            .build()
                    )
                    .build()
            )
            .build()

        val incomingBaselineActorsObj = Actors.newBuilder()
            .setLocalActor(2L)
            .putAllVersionVector(mapOf(1L to 5L))
            .build()

        val writeResult = resolver.applyLocalWrite(
            currentValue = localValue,
            currentNode = localNode,
            currentActors = incomingBaselineActorsObj,
            newValue = incomingValue,
            timestamp = 2000L
        )

        // When
        val result = resolver.applyChanges(
            localValue = localValue,
            localNode = localNode,
            localActors = localActors,
            incomingChanges = writeResult.changes,
            incomingBaselineActors = incomingBaselineActors
        )

        // Then - should create all intermediate structures and apply deep change
        val resultMessage = result.mergeResult.value as TestMessage
        assertThat(resultMessage.int32Value).isEqualTo(42)
        assertThat(resultMessage.stringValue).isEqualTo("root")
        assertThat(resultMessage.hasNestedValue()).isTrue()

        assertThat(resultMessage.nestedValue.intValue).isEqualTo(100)
        assertThat(resultMessage.nestedValue.nestedRepeatedMessageList).hasSize(1)

        val repeatedMsg = resultMessage.nestedValue.getNestedRepeatedMessage(0)
        assertThat(repeatedMsg.intValue).isEqualTo(50)
        assertThat(repeatedMsg.nestedMessageMapMap).containsKey("key1")
        val nestedKey1 = requireNotNull(repeatedMsg.nestedMessageMapMap["key1"])
        assertThat(nestedKey1.intValue).isEqualTo(999)
        assertThat(nestedKey1.stringValue).isEqualTo("new")
    }

    @Test
    fun `applyChanges - unset nested message at intermediate depth initializes path`() {
        // Test unset nested message at intermediate depth - tests path creation
        // Path: TestMessage without nestedValue set -> incoming sets deep field

        // Given - local state without nestedValue
        val localValue = TestMessage.newBuilder()
            .setInt32Value(42)
            .setStringValue("root")
            .build()

        val localActors = Actors.newBuilder()
            .setLocalActor(1L)
            .putAllVersionVector(mapOf(1L to 3L))
            .build()
        val localNode = VersionNode.newBuilder()
            .setVersion(
                Version.newBuilder()
                    .setTimestamp(1000L)
                    .setActorId(1L)
                    .setActorVersion(3L)
                    .build()
            )
            .build()

        val incomingBaselineActors = mapOf(1L to 3L)

        // Incoming - sets deep field in previously unset nestedValue
        val incomingValue = TestMessage.newBuilder()
            .setInt32Value(42)
            .setStringValue("root")
            .setNestedValue(
                NestedMessage.newBuilder()
                    .addNestedRepeatedMessage(
                        NestedRepeatedMessage.newBuilder()
                            .putNestedMessageMap(
                                "key",
                                NestedMessage.newBuilder()
                                    .setIntValue(123)
                                    .setStringValue("created")
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()

        val incomingBaselineActorsObj = Actors.newBuilder()
            .setLocalActor(2L)
            .putAllVersionVector(mapOf(1L to 3L))
            .build()

        val writeResult = resolver.applyLocalWrite(
            currentValue = localValue,
            currentNode = localNode,
            currentActors = incomingBaselineActorsObj,
            newValue = incomingValue,
            timestamp = 2000L
        )

        // When
        val result = resolver.applyChanges(
            localValue = localValue,
            localNode = localNode,
            localActors = localActors,
            incomingChanges = writeResult.changes,
            incomingBaselineActors = incomingBaselineActors
        )

        // Then - should create intermediate structures and apply change
        val resultMessage = result.mergeResult.value as TestMessage
        assertThat(resultMessage.int32Value).isEqualTo(42)
        assertThat(resultMessage.stringValue).isEqualTo("root")
        assertThat(resultMessage.hasNestedValue()).isTrue()
        assertThat(resultMessage.nestedValue.nestedRepeatedMessageList).hasSize(1)

        val repeatedMsg = resultMessage.nestedValue.getNestedRepeatedMessage(0)
        assertThat(repeatedMsg.nestedMessageMapMap).containsKey("key")
        val nestedKey = requireNotNull(repeatedMsg.nestedMessageMapMap["key"])
        assertThat(nestedKey.intValue).isEqualTo(123)
        assertThat(nestedKey.stringValue).isEqualTo("created")
    }

    @Test
    fun `applyChanges - modify one of multiple map entries at depth 5`() {
        // Test modifying one map entry while leaving others unchanged at deep level
        // Path: nestedValue.nestedRepeatedMessage[0].nestedMessageMap

        // Given - local has two map entries at depth
        val localValue = TestMessage.newBuilder()
            .setNestedValue(
                NestedMessage.newBuilder()
                    .addNestedRepeatedMessage(
                        NestedRepeatedMessage.newBuilder()
                            .putNestedMessageMap(
                                "key1",
                                NestedMessage.newBuilder()
                                    .setIntValue(100)
                                    .setStringValue("original1")
                                    .build()
                            )
                            .putNestedMessageMap(
                                "key2",
                                NestedMessage.newBuilder()
                                    .setIntValue(200)
                                    .setStringValue("original2")
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()

        val localActors = Actors.newBuilder()
            .setLocalActor(1L)
            .putAllVersionVector(mapOf(1L to 10L))
            .build()
        val localNode = VersionNode.newBuilder()
            .setVersion(
                Version.newBuilder()
                    .setTimestamp(5000L)
                    .setActorId(1L)
                    .setActorVersion(10L)
                    .build()
            )
            .build()

        val incomingBaselineActors = mapOf(1L to 10L)

        // Incoming - modifies only "key1" entry, leaves "key2" unchanged
        val incomingValue = TestMessage.newBuilder()
            .setNestedValue(
                NestedMessage.newBuilder()
                    .addNestedRepeatedMessage(
                        NestedRepeatedMessage.newBuilder()
                            .putNestedMessageMap(
                                "key1",
                                NestedMessage.newBuilder()
                                    .setIntValue(999) // Modified
                                    .setStringValue("modified")
                                    .build()
                            )
                            .putNestedMessageMap(
                                "key2",
                                NestedMessage.newBuilder()
                                    .setIntValue(200)
                                    .setStringValue("original2")
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()

        val incomingBaselineActorsObj = Actors.newBuilder()
            .setLocalActor(2L)
            .putAllVersionVector(mapOf(1L to 10L))
            .build()

        val writeResult = resolver.applyLocalWrite(
            currentValue = localValue,
            currentNode = localNode,
            currentActors = incomingBaselineActorsObj,
            newValue = incomingValue,
            timestamp = 6000L
        )

        // When
        val result = resolver.applyChanges(
            localValue = localValue,
            localNode = localNode,
            localActors = localActors,
            incomingChanges = writeResult.changes,
            incomingBaselineActors = incomingBaselineActors
        )

        // Then - key1 should be modified, key2 should be unchanged
        val resultMessage = result.mergeResult.value as TestMessage
        val repeatedMsg = resultMessage.nestedValue.getNestedRepeatedMessage(0)
        assertThat(repeatedMsg.nestedMessageMapMap).hasSize(2)
        assertThat(repeatedMsg.nestedMessageMapMap).containsKey("key1")
        assertThat(repeatedMsg.nestedMessageMapMap).containsKey("key2")

        // key1 modified
        val key1 = requireNotNull(repeatedMsg.nestedMessageMapMap["key1"])
        assertThat(key1.intValue).isEqualTo(999)
        assertThat(key1.stringValue).isEqualTo("modified")

        // key2 unchanged
        val key2 = requireNotNull(repeatedMsg.nestedMessageMapMap["key2"])
        assertThat(key2.intValue).isEqualTo(200)
        assertThat(key2.stringValue).isEqualTo("original2")
    }

    @Test
    fun `applyChanges - concurrent modifications to identical deeply nested field`() {
        // Test concurrent modifications to the same deeply nested field - tests LWW at same path
        // Path: nestedValue.nestedRepeatedMessage[0].nestedMessageMap["key1"].intValue

        val baseValue = TestMessage.newBuilder()
            .setNestedValue(
                NestedMessage.newBuilder()
                    .addNestedRepeatedMessage(
                        NestedRepeatedMessage.newBuilder()
                            .putNestedMessageMap(
                                "key1",
                                NestedMessage.newBuilder()
                                    .setIntValue(100)
                                    .setStringValue("base")
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()

        val baseNode = VersionNode.newBuilder()
            .setVersion(
                Version.newBuilder()
                    .setTimestamp(1000L)
                    .setActorId(1L)
                    .setActorVersion(5L)
                    .build()
            )
            .build()

        // Local change: sets intValue to 200 at timestamp 2000
        val localValue = TestMessage.newBuilder()
            .setNestedValue(
                NestedMessage.newBuilder()
                    .addNestedRepeatedMessage(
                        NestedRepeatedMessage.newBuilder()
                            .putNestedMessageMap(
                                "key1",
                                NestedMessage.newBuilder()
                                    .setIntValue(200) // Local change
                                    .setStringValue("base")
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()

        val localActors = Actors.newBuilder()
            .setLocalActor(1L)
            .putAllVersionVector(mapOf(1L to 6L))
            .build()
        val localNode = VersionNode.newBuilder()
            .setVersion(
                Version.newBuilder()
                    .setTimestamp(2000L)
                    .setActorId(1L)
                    .setActorVersion(6L)
                    .build()
            )
            .build()

        // Incoming change: sets intValue to 300 at timestamp 3000 (later)
        val incomingValue = TestMessage.newBuilder()
            .setNestedValue(
                NestedMessage.newBuilder()
                    .addNestedRepeatedMessage(
                        NestedRepeatedMessage.newBuilder()
                            .putNestedMessageMap(
                                "key1",
                                NestedMessage.newBuilder()
                                    .setIntValue(300) // Incoming change (later timestamp)
                                    .setStringValue("base")
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()

        val incomingBaselineActors = mapOf(1L to 5L)
        val incomingBaselineActorsObj = Actors.newBuilder()
            .setLocalActor(2L)
            .putAllVersionVector(mapOf(1L to 5L))
            .build()

        val writeResult = resolver.applyLocalWrite(
            currentValue = baseValue,
            currentNode = baseNode,
            currentActors = incomingBaselineActorsObj,
            newValue = incomingValue,
            timestamp = 3000L
        )

        // When
        val result = resolver.applyChanges(
            localValue = localValue,
            localNode = localNode,
            localActors = localActors,
            incomingChanges = writeResult.changes,
            incomingBaselineActors = incomingBaselineActors
        )

        // Then - incoming should win (later timestamp)
        val resultMessage = result.mergeResult.value as TestMessage
        val nestedInMap = requireNotNull(resultMessage.nestedValue.getNestedRepeatedMessage(0).nestedMessageMapMap["key1"])
        assertThat(nestedInMap.intValue).isEqualTo(300) // Incoming wins
        assertThat(nestedInMap.stringValue).isEqualTo("base") // Unchanged
    }

    @Test
    fun `applyChanges - add new map entry at depth 5`() {
        // Test adding new map entry at deep level - tests collection growth
        // Path: nestedValue.nestedRepeatedMessage[0].nestedMessageMap

        // Given - local has one map entry
        val localValue = TestMessage.newBuilder()
            .setNestedValue(
                NestedMessage.newBuilder()
                    .addNestedRepeatedMessage(
                        NestedRepeatedMessage.newBuilder()
                            .putNestedMessageMap(
                                "key1",
                                NestedMessage.newBuilder()
                                    .setIntValue(100)
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()

        val localActors = Actors.newBuilder()
            .setLocalActor(1L)
            .putAllVersionVector(mapOf(1L to 8L))
            .build()
        val localNode = VersionNode.newBuilder()
            .setVersion(
                Version.newBuilder()
                    .setTimestamp(4000L)
                    .setActorId(1L)
                    .setActorVersion(8L)
                    .build()
            )
            .build()

        val incomingBaselineActors = mapOf(1L to 8L)

        // Incoming - adds "key2"
        val incomingValue = TestMessage.newBuilder()
            .setNestedValue(
                NestedMessage.newBuilder()
                    .addNestedRepeatedMessage(
                        NestedRepeatedMessage.newBuilder()
                            .putNestedMessageMap(
                                "key1",
                                NestedMessage.newBuilder()
                                    .setIntValue(100)
                                    .build()
                            )
                            .putNestedMessageMap(
                                "key2",
                                NestedMessage.newBuilder()
                                    .setIntValue(200)
                                    .setStringValue("new")
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()

        val incomingBaselineActorsObj = Actors.newBuilder()
            .setLocalActor(2L)
            .putAllVersionVector(mapOf(1L to 8L))
            .build()

        val writeResult = resolver.applyLocalWrite(
            currentValue = localValue,
            currentNode = localNode,
            currentActors = incomingBaselineActorsObj,
            newValue = incomingValue,
            timestamp = 5000L
        )

        // When
        val result = resolver.applyChanges(
            localValue = localValue,
            localNode = localNode,
            localActors = localActors,
            incomingChanges = writeResult.changes,
            incomingBaselineActors = incomingBaselineActors
        )

        // Then - should have both key1 and key2
        val resultMessage = result.mergeResult.value as TestMessage
        val repeatedMsg = resultMessage.nestedValue.getNestedRepeatedMessage(0)
        assertThat(repeatedMsg.nestedMessageMapMap).hasSize(2)
        assertThat(repeatedMsg.nestedMessageMapMap).containsKey("key1")
        assertThat(repeatedMsg.nestedMessageMapMap).containsKey("key2")
        assertThat(requireNotNull(repeatedMsg.nestedMessageMapMap["key1"]).intValue).isEqualTo(100)
        val addedKey2 = requireNotNull(repeatedMsg.nestedMessageMapMap["key2"])
        assertThat(addedKey2.intValue).isEqualTo(200)
        assertThat(addedKey2.stringValue).isEqualTo("new")
    }

    @Test
    fun `applyChanges - depth 5 with int32 map key`() {
        // Test int32 map keys at deep level - tests PathComponent.int32_key
        // Path: int32KeyDeepMap[42].nestedMessageMap["key"].intValue (depth 5)

        // Given - local state with int32 key map
        val localValue = TestMessage.newBuilder()
            .putInt32KeyDeepMap(
                42,
                NestedRepeatedMessage.newBuilder()
                    .putNestedMessageMap(
                        "key",
                        NestedMessage.newBuilder()
                            .setIntValue(100)
                            .setStringValue("original")
                            .build()
                    )
                    .setIntValue(50)
                    .build()
            )
            .setStringValue("root")
            .build()

        val localActors = Actors.newBuilder()
            .setLocalActor(1L)
            .putAllVersionVector(mapOf(1L to 7L))
            .build()
        val localNode = VersionNode.newBuilder()
            .setVersion(
                Version.newBuilder()
                    .setTimestamp(3000L)
                    .setActorId(1L)
                    .setActorVersion(7L)
                    .build()
            )
            .build()

        val incomingBaselineActors = mapOf(1L to 7L)

        // Incoming - changes deeply nested intValue accessed via int32 key
        val incomingValue = TestMessage.newBuilder()
            .putInt32KeyDeepMap(
                42,
                NestedRepeatedMessage.newBuilder()
                    .putNestedMessageMap(
                        "key",
                        NestedMessage.newBuilder()
                            .setIntValue(999) // Changed
                            .setStringValue("original")
                            .build()
                    )
                    .setIntValue(50)
                    .build()
            )
            .setStringValue("root")
            .build()

        val incomingBaselineActorsObj = Actors.newBuilder()
            .setLocalActor(2L)
            .putAllVersionVector(mapOf(1L to 7L))
            .build()

        val writeResult = resolver.applyLocalWrite(
            currentValue = localValue,
            currentNode = localNode,
            currentActors = incomingBaselineActorsObj,
            newValue = incomingValue,
            timestamp = 4000L
        )

        // When
        val result = resolver.applyChanges(
            localValue = localValue,
            localNode = localNode,
            localActors = localActors,
            incomingChanges = writeResult.changes,
            incomingBaselineActors = incomingBaselineActors
        )

        // Then - deeply nested value accessed via int32 key should be updated
        val resultMessage = result.mergeResult.value as TestMessage
        assertThat(resultMessage.stringValue).isEqualTo("root")
        assertThat(resultMessage.int32KeyDeepMapMap).containsKey(42)

        val deepMsg = requireNotNull(resultMessage.int32KeyDeepMapMap[42])
        assertThat(deepMsg.intValue).isEqualTo(50) // Unchanged
        assertThat(deepMsg.nestedMessageMapMap).containsKey("key")

        val nestedInMap = requireNotNull(deepMsg.nestedMessageMapMap["key"])
        assertThat(nestedInMap.intValue).isEqualTo(999) // Changed via int32 key path
        assertThat(nestedInMap.stringValue).isEqualTo("original") // Unchanged
    }

    @Test
    fun `applyChanges - depth 5 with int64 map key`() {
        // Test int64 map keys at deep level - tests PathComponent.int64_key
        // Path: int64KeyDeepMap[9999L].nestedMessageMap["key"].stringValue (depth 5)

        // Given - local state with int64 key map
        val localValue = TestMessage.newBuilder()
            .putInt64KeyDeepMap(
                9999L,
                NestedRepeatedMessage.newBuilder()
                    .putNestedMessageMap(
                        "key",
                        NestedMessage.newBuilder()
                            .setIntValue(100)
                            .setStringValue("original")
                            .build()
                    )
                    .build()
            )
            .build()

        val localActors = Actors.newBuilder()
            .setLocalActor(1L)
            .putAllVersionVector(mapOf(1L to 12L))
            .build()
        val localNode = VersionNode.newBuilder()
            .setVersion(
                Version.newBuilder()
                    .setTimestamp(7000L)
                    .setActorId(1L)
                    .setActorVersion(12L)
                    .build()
            )
            .build()

        val incomingBaselineActors = mapOf(1L to 12L)

        // Incoming - changes stringValue accessed via int64 key
        val incomingValue = TestMessage.newBuilder()
            .putInt64KeyDeepMap(
                9999L,
                NestedRepeatedMessage.newBuilder()
                    .putNestedMessageMap(
                        "key",
                        NestedMessage.newBuilder()
                            .setIntValue(100)
                            .setStringValue("modified") // Changed
                            .build()
                    )
                    .build()
            )
            .build()

        val incomingBaselineActorsObj = Actors.newBuilder()
            .setLocalActor(3L)
            .putAllVersionVector(mapOf(1L to 12L))
            .build()

        val writeResult = resolver.applyLocalWrite(
            currentValue = localValue,
            currentNode = localNode,
            currentActors = incomingBaselineActorsObj,
            newValue = incomingValue,
            timestamp = 8000L
        )

        // When
        val result = resolver.applyChanges(
            localValue = localValue,
            localNode = localNode,
            localActors = localActors,
            incomingChanges = writeResult.changes,
            incomingBaselineActors = incomingBaselineActors
        )

        // Then - value accessed via int64 key should be updated
        val resultMessage = result.mergeResult.value as TestMessage
        assertThat(resultMessage.int64KeyDeepMapMap).containsKey(9999L)

        val deepMsg = requireNotNull(resultMessage.int64KeyDeepMapMap[9999L])
        assertThat(deepMsg.nestedMessageMapMap).containsKey("key")

        val nestedInMap = requireNotNull(deepMsg.nestedMessageMapMap["key"])
        assertThat(nestedInMap.intValue).isEqualTo(100) // Unchanged
        assertThat(nestedInMap.stringValue).isEqualTo("modified") // Changed via int64 key path
    }

    @Test
    fun `applyChanges - depth 7 with int32 key at intermediate level`() {
        // Test extreme depth (depth 7) using int32 keys - tests path component serialization at extreme depth
        // Path: int32KeyDeepMap[42].int32KeyMap[99].nestedRepeatedMessage[0].intValue (depth 7)
        // Depth: 1(int32KeyDeepMap) -> 2(key[42]) -> 3(int32KeyMap) -> 4(key[99]) -> 5(nestedRepeatedMessage) -> 6(index[0]) -> 7(intValue)

        // Given - local state with depth 7 structure
        val localValue = TestMessage.newBuilder()
            .putInt32KeyDeepMap(
                42,
                NestedRepeatedMessage.newBuilder()
                    .putInt32KeyMap(
                        99,
                        NestedMessage.newBuilder()
                            .addNestedRepeatedMessage(
                                NestedRepeatedMessage.newBuilder()
                                    .setIntValue(100)
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .setInt32Value(777)
            .build()

        val localActors = Actors.newBuilder()
            .setLocalActor(1L)
            .putAllVersionVector(mapOf(1L to 15L))
            .build()
        val localNode = VersionNode.newBuilder()
            .setVersion(
                Version.newBuilder()
                    .setTimestamp(9000L)
                    .setActorId(1L)
                    .setActorVersion(15L)
                    .build()
            )
            .build()

        val incomingBaselineActors = mapOf(1L to 15L)

        // Incoming - changes value at depth 7
        val incomingValue = TestMessage.newBuilder()
            .putInt32KeyDeepMap(
                42,
                NestedRepeatedMessage.newBuilder()
                    .putInt32KeyMap(
                        99,
                        NestedMessage.newBuilder()
                            .addNestedRepeatedMessage(
                                NestedRepeatedMessage.newBuilder()
                                    .setIntValue(888) // Changed at depth 7
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .setInt32Value(777)
            .build()

        val incomingBaselineActorsObj = Actors.newBuilder()
            .setLocalActor(2L)
            .putAllVersionVector(mapOf(1L to 15L))
            .build()

        val writeResult = resolver.applyLocalWrite(
            currentValue = localValue,
            currentNode = localNode,
            currentActors = incomingBaselineActorsObj,
            newValue = incomingValue,
            timestamp = 10000L
        )

        // When
        val result = resolver.applyChanges(
            localValue = localValue,
            localNode = localNode,
            localActors = localActors,
            incomingChanges = writeResult.changes,
            incomingBaselineActors = incomingBaselineActors
        )

        // Then - value at depth 7 should be updated
        val resultMessage = result.mergeResult.value as TestMessage
        assertThat(resultMessage.int32Value).isEqualTo(777) // Unchanged

        val level2 = requireNotNull(resultMessage.int32KeyDeepMapMap[42])
        val level4 = requireNotNull(level2.int32KeyMapMap[99])
        val level6 = level4.getNestedRepeatedMessage(0)
        assertThat(level6.intValue).isEqualTo(888) // Changed at depth 7
    }

    @Test
    fun `applyChanges - modifications to different array indices at same depth`() {
        // Test changes to different array indices - tests multiple repeated elements at same depth
        // Path: nestedValue.nestedRepeatedMessage[0] vs [1]

        // Given - local changes nestedRepeatedMessage[0]
        val localValue = TestMessage.newBuilder()
            .setNestedValue(
                NestedMessage.newBuilder()
                    .addNestedRepeatedMessage(
                        NestedRepeatedMessage.newBuilder()
                            .putNestedMessageMap(
                                "key",
                                NestedMessage.newBuilder()
                                    .setIntValue(100) // Changed by local
                                    .build()
                            )
                            .build()
                    )
                    .addNestedRepeatedMessage(
                        NestedRepeatedMessage.newBuilder()
                            .putNestedMessageMap(
                                "key",
                                NestedMessage.newBuilder()
                                    .setIntValue(200) // Original
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()

        val localActors = Actors.newBuilder()
            .setLocalActor(1L)
            .putAllVersionVector(mapOf(1L to 20L))
            .build()
        val localNode = VersionNode.newBuilder()
            .setVersion(
                Version.newBuilder()
                    .setTimestamp(11000L)
                    .setActorId(1L)
                    .setActorVersion(20L)
                    .build()
            )
            .build()

        // Baseline before either change
        val baseValue = TestMessage.newBuilder()
            .setNestedValue(
                NestedMessage.newBuilder()
                    .addNestedRepeatedMessage(
                        NestedRepeatedMessage.newBuilder()
                            .putNestedMessageMap(
                                "key",
                                NestedMessage.newBuilder()
                                    .setIntValue(50) // Original value
                                    .build()
                            )
                            .build()
                    )
                    .addNestedRepeatedMessage(
                        NestedRepeatedMessage.newBuilder()
                            .putNestedMessageMap(
                                "key",
                                NestedMessage.newBuilder()
                                    .setIntValue(200) // Original value
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()

        val baseNode = VersionNode.newBuilder()
            .setVersion(
                Version.newBuilder()
                    .setTimestamp(10000L)
                    .setActorId(1L)
                    .setActorVersion(19L)
                    .build()
            )
            .build()

        // Incoming changes nestedRepeatedMessage[1]
        val incomingValue = TestMessage.newBuilder()
            .setNestedValue(
                NestedMessage.newBuilder()
                    .addNestedRepeatedMessage(
                        NestedRepeatedMessage.newBuilder()
                            .putNestedMessageMap(
                                "key",
                                NestedMessage.newBuilder()
                                    .setIntValue(50) // Original
                                    .build()
                            )
                            .build()
                    )
                    .addNestedRepeatedMessage(
                        NestedRepeatedMessage.newBuilder()
                            .putNestedMessageMap(
                                "key",
                                NestedMessage.newBuilder()
                                    .setIntValue(300) // Changed by incoming
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()

        val incomingBaselineActors = mapOf(1L to 19L)
        val incomingBaselineActorsObj = Actors.newBuilder()
            .setLocalActor(2L)
            .putAllVersionVector(mapOf(1L to 19L))
            .build()

        val writeResult = resolver.applyLocalWrite(
            currentValue = baseValue,
            currentNode = baseNode,
            currentActors = incomingBaselineActorsObj,
            newValue = incomingValue,
            timestamp = 12000L
        )

        // When
        val result = resolver.applyChanges(
            localValue = localValue,
            localNode = localNode,
            localActors = localActors,
            incomingChanges = writeResult.changes,
            incomingBaselineActors = incomingBaselineActors
        )

        // Then - both changes should be preserved
        val resultMessage = result.mergeResult.value as TestMessage
        assertThat(resultMessage.nestedValue.nestedRepeatedMessageList).hasSize(2)

        // [0] should have local's change (100)
        val element0 = resultMessage.nestedValue.getNestedRepeatedMessage(0)
        assertThat(requireNotNull(element0.nestedMessageMapMap["key"]).intValue).isEqualTo(100)

        // [1] should have incoming's change (300)
        val element1 = resultMessage.nestedValue.getNestedRepeatedMessage(1)
        assertThat(requireNotNull(element1.nestedMessageMapMap["key"]).intValue).isEqualTo(300)
    }
}
