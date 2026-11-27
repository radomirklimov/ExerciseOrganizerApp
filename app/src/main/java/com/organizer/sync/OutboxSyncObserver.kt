package com.organizer.sync

import com.organizer.auth.OwnerProvider
import com.organizer.data.local.dao.SyncOutboxDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Requests a sync after outbox mutations become visible in Room. Room Flows
 * emit only after a transaction commits, so a mutation can never be missed by
 * scheduling from inside the transaction that created it.
 */
@Singleton
class OutboxSyncObserver @Inject constructor(
    private val ownerProvider: OwnerProvider,
    private val outboxDao: SyncOutboxDao,
    private val scheduler: SyncTrigger,
) {
    private val started = AtomicBoolean(false)

    fun start(scope: CoroutineScope) {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            var currentOwner: String? = null
            var seen = mutableSetOf<String>()
            ownerProvider.observeOwner()
                .distinctUntilChanged()
                .flatMapLatest { owner ->
                    if (owner == OwnerProvider.GUEST || owner == OwnerProvider.PUBLIC) {
                        flowOf(owner to emptyList())
                    } else {
                        outboxDao.observe(owner).map { rows ->
                            owner to rows.filter { it.syncStatus == SyncOutboxStatus.PENDING_UPLOAD }.map { it.mutationId }
                        }
                    }
                }
                .collect { (owner, eligibleIds) ->
                    if (owner != currentOwner) {
                        currentOwner = owner
                        seen = mutableSetOf()
                        // Existing pending work from a previous run is worth one sync.
                        if (eligibleIds.isNotEmpty()) runCatching { scheduler.requestSyncAfterLocalChange() }
                        seen.addAll(eligibleIds)
                        return@collect
                    }
                    val fresh = eligibleIds.filterNot { it in seen }
                    if (fresh.isNotEmpty()) {
                        seen.addAll(fresh)
                        runCatching { scheduler.requestSyncAfterLocalChange() }
                    }
                }
        }
    }
}
