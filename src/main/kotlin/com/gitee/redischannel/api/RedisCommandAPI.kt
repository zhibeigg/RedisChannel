package com.gitee.redischannel.api

import io.lettuce.core.api.async.RedisAsyncCommands
import io.lettuce.core.api.reactive.RedisReactiveCommands
import io.lettuce.core.api.sync.RedisCommands
import java.util.concurrent.CompletableFuture
import java.util.function.Function

/**
 * Redis 单机模式命令 API
 *
 * 提供同步、异步和响应式三种风格的 Redis 命令执行方式。
 * 通过 [RedisChannelPlugin.commandAPI()][com.gitee.redischannel.RedisChannelPlugin.commandAPI] 获取实例。
 *
 * ## 使用方式
 *
 * ```kotlin
 * val api = RedisChannelPlugin.commandAPI()
 *
 * // 同步操作
 * val value = api.useCommands { cmd ->
 *     cmd.set("key", "value")
 *     cmd.get("key")
 * }
 *
 * // 异步操作
 * api.useAsyncCommands { cmd ->
 *     cmd.set("key", "value")
 * }.thenAccept { result -> println(result) }
 *
 * // 响应式操作
 * api.useReactiveCommands { cmd ->
 *     cmd.get("key").subscribe { value -> println(value) }
 * }
 * ```
 *
 * ## 错误处理
 *
 * - 当连接池耗尽时，同步方法返回 `null`，异步方法的 Future 结果为 `null`
 * - 建议在调用后检查返回值是否为 `null`
 *
 * @see RedisChannelAPI 核心 API 接口
 * @see RedisPubSubAPI 发布订阅 API
 * @since 1.0.0
 */
interface RedisCommandAPI {

    /**
     * 使用同步 Redis 命令
     *
     * 从连接池获取连接，执行同步命令操作，完成后自动归还连接。
     * 此方法会阻塞当前线程直到操作完成。
     *
     * ## 示例
     *
     * ```kotlin
     * // 字符串操作
     * val value = api.useCommands { cmd ->
     *     cmd.set("player:uuid:name", "Steve")
     *     cmd.get("player:uuid:name")
     * }
     *
     * // Hash 操作
     * api.useCommands { cmd ->
     *     cmd.hset("player:data", mapOf(
     *         "level" to "10",
     *         "coins" to "100"
     *     ))
     * }
     *
     * // 带过期时间
     * api.useCommands { cmd ->
     *     cmd.setex("session:token", 3600, "abc123")
     * }
     * ```
     *
     * @param T 返回值类型
     * @param block 命令执行函数，接收 [RedisCommands] 参数，可执行任意 Redis 命令
     * @return 命令执行结果，如果连接池耗尽或执行失败则返回 `null`
     * @since 1.0.0
     */
    fun <T> useCommands(block: Function<RedisCommands<String, String>, T>): T?

    /**
     * 使用异步 Redis 命令
     *
     * 从异步连接池获取连接，执行异步命令操作，完成后自动归还连接。
     * 此方法不会阻塞当前线程，通过 [CompletableFuture] 返回结果。
     *
     * ## 示例
     *
     * ```kotlin
     * // 异步设置值
     * api.useAsyncCommands { cmd ->
     *     cmd.set("key", "value")
     * }.thenAccept { result ->
     *     println("设置结果: $result")
     * }
     *
     * // 异步获取值并处理
     * api.useAsyncCommands { cmd ->
     *     cmd.get("key")
     * }.thenAccept { value ->
     *     if (value != null) {
     *         processValue(value)
     *     }
     * }
     *
     * // 链式异步操作
     * api.useAsyncCommands { cmd ->
     *     cmd.incr("counter")
     * }.thenCompose { count ->
     *     api.useAsyncCommands { cmd ->
     *         cmd.set("last_count", count.toString())
     *     }
     * }
     * ```
     *
     * @param T 返回值类型
     * @param block 命令执行函数，接收 [RedisAsyncCommands] 参数
     * @return 包含命令执行结果的 [CompletableFuture]，如果连接池耗尽或执行失败则结果为 `null`
     * @since 1.0.0
     */
    fun <T> useAsyncCommands(block: Function<RedisAsyncCommands<String, String>, T>): CompletableFuture<T?>

    /**
     * 使用响应式 Redis 命令
     *
     * 从异步连接池获取连接，执行响应式命令操作，完成后自动归还连接。
     * 基于 Project Reactor 的响应式流，适合处理流式数据。
     *
     * ## 示例
     *
     * ```kotlin
     * // 响应式获取值
     * api.useReactiveCommands { cmd ->
     *     cmd.get("key")
     *         .subscribe { value ->
     *             println("获取到: $value")
     *         }
     * }
     *
     * // 响应式扫描 keys
     * api.useReactiveCommands { cmd ->
     *     cmd.scan()
     *         .doOnNext { key -> println("Key: $key") }
     *         .subscribe()
     * }
     *
     * // 响应式批量操作
     * api.useReactiveCommands { cmd ->
     *     Flux.fromIterable(keys)
     *         .flatMap { key -> cmd.get(key) }
     *         .collectList()
     *         .subscribe { values -> processValues(values) }
     * }
     * ```
     *
     * @param T 返回值类型
     * @param block 命令执行函数，接收 [RedisReactiveCommands] 参数
     * @return 包含命令执行结果的 [CompletableFuture]，如果连接池耗尽或执行失败则结果为 `null`
     * @since 1.0.0
     */
    fun <T> useReactiveCommands(block: Function<RedisReactiveCommands<String, String>, T>): CompletableFuture<T?>
}