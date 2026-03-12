package com.gitee.redischannel.core

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.LongAdder

class RedisMonitorTest {

    // ── Reflection helpers ──────────────────────────────────────────────

    private fun getPrivateField(name: String): Any? {
        val field = RedisMonitor::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.get(RedisMonitor)
    }

    private val commandCount get() = (getPrivateField("commandCount") as LongAdder).sum()
    private val commandSuccessCount get() = (getPrivateField("commandSuccessCount") as LongAdder).sum()
    private val commandFailCount get() = (getPrivateField("commandFailCount") as LongAdder).sum()

    @Suppress("UNCHECKED_CAST")
    private val latencyHistory get() = getPrivateField("latencyHistory") as ConcurrentLinkedDeque<Long>
    private val latencyHistorySize get() = (getPrivateField("latencyHistorySize") as AtomicInteger).get()

    private fun parseServerInfo(info: String?): RedisMonitor.ServerInfo? {
        val method = RedisMonitor::class.java.getDeclaredMethod("parseServerInfo", String::class.java)
        method.isAccessible = true
        return method.invoke(RedisMonitor, info) as RedisMonitor.ServerInfo?
    }

    // ── Reset state before each test ────────────────────────────────────

    @BeforeEach
    fun resetState() {
        RedisMonitor.onDisconnected()
    }

    // ── 1. ConnectionStatus enum ────────────────────────────────────────

    @Nested
    inner class ConnectionStatusEnumTest {

        @Test
        fun `should have exactly 4 values`() {
            val values = RedisMonitor.ConnectionStatus.values()
            assertEquals(4, values.size)
        }

        @Test
        fun `CONNECTED has correct display and color`() {
            val status = RedisMonitor.ConnectionStatus.CONNECTED
            assertEquals("已连接", status.display)
            assertEquals("§a", status.color)
        }

        @Test
        fun `DISCONNECTED has correct display and color`() {
            val status = RedisMonitor.ConnectionStatus.DISCONNECTED
            assertEquals("未连接", status.display)
            assertEquals("§c", status.color)
        }

        @Test
        fun `CONNECTING has correct display and color`() {
            val status = RedisMonitor.ConnectionStatus.CONNECTING
            assertEquals("连接中", status.display)
            assertEquals("§e", status.color)
        }

        @Test
        fun `ERROR has correct display and color`() {
            val status = RedisMonitor.ConnectionStatus.ERROR
            assertEquals("异常", status.display)
            assertEquals("§4", status.color)
        }
    }

    // ── 2. onConnected() ────────────────────────────────────────────────

    @Nested
    inner class OnConnectedTest {

        @Test
        fun `should set connectionStatus to CONNECTED`() {
            RedisMonitor.onConnected()
            assertEquals(RedisMonitor.ConnectionStatus.CONNECTED, RedisMonitor.connectionStatus)
        }

        @Test
        fun `should set startTime to non-null`() {
            assertNull(RedisMonitor.startTime)
            RedisMonitor.onConnected()
            assertNotNull(RedisMonitor.startTime)
        }

        @Test
        fun `startTime should be close to now`() {
            val before = Instant.now()
            RedisMonitor.onConnected()
            val after = Instant.now()
            assertTrue(RedisMonitor.startTime!! >= before)
            assertTrue(RedisMonitor.startTime!! <= after)
        }

        @Test
        fun `should reset all counters`() {
            // Accumulate some state first
            RedisMonitor.onConnected()
            RedisMonitor.recordCommand(true, 10)
            RedisMonitor.recordCommand(false)
            assertTrue(commandCount > 0)

            // Re-connect should reset
            RedisMonitor.onConnected()
            assertEquals(0L, commandCount)
            assertEquals(0L, commandSuccessCount)
            assertEquals(0L, commandFailCount)
        }

        @Test
        fun `should clear latency history`() {
            RedisMonitor.onConnected()
            RedisMonitor.recordCommand(true, 50)
            assertTrue(latencyHistory.isNotEmpty())

            RedisMonitor.onConnected()
            assertTrue(latencyHistory.isEmpty())
            assertEquals(0, latencyHistorySize)
        }
    }

