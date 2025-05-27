package com.gitee.redischannel.util

import com.gitee.redischannel.RedisChannelPlugin.Type.CLUSTER
import com.gitee.redischannel.RedisChannelPlugin.Type.SINGLE
import com.gitee.redischannel.RedisChannelPlugin.type
import com.gitee.redischannel.api.RedisChannelAPI
import com.gitee.redischannel.api.cluster.RedisClusterCommandAPI
import com.gitee.redischannel.api.RedisCommandAPI
import com.gitee.redischannel.api.RedisPubSubAPI
import com.gitee.redischannel.api.cluster.RedisClusterPubSubAPI
import com.gitee.redischannel.core.ClusterRedisManager
import com.gitee.redischannel.core.RedisManager

/**
 * 获取命令API
 * */
fun RedisChannelAPI.commandAPI(): RedisCommandAPI {
    val manager = when (type) {
        CLUSTER -> ClusterRedisManager
        SINGLE -> RedisManager
    }
    return manager as RedisCommandAPI
}

/**
 * 获取发布/订阅API
 * */
fun RedisChannelAPI.pubSubAPI(): RedisPubSubAPI {
    val manager = when (type) {
        CLUSTER -> ClusterRedisManager
        SINGLE -> RedisManager
    }
    return manager as RedisPubSubAPI
}