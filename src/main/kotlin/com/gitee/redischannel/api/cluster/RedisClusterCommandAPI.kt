package com.gitee.redischannel.api.cluster

import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands
import io.lettuce.core.cluster.api.reactive.RedisClusterReactiveCommands
import io.lettuce.core.cluster.api.sync.RedisClusterCommands
import java.util.concurrent.CompletableFuture
import java.util.function.Function

/**
 * Redis 集群模式命令 API
 *
 * 提供 Redis Cluster 的同步、异步和响应式命令执行方式。
 * 通过 [RedisChannelPlugin.clusterCommandAPI()][com.gitee.redischannel.RedisChannelPlugin.clusterCommandAPI] 获取实例。
 *
 * ## Redis Cluster 特性
 *
 * - **数据分片**: 数据自动分布在多个节点上
 * - **高可用**: 支持主从复制和自动故障转移
 * - **水平扩展**: 可动态添加或移除节点
 *
 * ## 使用方式
 *
 * ```kotlin
 * val api = RedisChannelPlugin.clusterCommandAPI()
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
 * ```
 *
 * ## 注意事项
 *
 * - 跨槽位的多键操作（如 MGET）需要使用 Hash Tag 确保键在同一槽位
 * - 使用 `{tag}key` 格式可以确保相关键分配到同一节点
 * - 当连接池耗尽时，方法会返回 `null`
 *
 * @see com.gitee.redischannel.api.RedisCommandAPI 单机模式命令 API
 * @see RedisClusterPubSubAPI 集群发布订阅 API
 * @since 1.0.0
 */
interface RedisClusterCommandAPI {

    /**
     * 使用同步 Redis 集群命令
     *
     * 从连接池获取集群连接，执行同步命令操作，完成后自动归还连接。
     * 此方法会阻塞当前线程直到操作完成。
     *
     * ## 示例
     *
     * ```kotlin
     * // 基本操作
     * val value = api.useCommands { cmd ->
     *     cmd.set("player:uuid:name", "Steve")
     *     cmd.get("player:uuid:name")
     * }
     *
     * // 使用 Hash Tag 确保键在同一槽位
     * api.useCommands { cmd ->
     *     cmd.mset(mapOf(
     *         "{user:123}:name" to "Steve",
     *         "{user:123}:level" to "10",
     *         "{user:123}:coins" to "100"
     *     ))
     * }
     *
     * // 集群特有命令
     * api.useCommands { cmd ->
     *     val slots = cmd.clusterSlots()
     *     val nodes = cmd.clusterNodes()
     * }
     * ```
     *
     * @param T 返回值类型
     * @param block 命令执行函数，接收 [RedisClusterCommands] 参数
     * @return 命令执行结果，如果连接池耗尽或执行失败则返回 `null`
     * @since 1.0.0
     */
    fun <T> useCommands(block: Function<RedisClusterCommands<String, String>, T>): T?

    /**
     * 使用异步 Redis 集群命令
     *
     * 从异步连接池获取集群连接，执行异步命令操作，完成后自动归还连接。
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
     * // 异步批量操作
     * api.useAsyncCommands { cmd ->
     *     cmd.mget("{user:123}:name", "{user:123}:level")
     * }.thenAccept { values ->
     *     values.forEach { kv -> println("${kv.key} = ${kv.value}") }
     * }
     *
     * // 异步获取集群信息
     * api.useAsyncCommands { cmd ->
     *     cmd.clusterInfo()
     * }.thenAccept { info ->
     *     println("集群状态: $info")
     * }
     * ```
     *
     * @param T 返回值类型
     * @param block 命令执行函数，接收 [RedisClusterAsyncCommands] 参数
     * @return 包含命令执行结果的 [CompletableFuture]，如果连接池耗尽或执行失败则结果为 `null`
     * @since 1.0.0
     */
    fun <T> useAsyncCommands(block: Function<RedisClusterAsyncCommands<String, String>, T>): CompletableFuture<T?>

    /**
     * 使用响应式 Redis 集群命令
     *
     * 从异步连接池获取集群连接，执行响应式命令操作，完成后自动归还连接。
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
     * // 响应式扫描所有节点
     * api.useReactiveCommands { cmd ->
     *     cmd.scan()
     *         .doOnNext { key -> println("Key: $key") }
     *         .subscribe()
     * }
     *
     * // 响应式获取集群拓扑
     * api.useReactiveCommands { cmd ->
     *     cmd.clusterSlots()
     *         .doOnNext { slot -> println("Slot: $slot") }
     *         .subscribe()
     * }
     * ```
     *
     * @param T 返回值类型
     * @param block 命令执行函数，接收 [RedisClusterReactiveCommands] 参数
     * @return 包含命令执行结果的 [CompletableFuture]，如果连接池耗尽或执行失败则结果为 `null`
     * @since 1.0.0
     */
    fun <T> useReactiveCommands(block: Function<RedisClusterReactiveCommands<String, String>, T>): CompletableFuture<T?>
}