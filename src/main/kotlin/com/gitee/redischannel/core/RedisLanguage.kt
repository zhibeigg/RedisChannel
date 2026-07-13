package com.gitee.redischannel.core

import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.releaseResourceFile
import taboolib.module.configuration.Configuration
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicReference

internal object RedisLanguage {

    private const val MESSAGE_DIRECTORY = "messages"
    private const val LEGACY_LANGUAGE_DIRECTORY = "lang"
    private const val FALLBACK_LOCALE = "zh_CN"

    private val language = AtomicReference<Configuration?>()

    fun reload(locale: String) {
        val dataFolder = getDataFolder()
        migrateLegacyLanguageFiles(dataFolder)

        val selected = locale.takeIf { it.matches(Regex("[A-Za-z_]+")) } ?: FALLBACK_LOCALE
        val actual = resolveLanguageFile(dataFolder, selected)
            ?: resolveLanguageFile(dataFolder, FALLBACK_LOCALE)
            ?: error("Missing bundled language resource: $MESSAGE_DIRECTORY/$FALLBACK_LOCALE.yml")
        language.set(Configuration.loadFromFile(actual))
    }

    private fun resolveLanguageFile(dataFolder: File, locale: String): File? {
        val relativePath = "$MESSAGE_DIRECTORY/$locale.yml"
        val messageFile = File(dataFolder, relativePath)
        if (messageFile.isFile) return messageFile

        val legacyFile = File(dataFolder, "$LEGACY_LANGUAGE_DIRECTORY/$locale.yml")
        if (legacyFile.isFile) return legacyFile

        runCatching { releaseResourceFile(relativePath, false) }
        return messageFile.takeIf { it.isFile }
    }

    fun text(path: String, vararg replacements: Pair<String, Any?>): String {
        var value = language.get()?.getString(path) ?: path
        replacements.forEach { (key, replacement) ->
            value = value.replace("{$key}", replacement?.toString() ?: "")
        }
        return value
    }
}

internal fun migrateLegacyLanguageFiles(dataFolder: File) {
    val legacyDirectory = File(dataFolder, "lang")
    val legacyFiles = legacyDirectory.listFiles { file ->
        file.isFile && file.extension.equals("yml", ignoreCase = true)
    } ?: return
    if (legacyFiles.isEmpty()) return

    val messageDirectory = File(dataFolder, "messages")
    if (!messageDirectory.isDirectory && !messageDirectory.mkdirs()) return

    legacyFiles.forEach { legacyFile ->
        val target = File(messageDirectory, legacyFile.name)
        if (!target.exists()) {
            copyWithoutOverwrite(legacyFile, target)
        }
    }
}

private fun copyWithoutOverwrite(source: File, target: File) {
    val temporary = runCatching {
        Files.createTempFile(target.parentFile.toPath(), ".${target.name}.", ".tmp")
    }.getOrNull() ?: return
    try {
        Files.copy(source.toPath(), temporary, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        Files.move(temporary, target.toPath())
    } catch (_: Exception) {
        // The legacy source remains untouched and is still a valid compatibility fallback.
    } finally {
        runCatching { Files.deleteIfExists(temporary) }
    }
}
