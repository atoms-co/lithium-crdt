package com.css.internal.shared.storage.crdt.wire

import com.css.android.internal.shared.storage.crdt.test.TestMessage
import com.css.internal.shared.storage.crdt.data.options.CrdtIdFieldOption
import com.css.internal.shared.storage.crdt.wire.internal.annotationsFor
import com.css.internal.shared.storage.crdt.wire.internal.firstNotNullOfOrNull
import com.css.internal.shared.storage.crdt.wire.internal.methodsByName
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
