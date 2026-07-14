package com.gitee.redischannel.core.lifecycle

import com.gitee.redischannel.RedisChannelBootstrap
import com.gitee.redischannel.api.RedisDeploymentMode
import com.gitee.redischannel.api.RedisLifecycleSnapshot
import com.gitee.redischannel.api.RedisLifecycleState
import com.gitee.redischannel.api.events.ClientStartEvent
import com.gitee.redischannel.api.events.ClientStopEvent
import com.gitee.redischannel.api.exception.RedisUnavailableException
import com.gitee.redischannel.core.RedisConfig
import com.gitee.redischannel.core.RedisMonitor
import com.gitee.redischannel.core.runtime.RedisRuntime
import com.gitee.redischannel.core.runtime.RedisRuntimeFactory
import com.gitee.redischannel.platform.BukkitThreadBoundary
import com.gitee.redischannel.util.CompletionStages
import org.bukkit.Bukkit
import taboolib.common.platform.function.severe
import taboolib.common.platform.function.warning
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier
import kotlin.time.toJavaDuration

internal object RedisLifecycleCoordinator {

    private val operationLock = Any()
    private val generation = AtomicLong(0)
    private val runtimeRef = AtomicReference<RedisRuntime?>()
    private val snapshotRef = AtomicReference(
        RedisLifecycleSnapshot(RedisLifecycleState.STOPPED, null, 0, null, null)
    )

    private var operationTail: CompletableFuture<RedisLifecycleSnapshot> =
        CompletableFuture.completedFuture(snapshotRef.get())

    fun lifecycle(): RedisLifecycleSnapshot = snapshotRef.get()

    fun runtime(): RedisRuntime? = runtimeRef.get()

    fun requireRuntime(): RedisRuntime {
        return runtimeRef.get() ?: throw RedisUnavailableException(snapshotRef.get().state)
    }

    fun startAsync(): CompletableFuture<RedisLifecycleSnapshot> = enqueue {
        if (runtimeRef.get() != null && snapshotRef.get().state == RedisLifecycleState.RUNNING) {
            return@enqueue CompletableFuture.completedFuture(snapshotRef.get())
        }
        loadAndStart(RedisLifecycleState.STARTING)
    }

    fun stopAsync(): CompletableFuture<RedisLifecycleSnapshot> = enqueue {
        val runtime = runtimeRef.get()
            ?: return@enqueue CompletableFuture.completedFuture(transition(RedisLifecycleState.STOPPED, null, null, null))
        transition(RedisLifecycleState.STOPPING, runtime.mode, runtime.generation, null)
        val result = CompletableFuture<RedisLifecycleSnapshot>()
        closeAndDetach(runtime).whenComplete { _, error ->
            if (error == null) {
                result.complete(transition(RedisLifecycleState.STOPPED, null, runtime.generation, null))
            } else {
                val failure = CompletionStages.unwrap(error)
                warning("关闭 Redis 资源时发生异常: ${failure.message}")
                transition(RedisLifecycleState.FAILED, null, runtime.generation, failure.message)
                result.completeExceptionally(failure)
            }
        }
        result
    }

    fun reconnectAsync(): CompletableFuture<RedisLifecycleSnapshot> = enqueue {
        val previous = runtimeRef.get()
        val nextGeneration = generation.incrementAndGet()
        transition(RedisLifecycleState.RECONNECTING, previous?.mode, nextGeneration, null)
        val result = CompletableFuture<RedisLifecycleSnapshot>()
        val closeStage = if (previous == null) CompletableFuture.completedFuture(null) else closeAndDetach(previous)
        closeStage.whenComplete { _, closeError ->
            if (closeError != null) {
                val failure = CompletionStages.unwrap(closeError)
                warning("重连前关闭旧 Redis 资源时发生异常: ${failure.message}")
                transition(RedisLifecycleState.FAILED, null, nextGeneration, failure.message)
                result.completeExceptionally(failure)
                return@whenComplete
            }
            loadAndStart(RedisLifecycleState.RECONNECTING, nextGeneration).whenComplete { snapshot, startError ->
                if (startError == null) result.complete(snapshot)
                else result.completeExceptionally(CompletionStages.unwrap(startError))
            }
        }
        result
    }

