package co.atoms.lithium.crdt.resolver.incoming

import co.atoms.lithium.crdt.resolver.ResolutionDeltaContext
import co.atoms.lithium.crdt.resolver.SingleValueResolver
import co.atoms.lithium.crdt.resolver.TestMessage
import co.atoms.lithium.crdt.resolver.TestVersionTreeResolver
import co.atoms.lithium.crdt.resolver.Version
import co.atoms.lithium.crdt.resolver.VersionNode
import co.atoms.lithium.crdt.resolver.descriptor.CollectionType
import co.atoms.lithium.crdt.resolver.descriptor.MessageBuilder
import co.atoms.lithium.crdt.resolver.descriptor.MessageFieldDescriptor
import co.atoms.lithium.crdt.resolver.descriptor.MessageFieldMergeStrategy
import co.atoms.lithium.crdt.resolver.descriptor.MessageFieldResolver
import co.atoms.lithium.crdt.resolver.descriptor.ValueType
import co.atoms.lithium.crdt.resolver.version.ResolutionStrategy
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers the branches of [MessageIncomingResolver.processFieldByField] that are
 * unreachable through the Wire and protoc adapters. Those adapters always attach
 * concrete, non-zero-actor version nodes via `applyLocalWrite`, so a few branches
 * (a null parent node reaching the merge, an empty/null-version `resultFields`
 * under MERGED_VALUES) can only be exercised by driving the resolver directly with
 * hand-built version trees.
 *
 * The realistic, end-to-end behaviour is covered by `WireCrdtIncomingResolverTest`
 * and `ProtoCrdtIncomingResolverTest`; this fills the remaining gaps so
 * `processFieldByField` has complete branch coverage.
 *
 * The fake message is [TestMessage] with two scalar fields: stringValue (tag 1) and
 * int32Value (tag 2). `TestVersionTreeResolver.fields` maps to `int_map`, so field
 * version nodes live there keyed by tag.
 */
class MessageIncomingResolverTest {

    private val resolver = TestMessageIncomingResolver()

    private fun node(version: Version, fields: Map<Int, VersionNode> = emptyMap()) =
        VersionNode(version = version, int_map = fields)

    @Test
    fun `field never set on both sides is skipped and produces no change`() {
        val localValue = TestMessage(stringValue = "", int32Value = 0)
        val localNode = node(Version(actorId = 1, actorVersion = 1, timestamp = 1))
        val incomingValue = TestMessage(stringValue = "", int32Value = 0)
        // Different node so the top-level NO_CHANGE fast path is skipped and we reach
        // processFieldByField, where every field hits `localNeverSet && incomingNeverSet`.
        val incomingNode = node(Version(actorId = 2, actorVersion = 1, timestamp = 5))

        val result = resolver.resolveConflict(
            localValue = localValue,
            localNode = localNode,
            localVersion = Version(actorId = 1, actorVersion = 1, timestamp = 1),
            incomingValue = incomingValue,
            incomingNode = incomingNode,
            incomingVersion = Version(actorId = 2, actorVersion = 1, timestamp = 5),
            context = ResolutionDeltaContext(),
        )

        assertEquals(ResolutionStrategy.NO_CHANGE, result.resolution)
        assertEquals(TestMessage(stringValue = "", int32Value = 0), result.value)
        // No field produced a node, so NO_CHANGE returns the local node unchanged.
        assertEquals(localNode, result.node)
    }

    @Test
    fun `local win with a null local node synthesizes a node at the local version`() {
        val result = resolver.resolveConflict(
            localValue = TestMessage(stringValue = "new", int32Value = 0),
            localNode = null,
            localVersion = Version(actorId = 1, actorVersion = 1, timestamp = 20),
            incomingValue = TestMessage(stringValue = "", int32Value = 0),
            incomingNode = node(Version(actorId = 2, actorVersion = 1, timestamp = 5)),
            incomingVersion = Version(actorId = 2, actorVersion = 1, timestamp = 5),
            context = ResolutionDeltaContext(),
        )

        assertEquals(ResolutionStrategy.LOCAL, result.resolution)
        assertEquals(TestMessage(stringValue = "new", int32Value = 0), result.value)
        // LOCAL branch returns localNode ?: createVersionNode(localVersion).
        assertEquals(20, result.node?.version?.timestamp)
    }

    @Test
    fun `incoming win with a null incoming node synthesizes a node at the incoming version`() {
        val result = resolver.resolveConflict(
            localValue = TestMessage(stringValue = "", int32Value = 0),
            localNode = node(Version(actorId = 1, actorVersion = 1, timestamp = 5)),
            localVersion = Version(actorId = 1, actorVersion = 1, timestamp = 5),
            incomingValue = TestMessage(stringValue = "new", int32Value = 0),
            incomingNode = null,
            incomingVersion = Version(actorId = 2, actorVersion = 1, timestamp = 20),
            context = ResolutionDeltaContext(),
        )

        assertEquals(ResolutionStrategy.INCOMING, result.resolution)
        assertEquals(TestMessage(stringValue = "new", int32Value = 0), result.value)
        // INCOMING branch returns incomingNode ?: createVersionNode(incomingVersion).
        assertEquals(20, result.node?.version?.timestamp)
    }

