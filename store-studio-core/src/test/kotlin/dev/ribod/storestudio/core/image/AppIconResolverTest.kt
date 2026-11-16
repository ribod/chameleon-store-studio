package dev.ribod.storestudio.core.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AppIconResolverTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun file(path: String, size: Int = 1): File = File(tmp.root, path).apply {
        parentFile.mkdirs()
        writeBytes(ByteArray(size))
    }

    private val roots get() = listOf(tmp.root)

    @Test
    fun nothingFoundResolvesToNull() {
        assertNull(AppIconResolver.findAndroid(roots))
        assertNull(AppIconResolver.findIos(roots))
    }

    @Test
    fun playStoreIconWinsOverLauncherIcons() {
        file("app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp")
        val store = file("app/src/main/ic_launcher-playstore.png")
        assertEquals(store, AppIconResolver.findAndroid(roots))
    }

    @Test
    fun highestDensityLauncherIconWinsAndPngBeatsWebp() {
        file("res/mipmap-hdpi/ic_launcher.png")
        file("res/mipmap-xxhdpi/ic_launcher.webp")
        val png = file("res/mipmap-xxhdpi/ic_launcher.png")
        assertEquals(png, AppIconResolver.findAndroid(roots))
    }

    @Test
    fun unconventionalNamesAreNotGuessed() {
        file("res/mipmap-xxhdpi/ic_launcher_round.png")
        file("store/icon.png")
        file("res/drawable/my_ic_launcher_custom.png")
        assertNull(AppIconResolver.findAndroid(roots))
    }

    @Test
    fun iosPicksTheLargestAppIconPng() {
        file("iosApp/Assets.xcassets/AppIcon.appiconset/small.png", 10)
        val big = file("iosApp/Assets.xcassets/AppIcon.appiconset/icon-1024.png", 100)
        assertEquals(big, AppIconResolver.findIos(roots))
    }

    @Test
    fun firstRootWithAMatchWins() {
        val module = file("module/res/mipmap-mdpi/ic_launcher.png")
        file("res/mipmap-xxxhdpi/ic_launcher.png")
        assertEquals(
            module,
            AppIconResolver.findAndroid(listOf(File(tmp.root, "module"), tmp.root)),
        )
    }
}
