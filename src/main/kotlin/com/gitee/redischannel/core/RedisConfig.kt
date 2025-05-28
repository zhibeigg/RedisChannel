package com.gitee.redischannel.core

import com.gitee.redischannel.util.files
import io.lettuce.core.ReadFrom
import io.lettuce.core.RedisURI
import io.lettuce.core.SslOptions
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.lettuce.core.masterreplica.StatefulRedisMasterReplicaConnection
import io.lettuce.core.support.BoundedPoolConfig
import org.apache.commons.pool2.impl.GenericObjectPoolConfig
import taboolib.common.platform.function.getDataFolder
import taboolib.common5.cint
import taboolib.library.configuration.ConfigurationSection
import taboolib.module.configuration.Configuration
import java.io.File
import kotlin.time.Duration
import kotlin.time.toJavaDuration

class RedisConfig(val configurationSection: ConfigurationSection) {

    val host = configurationSection.getString("host") ?: error("host must be set")
    val port = configurationSection.getInt("port", 6379)
    val password = configurationSection.getString("password")
    val ssl = configurationSection.getBoolean("ssl")
    val timeout = Duration.parse(configurationSection.getString("timeout") ?: error("timeout must be set"))
    val database = configurationSection.getInt("database", 0)

    val ioThreadPoolSize = configurationSection.getInt("ioThreadPoolSize")
    val computationThreadPoolSize = configurationSection.getInt("computationThreadPoolSize")

    val autoReconnect = configurationSection.getBoolean("autoReconnect", false)
    val pingBeforeActivateConnection = configurationSection.getBoolean("pingBeforeActivateConnection", true)

    val sslOptions: SslOptions
        get() {
            val password = configurationSection.getString("truststorePassword")
            return SslOptions.builder()
                .jdkSslProvider()
                .truststore(File(getDataFolder(), "default.jks"), password)
                .build()
        }

    val pool = Pool(configurationSection.getConfigurationSection("pool")!!)

    class Pool(configurationSection: ConfigurationSection) {

        val lifo = configurationSection.getBoolean("lifo", true)
        val fairness = configurationSection.getBoolean("fairness", false)

        val maxTotal = configurationSection.getInt("maxTotal", 8)
        val maxIdle = configurationSection.getInt("maxIdle", 8)
        val minIdle = configurationSection.getInt("minIdle", 0)

        val testOnCreate = configurationSection.getBoolean("testOnCreate", false)
        val testOnBorrow = configurationSection.getBoolean("testOnCreate", false)
        val testOnReturn = configurationSection.getBoolean("testOnCreate", false)
        val testWhileIdle = configurationSection.getBoolean("testOnCreate", false)

        val maxWaitDuration = configurationSection.getString("maxWaitDuration")?.let { Duration.parse(it) }
        val blockWhenExhausted = configurationSection.getBoolean("blockWhenExhausted", true)
        val timeBetweenEvictionRuns =
            configurationSection.getString("timeBetweenEvictionRuns")?.let { Duration.parse(it) }
        val minEvictableIdleDuration =
            configurationSection.getString("minEvictableIdleDuration")?.let { Duration.parse(it) }
        val softMinEvictableIdleDuration =
            configurationSection.getString("softMinEvictableIdleDuration")?.let { Duration.parse(it) }
        val numTestsPerEvictionRun = configurationSection.getInt("numTestsPerEvictionRun", 3)

        fun asyncPoolConfig(): BoundedPoolConfig {
            return BoundedPoolConfig.builder()
                .maxTotal(this@Pool.maxTotal)
                .maxIdle(this@Pool.maxIdle)
                .minIdle(this@Pool.minIdle)
                .build()
        }