    private fun closeAndDetach(runtime: RedisRuntime): CompletableFuture<Void> {
        val result = CompletableFuture<Void>()
        val eventStage = try {
            fireStopEvent(runtime)
        } catch (error: Throwable) {
            CompletionStages.failed<Void>(error)
        }
        eventStage.whenComplete { _, eventError ->
            val closeStage = try {
                runtime.closeAsync(runtime.config.lifecycle.shutdownGracePeriod.toJavaDuration())
            } catch (error: Throwable) {
                CompletionStages.failed<Void>(error)
            }
            closeStage.whenComplete { _, closeError ->
                runtimeRef.compareAndSet(runtime, null)
                RedisMonitor.onStopped(runtime.generation)
                val failure = mergeFailures(eventError, closeError)
                if (failure == null) result.complete(null) else result.completeExceptionally(failure)
            }
        }
        return result
    }

    private fun mergeFailures(first: Throwable?, second: Throwable?): Throwable? {
        val primary = first?.let(CompletionStages::unwrap)
        val secondary = second?.let(CompletionStages::unwrap)
        if (primary != null && secondary != null && secondary !== primary) primary.addSuppressed(secondary)
        return primary ?: secondary
    }

    private fun loadAndStart(
        state: RedisLifecycleState,
        reservedGeneration: Long? = null
    ): CompletableFuture<RedisLifecycleSnapshot> {
        val selectedGeneration = reservedGeneration ?: generation.incrementAndGet()
        transition(state, null, selectedGeneration, null)
        val result = CompletableFuture<RedisLifecycleSnapshot>()
        val configStage = try {
            loadConfigAsync()
        } catch (error: Throwable) {
            CompletionStages.failed<RedisConfig>(error)
        }
        configStage.whenComplete { config, configError ->
            if (configError != null) {
                val failure = CompletionStages.unwrap(configError)
                RedisMonitor.onFailed(selectedGeneration)
                transition(RedisLifecycleState.FAILED, null, selectedGeneration, failure.message)
                severe("Redis 配置加载失败: ${failure.message}")
                result.completeExceptionally(failure)
                return@whenComplete
            }
            val startStage = try {
                startRuntime(config, state, selectedGeneration)
            } catch (error: Throwable) {
                CompletionStages.failed<RedisLifecycleSnapshot>(error)
            }
            startStage.whenComplete { snapshot, startError ->
                if (startError == null) result.complete(snapshot)
                else result.completeExceptionally(CompletionStages.unwrap(startError))
            }
        }
        return result
    }

