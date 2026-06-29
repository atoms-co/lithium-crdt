package co.atoms.lithium.crdt.wire

import co.atoms.lithium.crdt.test.EnumSample
import co.atoms.lithium.crdt.test.NestedMessage
import co.atoms.lithium.crdt.test.NestedMessageWithId
import co.atoms.lithium.crdt.test.TestMessage
import co.atoms.lithium.crdt.data.Actors
import co.atoms.lithium.crdt.data.PathComponent
import co.atoms.lithium.crdt.data.Version
import co.atoms.lithium.crdt.data.VersionNode
import co.atoms.lithium.crdt.data.VersionNode.Struct
import co.atoms.lithium.crdt.resolver.ResolverDeltaResult
import co.atoms.lithium.crdt.resolver.version.ResolutionStrategy
import co.atoms.lithium.crdt.resolver.version.ResolutionStrategy.INCOMING
import co.atoms.lithium.crdt.resolver.version.ResolutionStrategy.MERGED_VALUES
import co.atoms.lithium.crdt.resolver.version.ResolutionStrategy.NO_CHANGE
import com.google.common.truth.Truth.assertThat
import org.checkerframework.checker.units.qual.m
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Tests for CrdtMessageIncomingResolver - resolveConflict operations.
 *
 * These tests focus on conflict resolution between local and incoming values,
 * handling version-based last-write-wins semantics.
 */
class WireCrdtIncomingResolverTest {
    private val adapter = WireCrdtResolverProvider().messageResolver(adapter = TestMessage.ADAPTER)

    private val sampleProtoMessage = TestMessage(
        int32Value = 100,
        stringValue = "sample"
    )

