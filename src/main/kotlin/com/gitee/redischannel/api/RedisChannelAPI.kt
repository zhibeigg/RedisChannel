package com.gitee.redischannel.api

import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import java.util.concurrent.CompletableFuture
import java.util.function.Function

interface RedisChannelAPI {
    
    // 同步
    fun <T> useConnection(
        use: Function<StatefulRedisConnection<String, String>, T>? = null,
        useCluster: Function<StatefulRedisClusterConnection<String, String>, T>? = null
    ): T?

    // 异步
    fun <T> useAsyncConnection(
        use: Function<StatefulRedisConnection<String, String>, T>? = null,
        useCluster: Function<StatefulRedisClusterConnection<String, String>, T>? = null
    ): CompletableFuture<T?>
}