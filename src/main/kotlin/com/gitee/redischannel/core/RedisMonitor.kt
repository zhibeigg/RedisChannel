package com.gitee.redischannel.core

import com.gitee.redischannel.RedisChannelPlugin
import com.gitee.redischannel.RedisChannelPlugin.Type.CLUSTER
import com.gitee.redischannel.RedisChannelPlugin.Type.SINGLE
import taboolib.common.platform.Schedule
import taboolib.common.platform.function.info
import taboolib.common.platform.function.warning
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicLong

/**
 * Redis 连接状态监控
 */
object RedisMonitor {

    /** 连接启动时间 */
    var startTime: Instant? = null
        private set

    /** 命令执行统计 */
    private val commandCount = AtomicLong(0)
    private val commandSuccessCount = AtomicLong(0)
    private val commandFailCount = AtomicLong(0)

    /** 最近的延迟记录 (保留最近100条) */
    private val latencyHistory = ConcurrentLinkedDeque<Long>()
    private const val MAX_LATENCY_HISTORY = 100

    /** 最后一次 PING 延迟 (毫秒) */
    @Volatile
    var lastPingLatency: Long = -1
        private set

    /** 连接状态 */
    @Volatile
    var connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED
        private set

    enum class ConnectionStatus(val display: String, val color: String) {
        CONNECTED("已连接", "§a"),
        DISCONNECTED("未连接", "§c"),
        CONNECTING("连接中", "§e"),
        ERROR("异常", "§4")
    }

    /**
     * 记录连接启动
     */
    internal fun onConnected() {
        startTime = Instant.now()
        connectionStatus = ConnectionStatus.CONNECTED
        commandCount.set(0)
        commandSuccessCount.set(0)
        commandFailCount.set(0)
        latencyHistory.clear()
    }

    /**
     * 记录连接断开
     */
    internal fun onDisconnected() {
        connectionStatus = ConnectionStatus.DISCONNECTED
        startTime = null
        lastPingLatency = -1
    }

    /**
     * 记录命令执行
     */
    fun recordCommand(success: Boolean, latencyMs: Long = 0) {
        commandCount.incrementAndGet()
        if (success) {
            commandSuccessCount.incrementAndGet()
            if (latencyMs > 0) {
                latencyHistory.addLast(latencyMs)
                while (latencyHistory.size > MAX_LATENCY_HISTORY) {
                    latencyHistory.pollFirst()
                }
            }
        } else {
            commandFailCount.incrementAndGet()
        }
    }

    /**
     * 获取监控快照
     */
    fun getSnapshot(): MonitorSnapshot {
        val poolStats = getPoolStats()
        val serverInfo = getServerInfo()
        val deploymentInfo = getDeploymentInfo()

        return MonitorSnapshot(
            status = connectionStatus,
            mode = RedisChannelPlugin.type,
            uptime = startTime?.let { Duration.between(it, Instant.now()) },
            pingLatency = lastPingLatency,
            avgLatency = if (latencyHistory.isNotEmpty()) latencyHistory.average().toLong() else -1,
            commandCount = commandCount.get(),
            successCount = commandSuccessCount.get(),
            failCount = commandFailCount.get(),
            poolStats = poolStats,
            serverInfo = serverInfo,
            deploymentInfo = deploymentInfo
        )
    }

