package com.gitee.redischannel.core

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

/**
 * 验证 config.yml 模板的完整性和默认值
 */
class ConfigTemplateTest {

    private val content: String by lazy {
        String(
            Files.readAllBytes(Paths.get("src/main/resources/config.yml")),
            StandardCharsets.UTF_8
        )
    }

    @Test
    fun `config template should exist`() {
        assertTrue(Files.exists(Paths.get("src/main/resources/config.yml")))
    }

    @Test
    fun `should have redis root section`() {
        assertTrue(content.contains("redis:"))
    }

    @Test
    fun `should have host field`() {
        assertTrue(Regex("(?m)^\\s*host:").containsMatchIn(content))
    }

    @Test
    fun `should have port field with default 6379`() {
        assertTrue(content.contains("port: 6379"))
    }

    @Test
    fun `should have password field`() {
        assertTrue(Regex("(?m)^\\s*password:").containsMatchIn(content))
    }

    @Test
    fun `should have ssl field defaulting to false`() {
        assertTrue(Regex("(?m)^\\s*ssl:\\s*false").containsMatchIn(content))
    }

    @Test
    fun `should have timeout field`() {
        assertTrue(Regex("(?m)^\\s*timeout:").containsMatchIn(content))
    }

    @Test
    fun `should have database field`() {
        assertTrue(Regex("(?m)^\\s*database:").containsMatchIn(content))
    }

    @Test
    fun `should have autoReconnect defaulting to true`() {
        assertTrue(Regex("(?m)^\\s*autoReconnect:\\s*true").containsMatchIn(content))
    }

    @Test
    fun `should have pingBeforeActivateConnection defaulting to true`() {
        assertTrue(Regex("(?m)^\\s*pingBeforeActivateConnection:\\s*true").containsMatchIn(content))
    }

    @Test
    fun `should have maintNotifications defaulting to false`() {
        assertTrue(Regex("(?m)^\\s*maintNotifications:\\s*false").containsMatchIn(content))
    }

    @Test
    fun `should have sentinel section`() {
        assertTrue(content.contains("sentinel:"))
    }

    @Test
    fun `should have sentinel enable defaulting to false`() {
        // Match "enable: false" under sentinel section
        assertTrue(content.contains("enable: false"))
    }

    @Test
    fun `should have slaves section`() {
        assertTrue(content.contains("slaves:"))
    }

    @Test
    fun `should have cluster section`() {
        assertTrue(content.contains("cluster:"))
    }

    @Test
    fun `should have pool section`() {
        assertTrue(content.contains("pool:"))
    }

    @Test
    fun `should have asyncPool section`() {
        assertTrue(content.contains("asyncPool:"))
    }

    @Test
    fun `should have pool maxTotal default 8`() {
        assertTrue(content.contains("maxTotal: 8"))
    }

    @Test
    fun `should have ioThreadPoolSize field`() {
        assertTrue(Regex("(?m)^\\s*ioThreadPoolSize:").containsMatchIn(content))
    }

    @Test
    fun `should have computationThreadPoolSize field`() {
        assertTrue(Regex("(?m)^\\s*computationThreadPoolSize:").containsMatchIn(content))
    }

    @Test
    fun `should have readFrom field in slaves section`() {
        assertTrue(Regex("(?m)^\\s*readFrom:").containsMatchIn(content))
    }

    @Test
    fun `should have maxRedirects in cluster section`() {
        assertTrue(content.contains("maxRedirects:"))
    }

    @Test
    fun `should have enablePeriodicRefresh in cluster section`() {
        assertTrue(content.contains("enablePeriodicRefresh:"))
    }
}
