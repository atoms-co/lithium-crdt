package co.atoms.lithium.crdt.protoc

import co.atoms.lithium.crdt.test.TestMessage
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Tests for ProtoCrdtResolverProvider - provider-level functionality.
 *
 * Tests for the three resolver interfaces have been split into separate test classes:
 * - ProtoCrdtLocalResolverTest: Tests for applyLocalWrite operations (CrdtMessageLocalResolver)
 * - ProtoCrdtIncomingResolverTest: Tests for resolveConflict operations (CrdtMessageIncomingResolver)
 * - ProtoCrdtDeltaResolverTest: Tests for delta/changes tracking (CrdtMessageDeltaResolver)
 */
class ProtoCrdtResolverProviderTest {
    private val provider = CrdtMessageResolverProvider()

    @Test
    fun `getOrCreateResolverFor returns resolver for message type`() {
        val resolver = provider.getOrCreateResolverFor(TestMessage.getDefaultInstance())
        assertThat(resolver).isNotNull()
    }
}
