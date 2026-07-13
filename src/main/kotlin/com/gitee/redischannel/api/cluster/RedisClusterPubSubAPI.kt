package com.gitee.redischannel.api.cluster

import io.lettuce.core.cluster.pubsub.api.async.RedisClusterPubSubAsyncCommands
import java.util.concurrent.CompletionStage
import java.util.function.Function

/**
 * Redis Cluster 非阻塞 Pub/Sub API。
 *
 * @since 2.14.12
 */
interface RedisClusterPubSubAPI {

    fun <T> executeClusterPubSubAsync(
        action: Function<RedisClusterPubSubAsyncCommands<String, String>, out CompletionStage<T>>
    ): CompletionStage<T>
}