        fun poolConfig(): GenericObjectPoolConfig<StatefulRedisConnection<String, String>> {
            return GenericObjectPoolConfig<StatefulRedisConnection<String, String>>().apply {
                lifo = this@Pool.lifo
                fairness = this@Pool.fairness

                maxTotal = this@Pool.maxTotal
                maxIdle = this@Pool.maxIdle
                minIdle = this@Pool.minIdle

                testOnCreate = this@Pool.testOnCreate
                testOnBorrow = this@Pool.testOnBorrow
                testOnReturn = this@Pool.testOnReturn
                testWhileIdle = this@Pool.testWhileIdle

                this@Pool.maxWaitDuration?.let { setMaxWait(it.toJavaDuration()) }
                blockWhenExhausted = this@Pool.blockWhenExhausted
                this@Pool.timeBetweenEvictionRuns?.let { timeBetweenEvictionRuns = it.toJavaDuration() }
                this@Pool.minEvictableIdleDuration?.let { minEvictableIdleDuration = it.toJavaDuration() }
                this@Pool.softMinEvictableIdleDuration?.let { softMinEvictableIdleDuration = it.toJavaDuration() }
                numTestsPerEvictionRun = this@Pool.numTestsPerEvictionRun
            }
        }

        fun asyncClusterPoolConfig(): BoundedPoolConfig {
            return BoundedPoolConfig.builder()
                .maxTotal(this@Pool.maxTotal)
                .maxIdle(this@Pool.maxIdle)
                .minIdle(this@Pool.minIdle)
                .build()
        }

        fun clusterPoolConfig(): GenericObjectPoolConfig<StatefulRedisClusterConnection<String, String>> {
            return GenericObjectPoolConfig<StatefulRedisClusterConnection<String, String>>().apply {
                lifo = this@Pool.lifo
                fairness = this@Pool.fairness

                maxTotal = this@Pool.maxTotal
                maxIdle = this@Pool.maxIdle
                minIdle = this@Pool.minIdle

                testOnCreate = this@Pool.testOnCreate
                testOnBorrow = this@Pool.testOnBorrow
                testOnReturn = this@Pool.testOnReturn
                testWhileIdle = this@Pool.testWhileIdle

                this@Pool.maxWaitDuration?.let { setMaxWait(it.toJavaDuration()) }
                blockWhenExhausted = this@Pool.blockWhenExhausted
                this@Pool.timeBetweenEvictionRuns?.let { timeBetweenEvictionRuns = it.toJavaDuration() }
                this@Pool.minEvictableIdleDuration?.let { minEvictableIdleDuration = it.toJavaDuration() }
                this@Pool.softMinEvictableIdleDuration?.let { softMinEvictableIdleDuration = it.toJavaDuration() }
                numTestsPerEvictionRun = this@Pool.numTestsPerEvictionRun
            }
        }

        fun asyncSlavesPoolConfig(): BoundedPoolConfig {
            return BoundedPoolConfig.builder()
                .maxTotal(this@Pool.maxTotal)
                .maxIdle(this@Pool.maxIdle)
                .minIdle(this@Pool.minIdle)
                .build()
        }

        fun slavesPoolConfig(): GenericObjectPoolConfig<StatefulRedisMasterReplicaConnection<String, String>> {
            return GenericObjectPoolConfig<StatefulRedisMasterReplicaConnection<String, String>>().apply {
                lifo = this@Pool.lifo
                fairness = this@Pool.fairness

                maxTotal = this@Pool.maxTotal
                maxIdle = this@Pool.maxIdle
                minIdle = this@Pool.minIdle

                testOnCreate = this@Pool.testOnCreate
                testOnBorrow = this@Pool.testOnBorrow
                testOnReturn = this@Pool.testOnReturn
                testWhileIdle = this@Pool.testWhileIdle

                this@Pool.maxWaitDuration?.let { setMaxWait(it.toJavaDuration()) }
                blockWhenExhausted = this@Pool.blockWhenExhausted
                this@Pool.timeBetweenEvictionRuns?.let { timeBetweenEvictionRuns = it.toJavaDuration() }
                this@Pool.minEvictableIdleDuration?.let { minEvictableIdleDuration = it.toJavaDuration() }
                this@Pool.softMinEvictableIdleDuration?.let { softMinEvictableIdleDuration = it.toJavaDuration() }
                numTestsPerEvictionRun = this@Pool.numTestsPerEvictionRun
            }
        }
    }

    // sentinel
    val enableSentinel = configurationSection.getBoolean("sentinel.enable", false)
    val sentinel
        get() = Sentinel(configurationSection.getConfigurationSection("sentinel")!!)

    class Sentinel(configurationSection: ConfigurationSection) {

