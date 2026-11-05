package com.organizer.data.local.storage

import android.net.Uri
import java.io.File

/**
 * Local file access for cached remote media (exercise images, category icons)
 * and user-created custom images.
 *
 * Extracted as an interface so repositories and the media queue can be unit
 * tested with an in-memory fake (no Android Context on the JVM).
 */
interface FileStore {
    fun dir(subfolder: String): File

    fun exists(subfolder: String, fileName: String): Boolean

    suspend fun saveBytes(subfolder: String, fileName: String, bytes: ByteArray)

    fun deleteLocalFile(subfolder: String, fileName: String)

    // saves file from uri to device storage and returns its name
    fun saveUriToStorage(subfolder: String, uri: Uri): String?
}
