package com.gitee.redischannel.api.cluster

import com.gitee.redischannel.api.RedisPubSubAPI
import io.lettuce.core.cluster.pubsub.api.async.RedisClusterPubSubAsyncCommands
import io.lettuce.core.cluster.pubsub.api.reactive.RedisClusterPubSubReactiveCommands
import io.lettuce.core.cluster.pubsub.api.sync.RedisClusterPubSubCommands
import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands
import io.lettuce.core.pubsub.api.reactive.RedisPubSubReactiveCommands
import io.lettuce.core.pubsub.api.sync.RedisPubSubCommands
import java.util.function.Function

/**
 * Redis 集群发布/订阅 API
 *
 * 提供 Redis Cluster 的 Pub/Sub 功能，支持同步、异步和响应式访问方式。
 * 继承自 [RedisPubSubAPI]，可以使用基础的 Pub/Sub 方法。
 * 通过 [RedisChannelPlugin.clusterPubSubAPI()][com.gitee.redischannel.RedisChannelPlugin.clusterPubSubAPI] 获取实例。
 *
 * ## 集群 Pub/Sub 特性
 *
 * - 消息会广播到集群中的所有节点
 * - 订阅者可以连接到任意节点接收消息
 * - 支持分片频道 (Sharded Pub/Sub, Redis 7.0+)
 *
 * ## 使用方式
 *
 * ```kotlin
 * val api = RedisChannelPlugin.clusterPubSubAPI()
 *
 * // 使用集群特有的 Pub/Sub 命令
 * api.useClusterPubSubCommands { cmd ->
 *     cmd.subscribe("cluster-events")
 * }
 *
 * // 也可以使用基础的 Pub/Sub 方法（继承自 RedisPubSubAPI）
 * api.usePubSubCommands { cmd ->
 *     cmd.publish("notifications", "Hello Cluster!")
 * }
 * ```
 *
 * ## 兼容性
 *
 * 此接口继承自 [RedisPubSubAPI]，因此可以使用统一的 Pub/Sub API：
 * - [usePubSubCommands] - 委托给 [useClusterPubSubCommands]
 * - [usePubSubAsyncCommands] - 委托给 [useClusterPubSubAsyncCommands]
 * - [usePubSubReactiveCommands] - 委托给 [useClusterPubSubReactiveCommands]
 *
 * @see RedisPubSubAPI 基础发布订阅 API
 * @see RedisClusterCommandAPI 集群命令 API
 * @since 1.0.0
 */
interface RedisClusterPubSubAPI: RedisPubSubAPI {

    /**
     * 使用同步集群 Pub/Sub 命令
     *
     * 使用专用的集群 Pub/Sub 连接执行同步命令。
     * 此方法会阻塞当前线程直到操作完成。
     *
     * ## 示例
     *
     * ```kotlin
     * // 订阅频道
     * api.useClusterPubSubCommands { cmd ->
     *     cmd.subscribe("cluster-chat", "cluster-events")
     * }
     *
     * // 发布消息到集群
     * api.useClusterPubSubCommands { cmd ->
     *     cmd.publish("cluster-events", "Node joined")
     * }
     *
     * // 获取集群 Pub/Sub 统计信息
     * api.useClusterPubSubCommands { cmd ->
     *     val channels = cmd.pubsubChannels()
     *     val numSub = cmd.pubsubNumsub("cluster-events")
     * }
     * ```
     *
     * @param T 返回值类型
     * @param block 命令执行函数，接收 [RedisClusterPubSubCommands] 参数
     * @return 命令执行结果，如果执行失败则返回 `null`
     * @since 1.0.0
     */
    fun <T> useClusterPubSubCommands(block: Function<RedisClusterPubSubCommands<String, String>, T>): T?