        val masterId = configurationSection.getString("masterId") ?: error("masterId must be set")

        val nodes = configurationSection.getStringList("nodes").map {
            Node(it.split(":")[0], it.split(":")[1].cint)
        }

        class Node(val host: String, val port: Int)
    }

    // slaves
    val enableSlaves = configurationSection.getBoolean("slaves.enable", false)
    val slaves
        get() = Slaves(configurationSection.getConfigurationSection("slaves")!!)

    class Slaves(configurationSection: ConfigurationSection) {

        val readFrom = ReadFrom.valueOf((configurationSection.getString("readFrom") ?: error("readFrom must be set")))
    }

    // cluster
    val enableCluster = configurationSection.getBoolean("cluster.enable", false)

    val cluster
        get() = Cluster(configurationSection.getConfigurationSection("cluster")!!)

    class Cluster(configurationSection: ConfigurationSection) {

        val nodes = files("clusters", "cluster0.yml") {
            val configuration = Configuration.loadFromFile(it)
            Node(configuration)
        }

        val enablePeriodicRefresh = configurationSection.getBoolean("enablePeriodicRefresh", false)
        val refreshPeriod = configurationSection.getString("refreshPeriod")?.let { Duration.parse(it) }
        val enableAdaptiveRefreshTrigger = configurationSection.getEnumList("enableAdaptiveRefreshTrigger", ClusterTopologyRefreshOptions.RefreshTrigger::class.java)
        val adaptiveRefreshTriggersTimeout = configurationSection.getString("adaptiveRefreshTriggersTimeout")?.let { Duration.parse(it) }
        val refreshTriggersReconnectAttempts = configurationSection.getInt("refreshTriggersReconnectAttempts", 5)
        val dynamicRefreshSources = configurationSection.getBoolean("dynamicRefreshSources", true)
        val closeStaleConnections = configurationSection.getBoolean("closeStaleConnections", true)
        val maxRedirects = configurationSection.getInt("maxRedirects", 5)
        val validateClusterNodeMembership = configurationSection.getBoolean("validateClusterNodeMembership", true)

        class Node(private val configurationSection: ConfigurationSection) {
            val host = configurationSection.getString("host") ?: error("host must be set")
            val port = configurationSection.getInt("port", 6379)
            val password = configurationSection.getString("password")
            val ssl = configurationSection.getBoolean("ssl")
            val timeout = Duration.parse(configurationSection.getString("timeout") ?: error("timeout must be set"))
            val database = configurationSection.getInt("database", 0)

            // sentinel
            val enableSentinel = configurationSection.getBoolean("sentinel.enable", false)
            val sentinel
                get() = Sentinel(configurationSection.getConfigurationSection("sentinel")!!)

            class Sentinel(configurationSection: ConfigurationSection) {

                val masterId = configurationSection.getString("masterId") ?: error("masterId must be set")

                val nodes = configurationSection.getStringList("nodes").map {
                    Node(it.split(":")[0], it.split(":")[1].cint)
                }

                class Node(val host: String, val port: Int)
            }

            fun redisURIBuilder(): RedisURI.Builder {
                val builder = RedisURI.builder()
                    .withHost(host)
                    .withPort(port)
                    .withSsl(ssl)
                    .withTimeout(timeout.toJavaDuration())
                    .withDatabase(database)

                password?.toCharArray()?.let { builder.withPassword(it) }

                if (enableSentinel) {

                    val sentinel = sentinel

                    builder.withSentinelMasterId(sentinel.masterId)
                    sentinel.nodes.forEach {
                        builder.withSentinel(it.host, it.port)
                    }
                }
                return builder
            }
        }
    }

    fun redisURIBuilder(): RedisURI.Builder {
        val builder = RedisURI.builder()
            .withHost(host)
            .withPort(port)
            .withSsl(ssl)
            .withTimeout(timeout.toJavaDuration())
            .withDatabase(database)

        password?.toCharArray()?.let { builder.withPassword(it) }

        if (enableSentinel) {

            val sentinel = sentinel

            builder.withSentinelMasterId(sentinel.masterId)
            sentinel.nodes.forEach {
                builder.withSentinel(it.host, it.port)
            }
        }
        return builder
    }
}