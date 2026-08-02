// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.ui

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.vifs.viroutefs.CardBlock
import dev.vifs.viroutefs.Header
import dev.vifs.viroutefs.ScreenList
import dev.vifs.viroutefs.WarningText
import dev.vifs.viroutefs.routing.ProfileQrCode
import dev.vifs.viroutefs.routing.packQrLumaPlane
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

@Composable
internal fun ProfileQrScannerScreen(
    padding: PaddingValues,
    onBack: () -> Unit,
    onDecoded: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val analysisExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val currentOnDecoded by rememberUpdatedState(onDecoded)
    var cameraError by remember { mutableStateOf<String?>(null) }
    var retryToken by remember { mutableIntStateOf(0) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    DisposableEffect(context, lifecycleOwner, previewView, retryToken) {
        val view = previewView
        if (view == null) {
            onDispose { }
        } else {
            val disposed = AtomicBoolean(false)
            var provider: ProcessCameraProvider? = null
            val analyzer = ProfileQrAnalyzer(
                onDecoded = { value ->
                    mainExecutor.execute {
                        if (!disposed.get()) currentOnDecoded(value)
                    }
                },
                onFailure = { message ->
                    mainExecutor.execute {
                        if (!disposed.get()) cameraError = message
                    }
                },
            )
            val providerFuture = ProcessCameraProvider.getInstance(context)
            providerFuture.addListener(
                {
                    if (disposed.get()) return@addListener
                    runCatching {
                        provider = providerFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = view.surfaceProvider
                        }
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { it.setAnalyzer(analysisExecutor, analyzer) }
                        provider?.unbindAll()
                        provider?.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis,
                        )
                    }.onFailure { throwable ->
                        cameraError = cameraFailureMessage(context, throwable)
                    }
                },
                mainExecutor,
            )
            onDispose {
                disposed.set(true)
                analyzer.close()
                provider?.unbindAll()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdownNow() }
    }

    ScreenList(padding) {
        item {
            Header(
                "Сканирование QR-кода",
                "Наведите камеру на QR-код профиля. Снимки не сохраняются и никуда не отправляются.",
            )
        }
        item {
            CardBlock {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f),
                    contentAlignment = Alignment.Center,
                ) {
                    AndroidView(
                        factory = { viewContext ->
                            PreviewView(viewContext).also { view ->
                                view.scaleType = PreviewView.ScaleType.FILL_CENTER
                                view.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                previewView = view
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        modifier = Modifier
                            .size(238.dp)
                            .border(
                                width = 3.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(24.dp),
                            ),
                    )
                }
                Text(
                    "Распознаётся только QR-код. Каждый кадр закрывается сразу после локальной обработки.",
                    style = MaterialTheme.typography.bodySmall,
                )
                cameraError?.let { message ->
                    WarningText(message)
                    Button(
                        onClick = {
                            cameraError = null
                            retryToken += 1
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Повторить запуск камеры")
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Назад к ручному импорту")
            }
        }
    }
}

private class ProfileQrAnalyzer(
    private val onDecoded: (String) -> Unit,
    private val onFailure: (String) -> Unit,
) : ImageAnalysis.Analyzer {
    private val active = AtomicBoolean(true)
    private val failureDelivered = AtomicBoolean(false)
    private val lastAnalyzedAtNanos = AtomicLong(0L)

    override fun analyze(image: ImageProxy) {
        try {
            if (!active.get()) return
            val now = System.nanoTime()
            val previous = lastAnalyzedAtNanos.get()
            if (previous != 0L && now - previous < MIN_ANALYSIS_INTERVAL_NANOS) return
            lastAnalyzedAtNanos.set(now)

            val plane = image.planes.firstOrNull() ?: return
            val frame = packQrLumaPlane(
                buffer = plane.buffer,
                width = image.width,
                height = image.height,
                rowStride = plane.rowStride,
                pixelStride = plane.pixelStride,
                rotationDegrees = image.imageInfo.rotationDegrees,
            )
            val payload = ProfileQrCode.decodeLuma(frame) ?: return
            if (active.compareAndSet(true, false)) onDecoded(payload)
        } catch (throwable: Throwable) {
            if (failureDelivered.compareAndSet(false, true)) {
                onFailure(
                    throwable.message
                        ?: "Не удалось обработать кадр камеры. Попробуйте ещё раз.",
                )
            }
        } finally {
            image.close()
        }
    }

    fun close() {
        active.set(false)
    }

    private companion object {
        const val MIN_ANALYSIS_INTERVAL_NANOS = 120_000_000L
    }
}

private fun cameraFailureMessage(context: Context, throwable: Throwable): String {
    val hasCamera = context.packageManager.hasSystemFeature("android.hardware.camera.any")
    return when {
        !hasCamera -> "На устройстве не найдена камера. Используйте буфер обмена или файл."
        throwable is SecurityException -> "Нет разрешения на камеру. Разрешите доступ в настройках Android."
        else -> throwable.message
            ?.takeIf(String::isNotBlank)
            ?.let { "Не удалось запустить камеру: $it" }
            ?: "Не удалось запустить камеру. Закройте другие приложения с камерой и повторите."
    }
}
