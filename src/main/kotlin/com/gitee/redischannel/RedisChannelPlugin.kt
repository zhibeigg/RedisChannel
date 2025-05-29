package com.gitee.redischannel

import com.gitee.redischannel.RedisChannelPlugin.Type.CLUSTER
import com.gitee.redischannel.RedisChannelPlugin.Type.SINGLE
import com.gitee.redischannel.api.RedisChannelAPI
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

    val redis by lazy { RedisConfig(config.getConfigurationSection("redis")!!) }

    lateinit var type: Type
        private set

    enum class Type {
        CLUSTER, SINGLE;
    }

    internal fun init(type: Type) {
        this.type = type
    }

    val api: RedisChannelAPI<*>
        get() = when (type) {
            CLUSTER -> ClusterRedisManager
            SINGLE -> RedisManager
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