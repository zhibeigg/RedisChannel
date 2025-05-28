package com.gitee.redischannel.core

import com.gitee.redischannel.RedisChannelPlugin
import com.gitee.redischannel.api.RedisChannelAPI
import com.gitee.redischannel.api.cluster.RedisClusterCommandAPI
import com.gitee.redischannel.api.cluster.RedisClusterPubSubAPI
import com.gitee.redischannel.api.proxy.ProxyAPI
import com.gitee.redischannel.api.proxy.RedisProxyCommand
import io.lettuce.core.AbstractRedisAsyncCommands
import io.lettuce.core.api.sync.BaseRedisCommands
import io.lettuce.core.api.sync.RedisAclCommands
import io.lettuce.core.api.sync.RedisFunctionCommands
import io.lettuce.core.api.sync.RedisGeoCommands
import io.lettuce.core.api.sync.RedisHLLCommands
import io.lettuce.core.api.sync.RedisHashCommands
import io.lettuce.core.api.sync.RedisJsonCommands
import io.lettuce.core.api.sync.RedisKeyCommands
import io.lettuce.core.api.sync.RedisListCommands
import io.lettuce.core.api.sync.RedisScriptingCommands
import io.lettuce.core.api.sync.RedisServerCommands
import io.lettuce.core.api.sync.RedisSetCommands
import io.lettuce.core.api.sync.RedisSortedSetCommands
import io.lettuce.core.api.sync.RedisStreamCommands
import io.lettuce.core.api.sync.RedisStringCommands
import io.lettuce.core.cluster.ClusterClientOptions
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions
import io.lettuce.core.cluster.RedisClusterClient
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.lettuce.core.cluster.api.async.RedisAdvancedClusterAsyncCommands
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

internal object ClusterRedisManager: RedisChannelAPI, RedisClusterCommandAPI, RedisClusterPubSubAPI, ProxyAPI {

    lateinit var client: RedisClusterClient

    lateinit var pool: GenericObjectPool<StatefulRedisClusterConnection<String, String>>
    lateinit var asyncPool: BoundedAsyncPool<StatefulRedisClusterConnection<String, String>>

    lateinit var pubSubConnection: StatefulRedisClusterPubSubConnection<String, String>

    @Parallel(runOn = LifeCycle.ENABLE)
    fun start() {
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

        asyncPool = AsyncConnectionPoolSupport.createBoundedObjectPool(
            { client.connectAsync(StringCodec.UTF8).whenComplete { v, _ ->
                if (redis.enableSlaves) {
                    val slaves = redis.slaves
                    v.readFrom = slaves.readFrom
                }
            } },
            redis.pool.asyncClusterPoolConfig()
        )
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
        val connection = try {
            pool.borrowObject()
        } catch (e: Exception) {
            warning("Failed to borrow connection: ${e.message}")
            return null
        }

        return try {
            block(connection.sync())
        } catch (e: Exception) {
            warning("Redis operation failed: ${e.message}")
            null
        } finally {
            pool.returnObject(connection)
        }
    }

    fun <T> useAsyncCommands(block: (RedisClusterAsyncCommands<String, String>) -> T): CompletableFuture<T?> {
        return try {
            asyncPool.acquire().thenApply { obj ->
                try {
                    block(obj.async())
                } catch (e: Throwable) {
                    warning("Redis operation failed: ${e.message}")
                    return@thenApply null
                } finally {
                    asyncPool.release(obj)
                }
            }
        } catch (e: Throwable) {
            warning("Failed to acquire connection: ${e.message}")
            CompletableFuture.completedFuture(null)
        }
    }

    fun <T> useReactiveCommands(block: (RedisClusterReactiveCommands<String, String>) -> T): CompletableFuture<T?> {
        return try {
            asyncPool.acquire().thenApply { obj ->
                try {
                    block(obj.reactive())
                } catch (e: Throwable) {
                    warning("Redis operation failed: ${e.message}")
                    return@thenApply null
                } finally {
                    asyncPool.release(obj)
                }
            }
        } catch (e: Throwable) {
            warning("Failed to acquire connection: ${e.message}")
            CompletableFuture.completedFuture(null)
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

    fun getProxyCommand(): RedisAdvancedClusterCommands<String, String> {
        val connection = try {
            pool.borrowObject()
        } catch (e: Exception) {
            warning("Failed to borrow connection: ${e.message}")
            null
        }

        val command = try {
            connection?.sync()
        } catch (e: Exception) {
            warning("Redis operation failed: ${e.message}")
            null
        } finally {
            pool.returnObject(connection)
        }
        return command!!
    }

    @Suppress("UNCHECKED_CAST")
    override fun getProxyAsyncCommand(): CompletableFuture<AbstractRedisAsyncCommands<String, String>> {
        val command = try {
            asyncPool.acquire().thenApply {
                try {
                    it.async()
                } catch (e: Exception) {
                    warning("Redis operation failed: ${e.message}")
                    null
                } finally {
                    asyncPool.release(it)
                }
            }
        } catch (e: Exception) {
            warning("Failed to borrow connection: ${e.message}")
            return CompletableFuture.completedFuture(null)
        }

        return command.thenApply {
            it as AbstractRedisAsyncCommands<String, String>
        }
    }

    override fun baseCommand(): BaseRedisCommands<String, String> {
        return getProxyCommand()
    }

    override fun aclCommand(): RedisAclCommands<String, String> {
        return getProxyCommand()
    }

    override fun functionCommand(): RedisFunctionCommands<String, String> {
        return getProxyCommand()
    }

    override fun geoCommand(): RedisGeoCommands<String, String> {
        return getProxyCommand()
    }

    override fun hashCommand(): RedisHashCommands<String, String> {
        return getProxyCommand()
    }

    override fun hllCommand(): RedisHLLCommands<String, String> {
        return getProxyCommand()
    }

    override fun keyCommand(): RedisKeyCommands<String, String> {
        return getProxyCommand()
    }

    override fun listCommand(): RedisListCommands<String, String> {
        return getProxyCommand()
    }

    override fun scriptingCommand(): RedisScriptingCommands<String, String> {
        return getProxyCommand()
    }

    override fun serverCommand(): RedisServerCommands<String, String> {
        return getProxyCommand()
    }

    override fun setCommand(): RedisSetCommands<String, String> {
        return getProxyCommand()
    }

    override fun sortedSetCommand(): RedisSortedSetCommands<String, String> {
        return getProxyCommand()
    }

    override fun streamCommand(): RedisStreamCommands<String, String> {
        return getProxyCommand()
    }

    override fun stringCommand(): RedisStringCommands<String, String> {
        return getProxyCommand()
    }

    override fun jsonCommand(): RedisJsonCommands<String, String> {
        return getProxyCommand()
    }
}