    @Test
    fun resolver_mergeProtos_withPrimitiveTypesOnly_lastWriteWins() {
        val protoV0 = sampleProtoMessage.copy(
            int32Value = 5,
            int64Value = 10,
            stringValue = "USD",
            enumValue = EnumSample.VALUE2,
            enumMapValue = mapOf("2" to EnumSample.VALUE1),
        )

        val delta1 =
            adapter.applyLocalWrite(
                currentValue = null,
                currentNode = null,
                currentActors = null,
                newValue = protoV0,
                timestamp = 1,
            )
        val result1 = delta1.mergeResult
        val actorId1 = delta1.actors.local_actor

        assertThat(result1.resolution).isTrue()
        assertThat(result1.value).isEqualTo(protoV0)
        assertThat(result1.node).isEqualTo(VersionNode(version = Version(1, actorId1, 1)))

        val protoV2 =
            protoV0.copy(
                int64Value = 20, // 4
                stringValue = "XXX", // 13
                oneOfValue1 = "1234", // 17
                oneOfValue2 = null,
                timestamp_value = Instant.ofEpochSecond(12, 34567890), // 26
            )
        val delta2 =
            adapter.applyLocalWrite(
                currentValue = result1.value,
                currentNode = result1.node,
                currentActors = delta1.actors,
                newValue = protoV2,
                timestamp = 2,
            )
        val result2 = delta2.mergeResult
        val actorId2 = delta2.actors.local_actor

        val mergedVersion = mapOf(
            4 to VersionNode(version = Version(2, actorId2, 2)),
            13 to VersionNode(version = Version(2, actorId2, 2)),
            17 to VersionNode(version = Version(2, actorId2, 2)),
            18 to VersionNode(version = Version(2, actorId2, 2)),
            26 to VersionNode(version = Version(2, actorId2, 2)),
        )

        assertThat(result2.resolution).isTrue()
        assertThat(result2.value).isEqualTo(protoV2)
        assertThat(result2.node).isEqualTo(
            VersionNode(
                version = Version(1, actorId1, 1),
                struct = Struct(fields = mergedVersion)
            )
        )

        val delta3 = adapter.resolveConflict(lhs = delta1, rhs = delta2)
        val result3 = delta3.mergeResult
        assertThat(result3.resolution).isEqualTo(INCOMING)
        assertThat(
            VersionNode(
                version = Version(1, actorId1, 1),
                struct = Struct(
                    fields = mergedVersion
                ),
            )
        )
            .isEqualTo(result3.node)

        val result4 =
            adapter.applyLocalWrite(
                currentValue = result1.value,
                currentNode = result1.node,
                currentActors = delta3.actors,
                newValue =
                protoV0.copy(
                    stringValue = "ARG", // 13
                    oneOfValue1 = null, // 17
                    oneOfValue2 = NestedMessage("1234"), // 18
                    timestamp_value = Instant.ofEpochSecond(98, 76543210), // 26
                    nestedBinaryValue = NestedMessage(stringValue = "9876", intValue = 4567), //  27
                ),
                timestamp = 3,
            )

        val delta5 = adapter.resolveConflict(lhs = result4, rhs = delta3)
        val result5 = delta5.mergeResult

        var mergedProto = result5.value
        assertNotNull(mergedProto)

        assertThat(mergedProto.int32Value).isEqualTo(5)
        assertThat(mergedProto.int64Value).isEqualTo(20)
        assertThat(mergedProto.stringValue).isEqualTo("ARG")
        assertThat(mergedProto.enumValue).isEqualTo(EnumSample.VALUE2)
        assertThat(mergedProto.enumMapValue).isEqualTo(mapOf("2" to EnumSample.VALUE1))
        assertThat(mergedProto.oneOfValue1).isNull()
        assertThat(mergedProto.oneOfValue2).isEqualTo(NestedMessage("1234"))
        assertThat(mergedProto.nestedBinaryValue).isEqualTo(NestedMessage(stringValue = "9876", intValue = 4567))
        assertThat(mergedProto.timestamp_value).isEqualTo(Instant.ofEpochSecond(98, 76543210))
        assertThat(result5.resolution).isEqualTo(MERGED_VALUES)

        assertThat(result5.node).isEqualTo(
            VersionNode(
                version = Version(1, delta1.actors.local_actor, 1),
                struct = Struct(
                    fields = mapOf(
                        4 to VersionNode(version = Version(2, delta1.actors.local_actor, 2)),
                        13 to VersionNode(version = Version(3, delta5.actors.local_actor, 3)),
                        17 to VersionNode(version = Version(3, delta5.actors.local_actor, 3)),
                        18 to VersionNode(version = Version(3, delta5.actors.local_actor, 3)),
                        26 to VersionNode(version = Version(3, delta5.actors.local_actor, 3)),
                        27 to VersionNode(version = Version(3, delta5.actors.local_actor, 3)),
                    )
                ),
            )
        )

        val delta6 = adapter.applyLocalWrite(
            currentValue = result5.value,
            currentNode = result5.node,
            currentActors = delta5.actors,
            newValue = mergedProto.copy(
                stringValue = "ARG", // 13
                oneOfValue1 = "1234", // 17
                oneOfValue2 = null, // 18
                timestamp_value = Instant.ofEpochSecond(123, 4567890), // 26
            ),
            timestamp = 2,
        )
        val result6 = delta6.mergeResult

        assertThat(result6.node).isEqualTo(
            VersionNode(
                version = Version(1, delta1.actors.local_actor, 1),
                struct = Struct(
                    fields = mapOf(
                        4 to VersionNode(version = Version(2, delta1.actors.local_actor, 2)),
                        13 to VersionNode(version = Version(3, delta6.actors.local_actor, 3)),
                        17 to VersionNode(version = Version(4, delta6.actors.local_actor, 4)),
                        18 to VersionNode(version = Version(4, delta6.actors.local_actor, 4)),
                        26 to VersionNode(version = Version(4, delta6.actors.local_actor, 4)),
                        27 to VersionNode(version = Version(3, delta5.actors.local_actor, 3)),
                    )
                ),
            )
        )

        val delta7 = adapter.resolveConflict(lhs = delta5, rhs = delta6)
        val result7 = delta7.mergeResult

        assertThat(result7.node).isEqualTo(
            VersionNode(
                version = Version(1, delta1.actors.local_actor, 1),
                struct = Struct(
                    fields = mapOf(
                        4 to VersionNode(version = Version(2, delta1.actors.local_actor, 2)),
                        13 to VersionNode(version = Version(3, delta6.actors.local_actor, 3)),
                        17 to VersionNode(version = Version(4, delta6.actors.local_actor, 4)),
                        18 to VersionNode(version = Version(4, delta6.actors.local_actor, 4)),
                        26 to VersionNode(version = Version(4, delta6.actors.local_actor, 4)),
                        27 to VersionNode(version = Version(3, delta5.actors.local_actor, 3)),
                    )
                ),
            )
        )

        mergedProto = result7.value
        assertNotNull(mergedProto)

        assertThat(mergedProto.int32Value).isEqualTo(5)
        assertThat(mergedProto.int64Value).isEqualTo(20)
        assertThat(mergedProto.stringValue).isEqualTo("ARG")
        assertThat(mergedProto.enumValue).isEqualTo(EnumSample.VALUE2)
        assertThat(mergedProto.enumMapValue).isEqualTo(mapOf("2" to EnumSample.VALUE1))
        assertThat(mergedProto.oneOfValue1).isEqualTo("1234")
        assertThat(mergedProto.oneOfValue2).isNull()
        assertThat(mergedProto.timestamp_value).isEqualTo(Instant.ofEpochSecond(123, 4567890))

        val result8 =
            adapter.applyLocalWrite(
                currentValue = result7.value,
                currentNode = result7.node,
                currentActors = delta7.actors,
                newValue =
                mergedProto.copy(
                    stringValue = "ARG", // 13
                    oneOfValue1 = null, // 17
                    oneOfValue2 = NestedMessage("4567"), // 18
                    timestamp_value = Instant.ofEpochSecond(1234, 567890), // 26
                ),
                timestamp = 4,
            )

        assertThat(result8.mergeResult.node).isEqualTo(
            VersionNode(
                version = Version(1, delta1.actors.local_actor, 1),
                struct = Struct(
                    fields = mapOf(
                        4 to VersionNode(version = Version(2, delta1.actors.local_actor, 2)),
                        13 to VersionNode(version = Version(3, delta6.actors.local_actor, 3)),
                        17 to VersionNode(version = Version(5, delta6.actors.local_actor, 5)),
                        18 to VersionNode(version = Version(5, delta6.actors.local_actor, 5)),
                        26 to VersionNode(version = Version(5, delta6.actors.local_actor, 5)),
                        27 to VersionNode(version = Version(3, delta5.actors.local_actor, 3)),
                    )
                ),
            )
        )

        val delta9 = adapter.resolveConflict(result8, delta7.copy(actors = Actors()))
        val result9 = delta9.mergeResult

        assertThat(result9.node).isEqualTo(
            VersionNode(
                version = Version(1, delta1.actors.local_actor, 1),
                struct = Struct(
                    fields = mapOf(
                        4 to VersionNode(version = Version(2, delta1.actors.local_actor, 2)),
                        13 to VersionNode(version = Version(3, delta6.actors.local_actor, 3)),
                        17 to VersionNode(version = Version(5, delta6.actors.local_actor, 5)),
                        18 to VersionNode(version = Version(5, delta6.actors.local_actor, 5)),
                        26 to VersionNode(version = Version(5, delta6.actors.local_actor, 5)),
                        27 to VersionNode(version = Version(3, delta5.actors.local_actor, 3)),
                    )
                ),
            )
        )

        mergedProto = result9.value
        assertNotNull(mergedProto)

        assertThat(mergedProto.int32Value).isEqualTo(5)
        assertThat(mergedProto.int64Value).isEqualTo(20)
        assertThat(mergedProto.stringValue).isEqualTo("ARG")
        assertThat(mergedProto.enumValue).isEqualTo(EnumSample.VALUE2)
        assertThat(mergedProto.enumMapValue).isEqualTo(mapOf("2" to EnumSample.VALUE1))
        assertThat(mergedProto.oneOfValue1).isNull()
        assertThat(mergedProto.oneOfValue2).isEqualTo(NestedMessage("4567"))
        assertThat(mergedProto.timestamp_value).isEqualTo(Instant.ofEpochSecond(1234, 567890))

        val result10 = adapter.resolveConflict(lhs = delta9, rhs = delta9).mergeResult
        assertThat(result10.resolution).isEqualTo(NO_CHANGE)
        assertThat(result10.node).isEqualTo(
            VersionNode(
                version = Version(1, delta1.actors.local_actor, 1),
                struct = Struct(
                    fields = mapOf(
                        4 to VersionNode(version = Version(2, delta1.actors.local_actor, 2)),
                        13 to VersionNode(version = Version(3, delta6.actors.local_actor, 3)),
                        17 to VersionNode(version = Version(5, delta6.actors.local_actor, 5)),
                        18 to VersionNode(version = Version(5, delta6.actors.local_actor, 5)),
                        26 to VersionNode(version = Version(5, delta6.actors.local_actor, 5)),
                        27 to VersionNode(version = Version(3, delta5.actors.local_actor, 3)),
                    )
                ),
            )
        )
    }

