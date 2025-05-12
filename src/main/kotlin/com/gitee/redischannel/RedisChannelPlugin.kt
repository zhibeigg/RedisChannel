package com.gitee.redischannel

import com.gitee.redischannel.api.RedisChannelAPI
import com.gitee.redischannel.core.RedisManager
import taboolib.common.platform.Plugin
import taboolib.common.platform.function.pluginVersion

object RedisChannelPlugin : Plugin() {

    val api: RedisChannelAPI
        get() = RedisManager

    override fun onEnable() {
        println()
        println("§9 ______     ______     _____     __     ______")
        println("§9/\\  == \\   /\\  ___\\   /\\  __-.  /\\ \\   /\\  ___\\         §8RedisChannel §eversion§7: §e$pluginVersion")
        println("§9\\ \\  __<   \\ \\  __\\   \\ \\ \\/\\ \\ \\ \\ \\  \\ \\___  \\        §7by. §bzhibei")
        println("§9 \\ \\_\\ \\_\\  \\ \\_____\\  \\ \\____-  \\ \\_\\  \\/\\_____\\")
        println("§9  \\/_/ /_/   \\/_____/   \\/____/   \\/_/   \\/_____/")
        println()
    }
}