// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.routing

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ProfileQrCodeTest {
    @Test
    fun generatedProfileQrIsDecodedLocally() {
        val expected = "vless://11111111-2222-3333-4444-555555555555@example.com:443?security=tls#Test"
        val matrix = QRCodeWriter().encode(expected, BarcodeFormat.QR_CODE, 320, 320)
        val pixels = IntArray(matrix.width * matrix.height) { index ->
            val x = index % matrix.width
            val y = index / matrix.width
            if (matrix[x, y]) 0xff000000.toInt() else 0xffffffff.toInt()
        }
        val luma = QrLumaFrame(
            bytes = ByteArray(matrix.width * matrix.height) { index ->
                val x = index % matrix.width
                val y = index / matrix.width
                if (matrix[x, y]) 0 else 0xff.toByte()
            },
            width = matrix.width,
            height = matrix.height,
        )

        assertEquals(expected, ProfileQrCode.decodeArgb(matrix.width, matrix.height, pixels))
        assertEquals(expected, ProfileQrCode.decodeLuma(luma))
        assertEquals(expected, ProfileQrCode.decodeLuma(rotateQrLuma(luma, 90)))
    }

    @Test
    fun nonQrImageIsIgnoredWithoutAnError() {
        assertNull(ProfileQrCode.decodeArgb(64, 64, IntArray(64 * 64) { 0xffffffff.toInt() }))
    }

    @Test
    fun cameraPaddingAndPixelStrideAreRemovedBeforeDecode() {
        val source = byteArrayOf(
            1, 99, 2, 99, 3, 99, 88, 88,
            4, 99, 5, 99, 6, 99, 88, 88,
        )

        val frame = packQrLumaPlane(
            buffer = ByteBuffer.wrap(source),
            width = 3,
            height = 2,
            rowStride = 8,
            pixelStride = 2,
            rotationDegrees = 0,
        )

        assertEquals(3, frame.width)
        assertEquals(2, frame.height)
        assertContentEquals(byteArrayOf(1, 2, 3, 4, 5, 6), frame.bytes)
    }

    @Test
    fun allCameraRotationsProduceExpectedUprightFrame() {
        val source = QrLumaFrame(
            bytes = byteArrayOf(1, 2, 3, 4, 5, 6),
            width = 3,
            height = 2,
        )

        assertContentEquals(
            byteArrayOf(4, 1, 5, 2, 6, 3),
            rotateQrLuma(source, 90).bytes,
        )
        assertContentEquals(
            byteArrayOf(6, 5, 4, 3, 2, 1),
            rotateQrLuma(source, 180).bytes,
        )
        assertContentEquals(
            byteArrayOf(3, 6, 2, 5, 1, 4),
            rotateQrLuma(source, 270).bytes,
        )
    }

    @Test
    fun invalidFrameAndOversizedPayloadFailClosed() {
        assertFailsWith<IllegalArgumentException> {
            packQrLumaPlane(
                buffer = ByteBuffer.wrap(byteArrayOf(1, 2, 3)),
                width = 3,
                height = 3,
                rowStride = 3,
                pixelStride = 1,
                rotationDegrees = 0,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ProfileQrCode.normalizePayload("x".repeat(MAX_QR_PROFILE_BYTES + 1))
        }
    }
}
