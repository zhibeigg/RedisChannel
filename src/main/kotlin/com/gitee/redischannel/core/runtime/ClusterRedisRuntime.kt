package com.gitee.redischannel.core.runtime

import com.gitee.redischannel.api.RedisDeploymentMode
import com.gitee.redischannel.core.RedisConfig
import com.gitee.redischannel.core.RedisMonitor
import com.gitee.redischannel.core.executor.AsyncPoolExecutor
import com.gitee.redischannel.core.executor.SharedConnectionExecutor
import com.gitee.redischannel.core.lifecycle.AsyncOperationGate
import com.gitee.redischannel.util.CompletionStages
import com.gitee.redischannel.util.asCompletableFuture
import io.lettuce.core.cluster.RedisClusterClient
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands
import io.lettuce.core.cluster.pubsub.StatefulRedisClusterPubSubConnection
import io.lettuce.core.cluster.pubsub.api.async.RedisClusterPubSubAsyncCommands
import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands
import io.lettuce.core.resource.DefaultClientResources
import io.lettuce.core.support.BoundedAsyncPool
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit
import java.util.function.Function
import java.util.function.Supplier

internal class ClusterRedisRuntime(
    override val generation: Long,
    override val config: RedisConfig,
    private val resources: DefaultClientResources,
    private val client: RedisClusterClient,
    private val pool: BoundedAsyncPool<StatefulRedisClusterConnection<String, String>>,
    private val pubSubConnection: StatefulRedisClusterPubSubConnection<String, String>
) : RedisRuntime {

    override val mode = RedisDeploymentMode.CLUSTER

    private val gate = AsyncOperationGate()
    private val commandExecutor = AsyncPoolExecutor<
        StatefulRedisClusterConnection<String, String>,
        RedisClusterAsyncCommands<String, String>
    >(
        generation,
        pool,
        gate
    ) { connection -> connection.async() }
    private val pubSubExecutor = SharedConnectionExecutor<RedisClusterPubSubAsyncCommands<String, String>>(
        generation,
        gate,
        pubSubConnection.async()
    )

    override fun <T> executeClusterAsync(
        action: Function<RedisClusterAsyncCommands<String, String>, out CompletionStage<T>>
    ): CompletionStage<T> = commandExecutor.executeAsync(action)

    override fun <T> executeClusterPubSubAsync(
        action: Function<RedisClusterPubSubAsyncCommands<String, String>, out CompletionStage<T>>
    ): CompletionStage<T> = pubSubExecutor.executeAsync(action)

    override fun <T> executePubSubAsync(
        action: Function<RedisPubSubAsyncCommands<String, String>, out CompletionStage<T>>
    ): CompletionStage<T> = pubSubExecutor.executeAsync(Function { commands -> action.apply(commands) })

    override fun pingAsync(): CompletionStage<Long> {
        val start = System.nanoTime()
        return commandExecutor.executeAsync(
            Function { commands -> commands.ping().thenApply { response ->
                if (response != "PONG") throw IllegalStateException("Redis Cluster PING 返回异常: $response")
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
            "等待 Redis Cluster 在途操作结束超时"
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
