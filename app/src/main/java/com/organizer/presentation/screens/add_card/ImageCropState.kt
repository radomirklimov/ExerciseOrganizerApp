package com.organizer.presentation.screens.add_card

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.organizer.data.local.storage.NormalizedCrop
import kotlin.math.max

/**
 * Pan/zoom state for the "Add Image" field.
 *
 * The image always fills the field (cover scale) and can be dragged or pinched
 * to pick the visible part; [cropRect] reports that part when the exercise is
 * created. All maths is done on the field and bitmap sizes, so the crop is
 * independent of screen density.
 */
@Stable
class ImageCropState {
    var offset by mutableStateOf(Offset.Zero)
        private set

    var zoom by mutableStateOf(MIN_ZOOM)
        private set

    private var imageWidth = 0
    private var imageHeight = 0
    private var containerWidth = 0
    private var containerHeight = 0

    val isReady: Boolean
        get() = imageWidth > 0 && imageHeight > 0 &&
                containerWidth > 0 && containerHeight > 0

    fun updateSizes(
        imageWidth: Int,
        imageHeight: Int,
        containerWidth: Int,
        containerHeight: Int,
    ) {
        if (imageWidth <= 0 || imageHeight <= 0 ||
            containerWidth <= 0 || containerHeight <= 0
        ) {
            return
        }
        val imageChanged = this.imageWidth != imageWidth || this.imageHeight != imageHeight
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight
        this.containerWidth = containerWidth
        this.containerHeight = containerHeight
        if (imageChanged) {
            offset = Offset.Zero
            zoom = MIN_ZOOM
        } else {
            offset = offset.coerceToBounds()
        }
    }

    fun reset() {
        offset = Offset.Zero
        zoom = MIN_ZOOM
    }

    /**
     * Applies one gesture frame: [pan] moves the image, [zoomChange] scales it
     * around [centroid] (in field coordinates) so the content under the
     * fingers stays put.
     */
    fun transform(centroid: Offset, pan: Offset, zoomChange: Float) {
        if (!isReady) return

        val newZoom = (zoom * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (newZoom != zoom) {
            val oldScale = coverScale() * zoom
            val newScale = coverScale() * newZoom
            val focus = centroid - Offset(containerWidth / 2f, containerHeight / 2f)
            val imagePoint = (focus - offset) / oldScale
            offset = focus - imagePoint * newScale
            zoom = newZoom
        }

        offset = (offset + pan).coerceToBounds()
    }

    /** Visible part of the image as 0..1 fractions, ready for storage. */
    fun cropRect(): NormalizedCrop {
        if (!isReady) return NormalizedCrop(0f, 0f, 1f, 1f)
        return normalizedCrop(
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            containerWidth = containerWidth,
            containerHeight = containerHeight,
            offsetX = offset.x,
            offsetY = offset.y,
            zoom = zoom,
        )
    }

    private fun Offset.coerceToBounds(): Offset {
        if (!isReady) return Offset.Zero
        return Offset(
            x = x.coerceIn(-maxOffsetX(), maxOffsetX()),
            y = y.coerceIn(-maxOffsetY(), maxOffsetY()),
        )
    }

    /** Scale that makes the image exactly cover the field at zoom 1. */
    private fun coverScale(): Float = max(
        containerWidth / imageWidth.toFloat(),
        containerHeight / imageHeight.toFloat(),
    )

    private fun maxOffsetX(): Float = max(0f, (imageWidth * coverScale() * zoom - containerWidth) / 2f)

    private fun maxOffsetY(): Float = max(0f, (imageHeight * coverScale() * zoom - containerHeight) / 2f)

    companion object {
        const val MIN_ZOOM = 1f
        const val MAX_ZOOM = 4f
    }
}

/**
 * Maps the field's [offset] and [zoom] (relative to a centred, cover-scaled
 * image) to the visible source rectangle in normalized image coordinates.
 */
internal fun normalizedCrop(
    imageWidth: Int,
    imageHeight: Int,
    containerWidth: Int,
    containerHeight: Int,
    offsetX: Float,
    offsetY: Float,
    zoom: Float = ImageCropState.MIN_ZOOM,
): NormalizedCrop {
    val scale = max(
        containerWidth / imageWidth.toFloat(),
        containerHeight / imageHeight.toFloat(),
    ) * zoom
    val displayedWidth = imageWidth * scale
    val displayedHeight = imageHeight * scale
    val maxOffsetX = max(0f, (displayedWidth - containerWidth) / 2f)
    val maxOffsetY = max(0f, (displayedHeight - containerHeight) / 2f)
    val clampedOffsetX = offsetX.coerceIn(-maxOffsetX, maxOffsetX)
    val clampedOffsetY = offsetY.coerceIn(-maxOffsetY, maxOffsetY)

    // Top-left of the visible area in source pixels.
    val left = ((displayedWidth - containerWidth) / 2f - clampedOffsetX) / scale
    val top = ((displayedHeight - containerHeight) / 2f - clampedOffsetY) / scale
    val width = containerWidth / scale
    val height = containerHeight / scale

    return NormalizedCrop(
        left = (left / imageWidth).coerceIn(0f, 1f),
        top = (top / imageHeight).coerceIn(0f, 1f),
        right = ((left + width) / imageWidth).coerceIn(0f, 1f),
        bottom = ((top + height) / imageHeight).coerceIn(0f, 1f),
    )
}
