package com.gitee.redischannel.api.events

import taboolib.platform.type.BukkitProxyEvent

/**
 * Redis 客户端关闭事件
 *
 * 在 Redis 连接池关闭之前触发，允许其他插件在此时完成最后的 Redis 操作。
 * 事件触发后，Redis 连接将被关闭，不应再尝试使用 Redis API。
 *
 * @param cluster 是否为集群模式
 */
class ClientStopEvent(val cluster: Boolean): BukkitProxyEvent()