    @Test
    fun `merged parent version falls back to the min seed when a field has no concrete version`() {
        // field1 (stringValue) carries a placeholder node with no concrete version (actorId 0),
        // so its versionValue is null and it contributes the seed - min(localVersion,
        // incomingVersion) - to the parent floor instead of a field version. field2 wins
        // incoming at a higher version, making the overall resolution MERGED_VALUES.
        val localValue = TestMessage(stringValue = "a", int32Value = 1)
        val localNode = node(
            Version(actorId = 1, actorVersion = 1, timestamp = 100),
            mapOf(
                1 to node(Version(actorId = 0, actorVersion = 0, timestamp = 0)),
                2 to node(Version(actorId = 1, actorVersion = 1, timestamp = 60)),
            ),
        )
        val incomingValue = TestMessage(stringValue = "b", int32Value = 2)
        val incomingNode = node(
            Version(actorId = 2, actorVersion = 1, timestamp = 50),
            mapOf(2 to node(Version(actorId = 2, actorVersion = 1, timestamp = 200))),
        )

        val result = resolver.resolveConflict(
            localValue = localValue,
            localNode = localNode,
            localVersion = Version(actorId = 1, actorVersion = 1, timestamp = 100),
            incomingValue = incomingValue,
            incomingNode = incomingNode,
            incomingVersion = Version(actorId = 2, actorVersion = 1, timestamp = 50),
            context = ResolutionDeltaContext(),
        )

        assertEquals(ResolutionStrategy.MERGED_VALUES, result.resolution)
        assertEquals(TestMessage(stringValue = "a", int32Value = 2), result.value)
        // Parent floor comes from the seed: min(localVersion=100, incomingVersion=50) = 50,
        // NOT the max (100). This is the only branch that exercises that ternary.
        assertEquals(50, result.node?.version?.timestamp)
    }
}

/**
 * Minimal in-module [MessageIncomingResolver] over [TestMessage] (stringValue=1, int32Value=2).
 * One [SingleValueResolver] backs every field-resolver role since it implements all of them.
 */
private class TestMessageIncomingResolver :
    MessageIncomingResolver<TestMessage, TestMessageBuilder, TestMessageBuilder, VersionNode, Version, String> {

    override val versionTreeResolver = TestVersionTreeResolver
    override val encoder: (TestMessage) -> ByteArray = { "${it.stringValue}|${it.int32Value}".toByteArray() }

    private val fieldResolver = SingleValueResolver<Any, VersionNode, Version, String>(
        decoder = { it.decodeToString() },
        encoder = { it.toString().toByteArray() },
        versionTreeResolver = TestVersionTreeResolver,
    )

    override fun newBuilder(): TestMessageBuilder = TestMessageBuilder()

    override val fields: Map<Int, MessageFieldResolver<TestMessage, TestMessageBuilder, Any, VersionNode, Version, String>> =
        listOf(
            ScalarFieldDescriptor(
                tag = 1,
                default = "",
                getter = { it.stringValue },
                setter = { builder, value -> builder.stringValue = value as? String ?: "" },
            ),
            ScalarFieldDescriptor(
                tag = 2,
                default = 0,
                getter = { it.int32Value },
                setter = { builder, value -> builder.int32Value = value as? Int ?: 0 },
            ),
        ).associate { binding ->
            binding.tag to MessageFieldResolver(
                binding = binding,
                localResolver = fieldResolver,
                incomingResolver = fieldResolver,
                deltaResolver = fieldResolver,
                changeDecoder = fieldResolver,
            )
        }
}

private class TestMessageBuilder : MessageBuilder<TestMessage, TestMessageBuilder> {
    var stringValue: String = ""
    var int32Value: Int = 0
    override val setter: TestMessageBuilder get() = this
    override fun build(): TestMessage = TestMessage(stringValue = stringValue, int32Value = int32Value)
}

private class ScalarFieldDescriptor(
    override val tag: Int,
    private val default: Any,
    private val getter: (TestMessage) -> Any?,
    private val setter: (TestMessageBuilder, Any?) -> Unit,
) : MessageFieldDescriptor<TestMessage, TestMessageBuilder, Any> {
    override val collectionType: CollectionType? = null
    override val oneOfName: String? = null
    override val valueType: ValueType = ValueType.REQUIRED
    override val mergeStrategy: MessageFieldMergeStrategy = MessageFieldMergeStrategy.MERGE
    override val valueTypeId: Any = tag
    override val decoder: (ByteArray) -> Any = { it.decodeToString() }
    override val encoder: (Any) -> ByteArray = { it.toString().toByteArray() }
    override val valueDecoder: (ByteArray) -> Any = { it.decodeToString() }
    override val valueEncoder: (Any) -> ByteArray = { it.toString().toByteArray() }

    override fun get(message: TestMessage): Any? = getter(message)
    override fun set(builder: TestMessageBuilder, value: Any?) = setter(builder, value)
    override fun isAbsent(value: Any?): Boolean = value == null || value == default
}
