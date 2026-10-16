package com.gpsstamp.camera.ultrawide

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resumeWithException

/**
 * Camera callbacks the session is waiting for. Closing the camera can mean a callback never arrives (a closed
 * ImageReader drops its frame, a quit HandlerThread drops onCaptureFailed), so [close] fails every waiter
 * instead of leaving it suspended forever, e.g. with the shutter stuck "capturing".
 */
class PendingCalls {
    private val lock = Any()
    private val pending = mutableSetOf<CancellableContinuation<*>>()
    private var closed = false

    val isClosed: Boolean get() = synchronized(lock) { closed }

    /** Suspends until [start]'s callback resumes the continuation, or [close] is called. */
    suspend fun <T> await(start: (CancellableContinuation<T>) -> Unit): T {
        var registered: CancellableContinuation<T>? = null
        try {
            return suspendCancellableCoroutine { cont ->
                val accepted = synchronized(lock) { if (closed) false else pending.add(cont) }
                if (!accepted) {
                    cont.resumeWithException(closedError())
                    return@suspendCancellableCoroutine
                }
                registered = cont
                start(cont)
            }
        } finally {
            registered?.let { cont -> synchronized(lock) { pending.remove(cont) } }
        }
    }

    fun close() {
        val waiting = synchronized(lock) {
            closed = true
            pending.toList().also { pending.clear() }
        }
        waiting.forEach { if (it.isActive) it.resumeWithException(closedError()) }
    }

    private fun closedError() = UltraWideUnavailable("Ultra-wide camera closed")
}
