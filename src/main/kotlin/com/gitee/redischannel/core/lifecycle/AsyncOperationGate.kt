package com.gitee.redischannel.core.lifecycle

import java.util.concurrent.CompletableFuture

internal class AsyncOperationGate {

    private val lock = Any()
    private var accepting = true
    private var inFlight = 0
    private var drained: CompletableFuture<Void>? = null

    fun tryEnter(): Boolean {
        synchronized(lock) {
            if (!accepting) return false
            inFlight++
            return true
        }
    }

    fun leave() {
        val complete: CompletableFuture<Void>?
        synchronized(lock) {
            if (inFlight > 0) inFlight--
            complete = if (!accepting && inFlight == 0) drained else null
        }
        complete?.complete(null)
    }

    fun stopAccepting(): CompletableFuture<Void> {
        synchronized(lock) {
            accepting = false
            if (inFlight == 0) return CompletableFuture.completedFuture(null)
            return drained ?: CompletableFuture<Void>().also { drained = it }
        }
    }

    fun isAccepting(): Boolean = synchronized(lock) { accepting }

    fun inFlight(): Int = synchronized(lock) { inFlight }
}
