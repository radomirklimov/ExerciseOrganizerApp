package com.organizer.data.local.storage

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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
    ): String? {
        return try {
            val dir = dir(subfolder)
            val fileName = "image_${System.currentTimeMillis()}.jpg"
            val destinationFile = File(dir, fileName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                destinationFile.outputStream().use { output ->
                    input.copyTo(output)
                }
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
}
