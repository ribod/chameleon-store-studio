package dev.ribod.storestudio.gradle.internal

import org.junit.Assert.assertEquals
import org.junit.Test

class LayoutlibPlatformTest {

    @Test
    fun detectsMacArm() {
        assertEquals("mac-arm", LayoutlibPlatform.osClassifier("Mac OS X", "aarch64"))
        assertEquals("mac-arm", LayoutlibPlatform.osClassifier("macOS", "arm64"))
        assertEquals("mac-arm", LayoutlibPlatform.osClassifier("Darwin", "arm"))
    }

    @Test
    fun detectsMacIntel() {
        assertEquals("mac", LayoutlibPlatform.osClassifier("Mac OS X", "x86_64"))
        assertEquals("mac", LayoutlibPlatform.osClassifier("Darwin", "amd64"))
    }

    @Test
    fun detectsLinux() {
        assertEquals("linux", LayoutlibPlatform.osClassifier("Linux", "amd64"))
        assertEquals("linux", LayoutlibPlatform.osClassifier("Linux", "x86_64"))
        assertEquals("linux", LayoutlibPlatform.osClassifier("Unix", "amd64"))
    }

    @Test
    fun detectsWindows() {
        assertEquals("win", LayoutlibPlatform.osClassifier("Windows 11", "amd64"))
        assertEquals("win", LayoutlibPlatform.osClassifier("Windows 10", "x86_64"))
    }
}
