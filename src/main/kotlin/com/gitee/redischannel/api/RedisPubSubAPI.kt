package com.gitee.redischannel.api

import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands
import io.lettuce.core.pubsub.api.reactive.RedisPubSubReactiveCommands
import io.lettuce.core.pubsub.api.sync.RedisPubSubCommands
import java.util.function.Function

/**
 * Redis 发布/订阅 API
 *
 * 提供 Redis Pub/Sub 功能的同步、异步和响应式访问方式。
 * 通过 [RedisChannelPlugin.pubSubAPI()][com.gitee.redischannel.RedisChannelPlugin.pubSubAPI] 获取实例。
 *
 * ## 发布/订阅模式
 *
 * Redis Pub/Sub 是一种消息传递模式：
 * - **发布者 (Publisher)**: 向频道发送消息
 * - **订阅者 (Subscriber)**: 订阅频道接收消息
 *
 * ## 使用方式
 *
 * ```kotlin
 * val api = RedisChannelPlugin.pubSubAPI()
 *
 * // 订阅频道
 * api.usePubSubCommands { cmd ->
 *     cmd.subscribe("my-channel")
 * }
 *
 * // 发布消息
 * api.usePubSubAsyncCommands { cmd ->
 *     cmd.publish("my-channel", "Hello, World!")
 * }
 * ```
 *
 * ## 注意事项
 *
 * - Pub/Sub 连接是独立的，不使用连接池
 * - 订阅操作会持续监听，直到取消订阅
 * - 集群模式下请使用 [cluster.RedisClusterPubSubAPI][com.gitee.redischannel.api.cluster.RedisClusterPubSubAPI]
 *
 * @see RedisCommandAPI 命令 API
 * @see com.gitee.redischannel.api.cluster.RedisClusterPubSubAPI 集群发布订阅 API
 * @since 1.0.0
 */
interface RedisPubSubAPI {

    /**
     * 使用同步 Pub/Sub 命令
     *
     * 使用专用的 Pub/Sub 连接执行同步命令。
     * 此方法会阻塞当前线程直到操作完成。
     *
     * ## 示例
     *
     * ```kotlin
     * // 订阅频道
     * api.usePubSubCommands { cmd ->
     *     cmd.subscribe("chat", "notifications")
     * }
     *
     * // 订阅模式匹配的频道
     * api.usePubSubCommands { cmd ->
     *     cmd.psubscribe("user:*", "server:*")
     * }
     *
     * // 取消订阅
     * api.usePubSubCommands { cmd ->
     *     cmd.unsubscribe("chat")
     * }
     *
     * // 发布消息
     * api.usePubSubCommands { cmd ->
     *     cmd.publish("chat", "Hello everyone!")
     * }
     * ```
     *
     * @param T 返回值类型
     * @param block 命令执行函数，接收 [RedisPubSubCommands] 参数
     * @return 命令执行结果，如果执行失败则返回 `null`
     * @since 1.0.0
     */
    fun <T> usePubSubCommands(block: Function<RedisPubSubCommands<String, String>, T>): T?

    /**
     * 使用异步 Pub/Sub 命令
     *
     * 使用专用的 Pub/Sub 连接执行异步命令。
     * 此方法不会阻塞当前线程。
     *
     * ## 示例
     *
     * ```kotlin
     * // 异步发布消息
     * api.usePubSubAsyncCommands { cmd ->
     *     cmd.publish("events", "player_joined")
     * }
     *
     * // 异步订阅
     * api.usePubSubAsyncCommands { cmd ->
     *     cmd.subscribe("game-events")
     * }
     *
     * // 获取订阅的频道列表
     * api.usePubSubAsyncCommands { cmd ->
     *     cmd.pubsubChannels()
     * }
     * ```
     *
     * @param T 返回值类型
     * @param block 命令执行函数，接收 [RedisPubSubAsyncCommands] 参数
     * @return 命令执行结果，如果执行失败则返回 `null`
     * @since 1.0.0
     */
    fun <T> usePubSubAsyncCommands(block: Function<RedisPubSubAsyncCommands<String, String>, T>): T?

    /**
     * 使用响应式 Pub/Sub 命令
     *
     * 使用专用的 Pub/Sub 连接执行响应式命令。
     * 基于 Project Reactor 的响应式流，适合处理消息流。
     *
     * ## 示例
     *
     * ```kotlin
     * // 响应式订阅并处理消息
     * api.usePubSubReactiveCommands { cmd ->
     *     cmd.subscribe("notifications")
     * }
     *
     * // 响应式发布
     * api.usePubSubReactiveCommands { cmd ->
     *     cmd.publish("alerts", "Server restarting")
     *         .subscribe { count ->
     *             println("消息发送给 $count 个订阅者")
     *         }
     * }
     *
     * // 观察消息流
     * api.usePubSubReactiveCommands { cmd ->
     *     cmd.observeChannels()
     *         .doOnNext { message ->
     *             println("收到消息: ${message.message}")
     *         }
     *         .subscribe()
     * }
     * ```
     *
     * @param T 返回值类型
     * @param block 命令执行函数，接收 [RedisPubSubReactiveCommands] 参数
     * @return 命令执行结果，如果执行失败则返回 `null`
     * @since 1.0.0
     */
    fun <T> usePubSubReactiveCommands(block: Function<RedisPubSubReactiveCommands<String, String>, T>): T?
}