package com.gitee.redischannel.core.runtime

import com.gitee.redischannel.api.RedisDeploymentMode
import com.gitee.redischannel.api.exception.WrongDeploymentModeException
import com.gitee.redischannel.core.RedisConfig
import com.gitee.redischannel.core.RedisMonitor
import com.gitee.redischannel.util.CompletionStages
import io.lettuce.core.api.async.RedisAsyncCommands
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands
import io.lettuce.core.cluster.pubsub.api.async.RedisClusterPubSubAsyncCommands
import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.function.Function

internal interface RedisRuntime {

    val generation: Long
    val mode: RedisDeploymentMode
    val config: RedisConfig

    fun <T> executeAsync(action: Function<RedisAsyncCommands<String, String>, out CompletionStage<T>>): CompletionStage<T> =
        CompletionStages.failed(WrongDeploymentModeException(RedisDeploymentMode.SINGLE, mode))

    fun <T> executeClusterAsync(action: Function<RedisClusterAsyncCommands<String, String>, out CompletionStage<T>>): CompletionStage<T> =
        CompletionStages.failed(WrongDeploymentModeException(RedisDeploymentMode.CLUSTER, mode))

    fun <T> executePubSubAsync(action: Function<RedisPubSubAsyncCommands<String, String>, out CompletionStage<T>>): CompletionStage<T> =
        CompletionStages.failed(WrongDeploymentModeException(RedisDeploymentMode.SINGLE, mode))

    fun <T> executeClusterPubSubAsync(action: Function<RedisClusterPubSubAsyncCommands<String, String>, out CompletionStage<T>>): CompletionStage<T> =
        CompletionStages.failed(WrongDeploymentModeException(RedisDeploymentMode.CLUSTER, mode))

    fun pingAsync(): CompletionStage<Long>

    fun serverInfoAsync(): CompletionStage<String>

    fun poolStats(): RedisMonitor.PoolStats

    fun closeAsync(gracePeriod: Duration): CompletableFuture<Void>
}
