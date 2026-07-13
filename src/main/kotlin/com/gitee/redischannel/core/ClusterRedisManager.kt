package com.gitee.redischannel.core

import com.gitee.redischannel.api.RedisPubSubAPI
import com.gitee.redischannel.api.cluster.RedisClusterCommandAPI
import com.gitee.redischannel.api.cluster.RedisClusterPubSubAPI
import com.gitee.redischannel.api.exception.RedisUnavailableException
import com.gitee.redischannel.core.lifecycle.RedisLifecycleCoordinator
import com.gitee.redischannel.util.CompletionStages
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands
import io.lettuce.core.cluster.pubsub.api.async.RedisClusterPubSubAsyncCommands
import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands
import java.util.concurrent.CompletionStage
import java.util.function.Function

internal object ClusterRedisManager : RedisClusterCommandAPI, RedisClusterPubSubAPI, RedisPubSubAPI {

    override fun <T> executeClusterAsync(
        action: Function<RedisClusterAsyncCommands<String, String>, out CompletionStage<T>>
    ): CompletionStage<T> = RedisLifecycleCoordinator.runtime()?.executeClusterAsync(action)
        ?: unavailable()

    override fun <T> executeClusterPubSubAsync(
        action: Function<RedisClusterPubSubAsyncCommands<String, String>, out CompletionStage<T>>
    ): CompletionStage<T> = RedisLifecycleCoordinator.runtime()?.executeClusterPubSubAsync(action)
        ?: unavailable()

    override fun <T> executePubSubAsync(
        action: Function<RedisPubSubAsyncCommands<String, String>, out CompletionStage<T>>
    ): CompletionStage<T> = RedisLifecycleCoordinator.runtime()?.executePubSubAsync(action)
        ?: unavailable()

    private fun <T> unavailable(): CompletionStage<T> {
        return CompletionStages.failed(RedisUnavailableException(RedisLifecycleCoordinator.lifecycle().state))
    }
}
