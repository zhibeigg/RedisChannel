package com.gitee.redischannel.core

import io.lettuce.core.ReadFrom
import io.lettuce.core.RedisURI
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import taboolib.library.configuration.ConfigurationSection
import kotlin.time.Duration
import kotlin.time.toJavaDuration

class RedisConfigTest {

    // ── Helper: create a minimal valid mock ──────────────────────────────

    private fun minimalMock(
        host: String = "localhost",
        port: Int = 6379,
        password: String? = null,
        ssl: Boolean = false,
        timeout: String = "10s",
        database: Int = 0,
        ioThreadPoolSize: Int = 0,
        computationThreadPoolSize: Int = 0,
        autoReconnect: Boolean = true,
        pingBefore: Boolean = true,
        maintNotifications: Boolean = false,
        enableSentinel: Boolean = false,
        enableSlaves: Boolean = false,
        enableCluster: Boolean = false
    ): ConfigurationSection {
        val section = mock<ConfigurationSection>()
        whenever(section.getString("host")).thenReturn(host)
        whenever(section.getInt("port", 6379)).thenReturn(port)
        whenever(section.getString("password")).thenReturn(password)
        whenever(section.getBoolean("ssl")).thenReturn(ssl)
        whenever(section.getString("timeout")).thenReturn(timeout)
        whenever(section.getInt("database", 0)).thenReturn(database)
        whenever(section.getInt("ioThreadPoolSize")).thenReturn(ioThreadPoolSize)
        whenever(section.getInt("computationThreadPoolSize")).thenReturn(computationThreadPoolSize)
        whenever(section.getBoolean("autoReconnect", true)).thenReturn(autoReconnect)
        whenever(section.getBoolean("pingBeforeActivateConnection", true)).thenReturn(pingBefore)
        whenever(section.getBoolean("maintNotifications", false)).thenReturn(maintNotifications)
        whenever(section.getConfigurationSection("pool")).thenReturn(null)
        whenever(section.getConfigurationSection("asyncPool")).thenReturn(null)
        whenever(section.getBoolean("sentinel.enable", false)).thenReturn(enableSentinel)
        whenever(section.getBoolean("slaves.enable", false)).thenReturn(enableSlaves)
        whenever(section.getBoolean("cluster.enable", false)).thenReturn(enableCluster)
        return section
    }

    // ── 1. Basic property parsing ────────────────────────────────────────

    @Nested
    inner class BasicPropertyParsingTest {

        @Test
        fun `should parse all basic properties correctly`() {
            val section = minimalMock(
                host = "redis.example.com",
                port = 6380,
                password = "secret",
                ssl = true,
                timeout = "30s",
                database = 2,
                ioThreadPoolSize = 4,
                computationThreadPoolSize = 8,
                autoReconnect = false,
                pingBefore = false,
                maintNotifications = true
            )
            val config = RedisConfig(section)

            assertEquals("redis.example.com", config.host)
            assertEquals(6380, config.port)
            assertEquals("secret", config.password)
            assertTrue(config.ssl)
            assertEquals(Duration.parse("30s"), config.timeout)
            assertEquals(2, config.database)
            assertEquals(4, config.ioThreadPoolSize)
            assertEquals(8, config.computationThreadPoolSize)
            assertFalse(config.autoReconnect)
            assertFalse(config.pingBeforeActivateConnection)
            assertTrue(config.maintNotifications)
        }

        @Test
        fun `should handle null password`() {
            val section = minimalMock(password = null)
            val config = RedisConfig(section)
            assertNull(config.password)
        }

        @Test
        fun `should parse duration with minutes`() {
            val section = minimalMock(timeout = "5m")
            val config = RedisConfig(section)
            assertEquals(Duration.parse("5m"), config.timeout)
        }
    }

    // ── 2. Missing required fields ──────────────────────────────────────

