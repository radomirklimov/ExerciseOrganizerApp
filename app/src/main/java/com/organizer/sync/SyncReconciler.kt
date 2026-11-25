package com.organizer.sync

import com.organizer.auth.OwnerProvider
import com.organizer.data.local.dao.SyncOutboxDao
import com.organizer.data.local.dao.SyncStagedDao
import com.organizer.data.local.db.entities.SyncStagedStatus
import javax.inject.Inject
import javax.inject.Singleton

data class ReconciliationResult(
    val reconsidered: Int = 0,
    val applied: Int = 0,
    val stillBlocked: Int = 0,
    val localPending: Int = 0,
    val tombstoneOnly: Int = 0,
    val unsupported: Int = 0,
    val failed: Int = 0,
    val ownerChanged: Boolean = false,
)

@Singleton
class SyncReconciler @Inject constructor(
    private val ownerProvider: OwnerProvider,
    private val outboxDao: SyncOutboxDao,
    private val stagedDao: SyncStagedDao,
    private val stagedApplier: SyncStagedApplier,
    private val transactions: SyncTransactionRunner,
) {
    suspend fun reconcile(): ReconciliationResult {
        val owner = ownerProvider.currentOwner()
        if (owner == OwnerProvider.GUEST) {
            return ReconciliationResult(ownerChanged = false)
        }

        val pending = stagedDao.getByStatus(owner, SyncStagedStatus.LOCAL_PENDING)
        var reconsidered = 0
        var stillBlocked = 0
        var applied = 0
        var localPending = 0
        var tombstoneOnly = 0
        var unsupported = 0
        var failed = 0

        for (staged in pending) {
            val outcome = try {
                transactions.run {
                    if (ownerProvider.currentOwner() != owner) {
                        throw OwnerChangedDuringReconciliationException()
                    }
                    val blocked = outboxDao.hasBlockingMutationForRecord(
                        owner, staged.entityType, staged.syncId, SyncOutboxStatus.BLOCKING
                    )
                    if (blocked) {
                        ReconcileOutcome.STILL_BLOCKED
                    } else {
                        stagedDao.updateStatus(
                            owner = owner,
                            stagedId = staged.stagedId,
                            status = SyncStagedStatus.PENDING,
                            retryCount = staged.retryCount,
                            error = null,
                            attemptAt = System.currentTimeMillis(),
                        )
                        val updated = staged.copy(processingStatus = SyncStagedStatus.PENDING)
                        when (stagedApplier.process(owner, updated)) {
                            StagedApplyResult.APPLIED -> ReconcileOutcome.APPLIED
                            StagedApplyResult.TOMBSTONE_ONLY -> ReconcileOutcome.TOMBSTONE_ONLY
                            StagedApplyResult.LOCAL_PENDING -> ReconcileOutcome.LOCAL_PENDING
                            StagedApplyResult.UNSUPPORTED,
                            StagedApplyResult.UNSUPPORTED_SCHEMA,
                            StagedApplyResult.WAITING_DEPENDENCY -> ReconcileOutcome.UNSUPPORTED
                            StagedApplyResult.FAILED -> ReconcileOutcome.FAILED
                        }
                    }
                }
            } catch (ex: OwnerChangedDuringReconciliationException) {
                return ReconciliationResult(
                    reconsidered = reconsidered,
                    applied = applied,
                    stillBlocked = stillBlocked,
                    localPending = localPending,
                    tombstoneOnly = tombstoneOnly,
                    unsupported = unsupported,
                    failed = failed,
                    ownerChanged = true,
                )
            }

            when (outcome) {
                ReconcileOutcome.STILL_BLOCKED -> stillBlocked++
                ReconcileOutcome.APPLIED -> { reconsidered++; applied++ }
                ReconcileOutcome.TOMBSTONE_ONLY -> { reconsidered++; tombstoneOnly++ }
                ReconcileOutcome.LOCAL_PENDING -> { reconsidered++; localPending++ }
                ReconcileOutcome.UNSUPPORTED -> { reconsidered++; unsupported++ }
                ReconcileOutcome.FAILED -> { reconsidered++; failed++ }
            }
        }

        return ReconciliationResult(
            reconsidered = reconsidered,
            applied = applied,
            stillBlocked = stillBlocked,
            localPending = localPending,
            tombstoneOnly = tombstoneOnly,
            unsupported = unsupported,
            failed = failed,
        )
    }

    private enum class ReconcileOutcome {
        STILL_BLOCKED,
        APPLIED,
        TOMBSTONE_ONLY,
        LOCAL_PENDING,
        UNSUPPORTED,
        FAILED,
    }
}

/** Thrown inside the reconciliation transaction to roll back an account-switched call. */
class OwnerChangedDuringReconciliationException : RuntimeException()