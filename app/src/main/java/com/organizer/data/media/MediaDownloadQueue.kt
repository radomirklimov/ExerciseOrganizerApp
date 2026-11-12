package com.organizer.data.media

import android.util.Log
import com.organizer.data.local.storage.FileStore
import com.organizer.data.remote.repo.FileRemoteDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import java.util.PriorityQueue

/** Stable identity of one downloadable media file: storage folder + file name. */
data class MediaKey(val folder: String, val fileName: String)

/** Download priorities. Lower value = downloaded sooner. */
object MediaPriority {
    /** Resources needed by the currently visible screen. */
    const val CURRENT = 0

    /** Resources likely needed next (e.g. breadcrumb icons). No prediction logic. */
    const val UPCOMING = 1

    /** Everything else: full-library catch-up after data sync. */
    const val BACKGROUND = 2
}

/**
 * Application-scoped coordinator for media (image/icon) downloads.
 *
 * Data synchronization only *enqueues* missing files here and never waits for
 * downloads, so Room is updated immediately. Screens re-request their visible
 * media at [MediaPriority.CURRENT], which outranks pending background work.
 *
 * Guarantees:
 * - a resource is never downloaded concurrently more than once (deduplicated
 *   by [MediaKey], re-requests only raise priority);
 * - at most [maxConcurrent] downloads run at the same time;
 * - a priority change reorders pending work even while downloads are running;
 * - an individual failure never stops the queue and never fails data sync;
 * - failures are NOT retried automatically (no infinite loop); a failed file
 *   stays eligible for retry via a later enqueue (next sync or screen visit).
 *
 * Every successfully saved file bumps [mediaVersion], which the UI collects to
 * recompose image models as files appear on disk.
 */
class MediaDownloadQueue(
    private val store: FileStore,
    private val remote: FileRemoteDataSource,
    private val scope: CoroutineScope,
    maxConcurrent: Int = DEFAULT_CONCURRENCY,
) {
    private data class Entry(val key: MediaKey, var priority: Int, val seq: Long)

    private val lock = Any()
    private val pending = PriorityQueue<Entry>(compareBy({ it.priority }, { it.seq }))
    private val active = mutableSetOf<MediaKey>()
    private var seq = 0L
    private val permits = Semaphore(maxConcurrent)
    private val wake = Channel<Unit>(Channel.CONFLATED)

    private val _mediaVersion = MutableStateFlow(0L)
    val mediaVersion: StateFlow<Long> = _mediaVersion

    init {
        scope.launch {
            wake.receiveAsFlow().collect { drain() }
        }
    }

    /**
     * Queue [fileName] from [folder] for download unless it is blank, already
     * cached, already queued, or already downloading. A queued entry keeps the
     * highest (lowest value) priority requested so far.
     */
    fun enqueue(folder: String, fileName: String, priority: Int = MediaPriority.BACKGROUND) {
        if (fileName.isBlank()) return
        val key = MediaKey(folder, fileName)
        val changed: Boolean = synchronized(lock) {
            if (key in active) return
            val existing = pending.firstOrNull { it.key == key }
            if (existing != null) {
                if (priority < existing.priority) {
                    pending.remove(existing)
                    existing.priority = priority
                    pending.add(existing)
                    true
                } else {
                    false
                }
            } else {
                if (store.exists(folder, fileName)) return
                pending.add(Entry(key, priority, seq++))
                true
            }
        }
        if (changed) wake.trySend(Unit)
    }

    /** True while [fileName] is queued or actively downloading. */
    fun isActive(folder: String, fileName: String): Boolean = synchronized(lock) {
        val key = MediaKey(folder, fileName)
        key in active || pending.any { it.key == key }
    }

    /** Number of entries waiting for a download slot. Exposed for tests. */
    fun pendingCount(): Int = synchronized(lock) { pending.size }

    private fun drain() {
        while (true) {
            val entry = synchronized(lock) {
                if (!permits.tryAcquire()) return
                pending.poll() ?: run {
                    permits.release()
                    return
                }
            }
            // Runs on the queue scope, never on the caller's scope: navigation
            // or sync cancellation cannot terminate these downloads.
            scope.launch {
                val owned = synchronized(lock) { active.add(entry.key) }
                try {
                    if (owned) download(entry.key)
                } finally {
                    if (owned) synchronized(lock) { active.remove(entry.key) }
                    permits.release()
                    wake.trySend(Unit)
                }
            }
        }
    }

    private suspend fun download(key: MediaKey) {
        // The file may have appeared while queued (e.g. restored cache).
        if (store.exists(key.folder, key.fileName)) return
        try {
            // Null = HTTP error or transport failure: stay silent, stay eligible.
            val bytes = remote.downloadFile(key.folder, key.fileName) ?: return
            store.saveBytes(key.folder, key.fileName, bytes)
            _mediaVersion.update { it + 1 }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("MEDIA", "Failed downloading ${key.fileName}", e)
        }
    }

    companion object {
        const val DEFAULT_CONCURRENCY = 3
    }
}
