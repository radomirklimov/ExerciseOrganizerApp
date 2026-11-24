package com.organizer.sync

import com.organizer.auth.AuthRepository
import com.organizer.auth.UserState
import com.organizer.data.remote.SyncApi
import com.organizer.data.remote.model.SyncPushRequestDto
import com.organizer.data.remote.model.SyncRecordRequestDto
import com.organizer.data.repo.WorkoutRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncApi: SyncApi,
    private val workoutRepository: WorkoutRepository,
) {
    suspend fun syncNow() {
        val state = authRepository.state.value
        if (state !is UserState.Authenticated) return

        // Push local workouts.
        val workouts = workoutRepository.observeWorkouts().first()
        val records = workouts.map { w ->
            SyncRecordRequestDto(
                entityType = SyncEntityTypes.WORKOUT,
                clientId = w.workoutId.toString(),
                payload = buildJsonObject {
                    put("name", w.name)
                    put("notes", w.notes)
                },
                updatedAt = null,
                deleted = false,
            )
        }
        if (records.isNotEmpty()) {
            syncApi.push(SyncPushRequestDto(records = records))
        }

        // Pull remote changes (best-effort; application of remote records is incremental).
        syncApi.changes()
    }
}