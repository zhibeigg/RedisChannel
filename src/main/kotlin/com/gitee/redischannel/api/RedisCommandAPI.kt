package com.gitee.redischannel.api

import io.lettuce.core.api.async.RedisAsyncCommands
import io.lettuce.core.api.reactive.RedisReactiveCommands
import io.lettuce.core.api.sync.RedisCommands
import java.util.concurrent.CompletableFuture
import java.util.function.Function

interface RedisCommandAPI {

    /**
     * 使用命令
     * @param block 匿名函数
     * @return [T]
     * */
    fun <T> useCommands(block: Function<RedisCommands<String, String>, T>): T?

    /**
     * 使用异步命令
     * @param block 匿名函数
     * @return [T]
     * */
    fun <T> useAsyncCommands(block: Function<RedisAsyncCommands<String, String>, T>): CompletableFuture<T?>

    /**
     * 使用反应式命令
     * @param block 匿名函数
     * @return [T]
     * */
    fun <T> useReactiveCommands(block: Function<RedisReactiveCommands<String, String>, T>): CompletableFuture<T?>
}