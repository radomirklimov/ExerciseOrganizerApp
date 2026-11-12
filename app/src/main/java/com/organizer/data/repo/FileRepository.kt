package com.organizer.data.repo

import android.net.Uri
import com.organizer.constants.StorageFolders
import com.organizer.data.local.storage.FileStore
import com.organizer.data.media.MediaPriority
import com.organizer.data.media.MediaDownloadQueue
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

class FileRepository @Inject constructor(
    private val fileStorage: FileStore,
    private val mediaQueue: MediaDownloadQueue,
) {
    /** Bumped on every saved media file; the UI collects it to reload images. */
    val mediaVersion: StateFlow<Long> = mediaQueue.mediaVersion

    /**
     * Queue icons for background download. Returns immediately; data
     * synchronization must never wait for media.
     */
    fun enqueueIcons(iconNames: List<String?>, priority: Int = MediaPriority.BACKGROUND) {
        iconNames.filterNotNull().filter { it.isNotBlank() }
            .forEach { mediaQueue.enqueue(StorageFolders.ICONS, it, priority) }
    }

    /** Queue exercise images for background download. Custom images are never queued. */
    fun enqueueImages(imageNames: List<String>, priority: Int = MediaPriority.BACKGROUND) {
        imageNames.filter { it.isNotBlank() }
            .forEach { mediaQueue.enqueue(StorageFolders.IMAGES, it, priority) }
    }

    fun saveCustomImage(uri: Uri): String? {
        return fileStorage.saveUriToStorage(StorageFolders.CUSTOM_IMAGE, uri)
    }

    fun getIcon(name: String): File? {
        val file = File(fileStorage.dir(StorageFolders.ICONS), name)
        return file.takeIf { it.exists() }
    }

    fun getImage(name: String, isCustom: Boolean): File? =
        File(
            fileStorage.dir(
                if (isCustom) StorageFolders.CUSTOM_IMAGE
                else StorageFolders.IMAGES
            ),
            name
        ).takeIf(File::exists)

    fun deleteIcon(iconName: String?) {
        if (iconName.isNullOrBlank()) return
        // Never delete a file that is queued or currently downloading.
        if (mediaQueue.isActive(StorageFolders.ICONS, iconName)) return
        fileStorage.deleteLocalFile(StorageFolders.ICONS, iconName)
    }

    fun deleteImage(imageNames: List<String>) {
        for (name in imageNames) {
            if (name.isBlank()) continue
            if (mediaQueue.isActive(StorageFolders.IMAGES, name)) continue
            fileStorage.deleteLocalFile(StorageFolders.IMAGES, name)
        }
    }
}
