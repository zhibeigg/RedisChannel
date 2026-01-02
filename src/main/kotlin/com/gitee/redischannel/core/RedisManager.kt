package com.gitee.redischannel.core

import com.gitee.redischannel.RedisChannelPlugin
import com.gitee.redischannel.api.RedisChannelAPI
import com.gitee.redischannel.api.RedisCommandAPI
import com.gitee.redischannel.api.RedisPubSubAPI
import io.lettuce.core.ClientOptions
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import io.lettuce.core.api.reactive.RedisReactiveCommands
import io.lettuce.core.api.sync.RedisCommands
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.lettuce.core.codec.StringCodec
import io.lettuce.core.masterreplica.MasterReplica
import io.lettuce.core.masterreplica.StatefulRedisMasterReplicaConnection
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection
import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands
import io.lettuce.core.pubsub.api.reactive.RedisPubSubReactiveCommands
import io.lettuce.core.pubsub.api.sync.RedisPubSubCommands
import io.lettuce.core.resource.DefaultClientResources
import io.lettuce.core.support.AsyncConnectionPoolSupport
import io.lettuce.core.support.BoundedAsyncPool
import io.lettuce.core.support.ConnectionPoolSupport
import org.apache.commons.pool2.impl.GenericObjectPool
import org.bukkit.Bukkit
import taboolib.common.LifeCycle
import taboolib.common.env.RuntimeDependencies
import taboolib.common.env.RuntimeDependency
import taboolib.common.platform.Awake
import taboolib.common.platform.function.severe
import taboolib.common.platform.function.warning
import taboolib.platform.BukkitPlugin
import taboolib.platform.bukkit.Parallel
import java.util.concurrent.CompletableFuture
import java.util.function.Function

@RuntimeDependencies(
    RuntimeDependency(
        "!io.lettuce:lettuce-core:7.2.1.RELEASE",
        test = "!io.lettuce.core.RedisURI",
        relocate = ["!io.netty", "!com.gitee.redischannel.netty",
            "!org.apache.commons.pool2", "!com.gitee.redischannel.commons.pool2",
            "!reactor", "!com.gitee.redischannel.reactor",
            "!org.reactivestreams", "!com.gitee.redischannel.reactivestreams"],
        transitive = false
    ),
    RuntimeDependency(
        "!org.reactivestreams:reactive-streams:1.0.4",
        test = "!com.gitee.redischannel.reactivestreams.Publisher",
        relocate = ["!org.reactivestreams", "!com.gitee.redischannel.reactivestreams"],
        transitive = false
    ),
    RuntimeDependency(
        "!io.projectreactor:reactor-core:3.6.6",
        test = "!com.gitee.redischannel.reactor.core.CorePublisher",
        relocate = ["!reactor", "!com.gitee.redischannel.reactor", "!org.reactivestreams", "!com.gitee.redischannel.reactivestreams"],
        transitive = false
    ),
    RuntimeDependency(
        "!org.apache.commons:commons-pool2:2.12.1",
        test = "!com.gitee.redischannel.commons.pool2.BaseObject",
        relocate = ["!org.apache.commons.pool2", "!com.gitee.redischannel.commons.pool2"],
        transitive = false
    ),
    RuntimeDependency(
        value = "!io.netty:netty-common:4.1.118.Final",
        test = "!com.gitee.redischannel.netty.util.AbstractConstant",
        relocate = ["!io.netty", "!com.gitee.redischannel.netty"],
        transitive = false
    ),
    RuntimeDependency(
        value = "!io.netty:netty-buffer:4.1.118.Final",
        test = "!com.gitee.redischannel.netty.buffer.AbstractByteBuf",
        relocate = ["!io.netty", "!com.gitee.redischannel.netty"],
        transitive = false
    ),
    RuntimeDependency(
        value = "!io.netty:netty-codec:4.1.118.Final",
        test = "!com.gitee.redischannel.netty.handler.codec.AsciiHeadersEncoder",
        relocate = ["!io.netty", "!com.gitee.redischannel.netty"],
        transitive = false
    ),
    RuntimeDependency(
        value = "!io.netty:netty-handler:4.1.118.Final",
        test = "!com.gitee.redischannel.netty.handler.address.ResolveAddressHandler",
        relocate = ["!io.netty", "!com.gitee.redischannel.netty"],
        transitive = false
    ),
    RuntimeDependency(
        value = "!io.netty:netty-resolver:4.1.118.Final",
        test = "!com.gitee.redischannel.netty.resolver.AbstractAddressResolver",
        relocate = ["!io.netty", "!com.gitee.redischannel.netty"],
        transitive = false
    ),
    RuntimeDependency(
        value = "!io.netty:netty-transport:4.1.118.Final",
        test = "!com.gitee.redischannel.netty.bootstrap.Bootstrap",
        relocate = ["!io.netty", "!com.gitee.redischannel.netty"],
        transitive = false
    ),
    RuntimeDependency(
        value = "!io.netty:netty-transport-native-unix-common:4.1.118.Final",
        test = "!com.gitee.redischannel.netty.channel.unix.Buffer",
        relocate = ["!io.netty", "!com.gitee.redischannel.netty"],
        transitive = false
    )
)
internal object RedisManager: RedisChannelAPI, RedisCommandAPI, RedisPubSubAPI {

