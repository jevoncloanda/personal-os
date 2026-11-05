package com.jevon.personalos.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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

/** The adjacent item title owns accessibility; artwork is decorative and does not repeat it. */
@Composable
internal fun LibraryCover(item: LibraryItemEntity, modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val bitmap by produceState<Bitmap?>(null, context, item.coverPath) {
        value = null
        value = item.coverPath?.let { loadLibraryCover(context, it) }
    }
    val image = remember(bitmap) { bitmap?.asImageBitmap() }
    Surface(modifier = modifier.size(64.dp, 96.dp).clip(MaterialTheme.shapes.small),
        shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
        if (image != null) Image(image, contentDescription = null, contentScale = ContentScale.Crop)
        else Box(contentAlignment = Alignment.Center) {
            Icon(when (item.kind) {
                LibraryKind.GAME -> AppIcons.SportsEsports
                LibraryKind.BOOK -> AppIcons.MenuBook
                else -> AppIcons.Movie
            }, contentDescription = null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}
