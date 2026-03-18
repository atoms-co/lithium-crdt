package co.atoms.protobuf.crdt.wire

import co.atoms.protobuf.crdt.test.NestedMessage
import co.atoms.protobuf.crdt.test.NestedRepeatedMessage
import co.atoms.protobuf.crdt.test.TestMessage
import co.atoms.protobuf.crdt.data.Actors
import co.atoms.protobuf.crdt.data.Version
import co.atoms.protobuf.crdt.data.VersionNode
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Tests for deep nesting (depth > 4) through repeated fields, maps, and nested messages.
 *
 * This tests the change detection and path component parsing for complex structures:
 * - Message -> Repeated -> Map -> Nested Message -> Field (depth 5)
 * - Map -> Nested Message -> Repeated -> Map -> Field (depth 6)
 * - Multiple levels of nesting with various field types
 * - Different PathComponent key types (string, int32, int64)
 * - Edge cases like empty collections and unset intermediate values
 */
class WireMessageResolverDeepNestingTest {
    private val resolver = WireCrdtResolverProvider().messageResolver(adapter = TestMessage.ADAPTER)

    @Test
    fun `applyChanges - depth 5 through nestedValue-repeatedMessage-map-nestedMessage-field`() {
        // Build a deep structure: TestMessage.nestedValue.nestedRepeatedMessage[0].nestedMessageMap["key1"].intValue
        // Path depth: 1(nestedValue) -> 2(nestedRepeatedMessage) -> 3(array[0]) -> 4(map["key1"]) -> 5(intValue)

        // Given - local state with deep nesting
        val localValue = TestMessage(
            nestedValue = NestedMessage(
                nestedRepeatedMessage = listOf(
                    NestedRepeatedMessage(
                        nestedMessageMap = mapOf(
                            "key1" to NestedMessage(
                                intValue = 100,
                                stringValue = "original"
                            )
                        ),
                        intValue = 50
                    )
                )
            ),
            int32Value = 42
        )

        val localActors = Actors(local_actor = 1L, version_vector = mapOf(1L to 5L))
        val localNode = VersionNode(version = Version(timestamp = 1000L, actor_id = 1L, actor_version = 5L))

        val incomingBaselineActors = mapOf(1L to 5L)

        // Generate change from actor 2 that modifies the deeply nested intValue
        val incomingValue = TestMessage(
            nestedValue = NestedMessage(
                nestedRepeatedMessage = listOf(
                    NestedRepeatedMessage(
                        nestedMessageMap = mapOf(
                            "key1" to NestedMessage(
                                intValue = 999, // Changed value
                                stringValue = "original"
                            )
                        ),
                        intValue = 50
                    )
                )
            ),
            int32Value = 42
        )

        val incomingBaselineActorsObj = Actors(local_actor = 2L, version_vector = mapOf(1L to 5L))

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
        val resultMessage = result.mergeResult.value
        assertNotNull(resultMessage)
        assertThat(resultMessage.int32Value).isEqualTo(42) // Unchanged
        assertThat(resultMessage.nestedValue?.nestedRepeatedMessage).hasSize(1)

        val repeatedMsg = resultMessage.nestedValue?.nestedRepeatedMessage?.get(0)
        assertNotNull(repeatedMsg)
        assertThat(repeatedMsg.intValue).isEqualTo(50) // Unchanged
        assertThat(repeatedMsg.nestedMessageMap).containsKey("key1")

        val nestedInMap = repeatedMsg.nestedMessageMap["key1"]
        assertNotNull(nestedInMap)
        assertThat(nestedInMap.intValue).isEqualTo(999) // Changed!
        assertThat(nestedInMap.stringValue).isEqualTo("original") // Unchanged

        assertThat(writeResult.mergeResult.value).isEqualTo(result.mergeResult.value)
        assertThat(writeResult.mergeResult.node).isEqualTo(result.mergeResult.node)
    }

