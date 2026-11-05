package com.organizer.data.remote

import com.organizer.data.remote.model.Exercise
import com.organizer.data.remote.repo.ExerciseRemoteDataSource
import com.organizer.fakes.FakeApiService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExercisePaginationTest {

    private fun items(count: Int) = (1..count).map { id ->
        Exercise(id.toLong(), "Ex $id", listOf("do it"), listOf("img$id.webp"))
    }

    @Test
    fun allPagesAreFetchedUntilPartialPage() = runTest {
        val api = FakeApiService(exercises = items(250))

        val all = ExerciseRemoteDataSource(api).getAll()

        assertEquals(250, all.size)
        assertEquals((1L..250L).toList(), all.map { it.exerciseId })
        // 100 + 100 + 50: stops after the partial page, no extra request.
        assertEquals(listOf(0 to 100, 1 to 100, 2 to 100), api.pageCalls)
    }

    @Test
    fun exactMultipleRequestsOneEmptyPage() = runTest {
        val api = FakeApiService(exercises = items(200))

        val all = ExerciseRemoteDataSource(api).getAll()

        assertEquals(200, all.size)
        assertEquals(listOf(0 to 100, 1 to 100, 2 to 100), api.pageCalls)
    }

    @Test
    fun emptyLibraryReturnsImmediately() = runTest {
        val api = FakeApiService(exercises = emptyList())

        val all = ExerciseRemoteDataSource(api).getAll()

        assertTrue(all.isEmpty())
        assertEquals(listOf(0 to 100), api.pageCalls)
    }
}
