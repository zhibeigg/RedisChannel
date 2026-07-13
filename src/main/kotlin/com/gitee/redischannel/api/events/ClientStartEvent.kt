package com.gitee.redischannel.api.events

import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Redis runtime 已完整建立并进入 RUNNING 后触发。
 *
 * 此事件始终在 Bukkit 主线程调用；触发时
 * [com.gitee.redischannel.RedisChannelPlugin.initialized] 已为 true。
 * 首次启动和 `/redis reconnect` 成功都会触发该事件。
 *
 * Redis I/O 必须继续使用 API v2 的 CompletionStage 接口，
 * 不要在事件回调中阻塞等待结果。
 *
 * @property cluster 当前是否为 Redis Cluster 模式
 * @since 2.14.12
 */
class ClientStartEvent(val cluster: Boolean) : Event() {

    override fun getHandlers(): HandlerList = handlerList

    companion object {
        @JvmStatic
        val handlerList = HandlerList()
    }
}
