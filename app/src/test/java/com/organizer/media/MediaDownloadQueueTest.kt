package com.organizer.media

import com.organizer.data.media.MediaDownloadQueue
import com.organizer.data.media.MediaPriority
import com.organizer.fakes.FakeFileRemote
import com.organizer.fakes.FakeFileStore
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MediaDownloadQueueTest {

    private lateinit var store: FakeFileStore
    private lateinit var remote: FakeFileRemote

    @Before
    fun setUp() {
        store = FakeFileStore()
        remote = FakeFileRemote()
        remote.files["images/a.webp"] = byteArrayOf(1)
        remote.files["images/b.webp"] = byteArrayOf(2)
        remote.files["images/c.webp"] = byteArrayOf(3)
    }

    private fun kotlinx.coroutines.test.TestScope.queue(concurrency: Int = 1) =
        MediaDownloadQueue(store, remote, backgroundScope, concurrency)

    @Test
    fun currentScreenOutranksPendingBackground() = runTest {
        val queue = queue()
        queue.enqueue("images", "a.webp", MediaPriority.BACKGROUND)
        queue.enqueue("images", "b.webp", MediaPriority.BACKGROUND)
        queue.enqueue("images", "c.webp", MediaPriority.CURRENT)
        runCurrent()
        advanceUntilIdle()
        assertEquals(listOf("images/c.webp", "images/a.webp", "images/b.webp"), remote.downloadOrder)
    }

    @Test
    fun reprioritizeRaisesWithoutDuplicating() = runTest {
        val queue = queue()
        queue.enqueue("images", "a.webp", MediaPriority.BACKGROUND)
        queue.enqueue("images", "b.webp", MediaPriority.BACKGROUND)
        queue.enqueue("images", "a.webp", MediaPriority.CURRENT)
        runCurrent()
        advanceUntilIdle()
        assertEquals(listOf("images/a.webp", "images/b.webp"), remote.downloadOrder)
        assertEquals(1, remote.attempts.count { it == "images/a.webp" })
    }

    @Test
    fun duplicateEnqueueDownloadsOnce() = runTest {
        val queue = queue()
        queue.enqueue("images", "a.webp")
        queue.enqueue("images", "a.webp")
        runCurrent()
        advanceUntilIdle()
        assertEquals(1, remote.attempts.count { it == "images/a.webp" })
        assertTrue(store.exists("images", "a.webp"))
    }

    @Test
    fun cachedFileIsNotDownloaded() = runTest {
        store.preload("images", "a.webp")
        val queue = queue()
        queue.enqueue("images", "a.webp")
        runCurrent()
        advanceUntilIdle()
        assertTrue(remote.attempts.isEmpty())
    }

    @Test
    fun blankFileNameIsIgnored() = runTest {
        val queue = queue()
        queue.enqueue("images", "  ")
        runCurrent()
        advanceUntilIdle()
        assertEquals(0, queue.pendingCount())
        assertTrue(remote.attempts.isEmpty())
    }

    @Test
    fun failureDoesNotStopQueueAndStaysRetryable() = runTest {
        val queue = queue()
        remote.failures.add("images/a.webp")
        queue.enqueue("images", "a.webp", MediaPriority.BACKGROUND)
        queue.enqueue("images", "b.webp", MediaPriority.BACKGROUND)
        runCurrent()
        advanceUntilIdle()
        // b still downloads; a fails without a save or version bump.
        assertTrue(store.exists("images", "b.webp"))
        assertTrue(!store.exists("images", "a.webp"))
        assertEquals(1, queue.mediaVersion.value)
        assertEquals(1, remote.attempts.count { it == "images/a.webp" })
        // No automatic immediate retry loop happened; an explicit re-request retries.
        remote.failures.remove("images/a.webp")
        queue.enqueue("images", "a.webp", MediaPriority.CURRENT)
        runCurrent()
        advanceUntilIdle()
        assertTrue(store.exists("images", "a.webp"))
        assertEquals(2, queue.mediaVersion.value)
        assertEquals(2, remote.attempts.count { it == "images/a.webp" })
    }

    @Test
    fun mediaVersionBumpsPerSavedFile() = runTest {
        val queue = queue()
        queue.enqueue("images", "a.webp")
        queue.enqueue("images", "b.webp")
        runCurrent()
        advanceUntilIdle()
        assertEquals(2, queue.mediaVersion.value)
    }

    @Test
    fun concurrencyIsBounded() = runTest {
        val started = mutableListOf<String>()
        var running = 0
        var maxRunning = 0
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val gatedRemote = object : FakeFileRemote() {
            override suspend fun downloadFile(subfolder: String, fileName: String): ByteArray? {
                val key = "$subfolder/$fileName"
                attempts.add(key)
                synchronized(started) {
                    started.add(key)
                    running += 1
                    maxRunning = maxOf(maxRunning, running)
                }
                try {
                    gate.await()
                } finally {
                    synchronized(started) { running -= 1 }
                }
                downloadOrder.add(key)
                return byteArrayOf(9)
            }
        }
        val queue = MediaDownloadQueue(store, gatedRemote, backgroundScope, 2)
        queue.enqueue("images", "a.webp")
        queue.enqueue("images", "b.webp")
        queue.enqueue("images", "c.webp")
        // Let workers pick up work while downloads stay gated, then verify the bound.
        runCurrent()
        assertEquals(2, synchronized(started) { started.size })
        assertEquals(2, synchronized(started) { maxRunning })
        gate.complete(Unit)
        runCurrent()
        advanceUntilIdle()
        assertTrue(synchronized(started) { maxRunning } <= 2)
        assertEquals(3, gatedRemote.attempts.size)
    }
}