    // ── 3. onDisconnected() ─────────────────────────────────────────────

    @Nested
    inner class OnDisconnectedTest {

        @Test
        fun `should set connectionStatus to DISCONNECTED`() {
            RedisMonitor.onConnected()
            RedisMonitor.onDisconnected()
            assertEquals(RedisMonitor.ConnectionStatus.DISCONNECTED, RedisMonitor.connectionStatus)
        }

        @Test
        fun `should set startTime to null`() {
            RedisMonitor.onConnected()
            assertNotNull(RedisMonitor.startTime)
            RedisMonitor.onDisconnected()
            assertNull(RedisMonitor.startTime)
        }

        @Test
        fun `should set lastPingLatency to -1`() {
            RedisMonitor.onDisconnected()
            assertEquals(-1L, RedisMonitor.lastPingLatency)
        }
    }

    // ── 4. recordCommand(success=true) ──────────────────────────────────

    @Nested
    inner class RecordCommandSuccessTest {

        @Test
        fun `should increment commandCount`() {
            RedisMonitor.onConnected()
            RedisMonitor.recordCommand(true, 5)
            assertEquals(1L, commandCount)
        }

        @Test
        fun `should increment commandSuccessCount`() {
            RedisMonitor.onConnected()
            RedisMonitor.recordCommand(true, 5)
            assertEquals(1L, commandSuccessCount)
        }

        @Test
        fun `should not increment commandFailCount`() {
            RedisMonitor.onConnected()
            RedisMonitor.recordCommand(true, 5)
            assertEquals(0L, commandFailCount)
        }

        @Test
        fun `should add latency to history when latencyMs greater than 0`() {
            RedisMonitor.onConnected()
            RedisMonitor.recordCommand(true, 42)
            assertEquals(1, latencyHistory.size)
            assertEquals(42L, latencyHistory.first)
        }

        @Test
        fun `should accumulate multiple successful commands`() {
            RedisMonitor.onConnected()
            repeat(5) { RedisMonitor.recordCommand(true, (it + 1).toLong()) }
            assertEquals(5L, commandCount)
            assertEquals(5L, commandSuccessCount)
            assertEquals(5, latencyHistory.size)
        }
    }

    // ── 5. recordCommand(success=false) ─────────────────────────────────

    @Nested
    inner class RecordCommandFailTest {

        @Test
        fun `should increment commandCount`() {
            RedisMonitor.onConnected()
            RedisMonitor.recordCommand(false)
            assertEquals(1L, commandCount)
        }

        @Test
        fun `should increment commandFailCount`() {
            RedisMonitor.onConnected()
            RedisMonitor.recordCommand(false)
            assertEquals(1L, commandFailCount)
        }

        @Test
        fun `should not increment commandSuccessCount`() {
            RedisMonitor.onConnected()
            RedisMonitor.recordCommand(false)
            assertEquals(0L, commandSuccessCount)
        }

        @Test
        fun `should not add to latency history`() {
            RedisMonitor.onConnected()
            RedisMonitor.recordCommand(false)
            assertTrue(latencyHistory.isEmpty())
        }
    }

    // ── 6. recordCommand with latency=0 ─────────────────────────────────

    @Nested
    inner class RecordCommandZeroLatencyTest {

        @Test
        fun `should not add to latency history when latencyMs is 0`() {
            RedisMonitor.onConnected()
            RedisMonitor.recordCommand(true, 0)
            assertTrue(latencyHistory.isEmpty())
            assertEquals(0, latencyHistorySize)
        }

        @Test
        fun `should still increment success counter`() {
            RedisMonitor.onConnected()
            RedisMonitor.recordCommand(true, 0)
            assertEquals(1L, commandSuccessCount)
        }

        @Test
        fun `should not add to latency history when latencyMs is negative`() {
            RedisMonitor.onConnected()
            RedisMonitor.recordCommand(true, -5)
            assertTrue(latencyHistory.isEmpty())
            assertEquals(0, latencyHistorySize)
        }
    }

