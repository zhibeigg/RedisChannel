package com.gitee.redischannel.core

import com.gitee.redischannel.RedisChannelPlugin
import com.gitee.redischannel.api.JsonData
import com.gitee.redischannel.api.RedisChannelAPI
import com.gitee.redischannel.api.RedisCommandAPI
import io.lettuce.core.ClientOptions
import io.lettuce.core.RedisClient
import io.lettuce.core.SetArgs
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import io.lettuce.core.api.sync.RedisCommands
import io.lettuce.core.codec.StringCodec
import io.lettuce.core.masterreplica.MasterReplica
import io.lettuce.core.masterreplica.StatefulRedisMasterReplicaConnection
import io.lettuce.core.resource.DefaultClientResources
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
internal object RedisManager: RedisChannelAPI, RedisCommandAPI {

    lateinit var client: RedisClient
    lateinit var pool: GenericObjectPool<StatefulRedisConnection<String, String>>
    lateinit var masterReplicaPool: GenericObjectPool<StatefulRedisMasterReplicaConnection<String, String>>

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

            masterReplicaPool = ConnectionPoolSupport.createGenericObjectPool(
                { MasterReplica.connect(client, StringCodec.UTF8, uri).apply {
                    readFrom = slaves.readFrom
                } },
                redis.pool.slavesPoolConfig()
            )
        } else {
            pool = ConnectionPoolSupport.createGenericObjectPool(
                { client.connect() },
                redis.pool.poolConfig()
            )
        }
    }

    @Awake(LifeCycle.DISABLE)
    private fun stop() {
        if (RedisChannelPlugin.type == RedisChannelPlugin.Type.SINGLE) {
            if (enabledSlaves) {
                masterReplicaPool.close()
            } else {
                pool.close()
            }
            client.shutdown()
        }
    }

    override fun <T> useCommands(block: Function<RedisCommands<String, String>, T>): T? {
        return useCommands { block.apply(it) }
    }

    override fun <T> useAsyncCommands(block: Function<RedisAsyncCommands<String, String>, T>): T? {
        return useAsyncCommands { block.apply(it) }
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

    fun <T> useAsyncCommands(block: (RedisAsyncCommands<String, String>) -> T): T? {
        if (enabledSlaves) {
            val connection = try {
                masterReplicaPool.borrowObject()
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
                block(connection.async())
            } catch (e: Exception) {
                warning("Redis operation failed: ${e.message}")
                null
            } finally {
                pool.returnObject(connection)
            }
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
}