    @Test
    fun `applyChanges - depth 6 through nestedMapValue-nestedMessage-repeatedMessage-map-field`() {
        // Build structure: TestMessage.nestedMapValue["outer"].nestedRepeatedMessage[0].nestedMessageMap["inner"].stringValue
        // Path depth: 1(nestedMapValue) -> 2(map["outer"]) -> 3(nestedRepeatedMessage) -> 4(array[0]) -> 5(map["inner"]) -> 6(stringValue)

        val localValue = TestMessage(
            nestedMapValue = mapOf(
                "outer" to NestedMessage(
                    nestedRepeatedMessage = listOf(
                        NestedRepeatedMessage(
                            nestedMessageMap = mapOf(
                                "inner" to NestedMessage(
                                    stringValue = "deep-original",
                                    intValue = 123
                                )
                            )
                        )
                    )
                )
            ),
            stringValue = "root-value"
        )

        val localActors = Actors(local_actor = 1L, version_vector = mapOf(1L to 10L))
        val localNode = VersionNode(version = Version(timestamp = 5000L, actor_id = 1L, actor_version = 10L))

        val incomingBaselineActors = mapOf(1L to 10L)

        // Create incoming change that modifies the deeply nested stringValue
        val incomingValue = TestMessage(
            nestedMapValue = mapOf(
                "outer" to NestedMessage(
                    nestedRepeatedMessage = listOf(
                        NestedRepeatedMessage(
                            nestedMessageMap = mapOf(
                                "inner" to NestedMessage(
                                    stringValue = "deep-modified", // Changed
                                    intValue = 123
                                )
                            )
                        )
                    )
                )
            ),
            stringValue = "root-value"
        )

        val incomingBaselineActorsObj = Actors(local_actor = 3L, version_vector = mapOf(1L to 10L))

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
        val resultMessage = result.mergeResult.value
        assertNotNull(resultMessage)
        assertThat(resultMessage.stringValue).isEqualTo("root-value") // Unchanged
        assertThat(resultMessage.nestedMapValue).containsKey("outer")

        val outerNested = resultMessage.nestedMapValue["outer"]
        assertNotNull(outerNested)
        assertThat(outerNested.nestedRepeatedMessage).hasSize(1)

        val repeatedMsg = outerNested.nestedRepeatedMessage[0]
        assertThat(repeatedMsg.nestedMessageMap).containsKey("inner")

        val innerNested = repeatedMsg.nestedMessageMap["inner"]
        assertNotNull(innerNested)
        assertThat(innerNested.stringValue).isEqualTo("deep-modified") // Changed!
        assertThat(innerNested.intValue).isEqualTo(123) // Unchanged
    }

    @Test
    fun `applyChanges - multiple deep changes in same message`() {
        // Test multiple changes at different depths in the same message

        val localValue = TestMessage(
            int32Value = 100,
            nestedValue = NestedMessage(
                intValue = 200,
                nestedRepeatedMessage = listOf(
                    NestedRepeatedMessage(
                        intValue = 300,
                        nestedMessageMap = mapOf(
                            "key1" to NestedMessage(intValue = 400)
                        )
                    )
                )
            ),
            nestedMapValue = mapOf(
                "mapKey1" to NestedMessage(intValue = 500)
            )
        )

        val localActors = Actors(local_actor = 1L, version_vector = mapOf(1L to 20L))
        val localNode = VersionNode(version = Version(timestamp = 10000L, actor_id = 1L, actor_version = 20L))

        val incomingBaselineActors = mapOf(1L to 20L)

        // Change values at multiple depths
        val incomingValue = TestMessage(
            int32Value = 111, // Depth 1
            nestedValue = NestedMessage(
                intValue = 222, // Depth 2
                nestedRepeatedMessage = listOf(
                    NestedRepeatedMessage(
                        intValue = 333, // Depth 3
                        nestedMessageMap = mapOf(
                            "key1" to NestedMessage(intValue = 444) // Depth 5
                        )
                    )
                )
            ),
            nestedMapValue = mapOf(
                "mapKey1" to NestedMessage(intValue = 555) // Depth 3
            )
        )

        val incomingBaselineActorsObj = Actors(local_actor = 2L, version_vector = mapOf(1L to 20L))

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
        val resultMessage = result.mergeResult.value
        assertNotNull(resultMessage)
        assertThat(resultMessage.int32Value).isEqualTo(111) // Depth 1
        assertThat(resultMessage.nestedValue?.intValue).isEqualTo(222) // Depth 2

        val repeatedMsg = resultMessage.nestedValue?.nestedRepeatedMessage?.get(0)
        assertNotNull(repeatedMsg)
        assertThat(repeatedMsg.intValue).isEqualTo(333) // Depth 3
        assertThat(repeatedMsg.nestedMessageMap["key1"]?.intValue).isEqualTo(444) // Depth 5

        assertThat(resultMessage.nestedMapValue["mapKey1"]?.intValue).isEqualTo(555) // Depth 3
    }