    // ── 7. Latency history capping at 100 ───────────────────────────────

    @Nested
    inner class LatencyHistoryCappingTest {

        @Test
        fun `should not grow beyond MAX_LATENCY_HISTORY`() {
            RedisMonitor.onConnected()
            repeat(110) { i ->
                RedisMonitor.recordCommand(true, (i + 1).toLong())
            }
            // The deque should be capped at ~100
            assertTrue(latencyHistory.size <= 100,
                "Latency history size ${latencyHistory.size} should be <= 100")
        }

        @Test
        fun `oldest entries should be evicted first`() {
            RedisMonitor.onConnected()
            repeat(110) { i ->
                RedisMonitor.recordCommand(true, (i + 1).toLong())
            }
            // The first entry should have been evicted; earliest remaining should be > 10
            val first = latencyHistory.first
            assertTrue(first > 1, "Oldest entries should have been evicted, but first=$first")
        }

        @Test
        fun `latencyHistorySize should track deque size`() {
            RedisMonitor.onConnected()
            repeat(110) { i ->
                RedisMonitor.recordCommand(true, (i + 1).toLong())
            }
            assertTrue(latencyHistorySize <= 100,
                "latencyHistorySize=$latencyHistorySize should be <= 100")
            // Size tracker should be consistent with actual deque size
            assertEquals(latencyHistory.size, latencyHistorySize)
        }
    }

    // ── 8. onConnected resets everything ────────────────────────────────

    @Nested
    inner class OnConnectedResetsEverythingTest {

        @Test
        fun `should reset counters after recording commands`() {
            RedisMonitor.onConnected()
            RedisMonitor.recordCommand(true, 10)
            RedisMonitor.recordCommand(true, 20)
            RedisMonitor.recordCommand(false)
            assertEquals(3L, commandCount)
            assertEquals(2L, commandSuccessCount)
            assertEquals(1L, commandFailCount)

            RedisMonitor.onConnected()
            assertEquals(0L, commandCount)
            assertEquals(0L, commandSuccessCount)
            assertEquals(0L, commandFailCount)
        }

        @Test
        fun `should clear latency history after recording commands`() {
            RedisMonitor.onConnected()
            repeat(50) { RedisMonitor.recordCommand(true, (it + 1).toLong()) }
            assertEquals(50, latencyHistory.size)

            RedisMonitor.onConnected()
            assertTrue(latencyHistory.isEmpty())
            assertEquals(0, latencyHistorySize)
        }

        @Test
        fun `should update startTime to a new value`() {
            RedisMonitor.onConnected()
            val firstStart = RedisMonitor.startTime
            assertNotNull(firstStart)

            Thread.sleep(10)
            RedisMonitor.onConnected()
            val secondStart = RedisMonitor.startTime
            assertNotNull(secondStart)
            assertTrue(secondStart!! > firstStart!!, "New startTime should be after the first one")
        }
    }

    // ── 9. parseServerInfo via reflection ───────────────────────────────

