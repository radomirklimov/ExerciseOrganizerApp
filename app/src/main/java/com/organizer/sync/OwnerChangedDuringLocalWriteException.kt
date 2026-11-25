package com.organizer.sync

/** Thrown inside a local write transaction to roll back a stale-owner write. */
class OwnerChangedDuringLocalWriteException : RuntimeException()
