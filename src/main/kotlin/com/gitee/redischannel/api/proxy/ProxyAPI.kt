package com.gitee.redischannel.api.proxy

interface ProxyAPI {

    /**
     * 代理集群/单机 Command
     * */
    fun getProxyCommand(): RedisProxyCommand<String, String>

    /**
     * 代理集群/单机 AsyncCommand
     * */
    fun getProxyAsyncCommand(): RedisProxyAsyncCommand<String, String>
}