    @Test
    fun `applyChanges - conflicting changes at different depths are resolved independently`() {
        // Test that changes at different depths are resolved independently using LWW

        val baseValue = TestMessage(
            nestedValue = NestedMessage(
                intValue = 100,
                nestedRepeatedMessage = listOf(
                    NestedRepeatedMessage(
                        nestedMessageMap = mapOf(
                            "key" to NestedMessage(
                                intValue = 200,
                                stringValue = "base"
                            )
                        )
                    )
                )
            )
        )

        // Local makes change at depth 2 (timestamp 2000)
        val localValue = TestMessage(
            nestedValue = NestedMessage(
                intValue = 111, // Local change at depth 2
                nestedRepeatedMessage = listOf(
                    NestedRepeatedMessage(
                        nestedMessageMap = mapOf(
                            "key" to NestedMessage(
                                intValue = 200,
                                stringValue = "base"
                            )
                        )
                    )
                )
            )
        )

        val localActors = Actors(local_actor = 1L, version_vector = mapOf(1L to 10L))
        val localNode = VersionNode(version = Version(timestamp = 2000L, actor_id = 1L, actor_version = 10L))

        // Incoming makes change at depth 5 (timestamp 3000) - should win at that depth
        val incomingValue = TestMessage(
            nestedValue = NestedMessage(
                intValue = 100, // No change at depth 2
                nestedRepeatedMessage = listOf(
                    NestedRepeatedMessage(
                        nestedMessageMap = mapOf(
                            "key" to NestedMessage(
                                intValue = 999, // Incoming change at depth 5
                                stringValue = "base"
                            )
                        )
                    )
                )
            )
        )

        val incomingBaselineActors = mapOf(1L to 9L)
        val incomingBaselineActorsObj = Actors(local_actor = 2L, version_vector = mapOf(1L to 9L))

        // Create base state for incoming
        val baseNode = VersionNode(version = Version(timestamp = 1000L, actor_id = 1L, actor_version = 9L))

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
        val resultMessage = result.mergeResult.value
        assertNotNull(resultMessage)
        assertThat(resultMessage.nestedValue?.intValue).isEqualTo(111) // Local kept (depth 2)

        val nestedInMap = resultMessage.nestedValue?.nestedRepeatedMessage?.get(0)?.nestedMessageMap?.get("key")
        assertNotNull(nestedInMap)
        assertThat(nestedInMap.intValue).isEqualTo(999) // Incoming applied (depth 5)
    }

    // ========== Priority Test Cases for Coverage Gaps ==========

