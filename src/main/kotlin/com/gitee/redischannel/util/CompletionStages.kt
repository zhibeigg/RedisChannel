package com.gitee.redischannel.util

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier

internal object CompletionStages {

    private val schedulerRef = AtomicReference(createScheduler())

    private fun createScheduler(): ScheduledExecutorService {
        return Executors.newSingleThreadScheduledExecutor(ThreadFactory { runnable ->
            Thread(runnable, "RedisChannel-Timeout").apply {
                isDaemon = true
                contextClassLoader = ClassLoader.getSystemClassLoader()
            }
        })
    }

    private fun scheduler(): ScheduledExecutorService {
        while (true) {
            val current = schedulerRef.get()
            if (!current.isShutdown) return current
            val replacement = createScheduler()
            if (schedulerRef.compareAndSet(current, replacement)) return replacement
            replacement.shutdownNow()
        }
    }

    fun <T> completed(value: T): CompletableFuture<T> = CompletableFuture.completedFuture(value)

    fun <T> failed(error: Throwable): CompletableFuture<T> {
        return CompletableFuture<T>().apply { completeExceptionally(unwrap(error)) }
    }

    fun unwrap(error: Throwable): Throwable {
        var current = error
        while (current is CompletionException) {
            val cause = current.cause ?: break
            current = cause
        }
        return current
    }

    fun <T> withTimeout(stage: CompletionStage<T>, timeout: Duration, message: String): CompletableFuture<T> {
        val result = CompletableFuture<T>()
        val timedOut = AtomicBoolean(false)
        val timeoutTask = scheduler().schedule(
            {
                if (!result.isDone) {
                    timedOut.set(true)
                    if (stage is Future<*>) stage.cancel(true)
                    result.completeExceptionally(TimeoutException(message))
                }
            },
            timeout.toMillis().coerceAtLeast(1),
            TimeUnit.MILLISECONDS
        )
        result.whenComplete { _, _ ->
            if (result.isCancelled && stage is Future<*>) stage.cancel(true)
        }
        stage.whenComplete { value, error ->
            timeoutTask.cancel(false)
            if (timedOut.get()) return@whenComplete
            if (error == null) {
                result.complete(value)
            } else {
                result.completeExceptionally(unwrap(error))
            }
        }
        return result
    }

    fun shutdownScheduler() {
        schedulerRef.get().shutdownNow()
    }

    fun runAll(stages: List<Supplier<out CompletionStage<Void>>>): CompletableFuture<Void> {
        val result = CompletableFuture<Void>()
        runNext(stages, 0, null, result)
        return result
    }

    private fun runNext(
        stages: List<Supplier<out CompletionStage<Void>>>,
        index: Int,
        firstError: Throwable?,
        result: CompletableFuture<Void>
    ) {
        if (index >= stages.size) {
            if (firstError == null) result.complete(null) else result.completeExceptionally(firstError)
            return
        }
        val stage = try {
            stages[index].get()
        } catch (error: Throwable) {
            runNext(stages, index + 1, firstError ?: unwrap(error), result)
            return
        }
        stage.whenComplete { _, error ->
            val unwrapped = error?.let(::unwrap)
            val retained = firstError ?: unwrapped
            if (firstError != null && unwrapped != null && unwrapped !== firstError) {
                firstError.addSuppressed(unwrapped)
            }
            runNext(stages, index + 1, retained, result)
        }
    }
}
