package com.gitee.redischannel.core

import com.gitee.redischannel.api.RedisChannelAPI
import com.gitee.redischannel.api.RedisLifecycleSnapshot
import com.gitee.redischannel.core.lifecycle.RedisLifecycleCoordinator
import java.util.concurrent.CompletionStage

internal object RedisChannelFacade : RedisChannelAPI {

    override fun lifecycle(): RedisLifecycleSnapshot = RedisLifecycleCoordinator.lifecycle()

    override fun startAsync(): CompletionStage<RedisLifecycleSnapshot> = RedisLifecycleCoordinator.startAsync()

    override fun stopAsync(): CompletionStage<RedisLifecycleSnapshot> = RedisLifecycleCoordinator.stopAsync()

    override fun reconnectAsync(): CompletionStage<RedisLifecycleSnapshot> = RedisLifecycleCoordinator.reconnectAsync()
}
