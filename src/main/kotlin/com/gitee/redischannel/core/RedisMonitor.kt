package com.gitee.redischannel.core

import com.gitee.redischannel.api.RedisDeploymentMode
import com.gitee.redischannel.api.RedisLifecycleSnapshot
import com.gitee.redischannel.api.RedisLifecycleState
import com.gitee.redischannel.api.exception.RedisOperationException
import com.gitee.redischannel.core.lifecycle.RedisLifecycleCoordinator
import com.gitee.redischannel.core.runtime.RedisRuntime
import com.gitee.redischannel.util.CompletionStages
import taboolib.common.platform.Schedule
import taboolib.common.platform.function.info
import taboolib.common.platform.function.warning
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.LongAdder
import kotlin.time.toJavaDuration

/**
 * Redis 连接状态与业务命令监控。
 */
object RedisMonitor {

    private const val MAX_LATENCY_HISTORY = 100
    private val metrics = AtomicReference(MetricBucket(0))
    private val connection = AtomicReference(ConnectionState(0, ConnectionStatus.DISCONNECTED, -1))
    private val healthCheck = AtomicReference(HealthCheckState(0, null, 0))

    val lastPingLatency: Long
        get() = connection.get().latency

    val connectionStatus: ConnectionStatus
        get() = connection.get().status

    enum class ConnectionStatus(val languageKey: String, val color: String) {
        CONNECTED("connection-status.connected", "§a"),
        DISCONNECTED("connection-status.disconnected", "§c"),
        CONNECTING("connection-status.connecting", "§e"),
        ERROR("connection-status.error", "§4")
    }

    internal fun beginGeneration(runtime: RedisRuntime) {
        metrics.set(MetricBucket(runtime.generation))
        connection.set(ConnectionState(runtime.generation, ConnectionStatus.CONNECTED, -1))
        healthCheck.set(HealthCheckState(runtime.generation, null, 0))
    }

    internal fun onStopped(generation: Long) {
        updateConnection(generation, ConnectionStatus.DISCONNECTED, -1)
    }

    internal fun onFailed(generation: Long) {
        while (true) {
            val current = connection.get()
            if (current.generation > generation) return
            if (connection.compareAndSet(current, ConnectionState(generation, ConnectionStatus.ERROR, -1))) return
        }
    }

    fun recordBusinessCommand(generation: Long, success: Boolean, latencyMs: Long) {
        val bucket = metrics.get()
        if (bucket.generation != generation) return
        bucket.commandCount.increment()
        if (success) {
            bucket.successCount.increment()
            bucket.recordLatency(latencyMs)
        } else {
            bucket.failCount.increment()
        }
    }

    fun getSnapshot(): MonitorSnapshot {
        val context = runtimeContext()
        return localSnapshot(context.lifecycle, context.runtime)
    }

    fun statusAsync(): CompletableFuture<MonitorSnapshot> {
        val context = runtimeContext()
        val runtime = context.runtime
            ?: return CompletableFuture.completedFuture(localSnapshot(context.lifecycle, null))
        val timeout = runtime.config.lifecycle.statusTimeout.toJavaDuration()
        val ping = remoteValue(timeout, "Redis PING 超时") { runtime.pingAsync() }
        return ping.thenCompose { pingResult ->
            if (pingResult.error != null) {
                CompletableFuture.completedFuture(pingResult to RemoteValue<String>(null, null))
            } else {
                remoteValue(timeout, "Redis INFO 超时") { runtime.serverInfoAsync() }
                    .thenApply { infoResult -> pingResult to infoResult }
            }
        }.thenApply { results ->
            val pingResult = results.first
            val infoResult = results.second
            val current = runtimeContext()
            if (current.runtime !== runtime) {
                return@thenApply localSnapshot(current.lifecycle, current.runtime)
            }
            if (pingResult.error == null) {
                updateConnection(runtime.generation, ConnectionStatus.CONNECTED, pingResult.value ?: -1)
            } else if (!isLocalPoolFailure(pingResult.error)) {
                updateConnection(runtime.generation, ConnectionStatus.ERROR, -1)
            }
            val base = localSnapshot(current.lifecycle, current.runtime)
            base.copy(
                pingLatency = pingResult.value ?: base.pingLatency,
                serverInfo = infoResult.value?.let(::parseServerInfo),
                remoteError = pingResult.error?.message ?: infoResult.error?.message
            )
        }
    }

    private fun runtimeContext(): RuntimeContext {
        while (true) {
            val before = RedisLifecycleCoordinator.lifecycle()
            val runtime = RedisLifecycleCoordinator.runtime()
            val after = RedisLifecycleCoordinator.lifecycle()
            if (before === after &&
                (after.state != RedisLifecycleState.RUNNING || runtime?.generation == after.generation)) {
                return RuntimeContext(after, runtime)
            }
        }
    }

