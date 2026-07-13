package com.gitee.redischannel.platform

import org.bukkit.Bukkit
import taboolib.common.platform.function.submit
import taboolib.common.platform.service.PlatformExecutor
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal object BukkitThreadBoundary {

    fun runMain(action: () -> Unit): CompletableFuture<Void> {
        if (Bukkit.isPrimaryThread()) {
            return execute(action)
        }
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

    private fun execute(action: () -> Unit): CompletableFuture<Void> {
        return try {
            action()
            CompletableFuture.completedFuture(null)
        } catch (error: Throwable) {
            CompletableFuture<Void>().apply { completeExceptionally(error) }
        }
    }
}
