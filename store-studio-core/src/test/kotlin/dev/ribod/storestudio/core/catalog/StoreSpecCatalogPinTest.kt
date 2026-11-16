package dev.ribod.storestudio.core.catalog

import dev.ribod.storestudio.model.SizeRule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class StoreSpecCatalogPinTest {

    @Test
    fun catalogMatchesPinnedSnapshot() {
        val table = generateMarkdownSnapshot()

        val snapshotFile = File("src/test/resources/catalog-snapshot.md")
        val updateSnapshot = java.lang.Boolean.getBoolean("updateCatalogSnapshot") ||
            System.getenv("UPDATE_CATALOG_SNAPSHOT") == "true"

        if (updateSnapshot || !snapshotFile.exists()) {
            snapshotFile.parentFile?.mkdirs()
            snapshotFile.writeText(table)
        }

        val expected = snapshotFile.readText()
        assertEquals(
            "Store spec catalog diverged from pinned snapshot in catalog-snapshot.md. " +
                "If this change is intentional, re-run with -DupdateCatalogSnapshot=true to update.",
            expected.trim(),
            table.trim(),
        )
    }

    private fun generateMarkdownSnapshot(): String {
        val sb = StringBuilder()
        sb.appendLine("# Store Studio Device and Store Spec Catalog Snapshot")
        sb.appendLine()
        sb.appendLine("Catalog Version: ${DefaultStoreSpecCatalog.catalogVersion}")
        sb.appendLine()
        sb.appendLine(
            "| Slot | Target | Store API ID | Verified | Families | Default Px | DPI | " +
                "Default Dp | Size Rule | Count | Formats | Alpha | Max Bytes | Frame | Optional |",
        )
        sb.appendLine(
            "|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|",
        )

        for (spec in DefaultStoreSpecCatalog.slots) {
            val sizeRuleStr = when (val rule = spec.sizeRule) {
                is SizeRule.Exact -> {
                    val acc = rule.accepted.joinToString { "${it.width}x${it.height}" }
                    "Exact($acc, transposed=${rule.allowTransposed})"
                }

                is SizeRule.Range -> {
                    "Range(min=${rule.minShortSidePx}, max=${rule.maxLongSidePx}, " +
                        "maxAspect=${rule.maxAspect}, square=${rule.requireSquare})"
                }
            }
            val familiesStr = if (spec.families.isEmpty()) {
                "-"
            } else {
                spec.families.sortedBy { it.name }.joinToString { it.name }
            }
            val formatsStr = spec.formats.sortedBy { it.name }.joinToString { it.name }
            val countStr = "${spec.count.min}..${spec.count.max}" +
                (spec.count.recommendedMin?.let { " (rec: $it)" } ?: "")
            val maxBytesStr = spec.maxBytes?.let { "${it / 1_000_000} MB" } ?: "unlimited"

            val cells = listOf(
                spec.slot.name,
                spec.slot.target.name,
                spec.storeApiId,
                spec.storeApiIdVerified.toString(),
                familiesStr,
                spec.defaultGeometry.size.toString(),
                spec.defaultGeometry.dpi.toString(),
                "${spec.defaultGeometry.widthDp}x${spec.defaultGeometry.heightDp}",
                sizeRuleStr,
                countStr,
                formatsStr,
                spec.alphaAllowed.toString(),
                maxBytesStr,
                spec.frame.name,
                spec.optional.toString(),
            )
            sb.appendLine("| ${cells.joinToString(" | ")} |")
        }

        return sb.toString()
    }
}