    @Test
    fun `applyChanges - unset nested message creates structure for deep change`() {
        // Test that unset nested message at intermediate depth initializes path for deep changes
        // This tests the ability of CRDT to create missing intermediate structures

        // Given - local state without nestedValue set
        val localValue = TestMessage(
            int32Value = 42,
            stringValue = "root"
        )

        val localActors = Actors(local_actor = 1L, version_vector = mapOf(1L to 5L))
        val localNode = VersionNode(version = Version(timestamp = 1000L, actor_id = 1L, actor_version = 5L))

        val incomingBaselineActors = mapOf(1L to 5L)

        // Incoming - creates entire structure from scratch including deep nested changes
        val incomingValue = TestMessage(
            int32Value = 42,
            stringValue = "root",
            nestedValue = NestedMessage(
                intValue = 100,
                nestedRepeatedMessage = listOf(
                    NestedRepeatedMessage(
                        nestedMessageMap = mapOf(
                            "key1" to NestedMessage(
                                intValue = 999,
                                stringValue = "new"
                            )
                        ),
                        intValue = 50
                    )
                )
            )
        )

        val incomingBaselineActorsObj = Actors(local_actor = 2L, version_vector = mapOf(1L to 5L))

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
        val resultMessage = result.mergeResult.value
        assertNotNull(resultMessage)
        assertThat(resultMessage.int32Value).isEqualTo(42)
        assertThat(resultMessage.stringValue).isEqualTo("root")
        assertThat(resultMessage.nestedValue).isNotNull()

        assertThat(resultMessage.nestedValue?.intValue).isEqualTo(100)
        assertThat(resultMessage.nestedValue?.nestedRepeatedMessage).hasSize(1)

        val repeatedMsg = resultMessage.nestedValue?.nestedRepeatedMessage?.get(0)
        assertNotNull(repeatedMsg)
        assertThat(repeatedMsg.intValue).isEqualTo(50)
        assertThat(repeatedMsg.nestedMessageMap).containsKey("key1")
        assertThat(repeatedMsg.nestedMessageMap["key1"]?.intValue).isEqualTo(999)
        assertThat(repeatedMsg.nestedMessageMap["key1"]?.stringValue).isEqualTo("new")
    }

    @Test
    fun `applyChanges - unset nested message at intermediate depth initializes path`() {
        // Test unset nested message at intermediate depth - tests path creation
        // Path: TestMessage without nestedValue set -> incoming sets deep field

        // Given - local state without nestedValue
        val localValue = TestMessage(
            int32Value = 42,
            stringValue = "root"
        )

        val localActors = Actors(local_actor = 1L, version_vector = mapOf(1L to 3L))
        val localNode = VersionNode(version = Version(timestamp = 1000L, actor_id = 1L, actor_version = 3L))

        val incomingBaselineActors = mapOf(1L to 3L)

        // Incoming - sets deep field in previously unset nestedValue
        val incomingValue = TestMessage(
            int32Value = 42,
            stringValue = "root",
            nestedValue = NestedMessage(
                nestedRepeatedMessage = listOf(
                    NestedRepeatedMessage(
                        nestedMessageMap = mapOf(
                            "key" to NestedMessage(
                                intValue = 123,
                                stringValue = "created"
                            )
                        )
                    )
                )
            )
        )

        val incomingBaselineActorsObj = Actors(local_actor = 2L, version_vector = mapOf(1L to 3L))

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
        val resultMessage = result.mergeResult.value
        assertNotNull(resultMessage)
        assertThat(resultMessage.int32Value).isEqualTo(42)
        assertThat(resultMessage.stringValue).isEqualTo("root")
        assertThat(resultMessage.nestedValue).isNotNull()
        assertThat(resultMessage.nestedValue?.nestedRepeatedMessage).hasSize(1)

        val repeatedMsg = resultMessage.nestedValue?.nestedRepeatedMessage?.get(0)
        assertNotNull(repeatedMsg)
        assertThat(repeatedMsg.nestedMessageMap).containsKey("key")
        assertThat(repeatedMsg.nestedMessageMap["key"]?.intValue).isEqualTo(123)
        assertThat(repeatedMsg.nestedMessageMap["key"]?.stringValue).isEqualTo("created")
    }

