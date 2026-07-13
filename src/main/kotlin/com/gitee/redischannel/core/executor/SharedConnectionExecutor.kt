package com.gitee.redischannel.core.executor

import com.gitee.redischannel.api.RedisLifecycleState
import com.gitee.redischannel.api.exception.RedisOperationException
import com.gitee.redischannel.api.exception.RedisUnavailableException
import com.gitee.redischannel.core.RedisMonitor
import com.gitee.redischannel.core.lifecycle.AsyncOperationGate
import com.gitee.redischannel.util.CompletionStages
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.Future
import java.util.function.Function

internal class SharedConnectionExecutor<A>(
    private val generation: Long,
    private val gate: AsyncOperationGate,
    private val asyncCommands: A
) {

    fun <T> executeAsync(action: Function<A, out CompletionStage<T>>): CompletableFuture<T> {
        if (!gate.tryEnter()) return CompletionStages.failed(RedisUnavailableException(RedisLifecycleState.STOPPING))
        val startedAt = System.nanoTime()
        val stage = try {
            action.apply(asyncCommands) ?: throw RedisOperationException("Redis Pub/Sub action 返回了 null CompletionStage")
        } catch (error: Throwable) {
            gate.leave()
            RedisMonitor.recordBusinessCommand(generation, false, elapsedMillis(startedAt))
            return CompletionStages.failed(error)
        }
        val result = CompletableFuture<T>()
        result.whenComplete { _, _ ->
            if (result.isCancelled && stage is Future<*>) stage.cancel(true)
        }
        stage.whenComplete { value, error ->
            val failure = error?.let(CompletionStages::unwrap)
            RedisMonitor.recordBusinessCommand(generation, failure == null, elapsedMillis(startedAt))
            gate.leave()
            if (!result.isDone) {
                if (failure == null) result.complete(value) else result.completeExceptionally(failure)
            }
        }
        return result
    }

    private fun elapsedMillis(startedAt: Long): Long {
        return (System.nanoTime() - startedAt).coerceAtLeast(0) / 1_000_000
    }
}
