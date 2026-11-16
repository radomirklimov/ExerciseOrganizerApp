package com.organizer.presentation.screens.add_card

import org.junit.Assert.assertEquals
import org.junit.Test
import androidx.compose.ui.geometry.Offset

class ImageCropStateTest {

    @Test
    fun `wide image is centre cropped to the field`() {
        val crop = normalizedCrop(
            imageWidth = 2000,
            imageHeight = 1000,
            containerWidth = 500,
            containerHeight = 500,
            offsetX = 0f,
            offsetY = 0f,
        )

        assertCrop(0.25f, 0f, 0.75f, 1f, crop)
    }

    @Test
    fun `tall image is centre cropped to the field`() {
        val crop = normalizedCrop(
            imageWidth = 1000,
            imageHeight = 2000,
            containerWidth = 500,
            containerHeight = 500,
            offsetX = 0f,
            offsetY = 0f,
        )

        assertCrop(0f, 0.25f, 1f, 0.75f, crop)
    }

    @Test
    fun `square image keeps the whole source visible`() {
        val crop = normalizedCrop(
            imageWidth = 1000,
            imageHeight = 1000,
            containerWidth = 500,
            containerHeight = 500,
            offsetX = 0f,
            offsetY = 0f,
        )

        assertCrop(0f, 0f, 1f, 1f, crop)
    }

    @Test
    fun `dragging right reveals the left side of the image`() {
        val crop = normalizedCrop(
            imageWidth = 2000,
            imageHeight = 1000,
            containerWidth = 500,
            containerHeight = 500,
            offsetX = 250f,
            offsetY = 0f,
        )

        assertCrop(0f, 0f, 0.5f, 1f, crop)
    }

    @Test
    fun `dragging outside the image bounds is clamped`() {
        val crop = normalizedCrop(
            imageWidth = 2000,
            imageHeight = 1000,
            containerWidth = 500,
            containerHeight = 500,
            offsetX = 10_000f,
            offsetY = 10_000f,
        )

        assertCrop(0f, 0f, 0.5f, 1f, crop)
    }

    @Test
    fun `image smaller than the field still covers it`() {
        val crop = normalizedCrop(
            imageWidth = 100,
            imageHeight = 100,
            containerWidth = 500,
            containerHeight = 800,
            offsetX = 0f,
            offsetY = 0f,
        )

        assertEquals(0.1875f, crop.left, 0.0001f)
        assertEquals(0f, crop.top, 0.0001f)
        assertEquals(0.8125f, crop.right, 0.0001f)
        assertEquals(1f, crop.bottom, 0.0001f)
        assertEquals(
            800f / 500f,
            crop.height / crop.width,
            0.0001f,
        )
    }

    @Test
    fun `zoom crops a smaller part of the image`() {
        val crop = normalizedCrop(
            imageWidth = 2000,
            imageHeight = 1000,
            containerWidth = 500,
            containerHeight = 500,
            offsetX = 0f,
            offsetY = 0f,
            zoom = 2f,
        )

        assertCrop(0.375f, 0.25f, 0.625f, 0.75f, crop)
    }

    @Test
    fun `zoom is clamped between the supported range`() {
        val state = ImageCropState()
        state.updateSizes(
            imageWidth = 2000,
            imageHeight = 1000,
            containerWidth = 500,
            containerHeight = 500,
        )

        repeat(10) {
            state.transform(Offset(250f, 250f), Offset.Zero, 10f)
        }
        assertEquals(ImageCropState.MAX_ZOOM, state.zoom, 0.0001f)

        repeat(10) {
            state.transform(Offset(250f, 250f), Offset.Zero, 0.1f)
        }
        assertEquals(ImageCropState.MIN_ZOOM, state.zoom, 0.0001f)
    }

    @Test
    fun `zooming at the field centre keeps the crop centred`() {
        val state = ImageCropState()
        state.updateSizes(
            imageWidth = 2000,
            imageHeight = 1000,
            containerWidth = 500,
            containerHeight = 500,
        )

        state.transform(Offset(250f, 250f), Offset.Zero, 2f)

        val crop = state.cropRect()
        assertEquals(0.5f, (crop.left + crop.right) / 2f, 0.0001f)
        assertEquals(0.5f, (crop.top + crop.bottom) / 2f, 0.0001f)
    }

    private fun assertCrop(
        expectedLeft: Float,
        expectedTop: Float,
        expectedRight: Float,
        expectedBottom: Float,
        actual: com.organizer.data.local.storage.NormalizedCrop,
    ) {
        assertEquals(expectedLeft, actual.left, 0.0001f)
        assertEquals(expectedTop, actual.top, 0.0001f)
        assertEquals(expectedRight, actual.right, 0.0001f)
        assertEquals(expectedBottom, actual.bottom, 0.0001f)
    }
}