    @Nested
    inner class MissingRequiredFieldsTest {

        @Test
        fun `should throw when host is null`() {
            val section = mock<ConfigurationSection>()
            whenever(section.getString("host")).thenReturn(null)
            // Other fields don't matter — host check comes first
            val ex = assertThrows<IllegalStateException> {
                RedisConfig(section)
            }
            assertTrue(ex.message!!.contains("host must be set"))
        }

        @Test
        fun `should throw when timeout is null`() {
            val section = mock<ConfigurationSection>()
            whenever(section.getString("host")).thenReturn("localhost")
            whenever(section.getInt("port", 6379)).thenReturn(6379)
            whenever(section.getString("password")).thenReturn(null)
            whenever(section.getBoolean("ssl")).thenReturn(false)
            whenever(section.getString("timeout")).thenReturn(null)
            val ex = assertThrows<IllegalStateException> {
                RedisConfig(section)
            }
            assertTrue(ex.message!!.contains("timeout must be set"))
        }
    }

    // ── 3. Default values ──────────────────────────────────────────────

    @Nested
    inner class DefaultValuesTest {

        @Test
        fun `should use default port 6379`() {
            val config = RedisConfig(minimalMock())
            assertEquals(6379, config.port)
        }

        @Test
        fun `should use default database 0`() {
            val config = RedisConfig(minimalMock())
            assertEquals(0, config.database)
        }

        @Test
        fun `should default autoReconnect to true`() {
            val config = RedisConfig(minimalMock())
            assertTrue(config.autoReconnect)
        }

        @Test
        fun `should default pingBeforeActivateConnection to true`() {
            val config = RedisConfig(minimalMock())
            assertTrue(config.pingBeforeActivateConnection)
        }

        @Test
        fun `should default maintNotifications to false`() {
            val config = RedisConfig(minimalMock())
            assertFalse(config.maintNotifications)
        }

        @Test
        fun `should default enableSentinel to false`() {
            val config = RedisConfig(minimalMock())
            assertFalse(config.enableSentinel)
        }

        @Test
        fun `should default enableSlaves to false`() {
            val config = RedisConfig(minimalMock())
            assertFalse(config.enableSlaves)
        }

        @Test
        fun `should default enableCluster to false`() {
            val config = RedisConfig(minimalMock())
            assertFalse(config.enableCluster)
        }
    }

    // ── 4. Pool with null ConfigurationSection ─────────────────────────

    @Nested
    inner class PoolNullSectionTest {

        @Test
        fun `should use all defaults when section is null`() {
            val pool = RedisConfig.Pool(null)
            assertTrue(pool.lifo)
            assertFalse(pool.fairness)
            assertEquals(8, pool.maxTotal)
            assertEquals(8, pool.maxIdle)
            assertEquals(0, pool.minIdle)
            assertFalse(pool.testOnCreate)
            assertFalse(pool.testOnBorrow)
            assertFalse(pool.testOnReturn)
            assertFalse(pool.testWhileIdle)
            assertNull(pool.maxWaitDuration)
            assertTrue(pool.blockWhenExhausted)
            assertNull(pool.timeBetweenEvictionRuns)
            assertNull(pool.minEvictableIdleDuration)
            assertNull(pool.softMinEvictableIdleDuration)
            assertEquals(3, pool.numTestsPerEvictionRun)
        }
    }

    // ── 5. Pool with configured values & poolConfig() methods ──────────

