package com.gitee.redischannel.util

import com.gitee.redischannel.api.RedisChannelAPI
import com.gitee.redischannel.api.RedisClusterCommandAPI
import com.gitee.redischannel.api.RedisCommandAPI

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