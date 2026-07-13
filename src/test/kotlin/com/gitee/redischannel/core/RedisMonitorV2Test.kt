package com.gitee.redischannel.core

import com.gitee.redischannel.core.runtime.RedisRuntime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RedisMonitorV2Test {

    @Test
    fun `business metrics are isolated by generation`() {
        val runtime = mock<RedisRuntime>()
        whenever(runtime.generation).thenReturn(42)
        RedisMonitor.beginGeneration(runtime)

        RedisMonitor.recordBusinessCommand(41, true, 100)
        RedisMonitor.recordBusinessCommand(42, true, 10)
        RedisMonitor.recordBusinessCommand(42, false, 20)

        val snapshot = RedisMonitor.getSnapshot()
        assertEquals(2, snapshot.commandCount)
        assertEquals(1, snapshot.successCount)
        assertEquals(1, snapshot.failCount)
        assertEquals(10, snapshot.avgLatency)
    }

    @Test
    fun `server info parser handles values and malformed numbers`() {
        val info = """
            redis_version:7.2.4
            os:Linux:custom
            uptime_in_seconds:not-a-number
            connected_clients:12
            used_memory_human:10M
        """.trimIndent()
        val parsed = RedisMonitor.parseServerInfo(info)
        assertEquals("7.2.4", parsed?.redisVersion)
        assertEquals("Linux:custom", parsed?.os)
        assertNull(parsed?.uptimeSeconds)
        assertEquals(12, parsed?.connectedClients)
    }

    @Test
    fun `pool utilization handles zero maximum`() {
        assertEquals(0.0, RedisMonitor.PoolStats(4, 0, 0, 0).utilization)
        assertEquals(50.0, RedisMonitor.PoolStats(4, 4, 8, 0).utilization)
    }
}