    @Nested
    inner class ParseServerInfoTest {

        @Test
        fun `should parse typical Redis INFO output`() {
            val info = """
                # Server
                redis_version:7.2.4
                os:Linux 5.15.0-91-generic x86_64
                uptime_in_seconds:123456
                
                # Clients
                connected_clients:42
                
                # Memory
                used_memory_human:10.50M
                used_memory_peak_human:15.00M
            """.trimIndent()

            val result = parseServerInfo(info)
            assertNotNull(result)
            assertEquals("7.2.4", result!!.redisVersion)
            assertEquals("Linux 5.15.0-91-generic x86_64", result.os)
            assertEquals(123456L, result.uptimeSeconds)
            assertEquals(42, result.connectedClients)
            assertEquals("10.50M", result.usedMemory)
            assertEquals("15.00M", result.usedMemoryPeak)
        }

        @Test
        fun `should return null for null input`() {
            val result = parseServerInfo(null)
            assertNull(result)
        }

        @Test
        fun `should handle empty string input`() {
            val result = parseServerInfo("")
            assertNotNull(result)
            // All fields should be null since no key-value pairs exist
            assertNull(result!!.redisVersion)
            assertNull(result.os)
            assertNull(result.uptimeSeconds)
            assertNull(result.connectedClients)
            assertNull(result.usedMemory)
            assertNull(result.usedMemoryPeak)
        }

        @Test
        fun `should handle lines without colons`() {
            val info = """
                # Server
                some_random_line_without_colon
                redis_version:6.0.0
            """.trimIndent()

            val result = parseServerInfo(info)
            assertNotNull(result)
            assertEquals("6.0.0", result!!.redisVersion)
        }

        @Test
        fun `should handle malformed numeric values gracefully`() {
            val info = """
                uptime_in_seconds:not_a_number
                connected_clients:also_not_a_number
            """.trimIndent()

            val result = parseServerInfo(info)
            assertNotNull(result)
            assertNull(result!!.uptimeSeconds)
            assertNull(result.connectedClients)
        }

        @Test
        fun `should handle values containing colons`() {
            // The split uses limit=2, so values with colons should be preserved
            val info = "os:Linux 5.15.0:custom:build"

            val result = parseServerInfo(info)
            assertNotNull(result)
            assertEquals("Linux 5.15.0:custom:build", result!!.os)
        }

        @Test
        fun `should handle whitespace around keys and values`() {
            val info = "  redis_version : 7.0.0 "

            val result = parseServerInfo(info)
            assertNotNull(result)
            assertEquals("7.0.0", result!!.redisVersion)
        }

        @Test
        fun `should return ServerInfo with all nulls for comment-only input`() {
            val info = """
                # Server
                # Memory
                # Clients
            """.trimIndent()

            val result = parseServerInfo(info)
            assertNotNull(result)
            assertNull(result!!.redisVersion)
            assertNull(result.os)
        }
    }

    // ── 10. MonitorSnapshot.successRate ──────────────────────────────────

    @Nested
    inner class MonitorSnapshotSuccessRateTest {

        private fun snapshot(commandCount: Long, successCount: Long) =
            RedisMonitor.MonitorSnapshot(
                status = RedisMonitor.ConnectionStatus.CONNECTED,
                mode = null,
                uptime = null,
                pingLatency = 0,
                avgLatency = 0,
                commandCount = commandCount,
                successCount = successCount,
                failCount = commandCount - successCount,
                poolStats = null,
                serverInfo = null,
                deploymentInfo = null
            )

        @Test
        fun `should return 100 when commandCount is 0`() {
            val snap = snapshot(0, 0)
            assertEquals(100.0, snap.successRate, 0.001)
        }

        @Test
        fun `should return 100 when all commands succeed`() {
            val snap = snapshot(50, 50)
            assertEquals(100.0, snap.successRate, 0.001)
        }

        @Test
        fun `should return 50 when half commands succeed`() {
            val snap = snapshot(100, 50)
            assertEquals(50.0, snap.successRate, 0.001)
        }

        @Test
        fun `should return 0 when no commands succeed`() {
            val snap = snapshot(100, 0)
            assertEquals(0.0, snap.successRate, 0.001)
        }

        @Test
        fun `should handle fractional rates`() {
            val snap = snapshot(3, 1)
            assertEquals(33.333, snap.successRate, 0.01)
        }
    }

    // ── 11. PoolStats.utilization ───────────────────────────────────────

