package de.mimuc.senseeverything.activity.esm.screenshot

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mimuc.senseeverything.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val MAX_HEIGHT = 480.dp

private sealed interface ImageState {
    object Loading : ImageState
    object Unavailable : ImageState
    data class Loaded(val image: ImageBitmap) : ImageState
}

/**
 * Decodes the image when it enters the composition. The bitmap is only referenced from here, so it can
 * be freed once the image leaves the composition (its accordion item is collapsed).
 */
@Composable
fun ScreenshotImage(screenshot: ScreenshotReference) {
    val state by produceState<ImageState>(ImageState.Loading, screenshot.image) {
        value = withContext(Dispatchers.IO) { decode(screenshot.image) }
            ?.let { ImageState.Loaded(it) }
            ?: ImageState.Unavailable
    }

    when (val current = state) {
        // reserves the space, so the questions below don't jump when the image appears
        is ImageState.Loading -> Box(Modifier.fillMaxWidth().height(MAX_HEIGHT), Alignment.Center) {
            CircularProgressIndicator()
        }

        is ImageState.Unavailable -> Text(stringResource(R.string.screenshot_rating_image_unavailable))

        is ImageState.Loaded -> Image(
            bitmap = current.image,
            contentDescription = screenshot.label(),
            modifier = Modifier.fillMaxWidth().heightIn(max = MAX_HEIGHT),
            contentScale = ContentScale.Fit
        )
    }
}

/** Null if the period was deleted meanwhile or the file is broken. */
private fun decode(file: File): ImageBitmap? {
    // a hardware bitmap lives in graphics memory only; others also keep a copy on the heap to draw from
    val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.HARDWARE }
    return BitmapFactory.decodeFile(file.path, options)?.asImageBitmap()
}
