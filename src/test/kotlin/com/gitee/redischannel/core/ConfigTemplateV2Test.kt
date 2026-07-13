package com.gitee.redischannel.core

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

class ConfigTemplateV2Test {

    private fun read(path: String): String = String(
        Files.readAllBytes(Paths.get(path)),
        StandardCharsets.UTF_8
    )

    @Test
    fun `default config uses blank password and async pool only`() {
        val content = read("src/main/resources/config.yml")
        assertTrue(content.contains("password: ''"))
        assertTrue(content.contains("shutdownGracePeriod:"))
        assertFalse(content.contains("maintNotifications:"))
        assertFalse(content.contains("blockWhenExhausted:"))
        assertFalse(content.contains("asyncPool:"))
    }

    @Test
    fun `cluster template has root host and valid timeout`() {
        val content = read("src/main/resources/clusters/cluster0.yml")
        assertTrue(Regex("(?m)^host:").containsMatchIn(content))
        assertTrue(content.contains("timeout: PT15S"))
        assertFalse(Regex("(?m)^redis:").containsMatchIn(content))
    }

    @Test
    fun `language files exist`() {
        assertTrue(Files.exists(Paths.get("src/main/resources/lang/zh_CN.yml")))
        assertTrue(Files.exists(Paths.get("src/main/resources/lang/en_US.yml")))
    }
}
