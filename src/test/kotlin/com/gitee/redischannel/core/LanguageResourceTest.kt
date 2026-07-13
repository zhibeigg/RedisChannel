package com.gitee.redischannel.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

class LanguageResourceTest {

    @Test
    fun `language files expose the same message keys`() {
        val chinese = leafKeys("src/main/resources/messages/zh_CN.yml")
        val english = leafKeys("src/main/resources/messages/en_US.yml")

        assertEquals(chinese, english)
        assertTrue("command.no-permission" in chinese)
        assertTrue("mode.sentinel" in chinese)
        assertTrue("connection-status.connected" in chinese)
        assertTrue("connection-status.error" in chinese)
    }

    @Test
    fun `legacy language files are copied without changing custom text`(@TempDir dataFolder: File) {
        val legacyDirectory = File(dataFolder, "lang")
        assertTrue(legacyDirectory.mkdirs())
        val legacy = File(legacyDirectory, "custom.yml")
        val customText = "message: '管理员自定义文本'\nmultiline: |\n  保持原样\n"
        legacy.writeText(customText, Charsets.UTF_8)

        migrateLegacyLanguageFiles(dataFolder)

        val migrated = File(dataFolder, "messages/custom.yml")
        assertTrue(migrated.isFile)
        assertEquals(customText, migrated.readText(Charsets.UTF_8))
        assertEquals(customText, legacy.readText(Charsets.UTF_8))
    }

    @Test
    fun `legacy migration never overwrites an existing message file`(@TempDir dataFolder: File) {
        val legacyDirectory = File(dataFolder, "lang")
        val messageDirectory = File(dataFolder, "messages")
        assertTrue(legacyDirectory.mkdirs())
        assertTrue(messageDirectory.mkdirs())
        val legacy = File(legacyDirectory, "zh_CN.yml")
        val current = File(messageDirectory, "zh_CN.yml")
        legacy.writeText("message: legacy", Charsets.UTF_8)
        current.writeText("message: current", Charsets.UTF_8)

        migrateLegacyLanguageFiles(dataFolder)

        assertEquals("message: current", current.readText(Charsets.UTF_8))
        assertEquals("message: legacy", legacy.readText(Charsets.UTF_8))
    }

    private fun leafKeys(path: String): Set<String> {
        val parents = mutableListOf<String>()
        val keys = linkedSetOf<String>()
        Files.readAllLines(Paths.get(path), StandardCharsets.UTF_8).forEach { raw ->
            if (raw.isBlank() || raw.trimStart().startsWith("#")) return@forEach
            val indent = raw.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
            val level = indent / 2
            val content = raw.trim()
            val separator = content.indexOf(':')
            if (separator <= 0) return@forEach
            val key = content.substring(0, separator).trim()
            while (parents.size > level) parents.removeAt(parents.lastIndex)
            val value = content.substring(separator + 1).trim()
            if (value.isEmpty()) {
                if (parents.size == level) parents += key
            } else {
                keys += (parents + key).joinToString(".")
            }
        }
        return keys
    }
}
