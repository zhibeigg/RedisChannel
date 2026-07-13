package com.gitee.redischannel.core.runtime

import com.gitee.redischannel.api.RedisDeploymentMode
import com.gitee.redischannel.core.RedisConfig
import com.gitee.redischannel.core.RedisMonitor
import com.gitee.redischannel.core.executor.AsyncPoolExecutor
import com.gitee.redischannel.core.executor.SharedConnectionExecutor
import com.gitee.redischannel.core.lifecycle.AsyncOperationGate
import com.gitee.redischannel.util.CompletionStages
import com.gitee.redischannel.util.asCompletableFuture
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection
import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands
import io.lettuce.core.resource.DefaultClientResources
import io.lettuce.core.support.BoundedAsyncPool
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit
import java.util.function.Function
import java.util.function.Supplier

internal class SingleRedisRuntime(
    override val generation: Long,
    override val mode: RedisDeploymentMode,
    override val config: RedisConfig,
    private val resources: DefaultClientResources,
    private val client: RedisClient,
    private val pool: BoundedAsyncPool<StatefulRedisConnection<String, String>>,
    private val pubSubConnection: StatefulRedisPubSubConnection<String, String>
) : RedisRuntime {

    private val gate = AsyncOperationGate()
    private val commandExecutor = AsyncPoolExecutor(
        generation,
        pool,
        gate
    ) { connection: StatefulRedisConnection<String, String> -> connection.async() }
    private val pubSubExecutor = SharedConnectionExecutor(
        generation,
        gate,
        pubSubConnection.async()
    )

    override fun <T> executeAsync(
        action: Function<RedisAsyncCommands<String, String>, out CompletionStage<T>>
    ): CompletionStage<T> = commandExecutor.executeAsync(action)

    override fun <T> executePubSubAsync(
        action: Function<RedisPubSubAsyncCommands<String, String>, out CompletionStage<T>>
    ): CompletionStage<T> = pubSubExecutor.executeAsync(action)

    override fun pingAsync(): CompletionStage<Long> {
        val start = System.nanoTime()
        return commandExecutor.executeAsync(
            Function { commands -> commands.ping().thenApply { response ->
                if (response != "PONG") throw IllegalStateException("Redis PING 返回异常: $response")
                (System.nanoTime() - start).coerceAtLeast(0) / 1_000_000
            } },
            recordMetrics = false
        )
    }

    override fun serverInfoAsync(): CompletionStage<String> {
        return commandExecutor.executeAsync(Function { commands -> commands.info() }, recordMetrics = false)
    }

    override fun poolStats(): RedisMonitor.PoolStats {
        return RedisMonitor.PoolStats(
            active = (pool.objectCount - pool.idle).coerceAtLeast(0),
            idle = pool.idle,
            maxTotal = pool.maxTotal,
            waiters = pool.creationInProgress
        )
    }

    override fun closeAsync(gracePeriod: Duration): CompletableFuture<Void> {
        val drained = CompletionStages.withTimeout(
            gate.stopAccepting(),
            gracePeriod,
            "等待 Redis 在途操作结束超时"
        ).handle { _, _ -> null }
        return drained.thenCompose {
            CompletionStages.runAll(listOf(
                Supplier { pubSubConnection.closeAsync() },
                Supplier { pool.closeAsync() },
                Supplier { client.shutdownAsync(0, gracePeriod.toMillis().coerceAtLeast(1), TimeUnit.MILLISECONDS) },
                Supplier {
                    resources.shutdown(0, gracePeriod.toMillis().coerceAtLeast(1), TimeUnit.MILLISECONDS)
                        .asCompletableFuture()
                        .thenApply { null }
                }
            ))
        }
    }
}
