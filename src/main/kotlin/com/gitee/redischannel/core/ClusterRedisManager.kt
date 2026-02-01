package com.gitee.redischannel.core

import com.gitee.redischannel.RedisChannelPlugin
import com.gitee.redischannel.api.RedisChannelAPI
import com.gitee.redischannel.api.cluster.RedisClusterCommandAPI
import com.gitee.redischannel.api.cluster.RedisClusterPubSubAPI
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.cluster.ClusterClientOptions
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions
import io.lettuce.core.cluster.RedisClusterClient
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands
import io.lettuce.core.cluster.api.reactive.RedisClusterReactiveCommands
import io.lettuce.core.cluster.api.sync.RedisClusterCommands
import io.lettuce.core.cluster.pubsub.StatefulRedisClusterPubSubConnection
import io.lettuce.core.cluster.pubsub.api.async.RedisClusterPubSubAsyncCommands
import io.lettuce.core.cluster.pubsub.api.reactive.RedisClusterPubSubReactiveCommands
import io.lettuce.core.cluster.pubsub.api.sync.RedisClusterPubSubCommands
import io.lettuce.core.codec.StringCodec
import io.lettuce.core.resource.DefaultClientResources
import io.lettuce.core.support.AsyncConnectionPoolSupport
import io.lettuce.core.support.BoundedAsyncPool
import io.lettuce.core.support.ConnectionPoolSupport
import org.apache.commons.pool2.impl.GenericObjectPool
import org.bukkit.Bukkit
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.severe
import taboolib.common.platform.function.warning
import taboolib.platform.BukkitPlugin
import java.util.concurrent.CompletableFuture
import java.util.function.Function
import kotlin.time.toJavaDuration

internal object ClusterRedisManager: RedisChannelAPI, RedisClusterCommandAPI, RedisClusterPubSubAPI {

    @Volatile
    lateinit var client: RedisClusterClient

    @Volatile
    lateinit var pool: GenericObjectPool<StatefulRedisClusterConnection<String, String>>
    @Volatile
    lateinit var asyncPool: BoundedAsyncPool<StatefulRedisClusterConnection<String, String>>

    @Volatile
    lateinit var pubSubConnection: StatefulRedisClusterPubSubConnection<String, String>
    @Volatile
    lateinit var resources: DefaultClientResources

    internal fun start() {
        val redis = RedisChannelPlugin.redis

        if (!redis.enableCluster) {
            return
        }

        RedisChannelPlugin.init(RedisChannelPlugin.Type.CLUSTER)

        try {
            val resource = DefaultClientResources.builder()

            if (redis.ioThreadPoolSize != 0) {
                resource.ioThreadPoolSize(redis.ioThreadPoolSize)
            }
            if (redis.computationThreadPoolSize != 0) {
                resource.computationThreadPoolSize(redis.computationThreadPoolSize)
            }

            val cluster = redis.cluster

            val uris = cluster.nodes.map {
                it.redisURIBuilder().build()
            }
            val clientOptions = ClusterClientOptions.builder()

            if (redis.ssl) {
                clientOptions.sslOptions(redis.sslOptions)
            }
            clientOptions.maintNotificationsConfig(MaintNotifications.fromEnabled(redis.maintNotifications))

            val topologyRefreshOptions = ClusterTopologyRefreshOptions.builder()
                .enablePeriodicRefresh(cluster.enablePeriodicRefresh)
                .refreshTriggersReconnectAttempts(cluster.refreshTriggersReconnectAttempts)
                .dynamicRefreshSources(cluster.dynamicRefreshSources)
                .closeStaleConnections(cluster.closeStaleConnections)

            // Lettuce 7.0+ 默认启用所有自适应触发器，需要禁用未配置的触发器
            val configuredTriggers = cluster.enableAdaptiveRefreshTrigger.toSet()
            if (configuredTriggers.isEmpty()) {
                // 如果未配置任何触发器，禁用所有
                topologyRefreshOptions.disableAllAdaptiveRefreshTriggers()
            } else {
                // 禁用未配置的触发器
                val triggersToDisable = ClusterTopologyRefreshOptions.RefreshTrigger.entries
                    .filter { it !in configuredTriggers }
                    .toTypedArray()
                if (triggersToDisable.isNotEmpty()) {
                    topologyRefreshOptions.disableAdaptiveRefreshTrigger(*triggersToDisable)
                }
            }

            cluster.adaptiveRefreshTriggersTimeout?.toJavaDuration()?.let { topologyRefreshOptions.adaptiveRefreshTriggersTimeout(it) }
            cluster.refreshPeriod?.toJavaDuration()?.let { topologyRefreshOptions.refreshPeriod(it) }
            clientOptions
                .topologyRefreshOptions(topologyRefreshOptions.build())
                .autoReconnect(redis.autoReconnect)
                .maxRedirects(cluster.maxRedirects)
                .validateClusterNodeMembership(cluster.validateClusterNodeMembership)
                .pingBeforeActivateConnection(redis.pingBeforeActivateConnection)

            resources = resource.build()
            client = RedisClusterClient.create(resources, uris)
            client.setOptions(clientOptions.build())

            // 连接 pub/sub 通道
            pubSubConnection = client.connectPubSub()
            // 连接同步
            pool = ConnectionPoolSupport.createGenericObjectPool(
                { client.connect().apply {
                    if (redis.enableSlaves) {
                        val slaves = redis.slaves
                        readFrom = slaves.readFrom
                    }
                } },
                redis.pool.clusterPoolConfig()
            )
            // 连接异步
            asyncPool = AsyncConnectionPoolSupport.createBoundedObjectPool(
                { client.connectAsync(StringCodec.UTF8).whenComplete { v, _ ->
                    if (redis.enableSlaves) {
                        val slaves = redis.slaves
                        v.readFrom = slaves.readFrom
                    }
                } },
                redis.asyncPool.poolConfig()
            )
            RedisMonitor.onConnected()
        } catch (e: Exception) {
            onConnectionFailed(e)
        }
    }

