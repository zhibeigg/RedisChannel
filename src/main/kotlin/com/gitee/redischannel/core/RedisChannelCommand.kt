package com.gitee.redischannel.core

import com.gitee.redischannel.RedisChannelPlugin
import com.gitee.redischannel.api.RedisDeploymentMode
import com.gitee.redischannel.platform.BukkitThreadBoundary
import com.gitee.redischannel.util.CompletionStages
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.subCommandExec
import taboolib.expansion.createHelper
import java.time.Duration

@CommandHeader(
    "redis",
    description = "RedisChannel"
)
object RedisChannelCommand {

    private const val COMMAND_PERMISSION = "RedisChannel.Command.Main"

    @CommandBody
    val main = mainCommand { createHelper() }

    @CommandBody
    val reconnect = subCommandExec<ProxyCommandSender> {
        if (!ensurePermission(sender)) return@subCommandExec
        sender.sendMessage(RedisLanguage.text("command.reconnect.starting"))
        RedisChannelPlugin.api.reconnectAsync().whenComplete { snapshot, error ->
            BukkitThreadBoundary.runMain {
                if (error == null) {
                    sender.sendMessage(RedisLanguage.text("command.reconnect.success", "mode" to modeName(snapshot.mode)))
                } else {
                    sender.sendMessage(RedisLanguage.text(
                        "command.reconnect.failed",
                        "error" to CompletionStages.unwrap(error).message
                    ))
                }
            }
        }
    }

    @CommandBody
    val status = subCommandExec<ProxyCommandSender> {
        if (!ensurePermission(sender)) return@subCommandExec
        sender.sendMessage(RedisLanguage.text("command.status.loading"))
        RedisMonitor.statusAsync().whenComplete { snapshot, error ->
            BukkitThreadBoundary.runMain {
                if (error != null) {
                    sender.sendMessage(RedisLanguage.text(
                        "command.status.failed",
                        "error" to CompletionStages.unwrap(error).message
                    ))
                } else {
                    renderStatus(sender, snapshot)
                }
            }
        }
    }

    private fun ensurePermission(sender: ProxyCommandSender): Boolean {
        if (sender.hasPermission(COMMAND_PERMISSION)) return true
        sender.sendMessage(RedisLanguage.text("command.no-permission"))
        return false
    }

