package com.frosthush.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.frosthush.app.data.AppRepository

/** 加载并展示应用图标（带缓存） */
@Composable
fun rememberAppIcon(packageName: String, sizePx: Int): ImageBitmap? {
    val context = LocalContext.current
    val repo = remember { AppRepository(context) }
    val drawable = remember(packageName) { repo.iconOf(packageName) }
    return remember(drawable, sizePx) {
        runCatching { drawable.toBitmap(sizePx, sizePx).asImageBitmap() }.getOrNull()
    }
}

/** 应用图标 Composable */
@Composable
fun AppIcon(packageName: String, size: Dp, corner: Dp = 8.dp) {
    val px = (size.value * LocalContext.current.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    val bitmap = rememberAppIcon(packageName, px)
    val shape = RoundedCornerShape(corner)
    if (bitmap != null) {
        androidx.compose.foundation.Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier.size(size).clip(shape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(Modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant))
    }
}
