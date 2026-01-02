package com.gitee.redischannel.core

import com.gitee.redischannel.RedisChannelPlugin
import com.gitee.redischannel.RedisChannelPlugin.Type.CLUSTER
import com.gitee.redischannel.RedisChannelPlugin.Type.SINGLE
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
        // 先停止当前连接（如果已初始化）
        when (RedisChannelPlugin.type) {
            CLUSTER -> ClusterRedisManager.stop()
            SINGLE -> RedisManager.stop()
            null -> {} // 未初始化，跳过
        }
        // 重新加载配置
        RedisChannelPlugin.reloadConfig()
        // 根据新配置决定启动哪个 Manager
        if (RedisChannelPlugin.redis.enableCluster) {
            ClusterRedisManager.start()
        } else {
            RedisManager.start()
        }
        sender.sendMessage("§aRedisChannel 重载成功")
    }

    @CommandBody
    val status = subCommandExec<ProxyCommandSender> {
        val snapshot = RedisMonitor.getSnapshot()

        sender.sendMessage("§8§m─────────────────────────────────────")
        sender.sendMessage("§b§l  Redis 监控面板")
        sender.sendMessage("§8§m─────────────────────────────────────")

        // 基本状态
        sender.sendMessage("")
        sender.sendMessage("§7▎ §f基本状态")
        sender.sendMessage("  §7连接状态: ${snapshot.status.color}${snapshot.status.display}")
        snapshot.mode?.let {
            sender.sendMessage("  §7运行模式: §e${if (it == CLUSTER) "集群模式" else "单机模式"}")
        }
        snapshot.uptime?.let {
            sender.sendMessage("  §7运行时间: §f${formatDuration(it)}")
        }

        // 部署模式详情
        snapshot.deploymentInfo?.let { deployment ->
            sender.sendMessage("")
            sender.sendMessage("§7▎ §f部署模式")

            when {
                deployment.isCluster -> {
                    sender.sendMessage("  §7架构类型: §b集群模式")
                    deployment.clusterNodeCount?.let {
                        sender.sendMessage("  §7集群节点: §f${it} 个")
                    }
                    if (deployment.isSlaves) {
                        sender.sendMessage("  §7读写分离: §a已启用")
                        deployment.readFrom?.let {
                            sender.sendMessage("  §7读取策略: §f${it}")
                        }
                    }
                }
                deployment.isSentinel -> {
                    sender.sendMessage("  §7架构类型: §d哨兵模式")
                    deployment.sentinelMasterId?.let {
                        sender.sendMessage("  §7Master ID: §f${it}")
                    }
                    deployment.sentinelNodes?.let { nodes ->
                        sender.sendMessage("  §7哨兵节点: §f${nodes.size} 个")
                        nodes.forEach { node ->
                            sender.sendMessage("    §8- §7${node}")
                        }
                    }
                    if (deployment.isSlaves) {
                        deployment.readFrom?.let {
                            sender.sendMessage("  §7读取策略: §f${it}")
                        }
                    }
                }
                deployment.isSlaves -> {
                    sender.sendMessage("  §7架构类型: §6主从模式")
                    deployment.readFrom?.let {
                        sender.sendMessage("  §7读取策略: §f${it}")
                    }
                }
                else -> {
                    sender.sendMessage("  §7架构类型: §f单机模式")
                }
            }
        }

        // 性能指标
        sender.sendMessage("")
        sender.sendMessage("§7▎ §f性能指标")
        if (snapshot.pingLatency >= 0) {
            val pingColor = when {
                snapshot.pingLatency < 10 -> "§a"
                snapshot.pingLatency < 50 -> "§e"
                else -> "§c"
            }
            sender.sendMessage("  §7PING 延迟: ${pingColor}${snapshot.pingLatency}ms")
        } else {
            sender.sendMessage("  §7PING 延迟: §8N/A")
        }
        if (snapshot.avgLatency >= 0) {
            sender.sendMessage("  §7平均延迟: §f${snapshot.avgLatency}ms")
        }

        // 命令统计
        sender.sendMessage("")
        sender.sendMessage("§7▎ §f命令统计")
        sender.sendMessage("  §7总执行数: §f${snapshot.commandCount}")
        sender.sendMessage("  §7成功/失败: §a${snapshot.successCount} §7/ §c${snapshot.failCount}")
        val rateColor = when {
            snapshot.successRate >= 99 -> "§a"
            snapshot.successRate >= 95 -> "§e"
            else -> "§c"
        }
        sender.sendMessage("  §7成功率: ${rateColor}${"%.2f".format(snapshot.successRate)}%")

        // 连接池状态
        snapshot.poolStats?.let { pool ->
            sender.sendMessage("")
            sender.sendMessage("§7▎ §f连接池状态")
            sender.sendMessage("  §7活跃连接: §f${pool.active} §7/ §f${pool.maxTotal}")
            sender.sendMessage("  §7空闲连接: §f${pool.idle}")
            if (pool.waiters > 0) {
                sender.sendMessage("  §7等待队列: §c${pool.waiters}")
            }
            val utilColor = when {
                pool.utilization < 50 -> "§a"
                pool.utilization < 80 -> "§e"
                else -> "§c"
            }
            sender.sendMessage("  §7使用率: ${utilColor}${"%.1f".format(pool.utilization)}%")
        }

        // 服务器信息
        snapshot.serverInfo?.let { server ->
            sender.sendMessage("")
            sender.sendMessage("§7▎ §fRedis 服务器")
            server.redisVersion?.let { sender.sendMessage("  §7版本: §f$it") }
            server.usedMemory?.let { sender.sendMessage("  §7内存使用: §f$it") }
            server.connectedClients?.let { sender.sendMessage("  §7客户端数: §f$it") }
            server.uptimeSeconds?.let {
                sender.sendMessage("  §7服务器运行: §f${formatSeconds(it)}")
            }
        }

        sender.sendMessage("§8§m─────────────────────────────────────")
    }

    private fun formatDuration(duration: java.time.Duration): String {
        val hours = duration.toHours()
        val minutes = duration.toMinutes() % 60
        val seconds = duration.seconds % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }

    private fun formatSeconds(seconds: Long): String {
        val days = seconds / 86400
        val hours = (seconds % 86400) / 3600
        val minutes = (seconds % 3600) / 60
        return when {
            days > 0 -> "${days}d ${hours}h ${minutes}m"
            hours > 0 -> "${hours}h ${minutes}m"
            else -> "${minutes}m"
        }
    }
}