    @Nested
    inner class PoolConfiguredTest {

        private fun mockPoolSection(): ConfigurationSection {
            val section = mock<ConfigurationSection>()
            whenever(section.getBoolean("lifo", true)).thenReturn(false)
            whenever(section.getBoolean("fairness", false)).thenReturn(true)
            whenever(section.getInt("maxTotal", 8)).thenReturn(16)
            whenever(section.getInt("maxIdle", 8)).thenReturn(12)
            whenever(section.getInt("minIdle", 0)).thenReturn(4)
            whenever(section.getBoolean("testOnCreate", false)).thenReturn(true)
            whenever(section.getBoolean("testOnBorrow", false)).thenReturn(true)
            whenever(section.getBoolean("testOnReturn", false)).thenReturn(true)
            whenever(section.getBoolean("testWhileIdle", false)).thenReturn(true)
            whenever(section.getString("maxWaitDuration")).thenReturn("5s")
            whenever(section.getBoolean("blockWhenExhausted", true)).thenReturn(false)
            whenever(section.getString("timeBetweenEvictionRuns")).thenReturn("30s")
            whenever(section.getString("minEvictableIdleDuration")).thenReturn("1m")
            whenever(section.getString("softMinEvictableIdleDuration")).thenReturn("2m")
            whenever(section.getInt("numTestsPerEvictionRun", 3)).thenReturn(5)
            return section
        }

        @Test
        fun `should read all configured pool properties`() {
            val pool = RedisConfig.Pool(mockPoolSection())
            assertFalse(pool.lifo)
            assertTrue(pool.fairness)
            assertEquals(16, pool.maxTotal)
            assertEquals(12, pool.maxIdle)
            assertEquals(4, pool.minIdle)
            assertTrue(pool.testOnCreate)
            assertTrue(pool.testOnBorrow)
            assertTrue(pool.testOnReturn)
            assertTrue(pool.testWhileIdle)
            assertEquals(Duration.parse("5s"), pool.maxWaitDuration)
            assertFalse(pool.blockWhenExhausted)
            assertEquals(Duration.parse("30s"), pool.timeBetweenEvictionRuns)
            assertEquals(Duration.parse("1m"), pool.minEvictableIdleDuration)
            assertEquals(Duration.parse("2m"), pool.softMinEvictableIdleDuration)
            assertEquals(5, pool.numTestsPerEvictionRun)
        }

        @Test
        fun `poolConfig should produce matching GenericObjectPoolConfig`() {
            val pool = RedisConfig.Pool(mockPoolSection())
            val cfg = pool.poolConfig()

            assertFalse(cfg.lifo)
            assertTrue(cfg.fairness)
            assertEquals(16, cfg.maxTotal)
            assertEquals(12, cfg.maxIdle)
            assertEquals(4, cfg.minIdle)
            assertTrue(cfg.testOnCreate)
            assertTrue(cfg.testOnBorrow)
            assertTrue(cfg.testOnReturn)
            assertTrue(cfg.testWhileIdle)
            assertEquals(Duration.parse("5s").toJavaDuration(), cfg.maxWaitDuration)
            assertFalse(cfg.blockWhenExhausted)
            assertEquals(Duration.parse("30s").toJavaDuration(), cfg.timeBetweenEvictionRuns)
            assertEquals(Duration.parse("1m").toJavaDuration(), cfg.minEvictableIdleDuration)
            assertEquals(Duration.parse("2m").toJavaDuration(), cfg.softMinEvictableIdleDuration)
            assertEquals(5, cfg.numTestsPerEvictionRun)
        }

        @Test
        fun `clusterPoolConfig should produce matching GenericObjectPoolConfig`() {
            val pool = RedisConfig.Pool(mockPoolSection())
            val cfg = pool.clusterPoolConfig()

            assertEquals(16, cfg.maxTotal)
            assertEquals(12, cfg.maxIdle)
            assertEquals(4, cfg.minIdle)
            assertTrue(cfg.testOnBorrow)
            assertFalse(cfg.blockWhenExhausted)
        }

        @Test
        fun `slavesPoolConfig should produce matching GenericObjectPoolConfig`() {
            val pool = RedisConfig.Pool(mockPoolSection())
            val cfg = pool.slavesPoolConfig()

            assertEquals(16, cfg.maxTotal)
            assertEquals(12, cfg.maxIdle)
            assertEquals(4, cfg.minIdle)
            assertTrue(cfg.testOnReturn)
            assertTrue(cfg.testWhileIdle)
        }

        @Test
        fun `poolConfig with null durations should not set them`() {
            val pool = RedisConfig.Pool(null)
            val cfg = pool.poolConfig()

            // defaults from GenericObjectPoolConfig
            assertEquals(8, cfg.maxTotal)
            assertEquals(8, cfg.maxIdle)
            assertEquals(0, cfg.minIdle)
            assertTrue(cfg.lifo)
        }
    }

