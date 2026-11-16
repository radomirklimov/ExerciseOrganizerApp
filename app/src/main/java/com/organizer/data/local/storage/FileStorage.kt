package com.organizer.data.local.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class FileStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) : FileStore {
    override fun dir(subfolder: String): File {
        val folder = File(context.filesDir, subfolder)
        if (!folder.exists()) folder.mkdirs()
        return folder
    }

    override fun exists(subfolder: String, fileName: String): Boolean {
        return File(dir(subfolder), fileName).isFile
    }

    override suspend fun saveBytes(subfolder: String, fileName: String, bytes: ByteArray) {
        withContext(Dispatchers.IO) {
            File(dir(subfolder), fileName).writeBytes(bytes)
        }
    }

    // saves file from uri to device storage and returns its name
    override fun saveUriToStorage(
        subfolder: String,
        uri: Uri,
        crop: NormalizedCrop?,
    ): String? {
        return try {
            val dir = dir(subfolder)
            val fileName = "image_${System.currentTimeMillis()}.jpg"
            val destinationFile = File(dir, fileName)

            if (crop == null) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    destinationFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } else {
                val bytes = croppedJpeg(uri, crop) ?: return null
                destinationFile.writeBytes(bytes)
            }

            fileName
        } catch (e: Exception) {
            Log.e("FILES", "Failed saving image", e)
            null
        }
    }

    override fun deleteLocalFile(
        subfolder: String,
        fileName: String
    ) {
        File(dir(subfolder), fileName).delete()
    }

    /**
     * Decodes the image with its EXIF orientation applied, crops it to [crop]
     * and returns a JPEG of at most [MAX_OUTPUT_WIDTH] pixels wide. Keeping the
     * output width fixed makes every custom exercise image the same size,
     * regardless of the source resolution.
     */
    private fun croppedJpeg(uri: Uri, crop: NormalizedCrop): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOptions)
        } ?: return null

        val oriented = applyExifOrientation(uri, decoded)

        val imageWidth = oriented.width
        val imageHeight = oriented.height
        if (imageWidth <= 0 || imageHeight <= 0) {
            oriented.recycle()
            return null
        }

        val left = (crop.left.coerceIn(0f, 0.99f) * imageWidth).roundToInt()
        val top = (crop.top.coerceIn(0f, 0.99f) * imageHeight).roundToInt()
        val right = (crop.right.coerceIn(0.01f, 1f) * imageWidth).roundToInt()
        val bottom = (crop.bottom.coerceIn(0.01f, 1f) * imageHeight).roundToInt()

        val sourceWidth = (right - left).coerceAtLeast(1)
        val sourceHeight = (bottom - top).coerceAtLeast(1)
        val outputWidth = min(sourceWidth, MAX_OUTPUT_WIDTH)
        val outputHeight = max(1, (outputWidth * sourceHeight / sourceWidth.toFloat()).roundToInt())

        val output = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        Canvas(output).drawBitmap(
            oriented,
            Rect(left, top, left + sourceWidth, top + sourceHeight),
            Rect(0, 0, outputWidth, outputHeight),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )

        oriented.recycle()

        return ByteArrayOutputStream().use { stream ->
            output.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
            output.recycle()
            stream.toByteArray()
        }
    }

    /** Keeps the decoded bitmap around twice the output width for good quality. */
    private fun sampleSizeFor(width: Int, height: Int): Int {
        var sample = 1
        val longestSide = max(width, height)
        while (longestSide / (sample * 2) >= MAX_OUTPUT_WIDTH * 2) {
            sample *= 2
        }
        return sample
    }

    @Suppress("DEPRECATION")
    private fun applyExifOrientation(uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: IOException) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            .also { if (it !== bitmap) bitmap.recycle() }
    }

    private companion object {
        const val MAX_OUTPUT_WIDTH = 1080
        const val JPEG_QUALITY = 90
    }
}
