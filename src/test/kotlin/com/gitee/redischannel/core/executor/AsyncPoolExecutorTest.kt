package com.gitee.redischannel.core.executor

import com.gitee.redischannel.core.lifecycle.AsyncOperationGate
import io.lettuce.core.support.BoundedAsyncPool
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.CompletableFuture
import java.util.function.Function

class AsyncPoolExecutorTest {

    @Test
    fun `connection is released only after command and release stages complete`() {
        val pool = mock<BoundedAsyncPool<String>>()
        val command = CompletableFuture<Int>()
        val release = CompletableFuture<Void>()
        whenever(pool.acquire()).thenReturn(CompletableFuture.completedFuture("connection"))
        whenever(pool.release("connection")).thenReturn(release)
        val executor = AsyncPoolExecutor(1, pool, AsyncOperationGate()) { it }

        val result = executor.executeAsync(Function { command })
        assertFalse(result.isDone)
        verify(pool, never()).release("connection")

        command.complete(7)
        verify(pool).release("connection")
        assertFalse(result.isDone)

        release.complete(null)
        assertTrue(result.isDone)
        assertEquals(7, result.getNow(-1))
    }

    @Test
    fun `null command result remains a successful value`() {
        val pool = mock<BoundedAsyncPool<String>>()
        whenever(pool.acquire()).thenReturn(CompletableFuture.completedFuture("connection"))
        whenever(pool.release("connection")).thenReturn(CompletableFuture.completedFuture(null))
        val executor = AsyncPoolExecutor(1, pool, AsyncOperationGate()) { it }

        val result = executor.executeAsync(Function<String, CompletableFuture<String?>> {
            CompletableFuture.completedFuture(null)
        })

        assertTrue(result.isDone)
        assertFalse(result.isCompletedExceptionally)
        assertNull(result.getNow("fallback"))
    }

    @Test
    fun `command failure is propagated after connection release`() {
        val pool = mock<BoundedAsyncPool<String>>()
        val command = CompletableFuture<Int>()
        whenever(pool.acquire()).thenReturn(CompletableFuture.completedFuture("connection"))
        whenever(pool.release("connection")).thenReturn(CompletableFuture.completedFuture(null))
        val executor = AsyncPoolExecutor(1, pool, AsyncOperationGate()) { it }

        val result = executor.executeAsync(Function { command })
        command.completeExceptionally(IllegalStateException("boom"))

        assertTrue(result.isCompletedExceptionally)
        verify(pool).release("connection")
    }

    @Test
    fun `cancellation is propagated to command before releasing connection`() {
        val pool = mock<BoundedAsyncPool<String>>()
        val command = CompletableFuture<Int>()
        whenever(pool.acquire()).thenReturn(CompletableFuture.completedFuture("connection"))
        whenever(pool.release("connection")).thenReturn(CompletableFuture.completedFuture(null))
        val executor = AsyncPoolExecutor(1, pool, AsyncOperationGate()) { it }

        val result = executor.executeAsync(Function { command })
        result.cancel(true)

        assertTrue(command.isCancelled)
        verify(pool).release("connection")
    }

    @Test
    fun `synchronous acquire failure is reported without leaking gate entry`() {
        val pool = mock<BoundedAsyncPool<String>>()
        whenever(pool.acquire()).thenThrow(IllegalStateException("closed"))
        val gate = AsyncOperationGate()
        val executor = AsyncPoolExecutor(1, pool, gate) { it }

        val result = executor.executeAsync(Function { CompletableFuture.completedFuture(it.length) })

        assertTrue(result.isCompletedExceptionally)
        assertTrue(gate.stopAccepting().isDone)
    }

    @Test
    fun `cancellation before acquire completes is propagated to acquire stage`() {
        val pool = mock<BoundedAsyncPool<String>>()
        val acquire = CompletableFuture<String>()
        whenever(pool.acquire()).thenReturn(acquire)
        val executor = AsyncPoolExecutor(1, pool, AsyncOperationGate()) { it }

        val result = executor.executeAsync(Function { CompletableFuture.completedFuture(it.length) })
        result.cancel(true)

        assertTrue(acquire.isCancelled)
    }
}