    // ── 6. AsyncPool ───────────────────────────────────────────────────

    @Nested
    inner class AsyncPoolTest {

        @Test
        fun `should use defaults when section is null`() {
            val asyncPool = RedisConfig.AsyncPool(null)
            assertEquals(8, asyncPool.maxTotal)
            assertEquals(8, asyncPool.maxIdle)
            assertEquals(0, asyncPool.minIdle)
        }

        @Test
        fun `should read configured values`() {
            val section = mock<ConfigurationSection>()
            whenever(section.getInt("maxTotal", 8)).thenReturn(20)
            whenever(section.getInt("maxIdle", 8)).thenReturn(15)
            whenever(section.getInt("minIdle", 0)).thenReturn(5)

            val asyncPool = RedisConfig.AsyncPool(section)
            assertEquals(20, asyncPool.maxTotal)
            assertEquals(15, asyncPool.maxIdle)
            assertEquals(5, asyncPool.minIdle)
        }

        @Test
        fun `poolConfig should produce BoundedPoolConfig with correct values`() {
            val section = mock<ConfigurationSection>()
            whenever(section.getInt("maxTotal", 8)).thenReturn(20)
            whenever(section.getInt("maxIdle", 8)).thenReturn(15)
            whenever(section.getInt("minIdle", 0)).thenReturn(5)

            val asyncPool = RedisConfig.AsyncPool(section)
            val cfg = asyncPool.poolConfig()

            assertEquals(20, cfg.maxTotal)
            assertEquals(15, cfg.maxIdle)
            assertEquals(5, cfg.minIdle)
        }

        @Test
        fun `poolConfig with defaults should match default BoundedPoolConfig values`() {
            val asyncPool = RedisConfig.AsyncPool(null)
            val cfg = asyncPool.poolConfig()

            assertEquals(8, cfg.maxTotal)
            assertEquals(8, cfg.maxIdle)
            assertEquals(0, cfg.minIdle)
        }
    }

    // ── 7. Sentinel ────────────────────────────────────────────────────

    @Nested
    inner class SentinelTest {

        @Test
        fun `should parse masterId`() {
            val section = mock<ConfigurationSection>()
            whenever(section.getString("masterId")).thenReturn("mymaster")
            whenever(section.getStringList("nodes")).thenReturn(emptyList())

            val sentinel = RedisConfig.Sentinel(section)
            assertEquals("mymaster", sentinel.masterId)
        }

        @Test
        fun `should throw when masterId is null`() {
            val section = mock<ConfigurationSection>()
            whenever(section.getString("masterId")).thenReturn(null)
            whenever(section.getStringList("nodes")).thenReturn(emptyList())

            val ex = assertThrows<IllegalStateException> {
                RedisConfig.Sentinel(section)
            }
            assertTrue(ex.message!!.contains("masterId must be set"))
        }

        @Test
        fun `should throw on invalid node format`() {
            val section = mock<ConfigurationSection>()
            whenever(section.getString("masterId")).thenReturn("mymaster")
            whenever(section.getStringList("nodes")).thenReturn(listOf("invalid-no-port"))

            assertThrows<IllegalArgumentException> {
                RedisConfig.Sentinel(section)
            }
        }

        @Test
        fun `enableSentinel should read from config`() {
            val section = minimalMock(enableSentinel = true)
            val config = RedisConfig(section)
            assertTrue(config.enableSentinel)
        }
    }

    // ── 8. Slaves ─────────────────────────────────────────────────────

