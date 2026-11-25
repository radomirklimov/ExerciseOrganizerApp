package com.organizer.sync

import com.organizer.auth.OwnerProvider
import com.organizer.data.local.dao.SyncCursorDao
import com.organizer.data.local.dao.SyncStagedDao
import com.organizer.data.local.db.entities.SyncCursorEntity
import com.organizer.data.local.db.entities.SyncStagedStatus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    private val ownerProvider: OwnerProvider,
    private val tokenProvider: SyncTokenProvider,
    private val apiClient: SyncApiClient,
    private val contractMapper: SyncContractMapper,
    private val transactions: SyncTransactionRunner,
    private val cursorDao: SyncCursorDao,
    private val stagedDao: SyncStagedDao,
    private val stagedApplier: SyncStagedApplier,
    private val operationLocks: SyncOperationLocks,
) {

    /** Manual one-page pull. Does not loop and does not schedule itself. */
    suspend fun pullOnePage(limit: Int = DEFAULT_PAGE_SIZE): PullResult {
        val owner = ownerProvider.currentOwner()
        if (owner == OwnerProvider.GUEST) {
            return PullResult(error = "Guest mode cannot pull cloud data")
        }

        return operationLocks.forOwner(owner).withLock { pullLocked(owner, limit) }
    }

    private suspend fun pullLocked(owner: String, limit: Int): PullResult {
        // Bind the request to the captured user exactly as push does; never
        // let ambient Firebase state choose the credential for this page.
        val token = tokenProvider.idTokenFor(owner)
        if (token == null) {
            return PullResult(
                ownerChanged = ownerProvider.currentOwner() != owner,
                error = "Authenticated user is no longer available for pull",
            )
        }
        val persistedCursor = cursorDao.get(owner)

        val apiResult = apiClient.changes(persistedCursor?.cursor, limit, "Bearer $token")
        val changes = when (apiResult) {
            is SyncApiResult.Success -> contractMapper.toChanges(apiResult.data).getOrElse {
                return PullResult(error = "Malformed pull response")
            }
            is SyncApiResult.HttpError -> return PullResult(error = "HTTP ${apiResult.code}")
            is SyncApiResult.TransportError -> return PullResult(error = "Network error")
            is SyncApiResult.MalformedResponse -> return PullResult(error = "Malformed pull response")
        }

        // The account may have changed while the request was in flight. Never
        // persist a response under a different owner.
        if (ownerProvider.currentOwner() != owner) {
            return PullResult(ownerChanged = true, error = "Authenticated account changed during pull")
        }

        val now = System.currentTimeMillis()
        try {
            transactions.run {
                if (ownerProvider.currentOwner() != owner) {
                    throw OwnerChangedDuringPullException()
                }
                changes.records.forEach { record ->
                    stagedDao.upsertIfNewer(
                        owner = owner,
                        entityType = record.entityType,
                        syncId = record.syncId,
                        serverVersion = record.serverVersion,
                        serverUpdatedAt = record.serverUpdatedAtMillis,
                        deleted = record.deleted,
                        payload = record.payload?.toString(),
                        processingStatus = SyncStagedStatus.PENDING,
                        createdAt = now,
                    )
                }
            cursorDao.upsert(
                SyncCursorEntity(
                    ownerFirebaseUid = owner,
                    cursor = changes.nextCursor,
                    lastSyncedAt = now,
                    hasMore = changes.hasMore,
                    lastServerTimeMillis = changes.serverTimeMillis,
                    updatedAt = now,
                )
            )
        }
        } catch (ex: OwnerChangedDuringPullException) {
            return PullResult(ownerChanged = true, error = "Authenticated account changed during staging")
        } catch (ex: Exception) {
            return PullResult(error = "Failed to persist pull page")
        }

        // The account may have changed between the staging transaction and
        // application. Never apply A's staged rows while B is signed in.
        if (ownerProvider.currentOwner() != owner) {
            return PullResult(ownerChanged = true, error = "Authenticated account changed before applying pull")
        }
        val results = stagedDao.getByStatus(owner, SyncStagedStatus.PENDING).map { stagedApplier.process(owner, it) }
        if (results.any { it == StagedApplyResult.OWNER_CHANGED }) {
            return PullResult(ownerChanged = true, error = "Authenticated account changed while applying pull")
        }
        return PullResult(
            received = changes.records.size,
            staged = changes.records.size,
            applied = results.count { it == StagedApplyResult.APPLIED },
            waitingForDependency = results.count { it == StagedApplyResult.WAITING_DEPENDENCY },
            unsupported = results.count { it == StagedApplyResult.UNSUPPORTED },
            unsupportedSchema = results.count { it == StagedApplyResult.UNSUPPORTED_SCHEMA },
            localPending = results.count { it == StagedApplyResult.LOCAL_PENDING },
            failed = results.count { it == StagedApplyResult.FAILED },
            hasMore = changes.hasMore,
        )
    }

    /**
     * Explicit recovery for staged rows whose application failed. Rows become
     * PENDING and are applied by the next manual pull. UNSUPPORTED_SCHEMA is
     * deliberately not requeued — it stays terminal until a compatible app
     * version explicitly reprocesses it.
     */
    suspend fun retryStagedFailed(): Int {
        val owner = ownerProvider.currentOwner()
        if (owner == OwnerProvider.GUEST) return 0
        return operationLocks.forOwner(owner).withLock {
            try {
                transactions.run {
                    if (ownerProvider.currentOwner() != owner) throw OwnerChangedDuringPullException()
                    stagedDao.resetToPending(owner, setOf(SyncStagedStatus.FAILED))
                }
            } catch (ex: OwnerChangedDuringPullException) {
                0
            }
        }
    }

    /** Kept for the existing worker placeholder; no automatic sync is performed. */
    suspend fun syncNow() = Unit

    companion object {
        const val DEFAULT_PAGE_SIZE = 100
    }
}

/** Thrown inside the staging transaction to roll back an account-switched pull. */
class OwnerChangedDuringPullException : RuntimeException()