    @Test
    fun `applyChanges - modify one of multiple map entries at depth 5`() {
        // Test modifying one map entry while leaving others unchanged at deep level
        // Path: nestedValue.nestedRepeatedMessage[0].nestedMessageMap

        // Given - local has two map entries at depth
        val localValue = TestMessage(
            nestedValue = NestedMessage(
                nestedRepeatedMessage = listOf(
                    NestedRepeatedMessage(
                        nestedMessageMap = mapOf(
                            "key1" to NestedMessage(
                                intValue = 100,
                                stringValue = "original1"
                            ),
                            "key2" to NestedMessage(
                                intValue = 200,
                                stringValue = "original2"
                            )
                        )
                    )
                )
            )
        )

        val localActors = Actors(local_actor = 1L, version_vector = mapOf(1L to 10L))
        val localNode = VersionNode(version = Version(timestamp = 5000L, actor_id = 1L, actor_version = 10L))

        val incomingBaselineActors = mapOf(1L to 10L)

        // Incoming - modifies only "key1" entry, leaves "key2" unchanged
        val incomingValue = TestMessage(
            nestedValue = NestedMessage(
                nestedRepeatedMessage = listOf(
                    NestedRepeatedMessage(
                        nestedMessageMap = mapOf(
                            "key1" to NestedMessage(
                                intValue = 999, // Modified
                                stringValue = "modified"
                            ),
                            "key2" to NestedMessage(
                                intValue = 200,
                                stringValue = "original2"
                            )
                        )
                    )
                )
            )
        )

        val incomingBaselineActorsObj = Actors(local_actor = 2L, version_vector = mapOf(1L to 10L))

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
        val resultMessage = result.mergeResult.value
        assertNotNull(resultMessage)
        val repeatedMsg = resultMessage.nestedValue?.nestedRepeatedMessage?.get(0)
        assertNotNull(repeatedMsg)
        assertThat(repeatedMsg.nestedMessageMap).hasSize(2)
        assertThat(repeatedMsg.nestedMessageMap).containsKey("key1")
        assertThat(repeatedMsg.nestedMessageMap).containsKey("key2")

        // key1 modified
        assertThat(repeatedMsg.nestedMessageMap["key1"]?.intValue).isEqualTo(999)
        assertThat(repeatedMsg.nestedMessageMap["key1"]?.stringValue).isEqualTo("modified")

        // key2 unchanged
        assertThat(repeatedMsg.nestedMessageMap["key2"]?.intValue).isEqualTo(200)
        assertThat(repeatedMsg.nestedMessageMap["key2"]?.stringValue).isEqualTo("original2")
    }

    @Test
    fun `applyChanges - concurrent modifications to identical deeply nested field`() {
        // Test concurrent modifications to the same deeply nested field - tests LWW at same path
        // Path: nestedValue.nestedRepeatedMessage[0].nestedMessageMap["key1"].intValue

        val baseValue = TestMessage(
            nestedValue = NestedMessage(
                nestedRepeatedMessage = listOf(
                    NestedRepeatedMessage(
                        nestedMessageMap = mapOf(
                            "key1" to NestedMessage(
                                intValue = 100,
                                stringValue = "base"
                            )
                        )
                    )
                )
            )
        )

        val baseNode = VersionNode(version = Version(timestamp = 1000L, actor_id = 1L, actor_version = 5L))

        // Local change: sets intValue to 200 at timestamp 2000
        val localValue = TestMessage(
            nestedValue = NestedMessage(
                nestedRepeatedMessage = listOf(
                    NestedRepeatedMessage(
                        nestedMessageMap = mapOf(
                            "key1" to NestedMessage(
                                intValue = 200, // Local change
                                stringValue = "base"
                            )
                        )
                    )
                )
            )
        )

        val localActors = Actors(local_actor = 1L, version_vector = mapOf(1L to 6L))
        val localNode = VersionNode(version = Version(timestamp = 2000L, actor_id = 1L, actor_version = 6L))

        // Incoming change: sets intValue to 300 at timestamp 3000 (later)
        val incomingValue = TestMessage(
            nestedValue = NestedMessage(
                nestedRepeatedMessage = listOf(
                    NestedRepeatedMessage(
                        nestedMessageMap = mapOf(
                            "key1" to NestedMessage(
                                intValue = 300, // Incoming change (later timestamp)
                                stringValue = "base"
                            )
                        )
                    )
                )
            )
        )

        val incomingBaselineActors = mapOf(1L to 5L)
        val incomingBaselineActorsObj = Actors(local_actor = 2L, version_vector = mapOf(1L to 5L))

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
        val resultMessage = result.mergeResult.value
        assertNotNull(resultMessage)
        val nestedInMap = resultMessage.nestedValue?.nestedRepeatedMessage?.get(0)?.nestedMessageMap?.get("key1")
        assertNotNull(nestedInMap)
        assertThat(nestedInMap.intValue).isEqualTo(300) // Incoming wins
        assertThat(nestedInMap.stringValue).isEqualTo("base") // Unchanged
    }