    lateinit var client: RedisClient

    lateinit var pool: GenericObjectPool<StatefulRedisConnection<String, String>>
    lateinit var asyncPool: BoundedAsyncPool<StatefulRedisConnection<String, String>>

    lateinit var masterReplicaPool: GenericObjectPool<StatefulRedisMasterReplicaConnection<String, String>>
    lateinit var masterAsyncReplicaPool: BoundedAsyncPool<StatefulRedisMasterReplicaConnection<String, String>>

    lateinit var pubSubConnection: StatefulRedisPubSubConnection<String, String>
    lateinit var resources: DefaultClientResources

    var enabledSlaves = false

    internal fun start() {
        val redis = RedisChannelPlugin.redis

        if (redis.enableCluster) {
            return
        }

        RedisChannelPlugin.init(RedisChannelPlugin.Type.SINGLE)

        try {
            val resource = DefaultClientResources.builder()

            if (redis.ioThreadPoolSize != 0) {
                resource.ioThreadPoolSize(redis.ioThreadPoolSize)
            }
            if (redis.computationThreadPoolSize != 0) {
                resource.computationThreadPoolSize(redis.computationThreadPoolSize)
            }

            val clientOptions = ClientOptions.builder()
                .autoReconnect(redis.autoReconnect)
                .pingBeforeActivateConnection(redis.pingBeforeActivateConnection)

            if (redis.ssl) {
                clientOptions.sslOptions(redis.sslOptions)
            }
            val uri = redis.redisURIBuilder().build()

            resources = resource.build()
            client = RedisClient.create(resources, uri).apply {
                options = clientOptions.build()
            }
            // 连接 pub/sub 通道
            pubSubConnection = client.connectPubSub()

            if (redis.enableSlaves) {
                enabledSlaves = true
                val slaves = redis.slaves

                // 连接同步
                masterReplicaPool = ConnectionPoolSupport.createGenericObjectPool(
                    { MasterReplica.connect(client, StringCodec.UTF8, uri).apply {
                        readFrom = slaves.readFrom
                    } },
                    redis.pool.slavesPoolConfig()
                )
                // 连接异步
                masterAsyncReplicaPool = AsyncConnectionPoolSupport.createBoundedObjectPool(
                    { MasterReplica.connectAsync(client, StringCodec.UTF8, uri).whenComplete { v, _ ->
                        v.readFrom = slaves.readFrom
                    } },
                    redis.asyncPool.poolConfig()
                )
            } else {
                // 连接同步
                pool = ConnectionPoolSupport.createGenericObjectPool(
                    { client.connect() },
                    redis.pool.poolConfig()
                )
                // 连接异步
                asyncPool = AsyncConnectionPoolSupport.createBoundedObjectPool(
                    { client.connectAsync(StringCodec.UTF8, uri) },
                    redis.asyncPool.poolConfig()
                )
            }
            RedisMonitor.onConnected()
        } catch (e: Exception) {
            onConnectionFailed(e)
        }
    }

    private fun onConnectionFailed(e: Throwable) {
        severe("Redis 连接失败: ${e.message}")
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
        if (RedisChannelPlugin.type != RedisChannelPlugin.Type.SINGLE) return
        RedisMonitor.onDisconnected()
        pubSubConnection.close()
        if (enabledSlaves) {
            masterAsyncReplicaPool.close()
            masterReplicaPool.close()
        } else {
            asyncPool.close()
            pool.close()
        }
        client.shutdown()
        resources.shutdown()
    }

    override fun <T> useCommands(block: Function<RedisCommands<String, String>, T>): T? {
        return useCommands { block.apply(it) }
    }

    override fun <T> useAsyncCommands(block: Function<RedisAsyncCommands<String, String>, T>): CompletableFuture<T?> {
        return useAsyncCommands { block.apply(it) }
    }

