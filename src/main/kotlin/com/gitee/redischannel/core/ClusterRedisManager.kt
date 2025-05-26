package com.gitee.redischannel.core

import com.gitee.redischannel.RedisChannelPlugin
import com.gitee.redischannel.api.JsonData
import com.gitee.redischannel.api.RedisChannelAPI
import com.gitee.redischannel.api.cluster.RedisClusterCommandAPI
import com.gitee.redischannel.api.cluster.RedisClusterPubSubAPI
import io.lettuce.core.SetArgs
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
import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands
import io.lettuce.core.pubsub.api.reactive.RedisPubSubReactiveCommands
import io.lettuce.core.pubsub.api.sync.RedisPubSubCommands
import io.lettuce.core.resource.DefaultClientResources
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
            pool.close()
            client.shutdown()
        }
    }

    override fun <T> useCommands(block: Function<RedisClusterCommands<String, String>, T>): T? {
        return useCommands { block.apply(it) }
    }

    override fun <T> useAsyncCommands(block: Function<RedisClusterAsyncCommands<String, String>, T>): T? {
        return useAsyncCommands { block.apply(it) }
    }

    override fun <T> useReactiveCommands(block: Function<RedisClusterReactiveCommands<String, String>, T>): T? {
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

    fun <T> useAsyncCommands(block: (RedisClusterAsyncCommands<String, String>) -> T): T? {
        val connection = try {
            pool.borrowObject()
        } catch (e: Exception) {
            warning("Failed to borrow connection: ${e.message}")
            return null
        }

        return try {
            block(connection.async())
        } catch (e: Exception) {
            warning("Redis operation failed: ${e.message}")
            null
        } finally {
            pool.returnObject(connection)
        }
    }

    fun <T> useReactiveCommands(block: (RedisClusterReactiveCommands<String, String>) -> T): T? {
        val connection = try {
            pool.borrowObject()
        } catch (e: Exception) {
            warning("Failed to borrow connection: ${e.message}")
            return null
        }

        return try {
            block(connection.reactive())
        } catch (e: Exception) {
            warning("Redis operation failed: ${e.message}")
            null
        } finally {
            pool.returnObject(connection)
        }
    }

    /**
     * 设置缓存数据，默认过期时间10秒
     * */
    override fun set(key: String, value: JsonData, timeout: Long, async: Boolean) {
        if (async) {
            useAsyncCommands { commands ->
                commands.set(key, value.toJson(), SetArgs().ex(timeout))
                // 返回操作结果
                true
            }
        } else {
            useCommands { commands ->
                commands.set(key, value.toJson(), SetArgs().ex(timeout))
                // 返回操作结果
                true
            }
        }
    }

    /**
     * 设置缓存数据，默认过期时间10秒
     * */
    override fun set(key: String, value: String, timeout: Long, async: Boolean) {
        if (async) {
            useAsyncCommands { commands ->
                commands.set(key, value, SetArgs().ex(timeout))
                // 返回操作结果
                true
            }
        } else {
            useCommands { commands ->
                commands.set(key, value, SetArgs().ex(timeout))
                // 返回操作结果
                true
            }
        }
    }

    /**
     * 设置哈希缓存数据，默认过期时间10秒
     * */
    override fun hSet(key: String, field: String, value: String, timeout: Long, async: Boolean) {
        if (async) {
            useAsyncCommands { commands ->
                commands.hset(key, field, value)
                commands.hexpire(key, timeout, field)
                // 返回操作结果
                true
            }
        } else {
            useCommands { commands ->
                commands.hset(key, field, value)
                commands.hexpire(key, timeout, field)
                // 返回操作结果
                true
            }
        }
    }

    /**
     * 获取缓存数据
     * */
    override fun get(key: String): String? {
        return useCommands { commands ->
            commands.get(key)
        }
    }


    override fun asyncGet(key: String): CompletableFuture<String?> {
        val future = CompletableFuture<String?>()
        useAsyncCommands { commands ->
            commands.get(key).whenComplete { v, throwable ->
                if (v != null) {
                    future.complete(v)
                } else {
                    future.completeExceptionally(throwable)
                }
            }
        }
        return future
    }

    /**
     * 获取缓存数据
     * */
    override fun hGet(key: String, field: String): String? {
        return useCommands { commands ->
            commands.hget(key, field)
        }
    }


    override fun hAsyncGet(key: String, field: String): CompletableFuture<String?> {
        val future = CompletableFuture<String?>()
        useAsyncCommands { commands ->
            commands.hget(key, field).whenComplete { v, throwable ->
                if (v != null) {
                    future.complete(v)
                } else {
                    future.completeExceptionally(throwable)
                }
            }
        }
        return future
    }

    override fun remove(key: String, async: Boolean) {
        if (async) {
            useAsyncCommands { commands ->
                commands.del(key)
                // 返回操作结果
                true
            }
        } else {
            useCommands { commands ->
                commands.del(key)
                // 返回操作结果
                true
            }
        }
    }

    override fun hRemove(key: String, field: String, async: Boolean) {
        if (async) {
            useAsyncCommands { commands ->
                commands.hdel(key, field)
                // 返回操作结果
                true
            }
        } else {
            useCommands { commands ->
                commands.hdel(key, field)
                // 返回操作结果
                true
            }
        }
    }

    /**
     * 刷新缓存数据
     * */
    override fun refreshExpire(key: String, timeout: Long, async: Boolean) {
        if (async) {
            useAsyncCommands { commands ->
                commands.expire(key, timeout)
                // 返回操作结果
                true
            }
        } else {
            useCommands { commands ->
                commands.expire(key, timeout)
                // 返回操作结果
                true
            }
        }
    }

    override fun publish(channel: String, message: String, async: Boolean) {
        if (async) {
            useAsyncCommands { commands ->
                commands.publish(channel, message)
                // 返回操作结果
                true
            }
        } else {
            useCommands { commands ->
                commands.publish(channel, message)
                // 返回操作结果
                true
            }
        }
    }

    override fun <T> usePubSubCommands(block: Function<RedisClusterPubSubCommands<String, String>, T>): T? {
        return block.apply(pubSubConnection.sync())
    }

    override fun <T> usePubSubAsyncCommands(block: Function<RedisClusterPubSubAsyncCommands<String, String>, T>): T? {
        return block.apply(pubSubConnection.async())
    }

    override fun <T> usePubSubReactiveCommands(block: Function<RedisClusterPubSubReactiveCommands<String, String>, T>): T? {
        return block.apply(pubSubConnection.reactive())
    }
}