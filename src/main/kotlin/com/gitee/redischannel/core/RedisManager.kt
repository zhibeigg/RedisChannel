package com.gitee.redischannel.core

import com.gitee.redischannel.RedisChannelPlugin
import com.gitee.redischannel.api.RedisChannelAPI
import com.gitee.redischannel.api.RedisCommandAPI
import com.gitee.redischannel.api.RedisPubSubAPI
import com.gitee.redischannel.api.proxy.ProxyAPI
import com.gitee.redischannel.api.proxy.RedisProxyAsyncCommand
import com.gitee.redischannel.api.proxy.RedisProxyCommand
import io.lettuce.core.ClientOptions
import io.lettuce.core.RedisClient
import io.lettuce.core.SetArgs
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import io.lettuce.core.api.reactive.RedisReactiveCommands
import io.lettuce.core.api.sync.RedisCommands
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
import taboolib.common.LifeCycle
import taboolib.common.env.RuntimeDependencies
import taboolib.common.env.RuntimeDependency
import taboolib.common.platform.Awake
import taboolib.common.platform.function.warning
import taboolib.platform.bukkit.Parallel
import java.util.concurrent.CompletableFuture
import java.util.function.Function

@RuntimeDependencies(
    RuntimeDependency(
        "!io.lettuce:lettuce-core:6.6.0.RELEASE",
        test = "!io.lettuce.core.RedisURI",
        relocate = ["!io.netty", "!com.gitee.redischannel.netty", "!org.apache.commons.pool2", "!com.gitee.redischannel.commons.pool2"],
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
internal object RedisManager: RedisChannelAPI, RedisCommandAPI, RedisPubSubAPI, ProxyAPI {

    lateinit var client: RedisClient

    lateinit var pool: GenericObjectPool<StatefulRedisConnection<String, String>>
    lateinit var asyncPool: BoundedAsyncPool<StatefulRedisConnection<String, String>>

    lateinit var masterReplicaPool: GenericObjectPool<StatefulRedisMasterReplicaConnection<String, String>>
    lateinit var masterAsyncReplicaPool: BoundedAsyncPool<StatefulRedisMasterReplicaConnection<String, String>>

    lateinit var pubSubConnection: StatefulRedisPubSubConnection<String, String>

    var enabledSlaves = false

    @Parallel(runOn = LifeCycle.ENABLE)
    private fun start() {
        val redis = RedisChannelPlugin.redis
        if (redis.enableCluster) return
        RedisChannelPlugin.init(RedisChannelPlugin.Type.SINGLE)

        val resource = DefaultClientResources.builder()

        if (redis.ioThreadPoolSize != 0) {
            resource.ioThreadPoolSize(4)
        }
        if (redis.computationThreadPoolSize != 0) {
            resource.computationThreadPoolSize(4)
        }

        val clientOptions = ClientOptions.builder()
            .autoReconnect(redis.autoReconnect)
            .pingBeforeActivateConnection(redis.pingBeforeActivateConnection)

        if (redis.ssl) {
            clientOptions.sslOptions(redis.sslOptions)
        }
        val uri = redis.redisURIBuilder().build()

        client = RedisClient.create(resource.build(), uri).apply {
            options = clientOptions.build()
        }

        if (redis.enableSlaves) {
            enabledSlaves = true
            val slaves = redis.slaves

            masterAsyncReplicaPool = AsyncConnectionPoolSupport.createBoundedObjectPool(
                { MasterReplica.connectAsync(client, StringCodec.UTF8, uri).whenComplete { v, _ ->
                    v.readFrom = slaves.readFrom
                } },
                redis.pool.asyncSlavesPoolConfig()
            )
            masterReplicaPool = ConnectionPoolSupport.createGenericObjectPool(
                { MasterReplica.connect(client, StringCodec.UTF8, uri).apply {
                    readFrom = slaves.readFrom
                } },
                redis.pool.slavesPoolConfig()
            )
        } else {

            asyncPool = AsyncConnectionPoolSupport.createBoundedObjectPool(
                { client.connectAsync(StringCodec.UTF8, uri) },
                redis.pool.asyncPoolConfig()
            )
            pool = ConnectionPoolSupport.createGenericObjectPool(
                { client.connect() },
                redis.pool.poolConfig()
            )
        }
        pubSubConnection = client.connectPubSub()
    }

    @Awake(LifeCycle.DISABLE)
    private fun stop() {
        if (RedisChannelPlugin.type == RedisChannelPlugin.Type.SINGLE) {
            if (enabledSlaves) {
                masterAsyncReplicaPool.close()
                masterReplicaPool.close()
            } else {
                asyncPool.close()
                pool.close()
            }
            client.shutdown()
        }
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

    fun <T> useCommands(block: (RedisCommands<String, String>) -> T): T? {
        if (enabledSlaves) {
            val connection = try {
                masterReplicaPool.borrowObject()
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
                masterReplicaPool.returnObject(connection)
            }
        } else {
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
    }

    fun <T> useAsyncCommands(block: (RedisAsyncCommands<String, String>) -> T): CompletableFuture<T?> {
        return if (enabledSlaves) {
            try {
                masterAsyncReplicaPool.acquire().thenApply { obj ->
                    try {
                        block(obj.async())
                    } catch (e: Throwable) {
                        warning("Redis operation failed: ${e.message}")
                        return@thenApply null
                    } finally {
                        masterAsyncReplicaPool.release(obj)
                    }
                }
            } catch (e: Throwable) {
                warning("Failed to acquire connection: ${e.message}")
                CompletableFuture.completedFuture(null)
            }
        } else {
            try {
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
    }

    fun <T> useReactiveCommands(block: (RedisReactiveCommands<String, String>) -> T): CompletableFuture<T?> {
        return if (enabledSlaves) {
            try {
                masterAsyncReplicaPool.acquire().thenApply { obj ->
                    try {
                        block(obj.reactive())
                    } catch (e: Throwable) {
                        warning("Redis operation failed: ${e.message}")
                        return@thenApply null
                    } finally {
                        masterAsyncReplicaPool.release(obj)
                    }
                }
            } catch (e: Throwable) {
                warning("Failed to acquire connection: ${e.message}")
                CompletableFuture.completedFuture(null)
            }
        } else {
            try {
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

    override fun getProxyCommand(): RedisProxyCommand<String, String> {
        val command = if (enabledSlaves) {
            val connection = try {
                masterReplicaPool.borrowObject()
            } catch (e: Exception) {
                warning("Failed to borrow connection: ${e.message}")
                null
            }

            try {
                connection?.sync()
            } catch (e: Exception) {
                warning("Redis operation failed: ${e.message}")
                null
            } finally {
                masterReplicaPool.returnObject(connection)
            }
        } else {
            val connection = try {
                pool.borrowObject()
            } catch (e: Exception) {
                warning("Failed to borrow connection: ${e.message}")
                null
            }

            try {
                connection?.sync()
            } catch (e: Exception) {
                warning("Redis operation failed: ${e.message}")
                null
            } finally {
                pool.returnObject(connection)
            }
        }
        return RedisProxyCommand(command!!)
    }

    override fun getProxyAsyncCommand(): CompletableFuture<RedisProxyAsyncCommand<String, String>> {
        val command = if (enabledSlaves) {
            try {
                masterAsyncReplicaPool.acquire().thenApply { obj ->
                    try {
                        obj.async()
                    } catch (e: Throwable) {
                        warning("Redis operation failed: ${e.message}")
                        return@thenApply null
                    } finally {
                        masterAsyncReplicaPool.release(obj)
                    }
                }
            } catch (e: Throwable) {
                warning("Failed to acquire connection: ${e.message}")
                return CompletableFuture.completedFuture(null)
            }
        } else {
            try {
                asyncPool.acquire().thenApply { obj ->
                    try {
                        obj.async()
                    } catch (e: Throwable) {
                        warning("Redis operation failed: ${e.message}")
                        return@thenApply null
                    } finally {
                        asyncPool.release(obj)
                    }
                }
            } catch (e: Throwable) {
                warning("Failed to acquire connection: ${e.message}")
                return CompletableFuture.completedFuture(null)
            }
        }
        return command.thenApply {
            RedisProxyAsyncCommand(it)
        }
    }
}