    @Test
    fun `applyChanges - add new map entry at depth 5`() {
        // Test adding new map entry at deep level - tests collection growth
        // Path: nestedValue.nestedRepeatedMessage[0].nestedMessageMap

        // Given - local has one map entry
        val localValue = TestMessage(
            nestedValue = NestedMessage(
                nestedRepeatedMessage = listOf(
                    NestedRepeatedMessage(
                        nestedMessageMap = mapOf(
                            "key1" to NestedMessage(intValue = 100)
                        )
                    )
                )
            )
        )

        val localActors = Actors(local_actor = 1L, version_vector = mapOf(1L to 8L))
        val localNode = VersionNode(version = Version(timestamp = 4000L, actor_id = 1L, actor_version = 8L))

        val incomingBaselineActors = mapOf(1L to 8L)

        // Incoming - adds "key2"
        val incomingValue = TestMessage(
            nestedValue = NestedMessage(
                nestedRepeatedMessage = listOf(
                    NestedRepeatedMessage(
                        nestedMessageMap = mapOf(
                            "key1" to NestedMessage(intValue = 100),
                            "key2" to NestedMessage(
                                intValue = 200,
                                stringValue = "new"
                            )
                        )
                    )
                )
            )
        )

        val incomingBaselineActorsObj = Actors(local_actor = 2L, version_vector = mapOf(1L to 8L))

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
        val resultMessage = result.mergeResult.value
        assertNotNull(resultMessage)
        val repeatedMsg = resultMessage.nestedValue?.nestedRepeatedMessage?.get(0)
        assertNotNull(repeatedMsg)
        assertThat(repeatedMsg.nestedMessageMap).hasSize(2)
        assertThat(repeatedMsg.nestedMessageMap).containsKey("key1")
        assertThat(repeatedMsg.nestedMessageMap).containsKey("key2")
        assertThat(repeatedMsg.nestedMessageMap["key1"]?.intValue).isEqualTo(100)
        assertThat(repeatedMsg.nestedMessageMap["key2"]?.intValue).isEqualTo(200)
        assertThat(repeatedMsg.nestedMessageMap["key2"]?.stringValue).isEqualTo("new")
    }

    @Test
    fun `applyChanges - depth 5 with int32 map key`() {
        // Test int32 map keys at deep level - tests PathComponent.int32_key
        // Path: int32KeyDeepMap[42].nestedMessageMap["key"].intValue (depth 5)

        // Given - local state with int32 key map
        val localValue = TestMessage(
            int32KeyDeepMap = mapOf(
                42 to NestedRepeatedMessage(
                    nestedMessageMap = mapOf(
                        "key" to NestedMessage(
                            intValue = 100,
                            stringValue = "original"
                        )
                    ),
                    intValue = 50
                )
            ),
            stringValue = "root"
        )

        val localActors = Actors(local_actor = 1L, version_vector = mapOf(1L to 7L))
        val localNode = VersionNode(version = Version(timestamp = 3000L, actor_id = 1L, actor_version = 7L))

        val incomingBaselineActors = mapOf(1L to 7L)

        // Incoming - changes deeply nested intValue accessed via int32 key
        val incomingValue = TestMessage(
            int32KeyDeepMap = mapOf(
                42 to NestedRepeatedMessage(
                    nestedMessageMap = mapOf(
                        "key" to NestedMessage(
                            intValue = 999, // Changed
                            stringValue = "original"
                        )
                    ),
                    intValue = 50
                )
            ),
            stringValue = "root"
        )

        val incomingBaselineActorsObj = Actors(local_actor = 2L, version_vector = mapOf(1L to 7L))

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
        val resultMessage = result.mergeResult.value
        assertNotNull(resultMessage)
        assertThat(resultMessage.stringValue).isEqualTo("root")
        assertThat(resultMessage.int32KeyDeepMap).containsKey(42)

        val deepMsg = resultMessage.int32KeyDeepMap[42]
        assertNotNull(deepMsg)
        assertThat(deepMsg.intValue).isEqualTo(50) // Unchanged
        assertThat(deepMsg.nestedMessageMap).containsKey("key")

        val nestedInMap = deepMsg.nestedMessageMap["key"]
        assertNotNull(nestedInMap)
        assertThat(nestedInMap.intValue).isEqualTo(999) // Changed via int32 key path
        assertThat(nestedInMap.stringValue).isEqualTo("original") // Unchanged
    }

