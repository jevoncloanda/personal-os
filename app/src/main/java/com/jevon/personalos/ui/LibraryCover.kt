package com.jevon.personalos.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jevon.personalos.data.LibraryItemEntity
import com.jevon.personalos.data.LibraryKind
import com.jevon.personalos.library.loadLibraryCover
import java.util.Locale

/** The adjacent item title owns accessibility; artwork is decorative and does not repeat it. */
@Composable
internal fun LibraryCover(
    item: LibraryItemEntity,
    modifier: Modifier = Modifier,
    detailedFallback: Boolean = false,
) {
    val context = LocalContext.current.applicationContext
    val bitmap by produceState<Bitmap?>(null, context, item.coverPath) {
        value = null
        value = item.coverPath?.let { loadLibraryCover(context, it) }
    }
    val image = remember(bitmap) { bitmap?.asImageBitmap() }
    val fallbackIcon = when (item.kind) {
        LibraryKind.GAME -> AppIcons.SportsEsports
        LibraryKind.BOOK -> AppIcons.MenuBook
        else -> AppIcons.Movie
    }
    Surface(modifier = modifier.size(64.dp, 96.dp).clip(MaterialTheme.shapes.small),
        shape = MaterialTheme.shapes.small,
        color = if (detailedFallback) MaterialTheme.colorScheme.surfaceContainerHigh
            else MaterialTheme.colorScheme.secondaryContainer) {
        if (image != null) Image(image, contentDescription = null, contentScale = ContentScale.Crop)
        else if (detailedFallback) BoxWithConstraints(contentAlignment = Alignment.Center) {
            val showInitials = maxHeight >= 96.dp && maxWidth >= 64.dp
            Column(Modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showInitials) Text(
                    item.title.trim().split(Regex("\\s+")).take(2)
                        .mapNotNull { it.firstOrNull()?.toString() }.joinToString("")
                        .uppercase(Locale.ROOT),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
                Icon(fallbackIcon, contentDescription = null, modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary)
            }
        } else Box(contentAlignment = Alignment.Center) {
            Icon(fallbackIcon, contentDescription = null, modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}