    @Test
    fun `repeated field with IDs - conflict resolution`() {
        // Initial state: 2 items with IDs
        val item1 = NestedMessageWithId(id = "item1", stringValue = "Product 1", intValue = 100)
        val item2 = NestedMessageWithId(id = "item2", stringValue = "Product 2", intValue = 200)
        val initial = TestMessage(nestedListWithIdValue = listOf(item1, item2))

        val result1 = adapter.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = initial,
            timestamp = 100,
        ).mergeResult

        // Device A: Modify item1 and add item3
        val modifiedItem1 = item1.copy(stringValue = "Updated Product 1")
        val item3 = NestedMessageWithId(id = "item3", stringValue = "Product 3", intValue = 300)
        val deviceA = initial.copy(nestedListWithIdValue = listOf(item3, modifiedItem1, item2))

        val resultA = adapter.applyLocalWrite(
            currentValue = result1.value,
            currentNode = result1.node,
            currentActors = null,
            newValue = deviceA,
            timestamp = 200,
        )

        // Device B: Modify item2 and reorder (item2 first)
        val modifiedItem2 = item2.copy(intValue = 250)
        val deviceB = initial.copy(nestedListWithIdValue = listOf(modifiedItem2, item1))

        val resultB = adapter.applyLocalWrite(
            currentValue = result1.value,
            currentNode = result1.node,
            currentActors = null,
            newValue = deviceB,
            timestamp = 300,
        )

