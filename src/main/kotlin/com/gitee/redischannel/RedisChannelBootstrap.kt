package com.gitee.redischannel

import com.gitee.redischannel.core.RedisConfig
import com.gitee.redischannel.core.RedisLanguage
import com.gitee.redischannel.util.CompletionStages
import org.bukkit.event.player.PlayerLoginEvent
import taboolib.common.platform.Plugin
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.pluginVersion
import taboolib.common.platform.function.severe
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

internal object RedisChannelBootstrap : Plugin() {

    @Config(migrate = true)
    lateinit var config: Configuration

    @Volatile
    private var redisSnapshot: RedisConfig? = null

    internal fun reloadConfigSnapshot(): RedisConfig {
        config.reload()
        val redisSection = config.getConfigurationSection("redis")
            ?: error("配置文件缺少 redis 节点，请检查 config.yml")
        return RedisConfig(redisSection).also {
            redisSnapshot = it
            RedisLanguage.reload(it.language)
        }
    }

    override fun onLoad() {
        RedisLanguage.reload(config.getString("language")?.takeIf { it.isNotBlank() } ?: "zh_CN")
    }

    override fun onEnable() {
        println()
        println("§9 ______     ______     _____     __     ______")
        println("§9/\\  == \\   /\\  ___\\   /\\  __-.  /\\ \\   /\\  ___\\         §8RedisChannel §eversion§7: §e$pluginVersion")
        println("§9\\ \\  __<   \\ \\  __\\   \\ \\ \\/\\ \\ \\ \\ \\  \\ \\___  \\        §7by. §bzhibei")
        println("§9 \\ \\_\\ \\_\\  \\ \\_____\\  \\ \\____-  \\ \\_\\  \\/\\_____\\")
        println("§9  \\/_/ /_/   \\/_____/   \\/____/   \\/_/   \\/_____/")
        println()
        RedisChannelPlugin.api.startAsync().whenComplete { _, error ->
            if (error != null) severe("RedisChannel 启动失败: ${CompletionStages.unwrap(error).message}")
        }
    }

    override fun onDisable() {
        RedisChannelPlugin.api.stopAsync().whenComplete { _, _ -> CompletionStages.shutdownScheduler() }
    }

    @SubscribeEvent
    fun onPlayerLogin(event: PlayerLoginEvent) {
        val shouldBlock = redisSnapshot?.bukkit?.blockLoginUntilReady ?: true
        if (shouldBlock && !RedisChannelPlugin.initialized) {
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, RedisLanguage.text("login.initializing"))
        }
    }
}
