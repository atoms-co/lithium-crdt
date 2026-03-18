package co.atoms.lithium.crdt.wire

import co.atoms.lithium.crdt.test.TestMessage
import co.atoms.lithium.crdt.data.options.CrdtIdFieldOption
import co.atoms.lithium.crdt.wire.internal.annotationsFor
import co.atoms.lithium.crdt.wire.internal.firstNotNullOfOrNull
import co.atoms.lithium.crdt.wire.internal.methodsByName
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test

/**
 * Tests for WireCrdtResolverProvider - provider-level functionality.
 *
 * Tests for the three resolver interfaces have been split into separate test classes:
 * - WireCrdtLocalResolverTest: Tests for applyLocalWrite operations (CrdtMessageLocalResolver)
 * - WireCrdtIncomingResolverTest: Tests for resolveConflict operations (CrdtMessageIncomingResolver)
 * - WireCrdtDeltaResolverTest: Tests for delta/changes tracking (CrdtMessageDeltaResolver)
 */
class WireCrdtResolverProviderTest {
    @Test
    fun propertyAnnotationTest() {
        val annotation: CrdtIdFieldOption? = TestMessage::class.java.methodsByName().annotationsFor(
            TestMessage::class.java.getDeclaredField("nestedListWithIdValue")
        ).firstNotNullOfOrNull()
        assertThat(annotation?.value).isEqualTo(45)
    }
}
