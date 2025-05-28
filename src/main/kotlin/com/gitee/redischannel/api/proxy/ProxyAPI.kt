package com.gitee.redischannel.api.proxy

import io.lettuce.core.AbstractRedisAsyncCommands
import java.util.concurrent.CompletableFuture

interface ProxyAPI {

    /**
     * 代理集群/单机 AsyncCommand
     * */
    fun getProxyAsyncCommand(): CompletableFuture<AbstractRedisAsyncCommands<String, String>>
}