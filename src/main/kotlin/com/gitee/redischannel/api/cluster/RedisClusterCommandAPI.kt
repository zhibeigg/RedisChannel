package com.gitee.redischannel.api.cluster

import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands
import java.util.concurrent.CompletionStage
import java.util.function.Function

/**
 * Redis Cluster 非阻塞命令 API。
 *
 * @since 2.14.12
 */
interface RedisClusterCommandAPI {

    fun <T> executeClusterAsync(
        action: Function<RedisClusterAsyncCommands<String, String>, out CompletionStage<T>>
    ): CompletionStage<T>
}