    /**
     * 使用异步集群 Pub/Sub 命令
     *
     * 使用专用的集群 Pub/Sub 连接执行异步命令。
     * 此方法不会阻塞当前线程。
     *
     * ## 示例
     *
     * ```kotlin
     * // 异步发布消息
     * api.useClusterPubSubAsyncCommands { cmd ->
     *     cmd.publish("cluster-notifications", "Server update")
     * }
     *
     * // 异步订阅多个频道
     * api.useClusterPubSubAsyncCommands { cmd ->
     *     cmd.subscribe("channel1", "channel2", "channel3")
     * }
     *
     * // 异步获取订阅者数量
     * api.useClusterPubSubAsyncCommands { cmd ->
     *     cmd.pubsubNumsub("cluster-events")
     * }
     * ```
     *
     * @param T 返回值类型
     * @param block 命令执行函数，接收 [RedisClusterPubSubAsyncCommands] 参数
     * @return 命令执行结果，如果执行失败则返回 `null`
     * @since 1.0.0
     */
    fun <T> useClusterPubSubAsyncCommands(block: Function<RedisClusterPubSubAsyncCommands<String, String>, T>): T?

    /**
     * 使用响应式集群 Pub/Sub 命令
     *
     * 使用专用的集群 Pub/Sub 连接执行响应式命令。
     * 基于 Project Reactor 的响应式流，适合处理消息流。
     *
     * ## 示例
     *
     * ```kotlin
     * // 响应式订阅
     * api.useClusterPubSubReactiveCommands { cmd ->
     *     cmd.subscribe("cluster-stream")
     * }
     *
     * // 响应式发布并获取订阅者数量
     * api.useClusterPubSubReactiveCommands { cmd ->
     *     cmd.publish("cluster-alerts", "Warning!")
     *         .subscribe { count ->
     *             println("消息发送给 $count 个订阅者")
     *         }
     * }
     *
     * // 响应式观察消息
     * api.useClusterPubSubReactiveCommands { cmd ->
     *     cmd.observeChannels()
     *         .doOnNext { message ->
     *             println("[${message.channel}] ${message.message}")
     *         }
     *         .subscribe()
     * }
     * ```
     *
     * @param T 返回值类型
     * @param block 命令执行函数，接收 [RedisClusterPubSubReactiveCommands] 参数
     * @return 命令执行结果，如果执行失败则返回 `null`
     * @since 1.0.0
     */
    fun <T> useClusterPubSubReactiveCommands(block: Function<RedisClusterPubSubReactiveCommands<String, String>, T>): T?

    /**
     * 使用同步 Pub/Sub 命令（兼容方法）
     *
     * 委托给 [useClusterPubSubCommands]，提供与 [RedisPubSubAPI] 的兼容性。
     *
     * @param T 返回值类型
     * @param block 命令执行函数
     * @return 命令执行结果
     * @since 1.0.0
     */
    override fun <T> usePubSubCommands(block: Function<RedisPubSubCommands<String, String>, T>): T? {
        return useClusterPubSubCommands {
            block.apply(it)
        }
    }

    /**
     * 使用异步 Pub/Sub 命令（兼容方法）
     *
     * 委托给 [useClusterPubSubAsyncCommands]，提供与 [RedisPubSubAPI] 的兼容性。
     *
     * @param T 返回值类型
     * @param block 命令执行函数
     * @return 命令执行结果
     * @since 1.0.0
     */
    override fun <T> usePubSubAsyncCommands(block: Function<RedisPubSubAsyncCommands<String, String>, T>): T? {
        return useClusterPubSubAsyncCommands {
            block.apply(it)
        }
    }

    /**
     * 使用响应式 Pub/Sub 命令（兼容方法）
     *
     * 委托给 [useClusterPubSubReactiveCommands]，提供与 [RedisPubSubAPI] 的兼容性。
     *
     * @param T 返回值类型
     * @param block 命令执行函数
     * @return 命令执行结果
     * @since 1.0.0
     */
    override fun <T> usePubSubReactiveCommands(block: Function<RedisPubSubReactiveCommands<String, String>, T>): T? {
        return useClusterPubSubReactiveCommands {
            block.apply(it)
        }
    }
}