    override fun <T> useReactiveCommands(block: Function<RedisReactiveCommands<String, String>, T>): CompletableFuture<T?> {
        return useReactiveCommands { block.apply(it) }
    }

    inline fun <T> useCommands(crossinline block: (RedisCommands<String, String>) -> T): T? {
        return useConnection(
            { block(it.sync()) }
        )
    }

    inline fun <T> useAsyncCommands(crossinline block: (RedisAsyncCommands<String, String>) -> T): CompletableFuture<T?> {
        return useAsyncConnection(
            { block(it.async()) }
        )
    }

    inline fun <T> useReactiveCommands(crossinline block: (RedisReactiveCommands<String, String>) -> T): CompletableFuture<T?> {
        return useAsyncConnection(
            { block(it.reactive()) }
        )
    }

    override fun <T> usePubSubCommands(block: Function<RedisPubSubCommands<String, String>, T>): T? {
        return block.apply(pubSubConnection.sync())
    }

    override fun <T> usePubSubAsyncCommands(block: Function<RedisPubSubAsyncCommands<String, String>, T>): T? {
        return block.apply(pubSubConnection.async())
    }

    override fun <T> usePubSubReactiveCommands(block: Function<RedisPubSubReactiveCommands<String, String>, T>): T? {
        return block.apply(pubSubConnection.reactive())
    }

    // sync
    override fun <T> useConnection(
        use: Function<StatefulRedisConnection<String, String>, T>?,
        useCluster: Function<StatefulRedisClusterConnection<String, String>, T>?
    ): T? {
        return if (enabledSlaves) {
            val connection = try {
                masterReplicaPool.borrowObject()
            } catch (e: Exception) {
                warning("Failed to borrow connection: ${e.message}")
                RedisMonitor.recordCommand(false)
                return null
            }

            val startTime = System.currentTimeMillis()
            try {
                val result = use!!.apply(connection)
                RedisMonitor.recordCommand(true, System.currentTimeMillis() - startTime)
                result
            } catch (e: Exception) {
                warning("Redis operation failed: ${e.message}")
                RedisMonitor.recordCommand(false)
                null
            } finally {
                masterReplicaPool.returnObject(connection)
            }
        } else {
            val connection = try {
                pool.borrowObject()
            } catch (e: Exception) {
                warning("Failed to borrow connection: ${e.message}")
                RedisMonitor.recordCommand(false)
                return null
            }

            val startTime = System.currentTimeMillis()
            try {
                val result = use!!.apply(connection)
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
    }

    // async
    override fun <T> useAsyncConnection(
        use: Function<StatefulRedisConnection<String, String>, T>?,
        useCluster: Function<StatefulRedisClusterConnection<String, String>, T>?
    ): CompletableFuture<T?> {
        return if (enabledSlaves) {
            try {
                val startTime = System.currentTimeMillis()
                masterAsyncReplicaPool.acquire().thenApply { obj ->
                    try {
                        val result = use!!.apply(obj)
                        RedisMonitor.recordCommand(true, System.currentTimeMillis() - startTime)
                        result
                    } catch (e: Throwable) {
                        warning("Redis operation failed: ${e.message}")
                        RedisMonitor.recordCommand(false)
                        null
                    } finally {
                        masterAsyncReplicaPool.release(obj)
                    }
                }.exceptionally { e ->
                    warning("Failed to acquire connection: ${e.message}")
                    RedisMonitor.recordCommand(false)
                    null
                }
            } catch (e: Throwable) {
                warning("Failed to acquire connection: ${e.message}")
                RedisMonitor.recordCommand(false)
                return CompletableFuture.completedFuture(null)
            }
        } else {
            try {
                val startTime = System.currentTimeMillis()
                asyncPool.acquire().thenApply { obj ->
                    try {
                        val result = use!!.apply(obj)
                        RedisMonitor.recordCommand(true, System.currentTimeMillis() - startTime)
                        result
                    } catch (e: Throwable) {
                        warning("Redis operation failed: ${e.message}")
                        RedisMonitor.recordCommand(false)
                        null
                    } finally {
                        asyncPool.release(obj)
                    }
                }.exceptionally { e ->
                    warning("Failed to acquire connection: ${e.message}")
                    RedisMonitor.recordCommand(false)
                    null
                }
            } catch (e: Throwable) {
                warning("Failed to acquire connection: ${e.message}")
                RedisMonitor.recordCommand(false)
                return CompletableFuture.completedFuture(null)
            }
        }
    }
}