package dev.ribod.storestudio.core.publish

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PublishPartTest {

    @Test
    fun parseCliPartsSupportsAllTokensAndAliases() {
        assertEquals(
            ParsedPublishSelection(emptySet(), includeBinary = true),
            PublishPart.parseCliParts("binary"),
        )
        assertEquals(
            ParsedPublishSelection(emptySet(), includeBinary = true),
            PublishPart.parseCliParts("aab,ipa"),
        )
        assertEquals(
            ParsedPublishSelection(setOf(PublishPart.LISTING), includeBinary = false),
            PublishPart.parseCliParts("texts"),
        )
        assertEquals(
            ParsedPublishSelection(setOf(PublishPart.LISTING), includeBinary = false),
            PublishPart.parseCliParts("text,listing"),
        )
        assertEquals(
            ParsedPublishSelection(setOf(PublishPart.IMAGES), includeBinary = false),
            PublishPart.parseCliParts("images"),
        )
        assertEquals(
            ParsedPublishSelection(setOf(PublishPart.IMAGES), includeBinary = false),
            PublishPart.parseCliParts("image,screenshots"),
        )
        assertEquals(
            ParsedPublishSelection(setOf(PublishPart.RELEASE_NOTES), includeBinary = false),
            PublishPart.parseCliParts("releaseNotes"),
        )
        assertEquals(
            ParsedPublishSelection(setOf(PublishPart.RELEASE_NOTES), includeBinary = false),
            PublishPart.parseCliParts("release_notes,release-notes,notes,whatsNew"),
        )
        assertEquals(
            ParsedPublishSelection(PublishPart.ALL, includeBinary = true),
            PublishPart.parseCliParts("all"),
        )
        assertEquals(
            ParsedPublishSelection(PublishPart.ALL, includeBinary = false),
            PublishPart.parseCliParts("listingAll"),
        )
        assertEquals(
            ParsedPublishSelection(PublishPart.ALL, includeBinary = false),
            PublishPart.parseCliParts("storefront"),
        )
        assertEquals(
            ParsedPublishSelection(PublishPart.ALL, includeBinary = true),
            PublishPart.parseCliParts("listingAll,binary"),
        )
        assertEquals(
            ParsedPublishSelection(
                setOf(PublishPart.LISTING, PublishPart.RELEASE_NOTES),
                includeBinary = true,
            ),
            PublishPart.parseCliParts(" binary , texts , release-notes "),
        )
    }

    @Test
    fun parseCliPartsRejectsEmptyOrUnknownTokens() {
        val emptyEx = assertThrows(IllegalArgumentException::class.java) {
            PublishPart.parseCliParts(" , ")
        }
        assertTrue(emptyEx.message!!.contains("Unknown publish part"))

        val unknownEx = assertThrows(IllegalArgumentException::class.java) {
            PublishPart.parseCliParts("texts,bogus")
        }
        assertTrue(unknownEx.message!!.contains("Unknown publish part 'bogus'"))

        // Google Play no longer accepts APKs, so it is not a binary alias
        val apkEx = assertThrows(IllegalArgumentException::class.java) {
            PublishPart.parseCliParts("apk")
        }
        assertTrue(apkEx.message!!.contains("Unknown publish part 'apk'"))
    }
}
