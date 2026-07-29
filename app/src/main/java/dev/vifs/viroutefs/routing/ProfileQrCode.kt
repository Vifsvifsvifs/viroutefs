// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.routing

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.Result
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.nio.ByteBuffer

internal const val MAX_QR_PROFILE_BYTES: Int = 64 * 1024

internal data class QrLumaFrame(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
)

internal object ProfileQrCode {
    private val hints = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(com.google.zxing.BarcodeFormat.QR_CODE),
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.CHARACTER_SET to Charsets.UTF_8.name(),
    )

    fun decodeArgb(width: Int, height: Int, pixels: IntArray): String? {
        require(width > 0 && height > 0) { "Размер кадра QR должен быть положительным." }
        require(pixels.size == width * height) { "Размер массива пикселей не совпадает с кадром QR." }
        val source = RGBLuminanceSource(width, height, pixels)
        return decode(BinaryBitmap(HybridBinarizer(source)))
    }

    fun decodeLuma(frame: QrLumaFrame): String? {
        require(frame.width > 0 && frame.height > 0) { "Размер кадра QR должен быть положительным." }
        require(frame.bytes.size == frame.width * frame.height) {
            "Размер яркостного кадра не совпадает с его шириной и высотой."
        }
        val source = PlanarYUVLuminanceSource(
            frame.bytes,
            frame.width,
            frame.height,
            0,
            0,
            frame.width,
            frame.height,
            false,
        )
        return decode(BinaryBitmap(HybridBinarizer(source)))
    }

    fun normalizePayload(value: String): String {
        val normalized = value.trim()
        require(normalized.isNotEmpty()) { "QR-код пуст." }
        require(normalized.toByteArray(Charsets.UTF_8).size <= MAX_QR_PROFILE_BYTES) {
            "Данные QR-кода слишком большие."
        }
        return normalized
    }

    private fun decode(bitmap: BinaryBitmap): String? {
        val result: Result = runCatching {
            QRCodeReader().decode(bitmap, hints)
        }.getOrNull() ?: return null
        return normalizePayload(result.text.orEmpty())
    }
}

internal fun packQrLumaPlane(
    buffer: ByteBuffer,
    width: Int,
    height: Int,
    rowStride: Int,
    pixelStride: Int,
    rotationDegrees: Int,
): QrLumaFrame {
    require(width > 0 && height > 0) { "Размер кадра камеры должен быть положительным." }
    require(rowStride > 0 && pixelStride > 0) { "Камера вернула неверный шаг пикселей." }
    val requiredBytes = (height - 1L) * rowStride + (width - 1L) * pixelStride + 1L
    val base = buffer.position()
    require(requiredBytes <= buffer.limit() - base.toLong()) {
        "Камера вернула неполный кадр."
    }

    val packed = ByteArray(width * height)
    for (y in 0 until height) {
        val rowOffset = base + y * rowStride
        for (x in 0 until width) {
            packed[y * width + x] = buffer.get(rowOffset + x * pixelStride)
        }
    }
    return rotateQrLuma(QrLumaFrame(packed, width, height), rotationDegrees)
}

internal fun rotateQrLuma(frame: QrLumaFrame, rotationDegrees: Int): QrLumaFrame {
    val normalizedRotation = ((rotationDegrees % 360) + 360) % 360
    require(normalizedRotation in setOf(0, 90, 180, 270)) {
        "Камера вернула неподдерживаемый поворот кадра: $rotationDegrees°."
    }
    if (normalizedRotation == 0) return frame

    val destinationWidth = if (normalizedRotation == 90 || normalizedRotation == 270) {
        frame.height
    } else {
        frame.width
    }
    val destinationHeight = if (normalizedRotation == 90 || normalizedRotation == 270) {
        frame.width
    } else {
        frame.height
    }
    val rotated = ByteArray(frame.bytes.size)
    for (y in 0 until frame.height) {
        for (x in 0 until frame.width) {
            val sourceIndex = y * frame.width + x
            val (destinationX, destinationY) = when (normalizedRotation) {
                90 -> frame.height - 1 - y to x
                180 -> frame.width - 1 - x to frame.height - 1 - y
                else -> y to frame.width - 1 - x
            }
            rotated[destinationY * destinationWidth + destinationX] = frame.bytes[sourceIndex]
        }
    }
    return QrLumaFrame(rotated, destinationWidth, destinationHeight)
}