    @Nested
    inner class PoolStatsUtilizationTest {

        @Test
        fun `should return 0 when maxTotal is 0`() {
            val stats = RedisMonitor.PoolStats(active = 5, idle = 0, maxTotal = 0, waiters = 0)
            assertEquals(0.0, stats.utilization, 0.001)
        }

        @Test
        fun `should return 100 when fully utilized`() {
            val stats = RedisMonitor.PoolStats(active = 10, idle = 0, maxTotal = 10, waiters = 0)
            assertEquals(100.0, stats.utilization, 0.001)
        }

        @Test
        fun `should return 50 when half utilized`() {
            val stats = RedisMonitor.PoolStats(active = 5, idle = 5, maxTotal = 10, waiters = 0)
            assertEquals(50.0, stats.utilization, 0.001)
        }

        @Test
        fun `should return 0 when no active connections`() {
            val stats = RedisMonitor.PoolStats(active = 0, idle = 8, maxTotal = 8, waiters = 0)
            assertEquals(0.0, stats.utilization, 0.001)
        }

        @Test
        fun `should handle fractional utilization`() {
            val stats = RedisMonitor.PoolStats(active = 1, idle = 2, maxTotal = 3, waiters = 0)
            assertEquals(33.333, stats.utilization, 0.01)
        }
    }

    // ── 12. DeploymentInfo data class ───────────────────────────────────

    @Nested
    inner class DeploymentInfoTest {

        @Test
        fun `should construct with all fields`() {
            val info = RedisMonitor.DeploymentInfo(
                isSentinel = true,
                sentinelMasterId = "mymaster",
                sentinelNodes = listOf("host1:26379", "host2:26379"),
                isSlaves = true,
                readFrom = "REPLICA_PREFERRED",
                isCluster = false,
                clusterNodeCount = null
            )
            assertTrue(info.isSentinel)
            assertEquals("mymaster", info.sentinelMasterId)
            assertEquals(listOf("host1:26379", "host2:26379"), info.sentinelNodes)
            assertTrue(info.isSlaves)
            assertEquals("REPLICA_PREFERRED", info.readFrom)
            assertFalse(info.isCluster)
            assertNull(info.clusterNodeCount)
        }

        @Test
        fun `should construct with minimal fields and defaults`() {
            val info = RedisMonitor.DeploymentInfo(
                isSentinel = false,
                isSlaves = false,
                isCluster = true,
                clusterNodeCount = 6
            )
            assertFalse(info.isSentinel)
            assertNull(info.sentinelMasterId)
            assertNull(info.sentinelNodes)
            assertFalse(info.isSlaves)
            assertNull(info.readFrom)
            assertTrue(info.isCluster)
            assertEquals(6, info.clusterNodeCount)
        }

        @Test
        fun `should support data class equality`() {
            val a = RedisMonitor.DeploymentInfo(
                isSentinel = false, isSlaves = false, isCluster = true, clusterNodeCount = 3
            )
            val b = RedisMonitor.DeploymentInfo(
                isSentinel = false, isSlaves = false, isCluster = true, clusterNodeCount = 3
            )
            assertEquals(a, b)
            assertEquals(a.hashCode(), b.hashCode())
        }

        @Test
        fun `should support data class copy`() {
            val original = RedisMonitor.DeploymentInfo(
                isSentinel = true,
                sentinelMasterId = "master1",
                isSlaves = false,
                isCluster = false
            )
            val copied = original.copy(sentinelMasterId = "master2")
            assertEquals("master2", copied.sentinelMasterId)
            assertTrue(copied.isSentinel)
        }

        @Test
        fun `should have meaningful toString`() {
            val info = RedisMonitor.DeploymentInfo(
                isSentinel = false, isSlaves = false, isCluster = true, clusterNodeCount = 3
            )
            val str = info.toString()
            assertTrue(str.contains("isCluster=true"))
            assertTrue(str.contains("clusterNodeCount=3"))
        }
    }
}
