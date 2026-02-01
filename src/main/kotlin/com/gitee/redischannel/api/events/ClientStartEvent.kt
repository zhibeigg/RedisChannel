package com.gitee.redischannel.api.events

import taboolib.platform.type.BukkitProxyEvent

/**
 * Redis 客户端启动事件
 *
 * 当 Redis 连接成功建立后触发此事件。
 * 其他插件可以监听此事件来初始化依赖 Redis 的功能。
 *
 * ## 触发时机
 *
 * - 服务器启动时，Redis 连接池初始化完成后
 * - 使用 `/redis reconnect` 命令重新连接成功后
 *
 * ## 使用示例
 *
 * ```kotlin
 * import com.gitee.redischannel.api.events.ClientStartEvent
 * import taboolib.common.platform.event.SubscribeEvent
 *
 * @SubscribeEvent
 * fun onRedisStart(event: ClientStartEvent) {
 *     // Redis 已连接，可以安全使用 API
 *     val api = RedisChannelPlugin.api
 *
 *     if (event.cluster) {
 *         println("Redis 集群已连接")
 *     } else {
 *         println("Redis 单机已连接")
 *     }
 *
 *     // 初始化你的功能...
 *     loadDataFromRedis()
 * }
 * ```
 *
 * ## 注意事项
 *
 * - 此事件在主线程上触发
 * - 事件触发后 [RedisChannelPlugin.initialized][com.gitee.redischannel.RedisChannelPlugin.initialized] 会被设置为 `true`
 * - 如果需要在 Redis 可用前阻止玩家登录，可以检查 `initialized` 标志
 *
 * @property cluster 是否为集群模式。`true` 表示 Redis Cluster 模式，`false` 表示单机/主从模式
 * @see ClientStopEvent Redis 客户端关闭事件
 * @since 1.0.0
 */
class ClientStartEvent(
    /**
     * 是否为集群模式
     *
     * - `true`: Redis Cluster 集群模式
     * - `false`: 单机模式或主从模式
     */
    val cluster: Boolean
): BukkitProxyEvent()