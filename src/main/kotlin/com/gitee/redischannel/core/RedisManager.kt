package com.gitee.redischannel.core

import com.gitee.redischannel.api.JsonData
import com.gitee.redischannel.api.RedisChannelAPI
import com.gitee.redischannel.api.RedisFuture
import com.gitee.redischannel.util.files
import io.lettuce.core.ReadFrom
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.SetArgs
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import io.lettuce.core.api.sync.RedisCommands
import io.lettuce.core.codec.StringCodec
import io.lettuce.core.masterreplica.MasterReplica
import io.lettuce.core.support.ConnectionPoolSupport
import org.apache.commons.pool2.impl.GenericObjectPool
import org.apache.commons.pool2.impl.GenericObjectPoolConfig
import taboolib.common.LifeCycle
import taboolib.common.env.RuntimeDependencies
import taboolib.common.env.RuntimeDependency
import taboolib.common.platform.Awake
import taboolib.common.platform.function.warning
import taboolib.common5.cint
import taboolib.library.configuration.ConfigurationSection
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration
import taboolib.platform.bukkit.Parallel
import java.util.function.Function
import kotlin.time.Duration
import kotlin.time.toJavaDuration


@RuntimeDependencies(
    RuntimeDependency(
        "!io.lettuce:lettuce-core:6.6.0.RELEASE",
        test = "!com.gitee.redischannel.lettuce.core.RedisURI",
        relocate = ["!io.lettuce", "!com.gitee.redischannel.lettuce", "!io.netty", "!com.gitee.redischannel.netty", "!org.apache.commons.pool2", "!com.gitee.redischannel.commons.pool2"],
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
object RedisManager: RedisChannelAPI {

    @Config(migrate = true)
    lateinit var config: Configuration

    val redis by lazy { Redis(config.getConfigurationSection("redis")!!) }

    class Redis(val configurationSection: ConfigurationSection) {

        val host = configurationSection.getString("host") ?: error("host must be set")
        val port = configurationSection.getInt("port", 6379)
        val password = configurationSection.getString("password")
        val ssl = configurationSection.getBoolean("ssl")
        val timeout = Duration.parse(configurationSection.getString("timeout") ?: error("timeout must be set"))
        val database = configurationSection.getInt("database", 0)

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
            val timeBetweenEvictionRuns = configurationSection.getString("timeBetweenEvictionRuns")?.let { Duration.parse(it) }
            val minEvictableIdleDuration = configurationSection.getString("minEvictableIdleDuration")?.let { Duration.parse(it) }
            val softMinEvictableIdleDuration = configurationSection.getString("softMinEvictableIdleDuration")?.let { Duration.parse(it) }
            val numTestsPerEvictionRun = configurationSection.getInt("numTestsPerEvictionRun", 3)

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
            get() = Cluster()

        class Cluster() {

            val nodes = files("clusters", "cluster0.yml") {
                val configuration = Configuration.loadFromFile(it)
                Node(configuration)
            }

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

    lateinit var client: RedisClient
    lateinit var pool: GenericObjectPool<StatefulRedisConnection<String, String>>

    @Parallel(runOn = LifeCycle.ENABLE)
    private fun start() {
        val redis = redis

        client = if (redis.enableCluster) {
            val cluster = redis.cluster

            val uris = cluster.nodes.map {
                it.redisURIBuilder().build()
            }

            val client = RedisClient.create()
            val masterSlaveClient = MasterReplica.connect(client, StringCodec.UTF8, uris)

            if (redis.enableSlaves) {
                val slaves = redis.slaves

                // 配置拓扑动态发现
                masterSlaveClient.readFrom = slaves.readFrom
            }
            client
        } else {
            val uri = redis.redisURIBuilder().build()

            if (redis.enableSlaves) {
                val client = RedisClient.create()
                val slaves = redis.slaves

                val masterSlaveClient = MasterReplica.connect(client, StringCodec.UTF8, uri)
                // 配置拓扑动态发现
                masterSlaveClient.readFrom = slaves.readFrom
                client
            } else {
                RedisClient.create(uri)
            }
        }

        pool = ConnectionPoolSupport.createGenericObjectPool(
            { client.connect() },
            redis.pool.poolConfig()
        )
    }

    @Awake(LifeCycle.DISABLE)
    private fun stop() {
        pool.close()
        client.shutdown()
    }

    fun <T> useCommands(block: (RedisCommands<String, String>) -> T): T? {
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

    fun <T> useAsyncCommands(block: (RedisAsyncCommands<String, String>) -> T): T? {
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
     * 获取缓存数据
     * */
    override fun get(key: String): String? {
        return useCommands { commands ->
            commands.get(key)
        }
    }


    override fun asyncGet(key: String): RedisFuture<String?>? {
        return RedisFuture(useAsyncCommands { commands ->
            commands.get(key)
        } ?: return null)
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