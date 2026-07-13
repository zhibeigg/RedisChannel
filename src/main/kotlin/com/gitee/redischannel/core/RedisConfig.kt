package com.gitee.redischannel.core

import com.gitee.redischannel.api.exception.RedisConfigurationException
import com.gitee.redischannel.util.files
import io.lettuce.core.ReadFrom
import io.lettuce.core.RedisURI
import io.lettuce.core.SslOptions
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions
import io.lettuce.core.support.BoundedPoolConfig
import taboolib.common.platform.function.getDataFolder
import taboolib.library.configuration.ConfigurationSection
import taboolib.module.configuration.Configuration
import java.io.File
import kotlin.time.Duration
import kotlin.time.toJavaDuration

class RedisConfig(private val section: ConfigurationSection) {

    val language = section.parent?.getString("language")?.takeIf { it.isNotBlank() } ?: "zh_CN"

    val host = requiredText(section, "host")
    val port = section.getInt("port", 6379)
    val password = section.getString("password")?.takeIf { it.isNotBlank() }
    val ssl = section.getBoolean("ssl")
    val truststorePassword = section.getString("truststorePassword")?.takeIf { it.isNotBlank() }
    val timeout = parsePositiveDuration(section.getString("timeout"), "redis.timeout")
    val database = section.getInt("database", 0)

    val ioThreadPoolSize = section.getInt("ioThreadPoolSize")
    val computationThreadPoolSize = section.getInt("computationThreadPoolSize")
    val autoReconnect = section.getBoolean("autoReconnect", true)
    val pingBeforeActivateConnection = section.getBoolean("pingBeforeActivateConnection", true)

    val pool = Pool(section.getConfigurationSection("pool"))
    val lifecycle = Lifecycle(section.getConfigurationSection("lifecycle"))
    val bukkit = BukkitOptions(section.parent?.getConfigurationSection("bukkit"))

    val enableSentinel = section.getBoolean("sentinel.enable", false)
    val enableSlaves = section.getBoolean("slaves.enable", false)
    val enableCluster = section.getBoolean("cluster.enable", false)

    init {
        if (enableCluster && enableSentinel) {
            throw RedisConfigurationException("Redis Cluster 与 Redis Sentinel 不能同时启用")
        }
    }

    val sentinel: Sentinel? = if (enableSentinel) {
        Sentinel(section.getConfigurationSection("sentinel")
            ?: throw RedisConfigurationException("sentinel.enable 为 true，但缺少 redis.sentinel 配置"))
    } else null

    val slaves: Slaves? = if (enableSlaves) {
        Slaves(section.getConfigurationSection("slaves")
            ?: throw RedisConfigurationException("slaves.enable 为 true，但缺少 redis.slaves 配置"))
    } else null

    val cluster: Cluster? = if (enableCluster) {
        Cluster(section.getConfigurationSection("cluster")
            ?: throw RedisConfigurationException("cluster.enable 为 true，但缺少 redis.cluster 配置"))
    } else null

    init {
        validatePort(port, "redis.port")
        requireRange(database, 0, 15, "redis.database")
        validateThreadPoolSize(ioThreadPoolSize, "redis.ioThreadPoolSize")
        validateThreadPoolSize(computationThreadPoolSize, "redis.computationThreadPoolSize")
        if (section.getConfigurationSection("asyncPool") != null) {
            throw RedisConfigurationException("redis.asyncPool 已在 API v2 删除，请迁移为 redis.pool")
        }
        if (enableCluster && database != 0) {
            throw RedisConfigurationException("Redis Cluster 仅支持 database: 0")
        }
        if (ssl || cluster?.nodes?.any { it.ssl } == true) validateTruststore()
    }

    val sslOptions: SslOptions
        get() = SslOptions.builder()
            .jdkSslProvider()
            .truststore(File(getDataFolder(), "default.jks"), truststorePassword)
            .build()

    fun redisURIBuilder(): RedisURI.Builder {
        val builder = RedisURI.builder()
            .withHost(host)
            .withPort(port)
            .withSsl(ssl)
            .withTimeout(timeout.toJavaDuration())
            .withDatabase(database)

        password?.toCharArray()?.let(builder::withPassword)
        sentinel?.let { sentinelConfig ->
            builder.withSentinelMasterId(sentinelConfig.masterId)
            sentinelConfig.nodes.forEach { node -> builder.withSentinel(node.host, node.port) }
        }
        return builder
    }

