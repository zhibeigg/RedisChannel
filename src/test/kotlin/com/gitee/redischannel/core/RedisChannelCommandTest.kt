package com.gitee.redischannel.core

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration

class RedisChannelCommandTest {

    // formatDuration and formatSeconds are private — test via reflection

    private fun formatDuration(duration: Duration): String {
        val method = RedisChannelCommand::class.java.getDeclaredMethod("formatDuration", Duration::class.java)
        method.isAccessible = true
        return method.invoke(RedisChannelCommand, duration) as String
    }

    private fun formatSeconds(seconds: Long): String {
        val method = RedisChannelCommand::class.java.getDeclaredMethod("formatSeconds", Long::class.javaPrimitiveType)
        method.isAccessible = true
        return method.invoke(RedisChannelCommand, seconds) as String
    }

    @Nested
    inner class FormatDurationTest {

        @Test
        fun `should format seconds only`() {
            assertEquals("45s", formatDuration(Duration.ofSeconds(45)))
        }

        @Test
        fun `should format zero seconds`() {
            assertEquals("0s", formatDuration(Duration.ZERO))
        }

        @Test
        fun `should format minutes and seconds`() {
            assertEquals("5m 30s", formatDuration(Duration.ofMinutes(5).plusSeconds(30)))
        }

        @Test
        fun `should format minutes only`() {
            assertEquals("10m 0s", formatDuration(Duration.ofMinutes(10)))
        }

        @Test
        fun `should format hours minutes and seconds`() {
            assertEquals("2h 15m 30s", formatDuration(Duration.ofHours(2).plusMinutes(15).plusSeconds(30)))
        }

        @Test
        fun `should format hours only`() {
            assertEquals("1h 0m 0s", formatDuration(Duration.ofHours(1)))
        }

        @Test
        fun `should format large durations`() {
            // 48 hours
            assertEquals("48h 0m 0s", formatDuration(Duration.ofHours(48)))
        }

        @Test
        fun `should format 59 seconds`() {
            assertEquals("59s", formatDuration(Duration.ofSeconds(59)))
        }

        @Test
        fun `should format 1 minute exactly`() {
            assertEquals("1m 0s", formatDuration(Duration.ofMinutes(1)))
        }
    }

    @Nested
    inner class FormatSecondsTest {

        @Test
        fun `should format minutes only`() {
            assertEquals("5m", formatSeconds(300))
        }

        @Test
        fun `should format zero seconds`() {
            assertEquals("0m", formatSeconds(0))
        }

        @Test
        fun `should format hours and minutes`() {
            assertEquals("2h 30m", formatSeconds(9000))
        }

        @Test
        fun `should format hours only`() {
            assertEquals("1h 0m", formatSeconds(3600))
        }

        @Test
        fun `should format days hours and minutes`() {
            // 1 day + 2 hours + 30 minutes = 86400 + 7200 + 1800 = 95400
            assertEquals("1d 2h 30m", formatSeconds(95400))
        }

        @Test
        fun `should format days only`() {
            assertEquals("1d 0h 0m", formatSeconds(86400))
        }

        @Test
        fun `should format multiple days`() {
            // 3 days + 12 hours = 259200 + 43200 = 302400
            assertEquals("3d 12h 0m", formatSeconds(302400))
        }

        @Test
        fun `should format less than a minute`() {
            // 30 seconds → 0m
            assertEquals("0m", formatSeconds(30))
        }

        @Test
        fun `should format 59 minutes`() {
            assertEquals("59m", formatSeconds(3540))
        }
    }
}