    @Nested
    inner class SlavesTest {

        @Test
        fun `should parse readFrom master`() {
            val section = mock<ConfigurationSection>()
            whenever(section.getString("readFrom")).thenReturn("master")

            val slaves = RedisConfig.Slaves(section)
            // ReadFrom.valueOf("master") returns UPSTREAM instance
            assertSame(ReadFrom.UPSTREAM, slaves.readFrom)
        }

        @Test
        fun `should parse readFrom replicaPreferred`() {
            val section = mock<ConfigurationSection>()
            whenever(section.getString("readFrom")).thenReturn("replicaPreferred")

            val slaves = RedisConfig.Slaves(section)
            assertSame(ReadFrom.REPLICA_PREFERRED, slaves.readFrom)
        }

        @Test
        fun `should throw when readFrom is null`() {
            val section = mock<ConfigurationSection>()
            whenever(section.getString("readFrom")).thenReturn(null)

            val ex = assertThrows<IllegalStateException> {
                RedisConfig.Slaves(section)
            }
            assertTrue(ex.message!!.contains("readFrom must be set"))
        }

        @Test
        fun `should throw on invalid readFrom value`() {
            val section = mock<ConfigurationSection>()
            whenever(section.getString("readFrom")).thenReturn("INVALID_VALUE")

            assertThrows<IllegalArgumentException> {
                RedisConfig.Slaves(section)
            }
        }

        @Test
        fun `enableSlaves should read from config`() {
            val section = minimalMock(enableSlaves = true)
            val config = RedisConfig(section)
            assertTrue(config.enableSlaves)
        }
    }

    // ── 9. redisURIBuilder ─────────────────────────────────────────────

    @Nested
    inner class RedisURIBuilderTest {

        @Test
        fun `should build URI with basic properties`() {
            val section = minimalMock(
                host = "redis.example.com",
                port = 6380,
                ssl = true,
                timeout = "15s",
                database = 3
            )
            val config = RedisConfig(section)
            val uri = config.redisURIBuilder().build()

            assertEquals("redis.example.com", uri.host)
            assertEquals(6380, uri.port)
            assertTrue(uri.isSsl)
            assertEquals(Duration.parse("15s").toJavaDuration(), uri.timeout)
            assertEquals(3, uri.database)
        }

        @Test
        fun `should include password when set`() {
            val section = minimalMock(password = "mypassword")
            val config = RedisConfig(section)
            val uri = config.redisURIBuilder().build()

            val credentials = uri.credentialsProvider.resolveCredentials().block()
            assertNotNull(credentials)
            assertTrue(credentials!!.hasPassword())
            assertEquals("mypassword", String(credentials.password))
        }

        @Test
        fun `should not include password when null`() {
            val section = minimalMock(password = null)
            val config = RedisConfig(section)
            val uri = config.redisURIBuilder().build()

            val credentials = uri.credentialsProvider.resolveCredentials().block()
            // When no password is set, credentials may have no password
            assertTrue(credentials == null || !credentials.hasPassword())
        }

        @Test
        fun `should not set sentinel when enableSentinel is false`() {
            val section = minimalMock(enableSentinel = false)
            val config = RedisConfig(section)
            val uri = config.redisURIBuilder().build()

            assertNull(uri.sentinelMasterId)
            assertTrue(uri.sentinels.isEmpty())
        }

        @Test
        fun `should set sentinel masterId and nodes when enabled`() {
            val section = minimalMock(enableSentinel = true)

            // Mock sentinel sub-section
            val sentinelSection = mock<ConfigurationSection>()
            whenever(sentinelSection.getString("masterId")).thenReturn("mymaster")
            whenever(sentinelSection.getStringList("nodes")).thenReturn(emptyList())
            whenever(section.getConfigurationSection("sentinel")).thenReturn(sentinelSection)

            val config = RedisConfig(section)
            val uri = config.redisURIBuilder().build()

            assertEquals("mymaster", uri.sentinelMasterId)
        }

        @Test
        fun `should build URI with default values`() {
            val section = minimalMock()
            val config = RedisConfig(section)
            val uri = config.redisURIBuilder().build()

            assertEquals("localhost", uri.host)
            assertEquals(6379, uri.port)
            assertFalse(uri.isSsl)
            assertEquals(0, uri.database)
        }
    }

    // ── 10. enableCluster flag ──────────────────────────────────────────

    @Nested
    inner class EnableClusterTest {

        @Test
        fun `enableCluster should be true when configured`() {
            val section = minimalMock(enableCluster = true)
            val config = RedisConfig(section)
            assertTrue(config.enableCluster)
        }

        @Test
        fun `enableCluster should default to false`() {
            val section = minimalMock(enableCluster = false)
            val config = RedisConfig(section)
            assertFalse(config.enableCluster)
        }
    }