        // Resolve conflict - higher version (v3) wins list size, element versions merge
        val conflict = adapter.resolveConflict(lhs = resultA, rhs = resultB).mergeResult

        assertThat(conflict.resolution).isEqualTo(MERGED_VALUES)
        // List size from v3 (device B) = 2 items, but element content merges
        assertThat(conflict.value?.nestedListWithIdValue).hasSize(3)
        assertThat(conflict.value?.nestedListWithIdValue).isEqualTo(listOf(modifiedItem2, modifiedItem1, item3))
    }

    @Test
    fun `optional fields - conflict resolution`() {
        val initial = TestMessage(
            stringValue = "base",
            primitiveOptionalValue = null,
            nestedOptionalValue = null
        )

        val result1 = adapter.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = initial,
            timestamp = 100,
        ).mergeResult

        // Device A: Set primitive optional value
        val deviceA = initial.copy(primitiveOptionalValue = 100)
        val resultA = adapter.applyLocalWrite(
            currentValue = result1.value,
            currentNode = result1.node,
            currentActors = null,
            newValue = deviceA,
            timestamp = 200,
        )

        // Device B: Set nested optional value
        val deviceB = initial.copy(
            nestedOptionalValue = NestedMessage("device_b", 200)
        )
        val resultB = adapter.applyLocalWrite(
            currentValue = result1.value,
            currentNode = result1.node,
            currentActors = null,
            newValue = deviceB,
            timestamp = 300,
        )

        // Resolve conflict - both optional values should be present
        val conflict = adapter.resolveConflict(lhs = resultA, rhs = resultB).mergeResult

        assertThat(conflict.resolution).isEqualTo(MERGED_VALUES)
        assertThat(conflict.value?.primitiveOptionalValue).isEqualTo(100)
        assertThat(conflict.value?.nestedOptionalValue).isEqualTo(NestedMessage("device_b", 200))
    }

    @Test
    fun `unset field does not overwrite populated field when parent version is higher`() {
        // Device A: created earlier, sets stringValue (field 13).
        val deviceA = adapter.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = TestMessage(stringValue = "hello"),
            timestamp = 1,
        )

        // Device B: created later (higher parent version), only sets int32Value (field 3).
        // stringValue is never set on B, so it inherits B's higher parent version.
        val deviceB = adapter.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = TestMessage(int32Value = 42),
            timestamp = 5,
        )

        val merged = adapter.resolveConflict(lhs = deviceA, rhs = deviceB).mergeResult

        // int32Value was genuinely set on B and should be present.
        assertThat(merged.value?.int32Value).isEqualTo(42)
        // stringValue was never set on B, so A's real write must survive.
        assertThat(merged.value?.stringValue).isEqualTo("hello")
    }

    @Test
    fun `unset field does not overwrite populated field regardless of merge order`() {
        val deviceA = adapter.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = TestMessage(stringValue = "hello"),
            timestamp = 1,
        )
        val deviceB = adapter.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = TestMessage(int32Value = 42),
            timestamp = 5,
        )

        // Swap local/incoming: B (higher parent version) as local, A as incoming.
        val merged = adapter.resolveConflict(lhs = deviceB, rhs = deviceA).mergeResult

        assertThat(merged.value?.int32Value).isEqualTo(42)
        assertThat(merged.value?.stringValue).isEqualTo("hello")
    }

    @Test
    fun `reset to default is a real write and survives the collapse optimization`() {
        // Base: two fields set so the parent stays low.
        val base = adapter.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = TestMessage(stringValue = "hello", int32Value = 1),
            timestamp = 100,
        )

        // Reset stringValue (field 13) back to default.
        val reset = adapter.applyLocalWrite(
            currentValue = base.mergeResult.value,
            currentNode = base.mergeResult.node,
            currentActors = base.actors,
            newValue = TestMessage(stringValue = "", int32Value = 1),
            timestamp = 300,
        )

        // The reset must leave an explicit node for field 13 (not collapsed away).
        val field13 = reset.mergeResult.node?.struct?.fields?.get(13)
        assertThat(field13).isNotNull()
        assertThat(field13?.version?.timestamp).isEqualTo(300)
    }

    @Test
    fun `reset wins over an older value on merge`() {
        val base = adapter.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = TestMessage(stringValue = "hello", int32Value = 1),
            timestamp = 100,
        )

        // Device A resets stringValue at a later time.
        val deviceA = adapter.applyLocalWrite(
            currentValue = base.mergeResult.value,
            currentNode = base.mergeResult.node,
            currentActors = base.actors,
            newValue = TestMessage(stringValue = "", int32Value = 1),
            timestamp = 300,
        )

        // Device B keeps the original "hello" (base, untouched).
        // Merge: A's reset (newer write) should win -> stringValue cleared.
        val merged = adapter.resolveConflict(lhs = base, rhs = deviceA).mergeResult
        assertThat(merged.value?.stringValue).isEqualTo("")

        // And order-independent.
        val mergedSwapped = adapter.resolveConflict(lhs = deviceA, rhs = base).mergeResult
        assertThat(mergedSwapped.value?.stringValue).isEqualTo("")
    }

    @Test
    fun `merged parent version is the floor of the two sides`() {
        val a = adapter.applyLocalWrite(
            currentValue = null, currentNode = null, currentActors = null,
            newValue = TestMessage(stringValue = "a"), timestamp = 10,
        )
        val b = adapter.applyLocalWrite(
            currentValue = null, currentNode = null, currentActors = null,
            newValue = TestMessage(int32Value = 1), timestamp = 5,
        )

        val merged = adapter.resolveConflict(lhs = a, rhs = b).mergeResult

        // Both real writes survive.
        assertThat(merged.value?.stringValue).isEqualTo("a")
        assertThat(merged.value?.int32Value).isEqualTo(1)
        // Parent baseline is the floor (5), not the max (10).
        assertThat(merged.node?.version?.timestamp).isEqualTo(5)
        // The field sitting at the floor collapses into the parent (no explicit node)...
        assertThat(merged.node?.struct?.fields?.get(3)).isNull()
        // ...while the higher field keeps its own leaf node at its real version.
        assertThat(merged.node?.struct?.fields?.get(13)?.version?.timestamp).isEqualTo(10)
    }

    @Test
    fun `clean local win preserves the winning version and does not apply the floor`() {
        // Older shared base so a real merge is possible, then local advances both fields.
        val base = adapter.applyLocalWrite(
            currentValue = null, currentNode = null, currentActors = null,
            newValue = TestMessage(stringValue = "old", int32Value = 1), timestamp = 5,
        )
        val local = adapter.applyLocalWrite(
            currentValue = base.mergeResult.value,
            currentNode = base.mergeResult.node,
            currentActors = base.actors,
            newValue = TestMessage(stringValue = "new", int32Value = 1), timestamp = 20,
        )

        // Incoming is the untouched base (strictly older on the only differing field).
        val merged = adapter.resolveConflict(lhs = local, rhs = base).mergeResult

        // Local strictly wins, so its node is reused as-is (version 20 preserved).
        assertThat(merged.value?.stringValue).isEqualTo("new")
        assertThat(merged.node).isEqualTo(local.mergeResult.node)
    }

    @Test
    fun `nested message merge applies the floor independently at each level`() {
        val a = adapter.applyLocalWrite(
            currentValue = null, currentNode = null, currentActors = null,
            newValue = TestMessage(nestedValue = NestedMessage(stringValue = "n")),
            timestamp = 10,
        )
        val b = adapter.applyLocalWrite(
            currentValue = null, currentNode = null, currentActors = null,
            newValue = TestMessage(nestedValue = NestedMessage(intValue = 7)),
            timestamp = 4,
        )

        val merged = adapter.resolveConflict(lhs = a, rhs = b).mergeResult

        // Both nested writes survive the merge.
        assertThat(merged.value?.nestedValue?.stringValue).isEqualTo("n")
        assertThat(merged.value?.nestedValue?.intValue).isEqualTo(7)
        // Top-level parent floor is the lower timestamp.
        assertThat(merged.node?.version?.timestamp).isEqualTo(4)
        // The nested message's own parent floor is also the lower timestamp,
        // not inflated by the sibling's higher version.
        val nestedNode = merged.node?.struct?.fields?.get(16)
        assertThat(nestedNode?.version?.timestamp).isEqualTo(4)
    }

    @Test
    fun `reset leaf at the parent floor is protected from collapse`() {
        val base = adapter.applyLocalWrite(
            currentValue = null, currentNode = null, currentActors = null,
            newValue = TestMessage(stringValue = "hello", int32Value = 1), timestamp = 100,
        )
        // Device A clears stringValue (real reset write) at an older time than B's edit.
        val deviceA = adapter.applyLocalWrite(
            currentValue = base.mergeResult.value,
            currentNode = base.mergeResult.node,
            currentActors = base.actors,
            newValue = TestMessage(stringValue = "", int32Value = 1), timestamp = 150,
        )
        // Device B advances int32Value to a higher version, branching from the same base.
        val deviceB = adapter.applyLocalWrite(
            currentValue = base.mergeResult.value,
            currentNode = base.mergeResult.node,
            currentActors = base.actors,
            newValue = TestMessage(stringValue = "hello", int32Value = 2), timestamp = 200,
        )

        val merged = adapter.resolveConflict(lhs = deviceA, rhs = deviceB).mergeResult

        // The reset wins over the stale "hello" -> stringValue stays cleared.
        assertThat(merged.value?.stringValue).isEqualTo("")
        // And the reset keeps an explicit node (not collapsed), preserving its version.
        val resetNode = merged.node?.struct?.fields?.get(13)
        assertThat(resetNode).isNotNull()
        assertThat(resetNode?.version?.timestamp).isEqualTo(150)
    }

    @Test
    fun `equal timestamps converge via deterministic actor tie-break regardless of order`() {
        val a = adapter.applyLocalWrite(
            currentValue = null, currentNode = null, currentActors = null,
            newValue = TestMessage(stringValue = "from-a"), timestamp = 50,
        )
        val b = adapter.applyLocalWrite(
            currentValue = null, currentNode = null, currentActors = null,
            newValue = TestMessage(stringValue = "from-b"), timestamp = 50,
        )

        val ab = adapter.resolveConflict(lhs = a, rhs = b).mergeResult
        val ba = adapter.resolveConflict(lhs = b, rhs = a).mergeResult

        // Same winner and same version tree no matter the merge order.
        assertThat(ab.value).isEqualTo(ba.value)
        assertThat(ab.node).isEqualTo(ba.node)
    }

    @Test
    fun `merge converges regardless of order when a field is equal-valued at different inherited versions`() {
        // Three independent origins. stringValue="same" on all (equal value), but each
        // device's parent baseline is a different version, so the inherited stringValue
        // sits at a different version on each side. Each device also sets one other field.
        val a = adapter.applyLocalWrite(
            currentValue = null, currentNode = null, currentActors = null,
            newValue = TestMessage(stringValue = "same", int32Value = 1),
            timestamp = 10,
        )
        val b = adapter.applyLocalWrite(
            currentValue = null, currentNode = null, currentActors = null,
            newValue = TestMessage(stringValue = "same", int64Value = 2),
            timestamp = 5,
        )
        val c = adapter.applyLocalWrite(
            currentValue = null, currentNode = null, currentActors = null,
            newValue = TestMessage(stringValue = "same", boolValue = true),
            timestamp = 7,
        )

        // (A . B) . C
        val abThenC = adapter.resolveConflict(
            lhs = adapter.resolveConflict(lhs = a, rhs = b),
            rhs = c,
        ).mergeResult

        // A . (B . C)
        val aThenBC = adapter.resolveConflict(
            lhs = a,
            rhs = adapter.resolveConflict(lhs = b, rhs = c),
        ).mergeResult

        // Values converge.
        assertThat(abThenC.value).isEqualTo(aThenBC.value)
        // And so do the version trees - this is what protects future merges.
        assertThat(abThenC.node).isEqualTo(aThenBC.node)
    }
}

fun <T, S1, S2> WireCrdtMessageResolver<T>.resolveConflict(
    lhs: ResolverDeltaResult<T, VersionNode, Version, S1, PathComponent, Actors>,
    rhs: ResolverDeltaResult<T, VersionNode, Version, S2, PathComponent, Actors>,
): ResolverDeltaResult<T, VersionNode, Version, ResolutionStrategy, PathComponent, Actors> {
    val incomingNode = rhs.mergeResult.node
    assertNotNull(incomingNode)
    return resolveConflict(
        localValue = lhs.mergeResult.value,
        localNode = lhs.mergeResult.node,
        localActors = lhs.actors,
        incomingValue = rhs.mergeResult.value,
        incomingNode = incomingNode,
        incomingVersionVector = rhs.actors.version_vector,
    )
}
