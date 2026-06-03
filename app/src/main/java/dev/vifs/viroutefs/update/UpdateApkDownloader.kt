// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max

internal const val APK_MIME_TYPE = "application/vnd.android.package-archive"

internal data class DownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long?,
) {
    val percent: Int? = totalBytes?.takeIf { it > 0 }?.let { ((bytesDownloaded * 100) / it).toInt().coerceIn(0, 100) }
}

internal sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState
    data object Checking : UpdateDownloadState
    data class UpdateAvailable(val release: ReleaseInfo) : UpdateDownloadState
    data class Downloading(val release: ReleaseInfo, val progress: DownloadProgress) : UpdateDownloadState
    data class DownloadFailed(val release: ReleaseInfo?, val message: String) : UpdateDownloadState
    data class ReadyToInstall(val release: ReleaseInfo, val file: File) : UpdateDownloadState
    data object UpToDate : UpdateDownloadState
    data object NoRelease : UpdateDownloadState
}

internal class UpdateApkDownloader(private val context: Context) {
    private val updatesDir: File get() = File(context.cacheDir, "updates")

    suspend fun download(release: ReleaseInfo, onProgress: suspend (DownloadProgress) -> Unit): UpdateDownloadState = withContext(Dispatchers.IO) {
        val asset = release.apkAsset ?: return@withContext UpdateDownloadState.DownloadFailed(release, "Release does not include an APK asset.")
        if (!asset.name.endsWith(".apk", ignoreCase = true)) {
            return@withContext UpdateDownloadState.DownloadFailed(release, "Selected asset is not an APK: ${asset.name}")
        }
        val url = URL(asset.downloadUrl)
        if (url.protocol != "https") {
            return@withContext UpdateDownloadState.DownloadFailed(release, "APK download URL must use HTTPS.")
        }

        updatesDir.mkdirs()
        val target = File(updatesDir, safeApkFileName(asset.name, release.displayVersion))
        val temp = File(updatesDir, "${target.name}.download")
        runCatching {
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/octet-stream")
                setRequestProperty("User-Agent", "ViRouteFS-manual-apk-download")
            }
            connection.useConnection {
                val status = connection.responseCode
                if (status !in 200..299) throw IllegalStateException("APK download returned HTTP $status")
                val total = asset.sizeBytes ?: connection.contentLengthLong.takeIf { it > 0 }
                var downloaded = 0L
                temp.outputStream().buffered().use { output ->
                    connection.inputStream.buffered().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            withContext(Dispatchers.Main) { onProgress(DownloadProgress(downloaded, total)) }
                        }
                    }
                }
                validateDownloadedApk(temp, asset, downloaded)
                if (target.exists()) target.delete()
                if (!temp.renameTo(target)) throw IllegalStateException("Could not move downloaded APK into cache.")
                UpdateDownloadState.ReadyToInstall(release, target)
            }
        }.getOrElse { error ->
            temp.delete()
            UpdateDownloadState.DownloadFailed(release, error.message ?: error::class.java.simpleName)
        }
    }

    fun delete(file: File): Boolean = file.exists() && file.delete()

    fun installIntent(file: File): Intent {
        validateInstallableFile(file)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun unknownAppSourcesIntent(): Intent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}"),
    )

    fun canRequestPackageInstalls(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()
}

internal fun validateDownloadedApk(file: File, asset: ReleaseAsset, downloadedBytes: Long) {
    validateInstallableFile(file)
    if (downloadedBytes <= 0L) throw IllegalStateException("Downloaded APK is empty.")
    val expectedSize = asset.sizeBytes
    if (expectedSize != null && expectedSize > 0 && downloadedBytes != expectedSize) {
        throw IllegalStateException("Downloaded APK size mismatch: $downloadedBytes of $expectedSize bytes.")
    }
}

internal fun validateInstallableFile(file: File) {
    if (!file.exists()) throw IllegalStateException("Downloaded APK file does not exist.")
    if (file.length() <= 0L) throw IllegalStateException("Downloaded APK file is empty.")
    if (!file.name.endsWith(".apk", ignoreCase = true)) throw IllegalStateException("Downloaded file is not an APK.")
}

internal fun safeApkFileName(assetName: String, version: String): String {
    val fallback = "ViRouteFS-$version.apk"
    val candidate = assetName.takeIf { it.endsWith(".apk", ignoreCase = true) } ?: fallback
    val safe = candidate.replace(Regex("[^A-Za-z0-9._-]"), "_")
    return safe.takeIf { it.endsWith(".apk", ignoreCase = true) } ?: fallback
}

private inline fun <T> HttpURLConnection.useConnection(block: () -> T): T = try {
    block()
} finally {
    disconnect()
}

internal fun formatBytes(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB")
    var value = max(bytes, 0L).toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }
    return if (unitIndex == 0) "${value.toLong()} ${units[unitIndex]}" else String.format(java.util.Locale.US, "%.1f %s", value, units[unitIndex])
}
