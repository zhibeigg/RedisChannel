package com.gitee.redischannel.api

import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import java.util.concurrent.CompletableFuture
import java.util.function.Function

/**
 * RedisChannel 核心 API 接口
 *
 * 提供统一的 Redis 连接访问入口，支持单机模式和集群模式。
 * 通过 [RedisChannelPlugin.api][com.gitee.redischannel.RedisChannelPlugin.api] 获取实例。
 *
 * ## 使用方式
 *
 * ```kotlin
 * val api = RedisChannelPlugin.api
 *
 * // 同步操作
 * api.useConnection(
 *     use = { connection -> connection.sync().get("key") }
 * )
 *
 * // 异步操作
 * api.useAsyncConnection(
 *     use = { connection -> connection.async().get("key") }
 * ).thenAccept { result -> println(result) }
 * ```
 *
 * ## 注意事项
 *
 * - 当连接池耗尽时，方法会返回 `null`
 * - 建议使用 [RedisCommandAPI] 或 [cluster.RedisClusterCommandAPI][com.gitee.redischannel.api.cluster.RedisClusterCommandAPI] 的高级方法
 * - 连接会自动归还到连接池，无需手动关闭
 *
 * @see RedisCommandAPI 单机模式命令 API
 * @see com.gitee.redischannel.api.cluster.RedisClusterCommandAPI 集群模式命令 API
 * @see RedisPubSubAPI 发布订阅 API
 * @since 1.0.0
 */
interface RedisChannelAPI {

    /**
     * 使用同步 Redis 连接执行操作
     *
     * 从连接池获取一个连接，执行指定操作后自动归还连接。
     * 根据当前运行模式（单机/集群），会调用对应的处理函数。
     *
     * ## 示例
     *
     * ```kotlin
     * // 单机模式
     * val result = api.useConnection(
     *     use = { conn -> conn.sync().get("mykey") }
     * )
     *
     * // 集群模式
     * val result = api.useConnection(
     *     useCluster = { conn -> conn.sync().get("mykey") }
     * )
     * ```
     *
     * @param T 返回值类型
     * @param use 单机模式下的连接处理函数，接收 [StatefulRedisConnection] 参数
     * @param useCluster 集群模式下的连接处理函数，接收 [StatefulRedisClusterConnection] 参数
     * @return 操作结果，如果连接池耗尽或操作失败则返回 `null`
     * @since 1.0.0
     */
    fun <T> useConnection(
        use: Function<StatefulRedisConnection<String, String>, T>? = null,
        useCluster: Function<StatefulRedisClusterConnection<String, String>, T>? = null
    ): T?

    /**
     * 使用异步 Redis 连接执行操作
     *
     * 从异步连接池获取一个连接，执行指定操作后自动归还连接。
     * 操作以非阻塞方式执行，通过 [CompletableFuture] 返回结果。
     *
     * ## 示例
     *
     * ```kotlin
     * // 单机模式异步操作
     * api.useAsyncConnection(
     *     use = { conn -> conn.async().set("key", "value") }
     * ).thenAccept { result ->
     *     println("设置结果: $result")
     * }
     *
     * // 集群模式异步操作
     * api.useAsyncConnection(
     *     useCluster = { conn -> conn.async().get("key") }
     * ).thenAccept { value ->
     *     println("获取到: $value")
     * }
     * ```
     *
     * @param T 返回值类型
     * @param use 单机模式下的连接处理函数，接收 [StatefulRedisConnection] 参数
     * @param useCluster 集群模式下的连接处理函数，接收 [StatefulRedisClusterConnection] 参数
     * @return 包含操作结果的 [CompletableFuture]，如果连接池耗尽或操作失败则结果为 `null`
     * @since 1.0.0
     */
    fun <T> useAsyncConnection(
        use: Function<StatefulRedisConnection<String, String>, T>? = null,
        useCluster: Function<StatefulRedisClusterConnection<String, String>, T>? = null
    ): CompletableFuture<T?>
}