    private fun startRuntime(
        config: RedisConfig,
        state: RedisLifecycleState,
        reservedGeneration: Long? = null
    ): CompletableFuture<RedisLifecycleSnapshot> {
        val newGeneration = reservedGeneration ?: generation.incrementAndGet()
        transition(state, null, newGeneration, null)
        val result = CompletableFuture<RedisLifecycleSnapshot>()
        val creationStage = try {
            RedisRuntimeFactory.createAsync(newGeneration, config)
        } catch (error: Throwable) {
            CompletionStages.failed<RedisRuntime>(error)
        }
        creationStage.whenComplete { runtime, error ->
            if (error != null) {
                failStart(result, newGeneration, CompletionStages.unwrap(error))
                return@whenComplete
            }
            val running = try {
                runtimeRef.set(runtime)
                RedisMonitor.beginGeneration(runtime)
                transition(
                    RedisLifecycleState.RUNNING,
                    runtime.mode,
                    runtime.generation,
                    null,
                    Instant.now()
                )
            } catch (publishError: Throwable) {
                runtimeRef.compareAndSet(runtime, null)
                val closeStage = try {
                    runtime.closeAsync(config.lifecycle.shutdownGracePeriod.toJavaDuration())
                } catch (closeError: Throwable) {
                    CompletionStages.failed<Void>(closeError)
                }
                closeStage.whenComplete { _, closeError ->
                    val failure = mergeFailures(publishError, closeError) ?: publishError
                    failStart(result, newGeneration, failure)
                }
                return@whenComplete
            }
            val eventStage = try {
                val dispatch = BukkitThreadBoundary.runMain {
                    val snapshot = snapshotRef.get()
                    if (runtimeRef.get() === runtime &&
                        snapshot.state == RedisLifecycleState.RUNNING &&
                        snapshot.generation == runtime.generation) {
                        Bukkit.getPluginManager().callEvent(
                            ClientStartEvent(runtime.mode == RedisDeploymentMode.CLUSTER)
                        )
                    }
                }
                CompletionStages.withTimeout(
                    dispatch,
                    config.lifecycle.statusTimeout.toJavaDuration(),
                    "等待 ClientStartEvent 主线程派发超时",
                    cancelSourceOnTimeout = false
                )
            } catch (eventError: Throwable) {
                CompletionStages.failed<Void>(eventError)
            }
            eventStage.whenComplete { _, eventError ->
                if (eventError != null) {
                    warning("触发 ClientStartEvent 时发生异常: ${CompletionStages.unwrap(eventError).message}")
                }
                result.complete(running)
            }
        }
        return result
    }

    private fun failStart(
        result: CompletableFuture<RedisLifecycleSnapshot>,
        generation: Long,
        failure: Throwable
    ) {
        RedisMonitor.onFailed(generation)
        transition(RedisLifecycleState.FAILED, null, generation, failure.message)
        severe("Redis 初始化失败: ${failure.message}")
        result.completeExceptionally(failure)
    }

    private fun fireStopEvent(runtime: RedisRuntime): CompletableFuture<Void> {
        val dispatch = BukkitThreadBoundary.runMain {
            if (runtimeRef.get() === runtime) {
                Bukkit.getPluginManager().callEvent(
                    ClientStopEvent(runtime.mode == RedisDeploymentMode.CLUSTER)
                )
            }
        }
        return CompletionStages.withTimeout(
            dispatch,
            runtime.config.lifecycle.statusTimeout.toJavaDuration(),
            "等待 ClientStopEvent 主线程派发超时",
            cancelSourceOnTimeout = false
        )
    }

    private fun loadConfigAsync(): CompletableFuture<RedisConfig> {
        return CompletableFuture.supplyAsync(Supplier { RedisChannelBootstrap.reloadConfigSnapshot() })
    }

    private fun enqueue(
        operation: () -> CompletionStage<RedisLifecycleSnapshot>
    ): CompletableFuture<RedisLifecycleSnapshot> {
        synchronized(operationLock) {
            val next = operationTail.handle { _, _ -> null }
                .thenCompose {
                    try {
                        operation().toCompletableFuture()
                    } catch (error: Throwable) {
                        CompletionStages.failed(error)
                    }
                }
                .toCompletableFuture()
            operationTail = next.handle { snapshot, _ -> snapshot ?: snapshotRef.get() }.toCompletableFuture()
            return next
        }
    }

    private fun transition(
        state: RedisLifecycleState,
        mode: RedisDeploymentMode?,
        generation: Long?,
        failureMessage: String?,
        startedAt: Instant? = if (state == RedisLifecycleState.RUNNING) Instant.now() else null
    ): RedisLifecycleSnapshot {
        val previous = snapshotRef.get()
        val snapshot = RedisLifecycleSnapshot(
            state = state,
            mode = mode,
            generation = generation ?: previous.generation,
            startedAt = startedAt,
            failureMessage = failureMessage
        )
        snapshotRef.set(snapshot)
        return snapshot
    }
}