    internal fun renderStatus(sender: ProxyCommandSender, snapshot: RedisMonitor.MonitorSnapshot) {
        sender.sendMessage(RedisLanguage.text("status.separator"))
        sender.sendMessage(RedisLanguage.text("status.title"))
        sender.sendMessage(RedisLanguage.text("status.separator"))
        sender.sendMessage("")
        sender.sendMessage(RedisLanguage.text("status.basic"))
        sender.sendMessage(RedisLanguage.text("status.lifecycle", "value" to snapshot.lifecycleState))
        sender.sendMessage(RedisLanguage.text(
            "status.connection",
            "color" to snapshot.status.color,
            "value" to RedisLanguage.text(snapshot.status.languageKey)
        ))
        snapshot.mode?.let {
            sender.sendMessage(RedisLanguage.text("status.mode", "value" to modeName(it)))
        }
        snapshot.uptime?.let {
            sender.sendMessage(RedisLanguage.text("status.uptime", "value" to formatDuration(it)))
        }
        snapshot.remoteError?.let {
            sender.sendMessage(RedisLanguage.text("status.last-error", "value" to it))
        }

        snapshot.deploymentInfo?.let { deployment ->
            sender.sendMessage("")
            sender.sendMessage(RedisLanguage.text("status.deployment"))
            val architecture = when {
                deployment.isCluster -> RedisLanguage.text("architecture.cluster")
                deployment.isSentinel -> RedisLanguage.text("architecture.sentinel")
                deployment.isSlaves -> RedisLanguage.text("architecture.master-replica")
                else -> RedisLanguage.text("architecture.single")
            }
            sender.sendMessage(RedisLanguage.text("status.architecture", "value" to architecture))
            deployment.clusterNodeCount?.let {
                sender.sendMessage(RedisLanguage.text("status.cluster-nodes", "value" to it))
            }
            deployment.sentinelMasterId?.let {
                sender.sendMessage(RedisLanguage.text("status.master-id", "value" to it))
            }
            deployment.sentinelNodes?.forEach {
                sender.sendMessage(RedisLanguage.text("status.node", "value" to it))
            }
            deployment.readFrom?.let {
                sender.sendMessage(RedisLanguage.text("status.read-from", "value" to it))
            }
        }

        sender.sendMessage("")
        sender.sendMessage(RedisLanguage.text("status.performance"))
        sender.sendMessage(RedisLanguage.text(
            "status.ping",
            "value" to metric(snapshot.pingLatency)
        ))
        sender.sendMessage(RedisLanguage.text(
            "status.average",
            "value" to metric(snapshot.avgLatency)
        ))

        sender.sendMessage("")
        sender.sendMessage(RedisLanguage.text("status.commands"))
        sender.sendMessage(RedisLanguage.text("status.command-total", "value" to snapshot.commandCount))
        sender.sendMessage(RedisLanguage.text(
            "status.command-result",
            "success" to snapshot.successCount,
            "failed" to snapshot.failCount
        ))
        sender.sendMessage(RedisLanguage.text(
            "status.success-rate",
            "value" to "%.2f".format(snapshot.successRate)
        ))

        snapshot.poolStats?.let { pool ->
            sender.sendMessage("")
            sender.sendMessage(RedisLanguage.text("status.pool"))
            sender.sendMessage(RedisLanguage.text(
                "status.pool-active",
                "active" to pool.active,
                "max" to pool.maxTotal
            ))
            sender.sendMessage(RedisLanguage.text("status.pool-idle", "value" to pool.idle))
            sender.sendMessage(RedisLanguage.text("status.pool-creating", "value" to pool.waiters))
            sender.sendMessage(RedisLanguage.text(
                "status.pool-utilization",
                "value" to "%.1f".format(pool.utilization)
            ))
        }

        snapshot.serverInfo?.let { server ->
            sender.sendMessage("")
            sender.sendMessage(RedisLanguage.text("status.server"))
            server.redisVersion?.let {
                sender.sendMessage(RedisLanguage.text("status.server-version", "value" to it))
            }
            server.usedMemory?.let {
                sender.sendMessage(RedisLanguage.text("status.server-memory", "value" to it))
            }
            server.connectedClients?.let {
                sender.sendMessage(RedisLanguage.text("status.server-clients", "value" to it))
            }
            server.uptimeSeconds?.let {
                sender.sendMessage(RedisLanguage.text("status.server-uptime", "value" to formatSeconds(it)))
            }
        }
        sender.sendMessage(RedisLanguage.text("status.separator"))
    }

    private fun modeName(mode: RedisDeploymentMode?): String {
        return when (mode) {
            RedisDeploymentMode.SINGLE -> RedisLanguage.text("mode.single")
            RedisDeploymentMode.SENTINEL -> RedisLanguage.text("mode.sentinel")
            RedisDeploymentMode.MASTER_REPLICA -> RedisLanguage.text("mode.master-replica")
            RedisDeploymentMode.CLUSTER -> RedisLanguage.text("mode.cluster")
            null -> RedisLanguage.text("common.unavailable")
        }
    }

    private fun metric(value: Long): String {
        return if (value >= 0) "§f${value}ms" else RedisLanguage.text("common.unavailable")
    }

    internal fun formatDuration(duration: Duration): String {
        val hours = duration.toHours()
        val minutes = duration.toMinutes() % 60
        val seconds = duration.seconds % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }

    internal fun formatSeconds(seconds: Long): String {
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
