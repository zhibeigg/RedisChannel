package com.gitee.redischannel

import com.gitee.redischannel.RedisChannelPlugin.Type.CLUSTER
import com.gitee.redischannel.RedisChannelPlugin.Type.SINGLE
import com.gitee.redischannel.api.RedisChannelAPI
import com.gitee.redischannel.api.RedisCommandAPI
import com.gitee.redischannel.api.RedisPubSubAPI
import com.gitee.redischannel.api.cluster.RedisClusterCommandAPI
import com.gitee.redischannel.api.cluster.RedisClusterPubSubAPI
import com.gitee.redischannel.core.ClusterRedisManager
import com.gitee.redischannel.core.RedisConfig
import com.gitee.redischannel.core.RedisManager
import taboolib.common.platform.Plugin
import taboolib.common.platform.function.pluginVersion
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object RedisChannelPlugin : Plugin() {

    @Config(migrate = true)
    lateinit var config: Configuration

    lateinit var redis: RedisConfig
        private set

    var type: Type? = null
        internal set

    enum class Type {
        CLUSTER, SINGLE;
    }

    internal fun init(type: Type) {
        this.type = type
    }

    internal fun reloadConfig() {
        config.reload()
        redis = RedisConfig(config.getConfigurationSection("redis")!!)
    }

    override fun onLoad() {
        redis = RedisConfig(config.getConfigurationSection("redis")!!)
    }

    val api: RedisChannelAPI
        get() = when (type) {
            CLUSTER -> ClusterRedisManager
            SINGLE -> RedisManager
            null -> error("Redis 连接未初始化")
        }

    /**
     * 获取集群命令API
     * @throws ClassCastException 当你使用的不是集群模式时
     * */
    fun clusterCommandAPI(): RedisClusterCommandAPI {
        return api as RedisClusterCommandAPI
    }

    /**
     * 获取命令API
     * @throws ClassCastException 当你使用的是集群模式时
     * */
    fun commandAPI(): RedisCommandAPI {
        return api as RedisCommandAPI
    }

    /**
     * 获取集群发布/订阅API
     * @throws ClassCastException 当你使用的不是集群模式时
     * */
    fun clusterPubSubAPI(): RedisClusterPubSubAPI {
        return api as RedisClusterPubSubAPI
    }

    /**
     * 获取发布/订阅API 集群的也可以用
     * */
    fun pubSubAPI(): RedisPubSubAPI {
        return api as RedisPubSubAPI
    }

    override fun onEnable() {
        println()
        println("§9 ______     ______     _____     __     ______")
        println("§9/\\  == \\   /\\  ___\\   /\\  __-.  /\\ \\   /\\  ___\\         §8RedisChannel §eversion§7: §e$pluginVersion")
        println("§9\\ \\  __<   \\ \\  __\\   \\ \\ \\/\\ \\ \\ \\ \\  \\ \\___  \\        §7by. §bzhibei")
        println("§9 \\ \\_\\ \\_\\  \\ \\_____\\  \\ \\____-  \\ \\_\\  \\/\\_____\\")
        println("§9  \\/_/ /_/   \\/_____/   \\/____/   \\/_/   \\/_____/")
        println()
    }
}