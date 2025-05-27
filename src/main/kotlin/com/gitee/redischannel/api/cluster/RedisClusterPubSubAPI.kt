package com.gitee.redischannel.api.cluster

import com.gitee.redischannel.api.RedisPubSubAPI
import io.lettuce.core.cluster.pubsub.api.async.RedisClusterPubSubAsyncCommands
import io.lettuce.core.cluster.pubsub.api.reactive.RedisClusterPubSubReactiveCommands
import io.lettuce.core.cluster.pubsub.api.sync.RedisClusterPubSubCommands
import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands
import io.lettuce.core.pubsub.api.reactive.RedisPubSubReactiveCommands
import io.lettuce.core.pubsub.api.sync.RedisPubSubCommands
import java.util.function.Function

interface RedisClusterPubSubAPI: RedisPubSubAPI {

    /**
     * 使用命令
     * @param block 匿名函数
     * @return [T]
     * */
    fun <T> useClusterPubSubCommands(block: Function<RedisClusterPubSubCommands<String, String>, T>): T?

    /**
     * 使用异步命令
     * @param block 匿名函数
     * @return [T]
     * */
    fun <T> useClusterPubSubAsyncCommands(block: Function<RedisClusterPubSubAsyncCommands<String, String>, T>): T?

    /**
     * 使用反应式命令
     * @param block 匿名函数
     * @return [T]
     * */
    fun <T> useClusterPubSubReactiveCommands(block: Function<RedisClusterPubSubReactiveCommands<String, String>, T>): T?

    override fun <T> usePubSubCommands(block: Function<RedisPubSubCommands<String, String>, T>): T? {
        return useClusterPubSubCommands {
            block.apply(it)
        }
    }

    override fun <T> usePubSubAsyncCommands(block: Function<RedisPubSubAsyncCommands<String, String>, T>): T? {
        return useClusterPubSubAsyncCommands {
            block.apply(it)
        }
    }

    override fun <T> usePubSubReactiveCommands(block: Function<RedisPubSubReactiveCommands<String, String>, T>): T? {
        return useClusterPubSubReactiveCommands {
            block.apply(it)
        }
    }
}