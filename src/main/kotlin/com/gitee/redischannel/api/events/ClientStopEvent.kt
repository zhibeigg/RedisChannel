package com.gitee.redischannel.api.events

import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Redis runtime 即将停止接受新操作时触发。
 *
 * 此事件始终在 Bukkit 主线程调用。监听器可以发起 API v2 异步操作，
 * 生命周期协调器会在关闭宽限时间内等待已登记的在途操作完成，但监听器
 * 不得使用阻塞等待。服务器 JVM 关闭时，异步资源释放属于尽力完成。
 *
 * 首次禁用、热重载以及 `/redis reconnect` 都可能触发该事件。
 *
 * @property cluster 当前是否为 Redis Cluster 模式
 * @since 2.14.12
 */
class ClientStopEvent(val cluster: Boolean) : Event() {

    override fun getHandlers(): HandlerList = handlerList

    companion object {
        @JvmStatic
        val handlerList = HandlerList()
    }
}