    private fun <T> remoteValue(
        timeout: Duration,
        message: String,
        supplier: () -> CompletionStage<T>
    ): CompletableFuture<RemoteValue<T>> {
        val stage = try {
            supplier()
        } catch (error: Throwable) {
            return CompletableFuture.completedFuture(RemoteValue(null, CompletionStages.unwrap(error)))
        }
        val timed = try {
            CompletionStages.withTimeout(stage, timeout, message)
        } catch (error: Throwable) {
            return CompletableFuture.completedFuture(RemoteValue(null, CompletionStages.unwrap(error)))
        }
        return timed.handle { value, error -> RemoteValue(value, error?.let(CompletionStages::unwrap)) }
    }

    private fun localSnapshot(lifecycle: RedisLifecycleSnapshot, runtime: RedisRuntime?): MonitorSnapshot {
        val bucket = metrics.get()
        val currentConnection = connection.get()
        return MonitorSnapshot(
            status = lifecycleStatus(lifecycle, currentConnection),
            lifecycleState = lifecycle.state,
            mode = lifecycle.mode,
            uptime = lifecycle.startedAt?.let { Duration.between(it, Instant.now()) },
            pingLatency = currentConnection.latency,
            avgLatency = bucket.averageLatency(),
            commandCount = bucket.commandCount.sum(),
            successCount = bucket.successCount.sum(),
            failCount = bucket.failCount.sum(),
            poolStats = runtime?.poolStats(),
            serverInfo = null,
            deploymentInfo = runtime?.let(::deploymentInfo),
            remoteError = lifecycle.failureMessage
        )
    }

    private fun lifecycleStatus(
        lifecycle: RedisLifecycleSnapshot,
        currentConnection: ConnectionState
    ): ConnectionStatus {
        return when (lifecycle.state) {
            RedisLifecycleState.RUNNING -> {
                if (currentConnection.generation == lifecycle.generation) currentConnection.status
                else ConnectionStatus.CONNECTING
            }
            RedisLifecycleState.STARTING, RedisLifecycleState.RECONNECTING, RedisLifecycleState.STOPPING -> ConnectionStatus.CONNECTING
            RedisLifecycleState.FAILED -> ConnectionStatus.ERROR
            RedisLifecycleState.STOPPED -> ConnectionStatus.DISCONNECTED
        }
    }

    private fun deploymentInfo(runtime: RedisRuntime): DeploymentInfo {
        val config = runtime.config
        return DeploymentInfo(
            isSentinel = config.enableSentinel,
            sentinelMasterId = config.sentinel?.masterId,
            sentinelNodes = config.sentinel?.nodes?.map { "${it.host}:${it.port}" },
            isSlaves = config.enableSlaves,
            readFrom = config.slaves?.readFrom?.toString(),
            isCluster = runtime.mode == RedisDeploymentMode.CLUSTER,
            clusterNodeCount = config.cluster?.nodes?.size
        )
    }

    internal fun parseServerInfo(raw: String?): ServerInfo? {
        if (raw == null) return null
        val values = raw.lineSequence()
            .filter { ':' in it }
            .associate { line ->
                val split = line.split(':', limit = 2)
                split[0].trim() to split.getOrElse(1) { "" }.trim()
            }
        return ServerInfo(
            redisVersion = values["redis_version"],
            os = values["os"],
            uptimeSeconds = values["uptime_in_seconds"]?.toLongOrNull(),
            connectedClients = values["connected_clients"]?.toIntOrNull(),
            usedMemory = values["used_memory_human"],
            usedMemoryPeak = values["used_memory_peak_human"]
        )
    }

    @Schedule(period = 20, async = true)
    fun healthCheck() {
        val context = runtimeContext()
        val runtime = context.runtime ?: return
        if (context.lifecycle.state != RedisLifecycleState.RUNNING) return
        val now = System.nanoTime()
        val token = Any()
        while (true) {
            val current = healthCheck.get()
            if (current.generation != runtime.generation || current.token != null) return
            if (current.nextCheckNanos != 0L && now - current.nextCheckNanos < 0) return
            val next = HealthCheckState(
                generation = runtime.generation,
                token = token,
                nextCheckNanos = now + runtime.config.lifecycle.healthCheckPeriodNanos
            )
            if (healthCheck.compareAndSet(current, next)) break
        }
        val pingStage = try {
            runtime.pingAsync()
        } catch (error: Throwable) {
            clearHealthCheck(runtime.generation, token)
            handleHealthResult(runtime, null, error)
            return
        }
        pingStage.whenComplete { _, _ -> clearHealthCheck(runtime.generation, token) }
        val timed = try {
            CompletionStages.withTimeout(
                pingStage,
                runtime.config.lifecycle.statusTimeout.toJavaDuration(),
                "Redis 健康检查超时"
            )
        } catch (error: Throwable) {
            CompletionStages.failed<Long>(error)
        }
        timed.whenComplete { latency, error -> handleHealthResult(runtime, latency, error) }
    }

