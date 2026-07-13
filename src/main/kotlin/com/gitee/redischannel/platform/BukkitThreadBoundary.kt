package com.gitee.redischannel.platform

import taboolib.common.platform.function.submit
import taboolib.common.platform.service.PlatformExecutor
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal object BukkitThreadBoundary {

    fun runMain(action: () -> Unit): CompletableFuture<Void> {
        val result = CompletableFuture<Void>()
        val active = AtomicBoolean(true)
        val taskRef = AtomicReference<PlatformExecutor.PlatformTask?>()
        result.whenComplete { _, _ ->
            if (result.isCancelled && active.compareAndSet(true, false)) {
                taskRef.get()?.cancel()
            }
        }
        try {
            val task = submit {
                if (!active.compareAndSet(true, false)) return@submit
                try {
                    action()
                    result.complete(null)
                } catch (error: Throwable) {
                    result.completeExceptionally(error)
                }
            }
            taskRef.set(task)
            if (result.isCancelled) task.cancel()
        } catch (error: Throwable) {
            active.set(false)
            result.completeExceptionally(error)
        }
        return result
    }
}