    private fun validateTruststore() {
        val truststore = File(getDataFolder(), "default.jks")
        if (!truststore.isFile || !truststore.canRead()) {
            throw RedisConfigurationException("SSL 已启用，但证书文件不可读: ${truststore.absolutePath}")
        }
    }

    class Pool(configuration: ConfigurationSection?) {
        val maxTotal = configuration?.getInt("maxTotal", 8) ?: 8
        val maxIdle = configuration?.getInt("maxIdle", 8) ?: 8
        val minIdle = configuration?.getInt("minIdle", 0) ?: 0

        init {
            if (maxTotal <= 0) throw RedisConfigurationException("redis.pool.maxTotal 必须大于 0")
            if (minIdle < 0 || maxIdle < 0 || minIdle > maxIdle || maxIdle > maxTotal) {
                throw RedisConfigurationException("Redis 连接池必须满足 0 <= minIdle <= maxIdle <= maxTotal")
            }
        }

        fun poolConfig(): BoundedPoolConfig = BoundedPoolConfig.builder()
            .maxTotal(maxTotal)
            .maxIdle(maxIdle)
            .minIdle(minIdle)
            .build()
    }

    class Lifecycle(configuration: ConfigurationSection?) {
        val shutdownGracePeriod = parsePositiveDuration(
            configuration?.getString("shutdownGracePeriod") ?: "10s",
            "redis.lifecycle.shutdownGracePeriod"
        )
        val healthCheckPeriod = parsePositiveDuration(
            configuration?.getString("healthCheckPeriod") ?: "5s",
            "redis.lifecycle.healthCheckPeriod"
        )
        val statusTimeout = parsePositiveDuration(
            configuration?.getString("statusTimeout") ?: "5s",
            "redis.lifecycle.statusTimeout"
        )

        val healthCheckPeriodNanos: Long = try {
            healthCheckPeriod.toJavaDuration().toNanos()
        } catch (error: ArithmeticException) {
            throw RedisConfigurationException("redis.lifecycle.healthCheckPeriod 数值过大", error)
        }

        init {
            if (healthCheckPeriod.inWholeMilliseconds < 1_000) {
                throw RedisConfigurationException("redis.lifecycle.healthCheckPeriod 不能小于 1 秒")
            }
            if (healthCheckPeriodNanos > Long.MAX_VALUE / 2) {
                throw RedisConfigurationException("redis.lifecycle.healthCheckPeriod 数值过大")
            }
        }
    }

    class BukkitOptions(configuration: ConfigurationSection?) {
        val blockLoginUntilReady = configuration?.getBoolean("blockLoginUntilReady", true) ?: true
    }

    class Sentinel(configuration: ConfigurationSection) {
        val masterId = requiredText(configuration, "masterId")
        val nodes = configuration.getStringList("nodes").map(::parseNodeAddress)

        init {
            if (nodes.isEmpty()) throw RedisConfigurationException("Redis Sentinel 至少需要一个节点")
        }

        data class Node(val host: String, val port: Int)

        companion object {
            internal fun parseNodeAddress(raw: String): Node {
                val value = raw.trim()
                val host: String
                val portText: String
                if (value.startsWith("[")) {
                    val boundary = value.lastIndexOf("]:")
                    if (boundary <= 1) throw RedisConfigurationException("哨兵节点格式错误: '$raw'，IPv6 请使用 [地址]:端口")
                    host = value.substring(1, boundary)
                    portText = value.substring(boundary + 2)
                } else {
                    val boundary = value.lastIndexOf(':')
                    if (boundary <= 0) throw RedisConfigurationException("哨兵节点格式错误: '$raw'，正确格式为 host:port")
                    host = value.substring(0, boundary)
                    portText = value.substring(boundary + 1)
                }
                val port = portText.toIntOrNull()
                    ?: throw RedisConfigurationException("哨兵节点端口无效: '$raw'")
                validatePort(port, "sentinel node")
                return Node(host, port)
            }
        }
    }

    class Slaves(configuration: ConfigurationSection) {
        val readFrom: ReadFrom = try {
            ReadFrom.valueOf(requiredText(configuration, "readFrom"))
        } catch (error: IllegalArgumentException) {
            throw RedisConfigurationException("redis.slaves.readFrom 无效: ${configuration.getString("readFrom")}", error)
        }
    }

