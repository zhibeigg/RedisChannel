package com.gitee.redischannel.architecture

import com.gitee.redischannel.api.RedisCommandAPI
import com.gitee.redischannel.api.RedisPubSubAPI
import com.gitee.redischannel.api.cluster.RedisClusterCommandAPI
import com.gitee.redischannel.api.cluster.RedisClusterPubSubAPI
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletionStage

class ApiV2ContractTest {

    @Test
    fun `command APIs expose only completion stage operations`() {
        assertMethods(RedisCommandAPI::class.java, setOf("executeAsync"))
        assertMethods(RedisPubSubAPI::class.java, setOf("executePubSubAsync"))
        assertMethods(RedisClusterCommandAPI::class.java, setOf("executeClusterAsync"))
        assertMethods(RedisClusterPubSubAPI::class.java, setOf("executeClusterPubSubAsync"))
    }

    private fun assertMethods(type: Class<*>, expected: Set<String>) {
        val methods = type.methods.filter { it.declaringClass != Any::class.java }
        assertEquals(expected, methods.map { it.name }.toSet())
        methods.forEach { method ->
            assertEquals(CompletionStage::class.java, method.returnType)
            assertFalse(method.toGenericString().contains("org.reactivestreams"))
            assertFalse(method.toGenericString().contains("reactor.core"))
        }
    }
}