    @Test
    fun `applyChanges - depth 5 with int64 map key`() {
        // Test int64 map keys at deep level - tests PathComponent.int64_key
        // Path: int64KeyDeepMap[9999L].nestedMessageMap["key"].stringValue (depth 5)

        // Given - local state with int64 key map
        val localValue = TestMessage(
            int64KeyDeepMap = mapOf(
                9999L to NestedRepeatedMessage(
                    nestedMessageMap = mapOf(
                        "key" to NestedMessage(
                            intValue = 100,
                            stringValue = "original"
                        )
                    )
                )
            )
        )

        val localActors = Actors(local_actor = 1L, version_vector = mapOf(1L to 12L))
        val localNode = VersionNode(version = Version(timestamp = 7000L, actor_id = 1L, actor_version = 12L))

        val incomingBaselineActors = mapOf(1L to 12L)

        // Incoming - changes stringValue accessed via int64 key
        val incomingValue = TestMessage(
            int64KeyDeepMap = mapOf(
                9999L to NestedRepeatedMessage(
                    nestedMessageMap = mapOf(
                        "key" to NestedMessage(
                            intValue = 100,
                            stringValue = "modified" // Changed
                        )
                    )
                )
            )
        )

        val incomingBaselineActorsObj = Actors(local_actor = 3L, version_vector = mapOf(1L to 12L))

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
        val resultMessage = result.mergeResult.value
        assertNotNull(resultMessage)
        assertThat(resultMessage.int64KeyDeepMap).containsKey(9999L)

        val deepMsg = resultMessage.int64KeyDeepMap[9999L]
        assertNotNull(deepMsg)
        assertThat(deepMsg.nestedMessageMap).containsKey("key")

        val nestedInMap = deepMsg.nestedMessageMap["key"]
        assertNotNull(nestedInMap)
        assertThat(nestedInMap.intValue).isEqualTo(100) // Unchanged
        assertThat(nestedInMap.stringValue).isEqualTo("modified") // Changed via int64 key path
    }

    @Test
    fun `applyChanges - depth 7 with int32 key at intermediate level`() {
        // Test extreme depth (depth 7) using int32 keys - tests path component serialization at extreme depth
        // Path: int32KeyDeepMap[42].int32KeyMap[99].nestedRepeatedMessage[0].intValue (depth 7)
        // Depth: 1(int32KeyDeepMap) -> 2(key[42]) -> 3(int32KeyMap) -> 4(key[99]) -> 5(nestedRepeatedMessage) -> 6(index[0]) -> 7(intValue)

        // Given - local state with depth 7 structure
        val localValue = TestMessage(
            int32KeyDeepMap = mapOf(
                42 to NestedRepeatedMessage(
                    int32KeyMap = mapOf(
                        99 to NestedMessage(
                            nestedRepeatedMessage = listOf(
                                NestedRepeatedMessage(intValue = 100)
                            )
                        )
                    )
                )
            ),
            int32Value = 777
        )

        val localActors = Actors(local_actor = 1L, version_vector = mapOf(1L to 15L))
        val localNode = VersionNode(version = Version(timestamp = 9000L, actor_id = 1L, actor_version = 15L))

        val incomingBaselineActors = mapOf(1L to 15L)

        // Incoming - changes value at depth 7
        val incomingValue = TestMessage(
            int32KeyDeepMap = mapOf(
                42 to NestedRepeatedMessage(
                    int32KeyMap = mapOf(
                        99 to NestedMessage(
                            nestedRepeatedMessage = listOf(
                                NestedRepeatedMessage(intValue = 888) // Changed at depth 7
                            )
                        )
                    )
                )
            ),
            int32Value = 777
        )

        val incomingBaselineActorsObj = Actors(local_actor = 2L, version_vector = mapOf(1L to 15L))

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
        val resultMessage = result.mergeResult.value
        assertNotNull(resultMessage)
        assertThat(resultMessage.int32Value).isEqualTo(777) // Unchanged

        val level2 = resultMessage.int32KeyDeepMap[42]
        assertNotNull(level2)
        val level4 = level2.int32KeyMap[99]
        assertNotNull(level4)
        val level6 = level4.nestedRepeatedMessage[0]
        assertThat(level6.intValue).isEqualTo(888) // Changed at depth 7
    }

