package com.gitee.redischannel.api.proxy

import java.util.concurrent.CompletableFuture

interface ProxyAPI {

    /**
     * 代理集群/单机 Command
     * */
    fun getProxyCommand(): RedisProxyCommand<String, String>

    /**
     * 代理集群/单机 AsyncCommand
     * */
    fun getProxyAsyncCommand(): CompletableFuture<RedisProxyAsyncCommand<String, String>>
}