    private fun onConnectionFailed(e: Throwable) {
        severe("Redis 集群连接失败: ${e.message}")
        severe("插件将被禁用，请检查配置后使用 /redis reconnect 重新连接")
        try {
            Bukkit.getPluginManager().disablePlugin(BukkitPlugin.getInstance())
        } catch (t: Throwable) {
            severe("禁用插件时发生异常: ${t.message}")
        }
        RedisChannelPlugin.type = null
    }

    @Awake(LifeCycle.DISABLE)
    internal fun stop() {
        if (RedisChannelPlugin.type != RedisChannelPlugin.Type.CLUSTER) return
        RedisMonitor.onDisconnected()
        pubSubConnection.close()
        asyncPool.close()
        pool.close()
        client.shutdown()
        resources.shutdown()
    }

    override fun <T> useCommands(block: Function<RedisClusterCommands<String, String>, T>): T? {
        return useCommands { block.apply(it) }
    }

    override fun <T> useAsyncCommands(block: Function<RedisClusterAsyncCommands<String, String>, T>): CompletableFuture<T?> {
        return useAsyncCommands { block.apply(it) }
    }

    override fun <T> useReactiveCommands(block: Function<RedisClusterReactiveCommands<String, String>, T>): CompletableFuture<T?> {
        return useReactiveCommands { block.apply(it) }
    }

    inline fun <T> useCommands(crossinline block: (RedisClusterCommands<String, String>) -> T): T? {
        return useConnection {
            block(it.sync())
        }
    }

    inline fun <T> useAsyncCommands(crossinline block: (RedisClusterAsyncCommands<String, String>) -> T): CompletableFuture<T?> {
        return useAsyncConnection {
            block(it.async())
        }
    }

    inline fun <T> useReactiveCommands(crossinline block: (RedisClusterReactiveCommands<String, String>) -> T): CompletableFuture<T?> {
        return useAsyncConnection {
            block(it.reactive())
        }
    }

    override fun <T> useClusterPubSubCommands(block: Function<RedisClusterPubSubCommands<String, String>, T>): T? {
        return block.apply(pubSubConnection.sync())
    }

    override fun <T> useClusterPubSubAsyncCommands(block: Function<RedisClusterPubSubAsyncCommands<String, String>, T>): T? {
        return block.apply(pubSubConnection.async())
    }

    override fun <T> useClusterPubSubReactiveCommands(block: Function<RedisClusterPubSubReactiveCommands<String, String>, T>): T? {
        return block.apply(pubSubConnection.reactive())
    }

    // sync
    override fun <T> useConnection(
        use: Function<StatefulRedisConnection<String, String>, T>?,
        useCluster: Function<StatefulRedisClusterConnection<String, String>, T>?
    ): T? {
        val connection = try {
            pool.borrowObject()
        } catch (e: Exception) {
            warning("Failed to borrow connection: ${e.message}")
            RedisMonitor.recordCommand(false)
            return null
        }

        val startTime = System.currentTimeMillis()
        return try {
            val result = useCluster!!.apply(connection)
            RedisMonitor.recordCommand(true, System.currentTimeMillis() - startTime)
            result
        } catch (e: Exception) {
            warning("Redis operation failed: ${e.message}")
            RedisMonitor.recordCommand(false)
            null
        } finally {
            pool.returnObject(connection)
        }
    }

    // async
    override fun <T> useAsyncConnection(
        use: Function<StatefulRedisConnection<String, String>, T>?,
        useCluster: Function<StatefulRedisClusterConnection<String, String>, T>?
    ): CompletableFuture<T?> {
        val startTime = System.currentTimeMillis()
        val result = CompletableFuture<T?>()

        asyncPool.acquire().whenComplete { connection, acquireError ->
            if (acquireError != null) {
                warning("Failed to borrow connection: ${acquireError.message}")
                RedisMonitor.recordCommand(false)
                result.complete(null)
                return@whenComplete
            }

            try {
                val value = useCluster!!.apply(connection)
                RedisMonitor.recordCommand(true, System.currentTimeMillis() - startTime)
                result.complete(value)
            } catch (e: Exception) {
                warning("Redis operation failed: ${e.message}")
                RedisMonitor.recordCommand(false)
                result.complete(null)
            } finally {
                asyncPool.release(connection)
            }
        }
        return result
    }
}