    // ── 11. Cluster.Node ────────────────────────────────────────────────

    @Nested
    inner class ClusterNodeTest {

        private fun mockNodeSection(
            host: String = "node1.example.com",
            port: Int = 7000,
            password: String? = null,
            ssl: Boolean = false,
            timeout: String = "10s",
            database: Int = 0,
            enableSentinel: Boolean = false
        ): ConfigurationSection {
            val section = mock<ConfigurationSection>()
            whenever(section.getString("host")).thenReturn(host)
            whenever(section.getInt("port", 6379)).thenReturn(port)
            whenever(section.getString("password")).thenReturn(password)
            whenever(section.getBoolean("ssl")).thenReturn(ssl)
            whenever(section.getString("timeout")).thenReturn(timeout)
            whenever(section.getInt("database", 0)).thenReturn(database)
            whenever(section.getBoolean("sentinel.enable", false)).thenReturn(enableSentinel)
            return section
        }

        @Test
        fun `should parse basic node properties`() {
            val section = mockNodeSection(
                host = "node1.example.com",
                port = 7000,
                password = "nodepass",
                ssl = true,
                timeout = "20s",
                database = 1
            )
            val node = RedisConfig.Cluster.Node(section)

            assertEquals("node1.example.com", node.host)
            assertEquals(7000, node.port)
            assertEquals("nodepass", node.password)
            assertTrue(node.ssl)
            assertEquals(Duration.parse("20s"), node.timeout)
            assertEquals(1, node.database)
        }

        @Test
        fun `should handle null password`() {
            val section = mockNodeSection(password = null)
            val node = RedisConfig.Cluster.Node(section)
            assertNull(node.password)
        }

        @Test
        fun `should throw when host is null`() {
            val section = mock<ConfigurationSection>()
            whenever(section.getString("host")).thenReturn(null)
            assertThrows<IllegalStateException> {
                RedisConfig.Cluster.Node(section)
            }
        }

        @Test
        fun `should throw when timeout is null`() {
            val section = mock<ConfigurationSection>()
            whenever(section.getString("host")).thenReturn("localhost")
            whenever(section.getInt("port", 6379)).thenReturn(6379)
            whenever(section.getString("password")).thenReturn(null)
            whenever(section.getBoolean("ssl")).thenReturn(false)
            whenever(section.getString("timeout")).thenReturn(null)
            assertThrows<IllegalStateException> {
                RedisConfig.Cluster.Node(section)
            }
        }

        @Test
        fun `redisURIBuilder should build URI with basic properties`() {
            val section = mockNodeSection(
                host = "cluster.example.com",
                port = 7001,
                ssl = true,
                timeout = "15s",
                database = 2
            )
            val node = RedisConfig.Cluster.Node(section)
            val uri = node.redisURIBuilder().build()

            assertEquals("cluster.example.com", uri.host)
            assertEquals(7001, uri.port)
            assertTrue(uri.isSsl)
            assertEquals(Duration.parse("15s").toJavaDuration(), uri.timeout)
            assertEquals(2, uri.database)
        }

        @Test
        fun `redisURIBuilder should include password when set`() {
            val section = mockNodeSection(password = "clusterpass")
            val node = RedisConfig.Cluster.Node(section)
            val uri = node.redisURIBuilder().build()

            val credentials = uri.credentialsProvider.resolveCredentials().block()
            assertNotNull(credentials)
            assertTrue(credentials!!.hasPassword())
            assertEquals("clusterpass", String(credentials.password))
        }

        @Test
        fun `redisURIBuilder should not include password when null`() {
            val section = mockNodeSection(password = null)
            val node = RedisConfig.Cluster.Node(section)
            val uri = node.redisURIBuilder().build()

            val credentials = uri.credentialsProvider.resolveCredentials().block()
            assertTrue(credentials == null || !credentials.hasPassword())
        }

        @Test
        fun `redisURIBuilder should not set sentinel when disabled`() {
            val section = mockNodeSection(enableSentinel = false)
            val node = RedisConfig.Cluster.Node(section)
            val uri = node.redisURIBuilder().build()

            assertNull(uri.sentinelMasterId)
            assertTrue(uri.sentinels.isEmpty())
        }

        @Test
        fun `redisURIBuilder should set sentinel when enabled`() {
            val section = mockNodeSection(enableSentinel = true)
            val sentinelSection = mock<ConfigurationSection>()
            whenever(sentinelSection.getString("masterId")).thenReturn("clustermaster")
            whenever(sentinelSection.getStringList("nodes")).thenReturn(emptyList())
            whenever(section.getConfigurationSection("sentinel")).thenReturn(sentinelSection)

            val node = RedisConfig.Cluster.Node(section)
            val uri = node.redisURIBuilder().build()

            assertEquals("clustermaster", uri.sentinelMasterId)
        }

        @Test
        fun `enableSentinel should read from config`() {
            val section = mockNodeSection(enableSentinel = true)
            val node = RedisConfig.Cluster.Node(section)
            assertTrue(node.enableSentinel)
        }
    }

