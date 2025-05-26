package com.gitee.redischannel.api

import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands
import io.lettuce.core.pubsub.api.reactive.RedisPubSubReactiveCommands
import io.lettuce.core.pubsub.api.sync.RedisPubSubCommands
import java.util.function.Function

interface RedisPubSubAPI {

    /**
     * 使用命令
     * @param block 匿名函数
     * @return [T]
     * */
    fun <T> usePubSubCommands(block: Function<RedisPubSubCommands<String, String>, T>): T?

    /**
     * 使用异步命令
     * @param block 匿名函数
     * @return [T]
     * */
    fun <T> usePubSubAsyncCommands(block: Function<RedisPubSubAsyncCommands<String, String>, T>): T?

    /**
     * 使用反应式命令
     * @param block 匿名函数
     * @return [T]
     * */
    fun <T> usePubSubReactiveCommands(block: Function<RedisPubSubReactiveCommands<String, String>, T>): T?
}