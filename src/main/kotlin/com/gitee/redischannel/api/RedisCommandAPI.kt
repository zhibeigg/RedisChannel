package com.gitee.redischannel.api

import io.lettuce.core.api.async.RedisAsyncCommands
import io.lettuce.core.api.sync.RedisCommands

interface RedisCommandAPI {

    /**
     * 使用命令
     * @param block 匿名函数
     * @return [T]
     * */
    fun <T> useCommands(block: (RedisCommands<String, String>) -> T): T?

    /**
     * 使用异步命令
     * @param block 匿名函数
     * @return [T]
     * */
    fun <T> useAsyncCommands(block: (RedisAsyncCommands<String, String>) -> T): T?
}