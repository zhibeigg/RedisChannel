package com.gitee.redischannel.api

import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands
import java.util.concurrent.CompletionStage
import java.util.function.Function

/**
 * 当前部署模式的非阻塞通用 Pub/Sub API。
 *
 * @since 2.14.12
 */
interface RedisPubSubAPI {

    fun <T> executePubSubAsync(
        action: Function<RedisPubSubAsyncCommands<String, String>, out CompletionStage<T>>
    ): CompletionStage<T>
}
