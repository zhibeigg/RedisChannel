package com.gitee.redischannel.core

import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.releaseResourceFile
import taboolib.module.configuration.Configuration
import java.io.File
import java.util.concurrent.atomic.AtomicReference

internal object RedisLanguage {

    private val language = AtomicReference<Configuration?>()

    fun reload(locale: String) {
        val selected = locale.takeIf { it.matches(Regex("[A-Za-z_]+")) } ?: "zh_CN"
        val relativePath = "lang/$selected.yml"
        val file = File(getDataFolder(), relativePath)
        if (!file.exists()) {
            runCatching { releaseResourceFile(relativePath, false) }
        }
        val actual = if (file.isFile) file else {
            val fallbackPath = "lang/zh_CN.yml"
            val fallback = File(getDataFolder(), fallbackPath)
            if (!fallback.exists()) releaseResourceFile(fallbackPath, false)
            fallback
        }
        language.set(Configuration.loadFromFile(actual))
    }

    fun text(path: String, vararg replacements: Pair<String, Any?>): String {
        var value = language.get()?.getString(path) ?: path
        replacements.forEach { (key, replacement) ->
            value = value.replace("{$key}", replacement?.toString() ?: "")
        }
        return value
    }
}
