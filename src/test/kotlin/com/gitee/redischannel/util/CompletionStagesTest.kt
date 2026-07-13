package com.gitee.redischannel.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.function.Supplier

class CompletionStagesTest {

    @Test
    fun `timeout cancels the underlying future`() {
        val source = CompletableFuture<String>()
        val timed = CompletionStages.withTimeout(source, Duration.ofMillis(10), "timeout")

        val failure = assertThrows(ExecutionException::class.java) {
            timed.get(1, TimeUnit.SECONDS)
        }

        assertTrue(failure.cause is TimeoutException)
        assertTrue(source.isCancelled)
    }

    @Test
    fun `run all continues cleanup after an earlier failure`() {
        val calls = mutableListOf<Int>()
        val result = CompletionStages.runAll(listOf(
            Supplier {
                calls += 1
                CompletionStages.failed<Void>(IllegalStateException("first"))
            },
            Supplier {
                calls += 2
                CompletableFuture.completedFuture(null)
            }
        ))

        assertThrows(ExecutionException::class.java) { result.get(1, TimeUnit.SECONDS) }
        assertEquals(listOf(1, 2), calls)
    }
}