    private fun clearHealthCheck(generation: Long, token: Any) {
        while (true) {
            val current = healthCheck.get()
            if (current.generation != generation || current.token !== token) return
            if (healthCheck.compareAndSet(current, current.copy(token = null))) return
        }
    }

    private fun handleHealthResult(runtime: RedisRuntime, latency: Long?, error: Throwable?) {
        val context = runtimeContext()
        if (context.runtime !== runtime || context.lifecycle.state != RedisLifecycleState.RUNNING) return
        if (error == null) {
            val previous = updateConnection(runtime.generation, ConnectionStatus.CONNECTED, latency ?: -1)
            if (previous != null && previous != ConnectionStatus.CONNECTED) info("Redis 连接已恢复")
            return
        }
        val failure = CompletionStages.unwrap(error)
        if (!isLocalPoolFailure(failure)) {
            val previous = updateConnection(runtime.generation, ConnectionStatus.ERROR, -1)
            if (previous == ConnectionStatus.CONNECTED) {
                warning("Redis 健康检查失败: ${failure.message}")
            }
        }
    }

    private fun updateConnection(
        generation: Long,
        status: ConnectionStatus,
        latency: Long?
    ): ConnectionStatus? {
        while (true) {
            val current = connection.get()
            if (current.generation != generation) return null
            val next = ConnectionState(generation, status, latency ?: current.latency)
            if (connection.compareAndSet(current, next)) return current.status
        }
    }

    private fun isLocalPoolFailure(error: Throwable): Boolean {
        return error is RedisOperationException && error.message?.contains("获取 Redis 异步连接") == true
    }

    private data class ConnectionState(
        val generation: Long,
        val status: ConnectionStatus,
        val latency: Long
    )

    private data class HealthCheckState(
        val generation: Long,
        val token: Any?,
        val nextCheckNanos: Long
    )

    private data class RuntimeContext(
        val lifecycle: RedisLifecycleSnapshot,
        val runtime: RedisRuntime?
    )

    private class MetricBucket(val generation: Long) {
        val commandCount = LongAdder()
        val successCount = LongAdder()
        val failCount = LongAdder()
        private val latencies = AtomicLongArray(MAX_LATENCY_HISTORY)
        private val cursor = AtomicLong(0)
        private val count = AtomicInteger(0)

        fun recordLatency(value: Long) {
            if (value < 0) return
            val position = cursor.getAndIncrement()
            latencies.set((position % MAX_LATENCY_HISTORY).toInt(), value)
            while (true) {
                val current = count.get()
                if (current >= MAX_LATENCY_HISTORY || count.compareAndSet(current, current + 1)) break
            }
        }

        fun averageLatency(): Long {
            val size = count.get()
            if (size == 0) return -1
            var total = 0L
            for (index in 0 until size) total += latencies.get(index)
            return total / size
        }
    }

    private data class RemoteValue<T>(val value: T?, val error: Throwable?)

    data class MonitorSnapshot(
        val status: ConnectionStatus,
        val lifecycleState: RedisLifecycleState,
        val mode: RedisDeploymentMode?,
        val uptime: Duration?,
        val pingLatency: Long,
        val avgLatency: Long,
        val commandCount: Long,
        val successCount: Long,
        val failCount: Long,
        val poolStats: PoolStats?,
        val serverInfo: ServerInfo?,
        val deploymentInfo: DeploymentInfo?,
        val remoteError: String?
    ) {
        val successRate: Double
            get() = if (commandCount > 0) successCount.toDouble() / commandCount * 100 else 100.0
    }

    data class PoolStats(
        val active: Int,
        val idle: Int,
        val maxTotal: Int,
        val waiters: Int
    ) {
        val utilization: Double
            get() = if (maxTotal > 0) active.toDouble() / maxTotal * 100 else 0.0
    }

    data class ServerInfo(
        val redisVersion: String?,
        val os: String?,
        val uptimeSeconds: Long?,
        val connectedClients: Int?,
        val usedMemory: String?,
        val usedMemoryPeak: String?
    )

    data class DeploymentInfo(
        val isSentinel: Boolean,
        val sentinelMasterId: String? = null,
        val sentinelNodes: List<String>? = null,
        val isSlaves: Boolean,
        val readFrom: String? = null,
        val isCluster: Boolean,
        val clusterNodeCount: Int? = null
    )
}
