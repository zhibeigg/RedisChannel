package com.gitee.redischannel.api.cluster

import io.lettuce.core.cluster.pubsub.api.async.RedisClusterPubSubAsyncCommands
import io.lettuce.core.cluster.pubsub.api.reactive.RedisClusterPubSubReactiveCommands
import io.lettuce.core.cluster.pubsub.api.sync.RedisClusterPubSubCommands
import java.util.function.Function

interface RedisClusterPubSubAPI {

    /**
     * 使用命令
     * @param block 匿名函数
     * @return [T]
     * */
    fun <T> usePubSubCommands(block: Function<RedisClusterPubSubCommands<String, String>, T>): T?

    /**
     * 使用异步命令
     * @param block 匿名函数
     * @return [T]
     * */
    fun <T> usePubSubAsyncCommands(block: Function<RedisClusterPubSubAsyncCommands<String, String>, T>): T?

    /**
     * 使用反应式命令
     * @param block 匿名函数
     * @return [T]
     * */
    fun <T> usePubSubReactiveCommands(block: Function<RedisClusterPubSubReactiveCommands<String, String>, T>): T?
}