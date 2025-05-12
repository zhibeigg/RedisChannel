package com.gitee.redischannel.api

import io.lettuce.core.api.async.RedisAsyncCommands
import io.lettuce.core.api.sync.RedisCommands

interface RedisChannelAPI {

    /**
     * 获取缓存数据
     * @param key 缓存数据键
     * */
    fun get(key: String): String?

    /**
     * 获取缓存数据
     * @param key 缓存数据键
     * */
    fun asyncGet(key: String): RedisFuture<String?>?

    /**
     * 设置缓存数据，默认过期时间10秒
     * @param key 缓存数据键
     * @param timeout 过期时间(秒)
     * @param async 是否异步
     * */
    fun set(key: String, value: JsonData, timeout: Long = 10, async: Boolean = false)

    /**
     * 设置缓存数据，默认过期时间10秒
     * @param key 缓存数据键
     * @param timeout 过期时间(秒)
     * @param async 是否异步
     * */
    fun set(key: String, value: String, timeout: Long = 10, async: Boolean = false)

    /**
     * 删除缓存数据
     * @param key 缓存数据键
     * @param async 是否异步
     * */
    fun remove(key: String, async: Boolean = false)

    /**
     * 刷新缓存数据到期时间
     * @param key 缓存数据键
     * @param timeout 过期时间(秒)
     * @param async 是否异步
     * */
    fun refreshExpire(key: String, timeout: Long, async: Boolean = false)

    /**
     * 发布消息到频道
     * @param channel 频道
     * @param message 消息
     * @param async 是否异步
     * */
    fun publish(channel: String, message: String, async: Boolean)

    /**
     * 使用异步命令
     * @param block 匿名函数
     * */
    fun <T> asyncCommands(block: (command: RedisAsyncCommands<String, String>) -> T): T?

    /**
     * 使用同步命令
     * @param block 匿名函数
     * */
    fun <T> commands(block: (command: RedisCommands<String, String>) -> T): T?
}