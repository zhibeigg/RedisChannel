package com.gitee.redischannel.core

import com.gitee.redischannel.RedisChannelPlugin.Type.CLUSTER
import com.gitee.redischannel.RedisChannelPlugin.Type.SINGLE
import com.gitee.redischannel.RedisChannelPlugin.type
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.subCommandExec
import taboolib.expansion.createHelper

@CommandHeader("redis", description = "RedisChannel插件主指令", permission = "RedisChannel.Command.Main", permissionMessage = "你没有权限使用此指令")
object RedisChannelCommand {

    @CommandBody
    val main = mainCommand {
        createHelper()
    }

    @CommandBody
    val reconnect = subCommandExec<ProxyCommandSender> {
        val future = when (type) {
            CLUSTER -> {
                ClusterRedisManager.stop()
                ClusterRedisManager.start()
            }
            SINGLE -> {
                RedisManager.stop()
                RedisManager.start()
            }
        }
        future.thenRun {
            sender.sendMessage("RedisChannel 重载成功")
        }
    }
}