package com.gitee.redischannel.core

import com.gitee.redischannel.api.RedisChannelAPI
import com.gitee.redischannel.api.RedisCommandAPI
import com.gitee.redischannel.api.RedisPubSubAPI
import com.gitee.redischannel.api.cluster.RedisClusterCommandAPI
import com.gitee.redischannel.api.cluster.RedisClusterPubSubAPI

internal object RedisApiBridge {

    @JvmStatic
    fun lifecycle(): RedisChannelAPI = RedisChannelFacade

    @JvmStatic
    fun commands(): RedisCommandAPI = RedisManager

    @JvmStatic
    fun clusterCommands(): RedisClusterCommandAPI = ClusterRedisManager

    @JvmStatic
    fun pubSub(): RedisPubSubAPI = RedisManager

    @JvmStatic
    fun clusterGeneralPubSub(): RedisPubSubAPI = ClusterRedisManager

    @JvmStatic
    fun clusterPubSub(): RedisClusterPubSubAPI = ClusterRedisManager
}