    @Test
    fun `applyChanges - modifications to different array indices at same depth`() {
        // Test changes to different array indices - tests multiple repeated elements at same depth
        // Path: nestedValue.nestedRepeatedMessage[0] vs [1]

        // Baseline before either change
        val baseValue = TestMessage(
            nestedValue = NestedMessage(
                nestedRepeatedMessage = listOf(
                    NestedRepeatedMessage(
                        nestedMessageMap = mapOf(
                            "key" to NestedMessage(intValue = 50) // Original value
                        )
                    ),
                    NestedRepeatedMessage(
                        nestedMessageMap = mapOf(
                            "key" to NestedMessage(intValue = 200) // Original value
                        )
                    )
                )
            )
        )

        val baseNode = VersionNode(version = Version(timestamp = 10000L, actor_id = 1L, actor_version = 19L))

        // Given - local changes nestedRepeatedMessage[0]
        val localValue = TestMessage(
            nestedValue = NestedMessage(
                nestedRepeatedMessage = listOf(
                    NestedRepeatedMessage(
                        nestedMessageMap = mapOf(
                            "key" to NestedMessage(intValue = 100) // Changed by local
                        )
                    ),
                    NestedRepeatedMessage(
                        nestedMessageMap = mapOf(
                            "key" to NestedMessage(intValue = 200) // Original
                        )
                    )
                )
            )
        )

        val localActors = Actors(local_actor = 1L, version_vector = mapOf(1L to 20L))
        val localNode = VersionNode(version = Version(timestamp = 11000L, actor_id = 1L, actor_version = 20L))

        // Incoming changes nestedRepeatedMessage[1]
        val incomingValue = TestMessage(
            nestedValue = NestedMessage(
                nestedRepeatedMessage = listOf(
                    NestedRepeatedMessage(
                        nestedMessageMap = mapOf(
                            "key" to NestedMessage(intValue = 50) // Original
                        )
                    ),
                    NestedRepeatedMessage(
                        nestedMessageMap = mapOf(
                            "key" to NestedMessage(intValue = 300) // Changed by incoming
                        )
                    )
                )
            )
        )

        val incomingBaselineActors = mapOf(1L to 19L)
        val incomingBaselineActorsObj = Actors(local_actor = 2L, version_vector = mapOf(1L to 19L))

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
        val resultMessage = result.mergeResult.value
        assertNotNull(resultMessage)
        assertThat(resultMessage.nestedValue?.nestedRepeatedMessage).hasSize(2)

        // [0] should have local's change (100)
        val element0 = resultMessage.nestedValue?.nestedRepeatedMessage?.get(0)
        assertNotNull(element0)
        assertThat(element0.nestedMessageMap["key"]?.intValue).isEqualTo(100)

        // [1] should have incoming's change (300)
        val element1 = resultMessage.nestedValue?.nestedRepeatedMessage?.get(1)
        assertNotNull(element1)
        assertThat(element1.nestedMessageMap["key"]?.intValue).isEqualTo(300)
    }
}