    // ── 12. Cluster.Node.Sentinel ───────────────────────────────────────

    @Nested
    inner class ClusterNodeSentinelTest {

        @Test
        fun `should parse masterId`() {
            val section = mock<ConfigurationSection>()
            whenever(section.getString("masterId")).thenReturn("clustermaster")
            whenever(section.getStringList("nodes")).thenReturn(emptyList())

            val sentinel = RedisConfig.Cluster.Node.Sentinel(section)
            assertEquals("clustermaster", sentinel.masterId)
        }

        @Test
        fun `should throw when masterId is null`() {
            val section = mock<ConfigurationSection>()
            whenever(section.getString("masterId")).thenReturn(null)
            whenever(section.getStringList("nodes")).thenReturn(emptyList())

            assertThrows<IllegalStateException> {
                RedisConfig.Cluster.Node.Sentinel(section)
            }
        }

        @Test
        fun `should throw on invalid node format`() {
            val section = mock<ConfigurationSection>()
            whenever(section.getString("masterId")).thenReturn("master")
            whenever(section.getStringList("nodes")).thenReturn(listOf("no-port-here"))

            assertThrows<IllegalArgumentException> {
                RedisConfig.Cluster.Node.Sentinel(section)
            }
        }

        @Test
        fun `should throw on node with too many colons`() {
            val section = mock<ConfigurationSection>()
            whenever(section.getString("masterId")).thenReturn("master")
            whenever(section.getStringList("nodes")).thenReturn(listOf("host:port:extra"))

            assertThrows<IllegalArgumentException> {
                RedisConfig.Cluster.Node.Sentinel(section)
            }
        }

        @Test
        fun `should handle empty nodes list`() {
            val section = mock<ConfigurationSection>()
            whenever(section.getString("masterId")).thenReturn("master")
            whenever(section.getStringList("nodes")).thenReturn(emptyList())

            val sentinel = RedisConfig.Cluster.Node.Sentinel(section)
            assertTrue(sentinel.nodes.isEmpty())
        }
    }

    // ── 13. Pool duration parsing edge cases ────────────────────────────

