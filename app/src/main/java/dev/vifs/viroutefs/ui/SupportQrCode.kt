// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.vifs.viroutefs.routing.generateSupportQrCode

@Composable
internal fun SupportQrCode(
    url: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val generated = remember(url) { generateSupportQrCode(url) }
    val image = remember(generated) {
        Bitmap.createBitmap(
            generated.argbPixels,
            generated.width,
            generated.height,
            Bitmap.Config.ARGB_8888,
        ).asImageBitmap()
    }
    Surface(
        modifier = modifier
            .size(260.dp)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 2.dp,
    ) {
        Image(
            bitmap = image,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier.padding(12.dp),
        )
    }
}
