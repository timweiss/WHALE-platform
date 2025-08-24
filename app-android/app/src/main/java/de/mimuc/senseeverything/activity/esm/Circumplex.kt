package de.mimuc.senseeverything.activity.esm

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.toSize
import de.mimuc.senseeverything.activity.ui.theme.Black
import de.mimuc.senseeverything.activity.ui.theme.Purple80
import de.mimuc.senseeverything.api.model.ema.CircumplexElement
import de.mimuc.senseeverything.data.getCircumplexImageBitmap


@Composable
fun CircumplexElementComponent(
    element: CircumplexElement,
    value: Pair<Double, Double>,
    onValueChange: (Pair<Double, Double>) -> Unit
) {
    CachedCircumplexImage(element, value, onTap = {
        onValueChange(it)
    })
}

@Composable
fun CachedCircumplexImage(element: CircumplexElement, initialValue: Pair<Double, Double>? = null, onTap : (Pair<Double, Double>) -> Unit = {}) {
    val context = LocalContext.current
    var circumplexImage: ImageBitmap? by remember { mutableStateOf(null) }

    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var imageSize by remember { mutableStateOf(Size.Zero) }

    val bitmapWidth = circumplexImage?.width
    val bitmapHeight = circumplexImage?.height

    var tapped by remember { mutableStateOf(false) }

    fun isInClipArea(x: Float, y: Float): Boolean {
        return x < element.clipLeft || x > element.clipRight || y < element.clipTop || y > element.clipBottom
    }

    fun handleTap(offset: Offset) {
        // Touch coordinates on image
        offsetX = offset.x
        offsetY = offset.y

        // Scale from Image touch coordinates to range in Bitmap
        val scaledX = (bitmapWidth?.div(imageSize.width))?.times(offsetX)
        val scaledY = (bitmapHeight?.div(imageSize.height))?.times(offsetY)

        // check if tap is in bounds of the clip area of the element
        if (!isInClipArea(scaledX!!, scaledY!!)) {
            tapped = false
            offsetX = 0f
            offsetY = 0f
            onTap(Pair(-99.0, -99.0))
        } else {
            tapped = true

            // Convert to range -1 to 1
            val x = (scaledX?.div(bitmapWidth)?.times(2))?.minus(1)
            val y = (scaledY?.div(bitmapHeight)?.times(2))?.minus(1)?.times(-1)

            // Call onTap with the new coordinates
            onTap(Pair(x!!.toDouble(), y!!.toDouble()))
        }
    }

    fun setupInitialValue() {
        if (initialValue != null) {
            // convert from -1 to 1 to bitmap coordinates
            val x = ((initialValue.first + 1) / 2) * bitmapWidth!!
            val y = ((-initialValue.second + 1) / 2) * bitmapHeight!!

            // convert to image coordinates
            offsetX = (x * (imageSize.width / bitmapWidth)).toFloat()
            offsetY = (y * (imageSize.height / bitmapHeight)).toFloat()
            tapped = true
        }
    }

    LaunchedEffect(Unit) {
        circumplexImage = getCircumplexImageBitmap(context, element)?.asImageBitmap()
    }

    if (circumplexImage != null) {

        Column(modifier = Modifier.fillMaxWidth().drawWithContent {
            drawContent()
            if (tapped) {
                drawCircle(Black, center = Offset(offsetX, offsetY), radius = 25f) // border
                drawCircle(Purple80, center = Offset(offsetX, offsetY), radius = 20f)
            }
        }) {
            Image(
                bitmap = circumplexImage!!,
                contentDescription = "Circumplex",
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { size ->
                        imageSize = size.toSize(); setupInitialValue()
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { offset: Offset ->
                            handleTap(offset)
                        }
                    },
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        }
    } else {
        Text("Loading element")
    }
}