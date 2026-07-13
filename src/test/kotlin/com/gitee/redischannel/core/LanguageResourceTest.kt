package com.gitee.redischannel.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

class LanguageResourceTest {

    @Test
    fun `language files expose the same message keys`() {
        val chinese = leafKeys("src/main/resources/lang/zh_CN.yml")
        val english = leafKeys("src/main/resources/lang/en_US.yml")

        assertEquals(chinese, english)
        assertTrue("command.no-permission" in chinese)
        assertTrue("mode.sentinel" in chinese)
        assertTrue("connection-status.connected" in chinese)
        assertTrue("connection-status.error" in chinese)
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
