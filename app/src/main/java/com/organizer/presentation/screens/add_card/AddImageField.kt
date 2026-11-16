package com.organizer.presentation.screens.add_card

import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun AddImageField(
    imageUri: Uri?,
    cropState: ImageCropState,
    onImageSelected: (Uri?) -> Unit,
) {
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        onImageSelected(uri)
    }

    val context = LocalContext.current
    var image by remember(imageUri) { mutableStateOf<ImageBitmap?>(null) }
    var isLoading by remember(imageUri) { mutableStateOf(imageUri != null) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var showHint by remember(imageUri) { mutableStateOf(true) }

    LaunchedEffect(imageUri) {
        isLoading = imageUri != null
        image = imageUri?.let { uri ->
            withContext(Dispatchers.IO) {
                val request = ImageRequest.Builder(context)
                    .data(uri)
                    .allowHardware(false)
                    .size(PREVIEW_MAX_SIZE)
                    .build()
                (context.imageLoader.execute(request).drawable as? BitmapDrawable)
                    ?.bitmap
                    ?.asImageBitmap()
            }
        }
        isLoading = false
    }

    LaunchedEffect(image, containerSize) {
        val loaded = image ?: return@LaunchedEffect
        if (containerSize == IntSize.Zero) return@LaunchedEffect
        cropState.updateSizes(
            imageWidth = loaded.width,
            imageHeight = loaded.height,
            containerWidth = containerSize.width,
            containerHeight = containerSize.height,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable {
                imagePickerLauncher.launch("image/*")
            }
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(18.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        val loadedImage = image
        when {
            imageUri == null -> AddImagePlaceholder()

            loadedImage == null -> {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp,
                    )
                } else {
                    AddImagePlaceholder()
                }
            }

            else -> {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { containerSize = it }
                        .pointerInput(loadedImage) {
                            detectTransformGestures { centroid, pan, zoom, _ ->
                                showHint = false
                                cropState.transform(
                                    centroid = centroid,
                                    pan = pan,
                                    zoomChange = zoom,
                                )
                            }
                        }
                ) {
                    val scale = max(
                        size.width / loadedImage.width,
                        size.height / loadedImage.height,
                    ) * cropState.zoom
                    val displayedWidth = loadedImage.width * scale
                    val displayedHeight = loadedImage.height * scale

                    drawImage(
                        image = loadedImage,
                        dstOffset = IntOffset(
                            x = ((size.width - displayedWidth) / 2f + cropState.offset.x).roundToInt(),
                            y = ((size.height - displayedHeight) / 2f + cropState.offset.y).roundToInt(),
                        ),
                        dstSize = IntSize(
                            width = displayedWidth.roundToInt(),
                            height = displayedHeight.roundToInt(),
                        ),
                    )
                }

                AnimatedVisibility(
                    visible = showHint,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp),
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    ImageAdjustHint()
                }
            }
        }
    }
}

@Composable
private fun ImageAdjustHint() {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.62f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = "Choose the visible part",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(modifier = Modifier.height(6.dp))

        HintRow(
            icon = Icons.Default.OpenWith,
            text = "Drag the photo to move it",
        )

        Spacer(modifier = Modifier.height(4.dp))

        HintRow(
            icon = Icons.Default.ZoomIn,
            text = "Pinch to zoom in or out",
        )
    }
}

@Composable
private fun HintRow(
    icon: ImageVector,
    text: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(14.dp),
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = text,
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun AddImagePlaceholder() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Add image",
            modifier = Modifier.size(42.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Add Image",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

private const val PREVIEW_MAX_SIZE = 1600
