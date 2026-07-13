package com.gitee.redischannel.api

import io.lettuce.core.api.async.RedisAsyncCommands
import java.util.concurrent.CompletionStage
import java.util.function.Function

/**
 * 单机、哨兵和主从模式的非阻塞命令 API。
 *
 * action 返回的 CompletionStage 完成后，连接才会归还连接池。
 *
 * @since 2.14.12
 */
interface RedisCommandAPI {

    fun <T> executeAsync(
        action: Function<RedisAsyncCommands<String, String>, out CompletionStage<T>>
    ): CompletionStage<T>
}
