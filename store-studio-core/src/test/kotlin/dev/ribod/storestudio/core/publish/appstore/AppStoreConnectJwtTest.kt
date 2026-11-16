package dev.ribod.storestudio.core.publish.appstore

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class AppStoreConnectJwtTest {

    private fun der(r: ByteArray, s: ByteArray): ByteArray {
        val body = byteArrayOf(0x02, r.size.toByte()) + r + byteArrayOf(0x02, s.size.toByte()) + s
        return byteArrayOf(0x30, body.size.toByte()) + body
    }

    private fun filled(size: Int, value: Int) = ByteArray(size) { value.toByte() }

    @Test
    fun stripsLeadingZeroFrom33ByteCoordinates() {
        val r = byteArrayOf(0) + filled(32, 0x81)
        val s = byteArrayOf(0) + filled(32, 0x90)
        val jose = AppStoreConnectJwt.derToJose(der(r, s))
        assertEquals(64, jose.size)
        assertArrayEquals(filled(32, 0x81), jose.copyOfRange(0, 32))
        assertArrayEquals(filled(32, 0x90), jose.copyOfRange(32, 64))
    }

    @Test
    fun leftPadsShortCoordinates() {
        val jose = AppStoreConnectJwt.derToJose(der(filled(30, 0x11), filled(1, 0x22)))
        assertEquals(64, jose.size)
        assertArrayEquals(ByteArray(2) + filled(30, 0x11), jose.copyOfRange(0, 32))
        assertArrayEquals(ByteArray(31) + filled(1, 0x22), jose.copyOfRange(32, 64))
    }

    @Test
    fun mixedLengthsStayAt64Bytes() {
        val r = byteArrayOf(0) + filled(32, 0x81)
        val jose = AppStoreConnectJwt.derToJose(der(r, filled(31, 0x33)))
        assertEquals(64, jose.size)
        assertArrayEquals(ByteArray(1) + filled(31, 0x33), jose.copyOfRange(32, 64))
    }
}