    class Cluster(configuration: ConfigurationSection) {
        val nodes = files("clusters", "cluster0.yml") { file ->
            val loaded = Configuration.loadFromFile(file)
            if (loaded.getConfigurationSection("redis") != null) {
                throw RedisConfigurationException("集群节点 ${file.name} 不再支持外层 redis 节点，请将 host、port 等字段移到文件根级")
            }
            Node(loaded, file.name)
        }

        val enablePeriodicRefresh = configuration.getBoolean("enablePeriodicRefresh", false)
        val refreshPeriod = configuration.getString("refreshPeriod")?.let {
            parsePositiveDuration(it, "redis.cluster.refreshPeriod")
        }
        val enableAdaptiveRefreshTrigger = configuration.getEnumList(
            "enableAdaptiveRefreshTrigger",
            ClusterTopologyRefreshOptions.RefreshTrigger::class.java
        )
        val adaptiveRefreshTriggersTimeout = configuration.getString("adaptiveRefreshTriggersTimeout")?.let {
            parsePositiveDuration(it, "redis.cluster.adaptiveRefreshTriggersTimeout")
        }
        val refreshTriggersReconnectAttempts = configuration.getInt("refreshTriggersReconnectAttempts", 5)
        val dynamicRefreshSources = configuration.getBoolean("dynamicRefreshSources", true)
        val closeStaleConnections = configuration.getBoolean("closeStaleConnections", true)
        val maxRedirects = configuration.getInt("maxRedirects", 5)
        val validateClusterNodeMembership = configuration.getBoolean("validateClusterNodeMembership", true)

        init {
            if (nodes.isEmpty()) throw RedisConfigurationException("Redis Cluster 至少需要一个 clusters/*.yml 节点文件")
            if (refreshTriggersReconnectAttempts <= 0) {
                throw RedisConfigurationException("redis.cluster.refreshTriggersReconnectAttempts 必须大于 0")
            }
            if (maxRedirects <= 0) throw RedisConfigurationException("redis.cluster.maxRedirects 必须大于 0")
        }

        class Node(configuration: ConfigurationSection, source: String) {
            val host = requiredText(configuration, "host")
            val port = configuration.getInt("port", 6379)
            val password = configuration.getString("password")?.takeIf { it.isNotBlank() }
            val ssl = configuration.getBoolean("ssl")
            val timeout = parsePositiveDuration(configuration.getString("timeout"), "$source.timeout")
            val database = configuration.getInt("database", 0)

            init {
                validatePort(port, "$source.port")
                if (database != 0) throw RedisConfigurationException("Redis Cluster 节点 $source 仅支持 database: 0")
                if (configuration.getBoolean("sentinel.enable", false)) {
                    throw RedisConfigurationException("Redis Cluster 节点不支持嵌套 Sentinel 配置: $source")
                }
            }

            fun redisURIBuilder(): RedisURI.Builder {
                val builder = RedisURI.builder()
                    .withHost(host)
                    .withPort(port)
                    .withSsl(ssl)
                    .withTimeout(timeout.toJavaDuration())
                    .withDatabase(0)
                password?.toCharArray()?.let(builder::withPassword)
                return builder
            }
        }
    }

    companion object {
        private fun requiredText(configuration: ConfigurationSection, path: String): String {
            return configuration.getString(path)?.takeIf { it.isNotBlank() }
                ?: throw RedisConfigurationException("配置项 $path 不能为空")
        }

        private fun parsePositiveDuration(raw: String?, path: String): Duration {
            val value = raw ?: throw RedisConfigurationException("配置项 $path 不能为空")
            val duration = try {
                Duration.parse(value)
            } catch (error: IllegalArgumentException) {
                throw RedisConfigurationException("配置项 $path 的时间格式无效: $value", error)
            }
            if (!duration.isFinite()) throw RedisConfigurationException("配置项 $path 不支持无限时间")
            if (!duration.isPositive()) throw RedisConfigurationException("配置项 $path 必须大于 0")
            try {
                duration.toJavaDuration().toMillis()
            } catch (error: ArithmeticException) {
                throw RedisConfigurationException("配置项 $path 数值过大", error)
            }
            return duration
        }

        private fun validatePort(port: Int, path: String) {
            requireRange(port, 1, 65535, path)
        }

        private fun validateThreadPoolSize(size: Int, path: String) {
            if (size < 0) throw RedisConfigurationException("配置项 $path 不能小于 0")
        }

        private fun requireRange(value: Int, min: Int, max: Int, path: String) {
            if (value !in min..max) throw RedisConfigurationException("配置项 $path 必须在 $min..$max 之间")
        }
    }
}
