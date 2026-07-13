package com.gitee.redischannel.core.lifecycle

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AsyncOperationGateTest {

    @Test
    fun `stop rejects new operations and waits for in flight operations`() {
        val gate = AsyncOperationGate()
        assertTrue(gate.tryEnter())

        val drained = gate.stopAccepting()
        assertFalse(drained.isDone)
        assertFalse(gate.tryEnter())

        gate.leave()
        assertTrue(drained.isDone)
    }

    @Test
    fun `empty gate drains immediately`() {
        val gate = AsyncOperationGate()
        assertTrue(gate.stopAccepting().isDone)
        assertFalse(gate.isAccepting())
    }
}