    /**
     * 获取连接池统计
     */
    private fun getPoolStats(): PoolStats? {
        return try {
            when (RedisChannelPlugin.type) {
                SINGLE -> {
                    if (RedisManager.enabledSlaves) {
                        PoolStats(
                            active = RedisManager.masterReplicaPool.numActive,
                            idle = RedisManager.masterReplicaPool.numIdle,
                            maxTotal = RedisManager.masterReplicaPool.maxTotal,
                            waiters = RedisManager.masterReplicaPool.numWaiters
                        )
                    } else {
                        PoolStats(
                            active = RedisManager.pool.numActive,
                            idle = RedisManager.pool.numIdle,
                            maxTotal = RedisManager.pool.maxTotal,
                            waiters = RedisManager.pool.numWaiters
                        )
                    }
                }
                CLUSTER -> {
                    PoolStats(
                        active = ClusterRedisManager.pool.numActive,
                        idle = ClusterRedisManager.pool.numIdle,
                        maxTotal = ClusterRedisManager.pool.maxTotal,
                        waiters = ClusterRedisManager.pool.numWaiters
                    )
                }
                null -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 获取 Redis 服务器信息
     */
    private fun getServerInfo(): ServerInfo? {
        if (connectionStatus != ConnectionStatus.CONNECTED) return null

        return try {
            when (RedisChannelPlugin.type) {
                SINGLE -> {
                    RedisManager.useCommands { cmd ->
                        parseServerInfo(cmd.info())
                    }
                }
                CLUSTER -> {
                    ClusterRedisManager.useCommands { cmd ->
                        parseServerInfo(cmd.info())
                    }
                }
                null -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseServerInfo(info: String?): ServerInfo? {
        if (info == null) return null
        val map = info.lines()
            .filter { it.contains(":") }
            .associate {
                val parts = it.split(":", limit = 2)
                parts[0].trim() to parts.getOrElse(1) { "" }.trim()
            }

        return ServerInfo(
            redisVersion = map["redis_version"],
            os = map["os"],
            uptimeSeconds = map["uptime_in_seconds"]?.toLongOrNull(),
            connectedClients = map["connected_clients"]?.toIntOrNull(),
            usedMemory = map["used_memory_human"],
            usedMemoryPeak = map["used_memory_peak_human"]
        )
    }

    /**
     * 获取部署模式信息
     */
    private fun getDeploymentInfo(): DeploymentInfo? {
        return try {
            val redis = RedisChannelPlugin.redis

            when (RedisChannelPlugin.type) {
                SINGLE -> {
                    val isSentinel = redis.enableSentinel
                    val isSlaves = redis.enableSlaves

                    DeploymentInfo(
                        isSentinel = isSentinel,
                        sentinelMasterId = if (isSentinel) redis.sentinel.masterId else null,
                        sentinelNodes = if (isSentinel) redis.sentinel.nodes.map { "${it.host}:${it.port}" } else null,
                        isSlaves = isSlaves,
                        readFrom = if (isSlaves) redis.slaves.readFrom.toString() else null,
                        isCluster = false,
                        clusterNodeCount = null
                    )
                }
                CLUSTER -> {
                    val isSlaves = redis.enableSlaves

                    DeploymentInfo(
                        isSentinel = false,
                        sentinelMasterId = null,
                        sentinelNodes = null,
                        isSlaves = isSlaves,
                        readFrom = if (isSlaves) redis.slaves.readFrom.toString() else null,
                        isCluster = true,
                        clusterNodeCount = redis.cluster.nodes.size
                    )
                }
                null -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 定时健康检查
     */
    @Schedule(period = 100, async = true)
    fun healthCheck() {
        if (RedisChannelPlugin.type == null || connectionStatus == ConnectionStatus.DISCONNECTED) return

        try {
            val start = System.currentTimeMillis()
            val pong = when (RedisChannelPlugin.type) {
                SINGLE -> RedisManager.useCommands { it.ping() }
                CLUSTER -> ClusterRedisManager.useCommands { it.ping() }
                else -> return
            }
            val latency = System.currentTimeMillis() - start

            if (pong == "PONG") {
                lastPingLatency = latency
                if (connectionStatus != ConnectionStatus.CONNECTED) {
                    connectionStatus = ConnectionStatus.CONNECTED
                    info("Redis 连接已恢复")
                }
            } else {
                connectionStatus = ConnectionStatus.ERROR
            }
        } catch (e: Exception) {
            if (connectionStatus == ConnectionStatus.CONNECTED) {
                connectionStatus = ConnectionStatus.ERROR
                warning("Redis 健康检查失败: ${e.message}")
            }
        }
    }

    /**
     * 监控数据快照
     */
    data class MonitorSnapshot(
        val status: ConnectionStatus,
        val mode: RedisChannelPlugin.Type?,
        val uptime: Duration?,
        val pingLatency: Long,
        val avgLatency: Long,
        val commandCount: Long,
        val successCount: Long,
        val failCount: Long,
        val poolStats: PoolStats?,
        val serverInfo: ServerInfo?,
        val deploymentInfo: DeploymentInfo?
    ) {
        val successRate: Double
            get() = if (commandCount > 0) (successCount.toDouble() / commandCount * 100) else 100.0
    }

    /**
     * 连接池统计
     */
    data class PoolStats(
        val active: Int,
        val idle: Int,
        val maxTotal: Int,
        val waiters: Int
    ) {
        val utilization: Double
            get() = if (maxTotal > 0) (active.toDouble() / maxTotal * 100) else 0.0
    }

    /**
     * Redis 服务器信息
     */
    data class ServerInfo(
        val redisVersion: String?,
        val os: String?,
        val uptimeSeconds: Long?,
        val connectedClients: Int?,
        val usedMemory: String?,
        val usedMemoryPeak: String?
    )

    /**
     * 部署模式信息
     */
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
