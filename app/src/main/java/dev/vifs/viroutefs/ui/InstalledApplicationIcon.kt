// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.ui

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads an installed application's own launcher icon locally.
 *
 * Bitmaps use a small in-memory cache and are never persisted or exported
 * with routing configuration or scanner data.
 */
@Composable
internal fun InstalledApplicationIcon(
    packageName: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier.size(40.dp),
) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(
        initialValue = packageName?.cachedApplicationIcon(),
        key1 = context,
        key2 = packageName,
    ) {
        if (packageName != null && value == null) {
            value = withContext(Dispatchers.IO) {
                context.loadApplicationIcon(packageName)
            }
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        val currentBitmap = bitmap
        if (currentBitmap != null) {
            Image(
                bitmap = currentBitmap,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Apps,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

private fun Context.loadApplicationIcon(packageName: String): ImageBitmap? {
    packageName.cachedApplicationIcon()?.let { return it }
    val bitmap = runCatching {
        packageManager
            .getApplicationIcon(packageName)
            .toBitmap(
                width = APP_ICON_BITMAP_SIZE,
                height = APP_ICON_BITMAP_SIZE,
                config = Bitmap.Config.ARGB_8888,
            )
            .asImageBitmap()
    }.getOrNull()
    if (bitmap != null) {
        synchronized(appIconCache) {
            appIconCache.put(packageName, bitmap)
        }
    }
    return bitmap
}

private fun String.cachedApplicationIcon(): ImageBitmap? = synchronized(appIconCache) {
    appIconCache.get(this)
}

private const val APP_ICON_BITMAP_SIZE = 96
private val appIconCache = LruCache<String, ImageBitmap>(128)
