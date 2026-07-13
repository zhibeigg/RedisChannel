package com.gitee.redischannel

import com.gitee.redischannel.api.RedisChannelAPI
import com.gitee.redischannel.api.RedisCommandAPI
import com.gitee.redischannel.api.RedisDeploymentMode
import com.gitee.redischannel.api.RedisPubSubAPI
import com.gitee.redischannel.api.cluster.RedisClusterCommandAPI
import com.gitee.redischannel.api.cluster.RedisClusterPubSubAPI

/**
 * RedisChannel API v2 的稳定服务入口。
 *
 * @since 2.14.12
 */
object RedisChannelPlugin {

    private val channelApi by lazy { resolve("lifecycle") as RedisChannelAPI }
    private val redisCommandApi by lazy { resolve("commands") as RedisCommandAPI }
    private val redisClusterCommandApi by lazy { resolve("clusterCommands") as RedisClusterCommandAPI }
    private val redisPubSubApi by lazy { resolve("pubSub") as RedisPubSubAPI }
    private val redisClusterGeneralPubSubApi by lazy { resolve("clusterGeneralPubSub") as RedisPubSubAPI }
    private val redisClusterPubSubApi by lazy { resolve("clusterPubSub") as RedisClusterPubSubAPI }

    val api: RedisChannelAPI
        get() = channelApi

    val initialized: Boolean
        get() = api.lifecycle().initialized

    fun commandAPI(): RedisCommandAPI = redisCommandApi

    fun clusterCommandAPI(): RedisClusterCommandAPI = redisClusterCommandApi

    fun pubSubAPI(): RedisPubSubAPI {
        return if (api.lifecycle().mode == RedisDeploymentMode.CLUSTER) {
            redisClusterGeneralPubSubApi
        } else {
            redisPubSubApi
        }
    }

    fun clusterPubSubAPI(): RedisClusterPubSubAPI = redisClusterPubSubApi

    private fun resolve(method: String): Any {
        val bridge = Class.forName("com.gitee.redischannel.core.RedisApiBridge")
        return bridge.getMethod(method).invoke(null)
            ?: error("RedisChannel API bridge returned null for $method")
    }
}
