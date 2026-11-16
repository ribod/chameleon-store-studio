package dev.ribod.storestudio.runtime

import dev.ribod.storestudio.model.FrameTypeName
import dev.ribod.storestudio.runtime.model.FrameType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Validates cross-module specification parity between `:store-studio-runtime` and `:store-studio-core`.
 *
 * Ensures all [FrameType] enum entries match [FrameTypeName] in name and count so that headless
 * rendering engines can safely map manifest entries to runtime vector frames without deserialization
 * errors or version drift.
 */
class FrameTypePinningTest {

    @Test
    fun frameTypeEntriesMatchCoreCatalogFrameTypeNames() {
        val runtimeEntries = FrameType.entries.map { it.name }
        val coreEntries = FrameTypeName.entries.map { it.name }

        assertEquals(
            expected = coreEntries,
            actual = runtimeEntries,
            message = "Runtime FrameType entries must match Core FrameTypeName entries.",
        )
    }
}
