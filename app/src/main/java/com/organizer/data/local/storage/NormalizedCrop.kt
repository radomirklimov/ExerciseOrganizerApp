package com.organizer.data.local.storage

/**
 * Visible part of a source image, expressed as fractions (0..1) of the
 * image's oriented width and height.
 *
 * Used when a custom exercise image is cropped to the image field before it
 * is written to local storage.
 */
data class NormalizedCrop(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}
