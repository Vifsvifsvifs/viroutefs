// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.routing

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

internal data class GeneratedSupportQrCode(
    val width: Int,
    val height: Int,
    val argbPixels: IntArray,
)

internal fun generateSupportQrCode(
    url: String,
    size: Int = DEFAULT_SUPPORT_QR_SIZE,
): GeneratedSupportQrCode {
    require(url.startsWith("https://", ignoreCase = true)) {
        "Support QR code requires an HTTPS URL."
    }
    require(size in 128..2048) { "Support QR code size is outside the safe range." }
    val matrix = QRCodeWriter().encode(
        url,
        BarcodeFormat.QR_CODE,
        size,
        size,
        mapOf(
            EncodeHintType.CHARACTER_SET to Charsets.UTF_8.name(),
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 4,
        ),
    )
    val pixels = IntArray(matrix.width * matrix.height) { index ->
        val x = index % matrix.width
        val y = index / matrix.width
        if (matrix[x, y]) QR_BLACK else QR_WHITE
    }
    return GeneratedSupportQrCode(matrix.width, matrix.height, pixels)
}

private const val DEFAULT_SUPPORT_QR_SIZE = 512
private const val QR_BLACK: Int = -0x1000000
private const val QR_WHITE: Int = -0x1
