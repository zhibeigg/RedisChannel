package com.gitee.redischannel.api

import io.lettuce.core.api.StatefulConnection
import java.util.concurrent.CompletableFuture
import java.util.function.Function

interface RedisChannelAPI<T : StatefulConnection<*, *>> {
    
    // 阻塞同步
    fun <V> useConnection(use: Function<T, V>): V?

    // 非阻塞异步
    fun <V> useAsyncConnection(use: Function<T, V>): CompletableFuture<V?>
}