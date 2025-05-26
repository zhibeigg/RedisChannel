package com.gitee.redischannel.api

import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands
import io.lettuce.core.cluster.api.sync.RedisClusterCommands
import java.util.function.Function

interface RedisClusterCommandAPI {

    /**
     * 使用命令
     * @param block 匿名函数
     * @return [T]
     * */
    fun <T> useCommands(block: Function<RedisClusterCommands<String, String>, T>): T?

    /**
     * 使用异步命令
     * @param block 匿名函数
     * @return [T]
     * */
    fun <T> useAsyncCommands(block: Function<RedisClusterAsyncCommands<String, String>, T>): T?
}