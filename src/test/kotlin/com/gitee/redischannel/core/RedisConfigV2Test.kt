package com.gitee.redischannel.core

import com.gitee.redischannel.api.exception.RedisConfigurationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import taboolib.library.configuration.ConfigurationSection

class RedisConfigV2Test {

    @Test
    fun `blank password is treated as no password`() {
        val config = RedisConfig(minimalSection(""))
        assertNull(config.password)
    }

    @Test
    fun `pool validates bounds`() {
        val section = mock<ConfigurationSection>()
        whenever(section.getInt("maxTotal", 8)).thenReturn(4)
        whenever(section.getInt("maxIdle", 8)).thenReturn(5)
        whenever(section.getInt("minIdle", 0)).thenReturn(0)
        assertThrows(RedisConfigurationException::class.java) { RedisConfig.Pool(section) }
    }

    @Test
    fun `legacy async pool section is rejected`() {
        val section = minimalSection(null)
        whenever(section.getConfigurationSection("asyncPool")).thenReturn(mock())
        assertThrows(RedisConfigurationException::class.java) { RedisConfig(section) }
    }

    @Test
    fun `infinite duration is rejected during configuration parsing`() {
        val lifecycle = mock<ConfigurationSection>()
        whenever(lifecycle.getString("shutdownGracePeriod")).thenReturn("Infinity")
        whenever(lifecycle.getString("healthCheckPeriod")).thenReturn("5s")
        whenever(lifecycle.getString("statusTimeout")).thenReturn("5s")
        assertThrows(RedisConfigurationException::class.java) { RedisConfig.Lifecycle(lifecycle) }
    }

    @Test
    fun `health check duration that overflows nanoseconds is rejected`() {
        val lifecycle = mock<ConfigurationSection>()
        whenever(lifecycle.getString("shutdownGracePeriod")).thenReturn("10s")
        whenever(lifecycle.getString("healthCheckPeriod")).thenReturn("100000000d")
        whenever(lifecycle.getString("statusTimeout")).thenReturn("5s")
        assertThrows(RedisConfigurationException::class.java) { RedisConfig.Lifecycle(lifecycle) }
    }

    @Test
    fun `cluster and sentinel cannot be enabled together`() {
        val section = minimalSection(null)
        whenever(section.getBoolean("sentinel.enable", false)).thenReturn(true)
        whenever(section.getBoolean("cluster.enable", false)).thenReturn(true)
        assertThrows(RedisConfigurationException::class.java) { RedisConfig(section) }
    }

    @Test
    fun `health check period shorter than one second is rejected`() {
        val lifecycle = mock<ConfigurationSection>()
        whenever(lifecycle.getString("shutdownGracePeriod")).thenReturn("10s")
        whenever(lifecycle.getString("healthCheckPeriod")).thenReturn("500ms")
        whenever(lifecycle.getString("statusTimeout")).thenReturn("5s")
        assertThrows(RedisConfigurationException::class.java) { RedisConfig.Lifecycle(lifecycle) }
    }

    @Test
    fun `sentinel parser supports bracketed ipv6`() {
        val node = RedisConfig.Sentinel.parseNodeAddress("[::1]:26379")
        assertEquals("::1", node.host)
        assertEquals(26379, node.port)
    }

    @Test
    fun `invalid sentinel port is rejected`() {
        assertThrows(RedisConfigurationException::class.java) {
            RedisConfig.Sentinel.parseNodeAddress("localhost:not-a-port")
        }
    }

    private fun minimalSection(password: String?): ConfigurationSection {
        val root = mock<ConfigurationSection>()
        val section = mock<ConfigurationSection>()
        whenever(section.parent).thenReturn(root)
        whenever(root.getString("language")).thenReturn("zh_CN")
        whenever(root.getConfigurationSection("bukkit")).thenReturn(null)
        whenever(section.getString("host")).thenReturn("localhost")
        whenever(section.getInt("port", 6379)).thenReturn(6379)
        whenever(section.getString("password")).thenReturn(password)
        whenever(section.getBoolean("ssl")).thenReturn(false)
        whenever(section.getString("truststorePassword")).thenReturn("")
        whenever(section.getString("timeout")).thenReturn("PT15S")
        whenever(section.getInt("database", 0)).thenReturn(0)
        whenever(section.getInt("ioThreadPoolSize")).thenReturn(0)
        whenever(section.getInt("computationThreadPoolSize")).thenReturn(0)
        whenever(section.getBoolean("autoReconnect", true)).thenReturn(true)
        whenever(section.getBoolean("pingBeforeActivateConnection", true)).thenReturn(true)
        whenever(section.getConfigurationSection("pool")).thenReturn(null)
        whenever(section.getConfigurationSection("asyncPool")).thenReturn(null)
        whenever(section.getConfigurationSection("lifecycle")).thenReturn(null)
        whenever(section.getBoolean("sentinel.enable", false)).thenReturn(false)
        whenever(section.getBoolean("slaves.enable", false)).thenReturn(false)
        whenever(section.getBoolean("cluster.enable", false)).thenReturn(false)
        return section
    }
}
