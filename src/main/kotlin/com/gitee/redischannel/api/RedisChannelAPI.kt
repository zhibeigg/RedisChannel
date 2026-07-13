package com.gitee.redischannel.api

import java.util.concurrent.CompletionStage

/**
 * RedisChannel API v2 稳定入口。
 *
 * 所有 Redis I/O 与生命周期操作均为非阻塞异步操作。
 *
 * @since 2.14.12
 */
interface RedisChannelAPI {

    fun lifecycle(): RedisLifecycleSnapshot

    fun startAsync(): CompletionStage<RedisLifecycleSnapshot>

    fun stopAsync(): CompletionStage<RedisLifecycleSnapshot>

    fun reconnectAsync(): CompletionStage<RedisLifecycleSnapshot>
}
