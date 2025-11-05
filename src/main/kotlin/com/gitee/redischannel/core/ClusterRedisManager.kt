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
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.warning
import taboolib.platform.bukkit.Parallel
import java.util.concurrent.CompletableFuture
import java.util.function.Function
import kotlin.time.toJavaDuration

internal object ClusterRedisManager: RedisChannelAPI, RedisClusterCommandAPI, RedisClusterPubSubAPI {

    lateinit var client: RedisClusterClient

    lateinit var pool: GenericObjectPool<StatefulRedisClusterConnection<String, String>>
    lateinit var asyncPool: BoundedAsyncPool<StatefulRedisClusterConnection<String, String>>

    lateinit var pubSubConnection: StatefulRedisClusterPubSubConnection<String, String>
    lateinit var resources: DefaultClientResources

    @Parallel(runOn = LifeCycle.ENABLE)
    internal fun start(): CompletableFuture<Void> {
        val completableFuture = CompletableFuture<Void>()
        val redis = RedisChannelPlugin.redis
        if (!redis.enableCluster) {
            completableFuture.complete(null)
            return completableFuture
        }
        RedisChannelPlugin.init(RedisChannelPlugin.Type.CLUSTER)

        val resource = DefaultClientResources.builder()

        if (redis.ioThreadPoolSize != 0) {
            resource.ioThreadPoolSize(4)
        }
        if (redis.computationThreadPoolSize != 0) {
            resource.computationThreadPoolSize(4)
        }

        val cluster = redis.cluster

        val uris = cluster.nodes.map {
            it.redisURIBuilder().build()
        }
        val clientOptions = ClusterClientOptions.builder()

        if (redis.ssl) {
            clientOptions.sslOptions(redis.sslOptions)
        }

        val topologyRefreshOptions = ClusterTopologyRefreshOptions.builder()
            .enablePeriodicRefresh(cluster.enablePeriodicRefresh)
            .enableAdaptiveRefreshTrigger(*cluster.enableAdaptiveRefreshTrigger.toTypedArray())
            .refreshTriggersReconnectAttempts(cluster.refreshTriggersReconnectAttempts)
            .dynamicRefreshSources(cluster.dynamicRefreshSources)
            .closeStaleConnections(cluster.closeStaleConnections)

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
        AsyncConnectionPoolSupport.createBoundedObjectPoolAsync(
            { client.connectAsync(StringCodec.UTF8).whenComplete { v, _ ->
                if (redis.enableSlaves) {
                    val slaves = redis.slaves
                    v.readFrom = slaves.readFrom
                }
            } },
            redis.asyncPool.asyncClusterPoolConfig()
        ).thenAccept {
            asyncPool = it
            completableFuture.complete(null)
        }
        return completableFuture
    }

    @Awake(LifeCycle.DISABLE)
    internal fun stop() {
        if (RedisChannelPlugin.type == RedisChannelPlugin.Type.CLUSTER) {
            asyncPool.close()
            pool.close()
            client.shutdown()
            resources.shutdown()
        }
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
            return null
        }

        return try {
            useCluster!!.apply(connection)
        } catch (e: Exception) {
            warning("Redis operation failed: ${e.message}")
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
        return try {
            asyncPool.acquire().thenApply { connection ->
                try {
                    useCluster!!.apply(connection)
                } catch (e: Exception) {
                    warning("Redis operation failed: ${e.message}")
                    null
                } finally {
                    asyncPool.release(connection)
                }
            }
        } catch (e: Exception) {
            warning("Failed to borrow connection: ${e.message}")
            CompletableFuture.completedFuture(null)
        }
    }
}