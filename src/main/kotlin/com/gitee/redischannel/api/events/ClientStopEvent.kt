package com.gitee.redischannel.api.events

import taboolib.platform.type.BukkitProxyEvent

/**
 * Redis 客户端关闭事件
 *
 * 在 Redis 连接池关闭**之前**触发此事件。
 * 其他插件可以监听此事件来完成最后的 Redis 操作，如保存数据。
 *
 * ## 触发时机
 *
 * - 服务器关闭时，在连接池关闭之前
 * - 插件被禁用时
 *
 * ## 使用示例
 *
 * ```kotlin
 * import com.gitee.redischannel.api.events.ClientStopEvent
 * import taboolib.common.platform.event.SubscribeEvent
 *
 * @SubscribeEvent
 * fun onRedisStop(event: ClientStopEvent) {
 *     // 在 Redis 关闭前保存所有数据
 *     saveAllPlayerData()
 *
 *     // 清理资源
 *     cleanup()
 *
 *     println("Redis ${if (event.cluster) "集群" else "单机"} 即将关闭")
 * }
 *
 * private fun saveAllPlayerData() {
 *     Bukkit.getOnlinePlayers().forEach { player ->
 *         val data = playerDataCache[player.uniqueId]
 *         if (data != null) {
 *             RedisChannelPlugin.api.useCommands { cmd ->
 *                 cmd.hset("player:${player.uniqueId}", data.toMap())
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * ## 重要注意事项
 *
 * 1. **时机关键**: 此事件在连接池关闭之前触发，你仍然可以执行 Redis 操作
 * 2. **不要阻塞太久**: 事件处理应该尽快完成，避免延迟服务器关闭
 * 3. **异常处理**: 在事件处理中捕获异常，避免影响其他监听器
 * 4. **事件后禁止操作**: 事件处理完成后，Redis 连接将被关闭，不应再尝试使用 API
 *
 * ## 执行顺序
 *
 * 1. 触发 `ClientStopEvent`
 * 2. 等待所有监听器处理完成
 * 3. 关闭 Pub/Sub 连接
 * 4. 关闭连接池
 * 5. 关闭 Redis 客户端
 * 6. 释放 Netty 资源
 *
 * @property cluster 是否为集群模式。`true` 表示 Redis Cluster 模式，`false` 表示单机/主从模式
 * @see ClientStartEvent Redis 客户端启动事件
 * @since 1.14.10
 */
class ClientStopEvent(
    /**
     * 是否为集群模式
     *
     * - `true`: Redis Cluster 集群模式
     * - `false`: 单机模式或主从模式
     */
    val cluster: Boolean
): BukkitProxyEvent()
