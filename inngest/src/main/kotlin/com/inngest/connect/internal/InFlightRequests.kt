package com.inngest.connect.internal

/**
 * Tracks executor requests owned by this worker.
 *
 * Counting starts when a request is accepted for processing (enqueue time,
 * before the ACK), so graceful shutdown's drain gate also covers queued work
 * that has not started executing yet — see plan 010, hazard 9. Lease
 * bookkeeping is added with the request processor milestone.
 */
internal class InFlightRequests {
    private val lock = Object()
    private var count = 0

    fun increment() {
        synchronized(lock) { count++ }
    }

    fun decrement() {
        synchronized(lock) {
            check(count > 0) { "in-flight count underflow" }
            count--
            if (count == 0) {
                lock.notifyAll()
            }
        }
    }

    fun isEmpty(): Boolean = synchronized(lock) { count == 0 }

    fun count(): Int = synchronized(lock) { count }

    /** Blocks until no requests are in flight. */
    fun awaitEmpty() {
        synchronized(lock) {
            while (count > 0) {
                lock.wait()
            }
        }
    }
}
