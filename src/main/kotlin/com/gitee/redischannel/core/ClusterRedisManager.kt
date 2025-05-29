package com.gitee.redischannel.core

import com.gitee.redischannel.RedisChannelPlugin
import com.gitee.redischannel.api.RedisChannelAPI
import com.gitee.redischannel.api.cluster.RedisClusterCommandAPI
import com.gitee.redischannel.api.cluster.RedisClusterPubSubAPI
import com.gitee.redischannel.core.RedisManager.enabledSlaves
import com.gitee.redischannel.core.RedisManager.masterAsyncReplicaPool
import com.gitee.redischannel.core.RedisManager.masterReplicaPool
import io.lettuce.core.api.StatefulConnection
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.*
import io.lettuce.core.api.reactive.BaseRedisReactiveCommands
import io.lettuce.core.api.reactive.RedisAclReactiveCommands
import io.lettuce.core.api.reactive.RedisFunctionReactiveCommands
import io.lettuce.core.api.reactive.RedisGeoReactiveCommands
import io.lettuce.core.api.reactive.RedisHLLReactiveCommands
import io.lettuce.core.api.reactive.RedisHashReactiveCommands
import io.lettuce.core.api.reactive.RedisJsonReactiveCommands
import io.lettuce.core.api.reactive.RedisKeyReactiveCommands
import io.lettuce.core.api.reactive.RedisListReactiveCommands
import io.lettuce.core.api.reactive.RedisScriptingReactiveCommands
import io.lettuce.core.api.reactive.RedisServerReactiveCommands
import io.lettuce.core.api.reactive.RedisSetReactiveCommands
import io.lettuce.core.api.reactive.RedisSortedSetReactiveCommands
import io.lettuce.core.api.reactive.RedisStreamReactiveCommands
import io.lettuce.core.api.reactive.RedisStringReactiveCommands
import io.lettuce.core.api.sync.*
import io.lettuce.core.cluster.ClusterClientOptions
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions
import io.lettuce.core.cluster.RedisClusterClient
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands
import io.lettuce.core.cluster.api.reactive.RedisClusterReactiveCommands
import io.lettuce.core.cluster.api.sync.RedisAdvancedClusterCommands
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

internal object ClusterRedisManager: RedisChannelAPI<StatefulRedisClusterConnection<String, String>>, RedisClusterCommandAPI, RedisClusterPubSubAPI {

    lateinit var client: RedisClusterClient

    lateinit var pool: GenericObjectPool<StatefulRedisClusterConnection<String, String>>
    lateinit var asyncPool: BoundedAsyncPool<StatefulRedisClusterConnection<String, String>>

    lateinit var pubSubConnection: StatefulRedisClusterPubSubConnection<String, String>

    @Parallel(runOn = LifeCycle.ENABLE)
    private fun start() {
        val redis = RedisChannelPlugin.redis
        if (!redis.enableCluster) return
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

        client = RedisClusterClient.create(resource.build(), uris)

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
        }
        pool = ConnectionPoolSupport.createGenericObjectPool(
            { client.connect().apply {
                if (redis.enableSlaves) {
                    val slaves = redis.slaves
                    readFrom = slaves.readFrom
                }
            } },
            redis.pool.clusterPoolConfig()
        )
        pubSubConnection = client.connectPubSub()
    }

    @Awake(LifeCycle.DISABLE)
    private fun stop() {
        if (RedisChannelPlugin.type == RedisChannelPlugin.Type.CLUSTER) {
            asyncPool.close()
            pool.close()
            client.shutdown()
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

    fun <T> useCommands(block: (RedisClusterCommands<String, String>) -> T): T? {
        return useConnection {
            block(it.sync())
        }
    }

    fun <T> useAsyncCommands(block: (RedisClusterAsyncCommands<String, String>) -> T): CompletableFuture<T?> {
        return useAsyncConnection {
            block(it.async())
        }
    }

    fun <T> useReactiveCommands(block: (RedisClusterReactiveCommands<String, String>) -> T): CompletableFuture<T?> {
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
    override fun <V> useConnection(use: Function<StatefulRedisClusterConnection<String, String>, V>): V? {
        val connection = try {
            pool.borrowObject()
        } catch (e: Exception) {
            warning("Failed to borrow connection: ${e.message}")
            return null
        }

        return try {
            use.apply(connection)
        } catch (e: Exception) {
            warning("Redis operation failed: ${e.message}")
            null
        } finally {
            pool.returnObject(connection)
        }
    }

    // async
    override fun <V> useAsyncConnection(use: Function<StatefulRedisClusterConnection<String, String>, V>): CompletableFuture<V?> {
        return try {
            asyncPool.acquire().thenApply { connection ->
                try {
                    use.apply(connection)
                } catch (e: Exception) {
                    warning("Redis operation failed: ${e.message}")
                    null
                } finally {
                    asyncPool.release(connection)
                }
            }
        } catch (e: Exception) {
            warning("Failed to borrow connection: ${e.message}")
            return CompletableFuture.completedFuture(null)
        }
    }
}