    @Nested
    inner class PoolDurationEdgeCasesTest {

        private fun mockPoolSectionWithDurations(
            maxWait: String? = null,
            timeBetween: String? = null,
            minEvictable: String? = null,
            softMinEvictable: String? = null
        ): ConfigurationSection {
            val section = mock<ConfigurationSection>()
            whenever(section.getBoolean("lifo", true)).thenReturn(true)
            whenever(section.getBoolean("fairness", false)).thenReturn(false)
            whenever(section.getInt("maxTotal", 8)).thenReturn(8)
            whenever(section.getInt("maxIdle", 8)).thenReturn(8)
            whenever(section.getInt("minIdle", 0)).thenReturn(0)
            whenever(section.getBoolean("testOnCreate", false)).thenReturn(false)
            whenever(section.getBoolean("testOnBorrow", false)).thenReturn(false)
            whenever(section.getBoolean("testOnReturn", false)).thenReturn(false)
            whenever(section.getBoolean("testWhileIdle", false)).thenReturn(false)
            whenever(section.getString("maxWaitDuration")).thenReturn(maxWait)
            whenever(section.getBoolean("blockWhenExhausted", true)).thenReturn(true)
            whenever(section.getString("timeBetweenEvictionRuns")).thenReturn(timeBetween)
            whenever(section.getString("minEvictableIdleDuration")).thenReturn(minEvictable)
            whenever(section.getString("softMinEvictableIdleDuration")).thenReturn(softMinEvictable)
            whenever(section.getInt("numTestsPerEvictionRun", 3)).thenReturn(3)
            return section
        }

        @Test
        fun `should handle all null durations`() {
            val pool = RedisConfig.Pool(mockPoolSectionWithDurations())
            assertNull(pool.maxWaitDuration)
            assertNull(pool.timeBetweenEvictionRuns)
            assertNull(pool.minEvictableIdleDuration)
            assertNull(pool.softMinEvictableIdleDuration)
        }

        @Test
        fun `should parse ISO 8601 duration format`() {
            val pool = RedisConfig.Pool(mockPoolSectionWithDurations(
                maxWait = "PT15S",
                timeBetween = "PT30M",
                minEvictable = "PT1H",
                softMinEvictable = "PT2H"
            ))
            assertEquals(Duration.parse("PT15S"), pool.maxWaitDuration)
            assertEquals(Duration.parse("PT30M"), pool.timeBetweenEvictionRuns)
            assertEquals(Duration.parse("PT1H"), pool.minEvictableIdleDuration)
            assertEquals(Duration.parse("PT2H"), pool.softMinEvictableIdleDuration)
        }

        @Test
        fun `poolConfig should apply durations correctly`() {
            val pool = RedisConfig.Pool(mockPoolSectionWithDurations(
                maxWait = "5s",
                timeBetween = "30s",
                minEvictable = "1m",
                softMinEvictable = "2m"
            ))
            val cfg = pool.poolConfig()
            assertEquals(Duration.parse("5s").toJavaDuration(), cfg.maxWaitDuration)
            assertEquals(Duration.parse("30s").toJavaDuration(), cfg.timeBetweenEvictionRuns)
            assertEquals(Duration.parse("1m").toJavaDuration(), cfg.minEvictableIdleDuration)
            assertEquals(Duration.parse("2m").toJavaDuration(), cfg.softMinEvictableIdleDuration)
        }
    }

    // ── 14. Slaves readFrom variants ────────────────────────────────────

    @Nested
    inner class SlavesReadFromVariantsTest {

        @Test
        fun `should parse readFrom upstream`() {
            val section = mock<ConfigurationSection>()
            whenever(section.getString("readFrom")).thenReturn("upstream")
            val slaves = RedisConfig.Slaves(section)
            assertSame(ReadFrom.UPSTREAM, slaves.readFrom)
        }

        @Test
        fun `should parse readFrom any`() {
            val section = mock<ConfigurationSection>()
            whenever(section.getString("readFrom")).thenReturn("any")
            val slaves = RedisConfig.Slaves(section)
            assertSame(ReadFrom.ANY, slaves.readFrom)
        }

        @Test
        fun `should parse readFrom anyReplica`() {
            val section = mock<ConfigurationSection>()
            whenever(section.getString("readFrom")).thenReturn("anyReplica")
            val slaves = RedisConfig.Slaves(section)
            assertSame(ReadFrom.ANY_REPLICA, slaves.readFrom)
        }

        @Test
        fun `should parse readFrom lowestLatency`() {
            val section = mock<ConfigurationSection>()
            whenever(section.getString("readFrom")).thenReturn("lowestLatency")
            val slaves = RedisConfig.Slaves(section)
            assertSame(ReadFrom.LOWEST_LATENCY, slaves.readFrom)
        }
    }
}
