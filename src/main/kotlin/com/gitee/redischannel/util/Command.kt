package com.gitee.redischannel.util

import com.gitee.redischannel.api.RedisChannelAPI
import com.gitee.redischannel.api.cluster.RedisClusterCommandAPI
import com.gitee.redischannel.api.RedisCommandAPI
import com.gitee.redischannel.api.RedisPubSubAPI
import com.gitee.redischannel.api.cluster.RedisClusterPubSubAPI

/**
 * 获取集群命令API
 * @throws ClassCastException 当你使用的不是集群模式时
 * */
fun RedisChannelAPI.clusterCommandAPI(): RedisClusterCommandAPI = this as RedisClusterCommandAPI

/**
 * 获取命令API
 * @throws ClassCastException 当你使用的是集群模式时
 * */
fun RedisChannelAPI.commandAPI(): RedisCommandAPI = this as RedisCommandAPI

/**
 * 获取集群发布/订阅API
 * @throws ClassCastException 当你使用的不是集群模式时
 * */
fun RedisChannelAPI.clusterPubSubAPI(): RedisClusterPubSubAPI = this as RedisClusterPubSubAPI

/**
 * 获取发布/订阅API
 * @throws ClassCastException 当你使用的是集群模式时
 * */
fun RedisChannelAPI.pubSubAPI(): RedisPubSubAPI = this as RedisPubSubAPI