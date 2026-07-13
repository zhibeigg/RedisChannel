package com.gitee.redischannel.core.executor

import com.gitee.redischannel.api.RedisLifecycleState
import com.gitee.redischannel.api.exception.RedisOperationException
import com.gitee.redischannel.api.exception.RedisUnavailableException
import com.gitee.redischannel.core.RedisMonitor
import com.gitee.redischannel.core.lifecycle.AsyncOperationGate
import com.gitee.redischannel.util.CompletionStages
import io.lettuce.core.support.BoundedAsyncPool
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Function

internal class AsyncPoolExecutor<C, A>(
    private val generation: Long,
    private val pool: BoundedAsyncPool<C>,
    private val gate: AsyncOperationGate,
    private val asyncCommands: (C) -> A
) {

    fun <T> executeAsync(
        action: Function<A, out CompletionStage<T>>,
        recordMetrics: Boolean = true
    ): CompletableFuture<T> {
        if (!gate.tryEnter()) {
            return CompletionStages.failed(RedisUnavailableException(RedisLifecycleState.STOPPING))
        }
        val startedAt = System.nanoTime()
        val result = CompletableFuture<T>()
        val commandStageRef = AtomicReference<CompletionStage<T>?>()
        val acquireStage = try {
            pool.acquire()
        } catch (error: Throwable) {
            completeFailure(
                result,
                startedAt,
                RedisOperationException("无法获取 Redis 异步连接", CompletionStages.unwrap(error)),
                recordMetrics
            )
            return result
        }
        result.whenComplete { _, _ ->
            if (result.isCancelled) {
                acquireStage.cancel(true)
                val commandStage = commandStageRef.get()
                if (commandStage is Future<*>) commandStage.cancel(true)
            }
        }
        acquireStage.whenComplete { connection, acquireError ->
            if (acquireError != null) {
                completeFailure(
                    result,
                    startedAt,
                    RedisOperationException("无法获取 Redis 异步连接", CompletionStages.unwrap(acquireError)),
                    recordMetrics
                )
                return@whenComplete
            }
            if (result.isCancelled) {
                try {
                    pool.release(connection).whenComplete { _, _ -> gate.leave() }
                } catch (_: Throwable) {
                    gate.leave()
                }
                return@whenComplete
            }
            val commandStage = try {
                action.apply(asyncCommands(connection))
                    ?: throw RedisOperationException("Redis 异步 action 返回了 null CompletionStage")
            } catch (error: Throwable) {
                releaseThenComplete(result, connection, startedAt, null, error, recordMetrics)
                return@whenComplete
            }
            commandStageRef.set(commandStage)
            if (result.isCancelled && commandStage is Future<*>) commandStage.cancel(true)
            commandStage.whenComplete { value, commandError ->
                releaseThenComplete(result, connection, startedAt, value, commandError, recordMetrics)
            }
        }
        return result
    }

    private fun <T> releaseThenComplete(
        result: CompletableFuture<T>,
        connection: C,
        startedAt: Long,
        value: T?,
        commandError: Throwable?,
        recordMetrics: Boolean
    ) {
        val releaseStage = try {
            pool.release(connection)
        } catch (releaseError: Throwable) {
            val commandFailure = commandError?.let(CompletionStages::unwrap)
            val releaseFailure = CompletionStages.unwrap(releaseError)
            if (commandFailure != null && releaseFailure !== commandFailure) commandFailure.addSuppressed(releaseFailure)
            val failure = commandFailure ?: releaseFailure
            if (recordMetrics) RedisMonitor.recordBusinessCommand(generation, false, elapsedMillis(startedAt))
            gate.leave()
            result.completeExceptionally(failure)
            return
        }
        releaseStage.whenComplete { _, releaseError ->
            val commandFailure = commandError?.let(CompletionStages::unwrap)
            val releaseFailure = releaseError?.let(CompletionStages::unwrap)
            val failure = commandFailure ?: releaseFailure
            if (commandFailure != null && releaseFailure != null && releaseFailure !== commandFailure) {
                commandFailure.addSuppressed(releaseFailure)
            }
            if (recordMetrics) {
                RedisMonitor.recordBusinessCommand(generation, failure == null, elapsedMillis(startedAt))
            }
            gate.leave()
            if (failure == null) {
                @Suppress("UNCHECKED_CAST")
                result.complete(value as T)
            } else {
                result.completeExceptionally(failure)
            }
        }
    }

    private fun <T> completeFailure(
        result: CompletableFuture<T>,
        startedAt: Long,
        error: Throwable,
        recordMetrics: Boolean
    ) {
        if (recordMetrics) RedisMonitor.recordBusinessCommand(generation, false, elapsedMillis(startedAt))
        gate.leave()
        result.completeExceptionally(error)
    }

    private fun elapsedMillis(startedAt: Long): Long {
        return (System.nanoTime() - startedAt).coerceAtLeast(0) / 1_000_000
    }
}
