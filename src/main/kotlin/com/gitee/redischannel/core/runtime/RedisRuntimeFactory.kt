package com.gitee.redischannel.core.runtime

import com.gitee.redischannel.api.RedisDeploymentMode
import com.gitee.redischannel.core.RedisConfig
import com.gitee.redischannel.util.CompletionStages
import com.gitee.redischannel.util.asCompletableFuture
import io.lettuce.core.AbstractRedisClient
import io.lettuce.core.ClientOptions
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.cluster.ClusterClientOptions
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions
import io.lettuce.core.cluster.RedisClusterClient
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.lettuce.core.codec.StringCodec
import io.lettuce.core.masterreplica.MasterReplica
import io.lettuce.core.resource.DefaultClientResources
import io.lettuce.core.support.AsyncConnectionPoolSupport
import io.lettuce.core.support.BoundedAsyncPool
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit
import java.util.function.Supplier
import kotlin.time.toJavaDuration

internal object RedisRuntimeFactory {

    fun createAsync(generation: Long, config: RedisConfig): CompletableFuture<RedisRuntime> {
        return if (config.enableCluster) createClusterAsync(generation, config) else createSingleAsync(generation, config)
    }

    private fun createSingleAsync(generation: Long, config: RedisConfig): CompletableFuture<RedisRuntime> {
        val resources = try {
            buildResources(config)
        } catch (error: Throwable) {
            return CompletionStages.failed(error)
        }
        val uri = try {
            config.redisURIBuilder().build()
        } catch (error: Throwable) {
            return failAfterClose(error, closeResources(config, resources))
        }
        val clientOptions = try {
            ClientOptions.builder()
                .autoReconnect(config.autoReconnect)
                .pingBeforeActivateConnection(config.pingBeforeActivateConnection)
                .apply { if (config.ssl) sslOptions(config.sslOptions) }
                .build()
        } catch (error: Throwable) {
            return failAfterClose(error, closeResources(config, resources))
        }
        val client = try {
            RedisClient.create(resources, uri).apply { options = clientOptions }
        } catch (error: Throwable) {
            return failAfterClose(error, closeResources(config, resources))
        }

        val pool: BoundedAsyncPool<StatefulRedisConnection<String, String>> = try {
            AsyncConnectionPoolSupport.createBoundedObjectPool(
                Supplier<CompletionStage<StatefulRedisConnection<String, String>>> {
                    if (config.enableSlaves) {
                        MasterReplica.connectAsync(client, StringCodec.UTF8, uri).thenApply { connection ->
                            config.slaves?.let { connection.readFrom = it.readFrom }
                            connection as StatefulRedisConnection<String, String>
                        }
                    } else {
                        client.connectAsync(StringCodec.UTF8, uri)
                    }
                },
                config.pool.poolConfig()
            )
        } catch (error: Throwable) {
            return failAfterClose(error, closeClientResources(config, resources, client))
        }

        val result = CompletableFuture<RedisRuntime>()
        val pubSubStage = try {
            client.connectPubSubAsync(StringCodec.UTF8, uri)
        } catch (error: Throwable) {
            return failAfterClose(error, closeSinglePartial(config, resources, client, pool))
        }
        pubSubStage.whenComplete { pubSub, pubSubError ->
            if (pubSubError != null) {
                val closeStage = try {
                    closeSinglePartial(config, resources, client, pool)
                } catch (closeError: Throwable) {
                    CompletionStages.failed<Void>(closeError)
                }
                closeStage.whenComplete { _, closeError ->
                    completeCreationFailure(result, pubSubError, closeError)
                }
                return@whenComplete
            }
            val mode = when {
                config.enableSentinel -> RedisDeploymentMode.SENTINEL
                config.enableSlaves -> RedisDeploymentMode.MASTER_REPLICA
                else -> RedisDeploymentMode.SINGLE
            }
            val runtime = SingleRedisRuntime(generation, mode, config, resources, client, pool, pubSub)
            validateRuntime(runtime, result)
        }
        return result
    }

    private fun createClusterAsync(generation: Long, config: RedisConfig): CompletableFuture<RedisRuntime> {
        val clusterConfig = config.cluster
            ?: return CompletionStages.failed(IllegalStateException("缺少 Redis Cluster 配置"))
        val resources = try {
            buildResources(config)
        } catch (error: Throwable) {
            return CompletionStages.failed(error)
        }
        val uris = try {
            clusterConfig.nodes.map { it.redisURIBuilder().build() }
        } catch (error: Throwable) {
            return failAfterClose(error, closeResources(config, resources))
        }
        val clientOptions = try {
            val topologyBuilder = ClusterTopologyRefreshOptions.builder()
                .enablePeriodicRefresh(clusterConfig.enablePeriodicRefresh)
                .refreshTriggersReconnectAttempts(clusterConfig.refreshTriggersReconnectAttempts)
                .dynamicRefreshSources(clusterConfig.dynamicRefreshSources)
                .closeStaleConnections(clusterConfig.closeStaleConnections)
            clusterConfig.refreshPeriod?.let { topologyBuilder.refreshPeriod(it.toJavaDuration()) }
            clusterConfig.adaptiveRefreshTriggersTimeout?.let {
                topologyBuilder.adaptiveRefreshTriggersTimeout(it.toJavaDuration())
            }
            if (clusterConfig.enableAdaptiveRefreshTrigger.isNotEmpty()) {
                topologyBuilder.enableAdaptiveRefreshTrigger(*clusterConfig.enableAdaptiveRefreshTrigger.toTypedArray())
            }
            ClusterClientOptions.builder()
                .topologyRefreshOptions(topologyBuilder.build())
                .autoReconnect(config.autoReconnect)
                .maxRedirects(clusterConfig.maxRedirects)
                .validateClusterNodeMembership(clusterConfig.validateClusterNodeMembership)
                .pingBeforeActivateConnection(config.pingBeforeActivateConnection)
                .apply {
                    if (config.ssl || clusterConfig.nodes.any { it.ssl }) sslOptions(config.sslOptions)
                }
                .build()
        } catch (error: Throwable) {
            return failAfterClose(error, closeResources(config, resources))
        }
        val client = try {
            RedisClusterClient.create(resources, uris).apply { setOptions(clientOptions) }
        } catch (error: Throwable) {
            return failAfterClose(error, closeResources(config, resources))
        }
        val pool: BoundedAsyncPool<StatefulRedisClusterConnection<String, String>> = try {
            AsyncConnectionPoolSupport.createBoundedObjectPool(
                Supplier<CompletionStage<StatefulRedisClusterConnection<String, String>>> {
                    client.connectAsync(StringCodec.UTF8).thenApply { connection ->
                        config.slaves?.let { connection.readFrom = it.readFrom }
                        connection
                    }
                },
                config.pool.poolConfig()
            )
        } catch (error: Throwable) {
            return failAfterClose(error, closeClientResources(config, resources, client))
        }

        val result = CompletableFuture<RedisRuntime>()
        val pubSubStage = try {
            client.connectPubSubAsync(StringCodec.UTF8)
        } catch (error: Throwable) {
            return failAfterClose(error, closeClusterPartial(config, resources, client, pool))
        }
        pubSubStage.whenComplete { pubSub, pubSubError ->
            if (pubSubError != null) {
                val closeStage = try {
                    closeClusterPartial(config, resources, client, pool)
                } catch (closeError: Throwable) {
                    CompletionStages.failed<Void>(closeError)
                }
                closeStage.whenComplete { _, closeError ->
                    completeCreationFailure(result, pubSubError, closeError)
                }
                return@whenComplete
            }
            validateRuntime(ClusterRedisRuntime(generation, config, resources, client, pool, pubSub), result)
        }
        return result
    }

    private fun validateRuntime(runtime: RedisRuntime, result: CompletableFuture<RedisRuntime>) {
        val pingStage = try {
            runtime.pingAsync()
        } catch (error: Throwable) {
            CompletionStages.failed<Long>(error)
        }
        val validationStage = try {
            CompletionStages.withTimeout(
                pingStage,
                runtime.config.lifecycle.statusTimeout.toJavaDuration(),
                "Redis 初始 PING 超时"
            )
        } catch (error: Throwable) {
            CompletionStages.failed<Long>(error)
        }
        validationStage.whenComplete { _, error ->
            if (error == null) {
                result.complete(runtime)
            } else {
                val closeStage = try {
                    runtime.closeAsync(runtime.config.lifecycle.shutdownGracePeriod.toJavaDuration())
                } catch (closeError: Throwable) {
                    CompletionStages.failed<Void>(closeError)
                }
                closeStage.whenComplete { _, closeError ->
                    completeCreationFailure(result, error, closeError)
                }
            }
        }
    }

    private fun buildResources(config: RedisConfig): DefaultClientResources {
        return DefaultClientResources.builder().apply {
            if (config.ioThreadPoolSize > 0) ioThreadPoolSize(config.ioThreadPoolSize)
            if (config.computationThreadPoolSize > 0) computationThreadPoolSize(config.computationThreadPoolSize)
        }.build()
    }

    private fun closeSinglePartial(
        config: RedisConfig,
        resources: DefaultClientResources,
        client: RedisClient,
        pool: BoundedAsyncPool<StatefulRedisConnection<String, String>>
    ): CompletableFuture<Void> {
        val timeout = config.lifecycle.shutdownGracePeriod.toJavaDuration().toMillis().coerceAtLeast(1)
        return CompletionStages.runAll(listOf(
            Supplier { pool.closeAsync() },
            Supplier { client.shutdownAsync(0, timeout, TimeUnit.MILLISECONDS) },
            Supplier { resources.shutdown(0, timeout, TimeUnit.MILLISECONDS).asCompletableFuture().thenApply { null } }
        ))
    }

    private fun closeClusterPartial(
        config: RedisConfig,
        resources: DefaultClientResources,
        client: RedisClusterClient,
        pool: BoundedAsyncPool<StatefulRedisClusterConnection<String, String>>
    ): CompletableFuture<Void> {
        val timeout = config.lifecycle.shutdownGracePeriod.toJavaDuration().toMillis().coerceAtLeast(1)
        return CompletionStages.runAll(listOf(
            Supplier { pool.closeAsync() },
            Supplier { client.shutdownAsync(0, timeout, TimeUnit.MILLISECONDS) },
            Supplier { resources.shutdown(0, timeout, TimeUnit.MILLISECONDS).asCompletableFuture().thenApply { null } }
        ))
    }

    private fun closeResources(
        config: RedisConfig,
        resources: DefaultClientResources
    ): CompletableFuture<Void> {
        val timeout = config.lifecycle.shutdownGracePeriod.toJavaDuration().toMillis().coerceAtLeast(1)
        return try {
            resources.shutdown(0, timeout, TimeUnit.MILLISECONDS)
                .asCompletableFuture()
                .thenApply { null }
        } catch (error: Throwable) {
            CompletionStages.failed(error)
        }
    }

    private fun closeClientResources(
        config: RedisConfig,
        resources: DefaultClientResources,
        client: AbstractRedisClient
    ): CompletableFuture<Void> {
        val timeout = config.lifecycle.shutdownGracePeriod.toJavaDuration().toMillis().coerceAtLeast(1)
        return CompletionStages.runAll(listOf(
            Supplier { client.shutdownAsync(0, timeout, TimeUnit.MILLISECONDS) },
            Supplier { resources.shutdown(0, timeout, TimeUnit.MILLISECONDS).asCompletableFuture().thenApply { null } }
        ))
    }

    private fun <T> failAfterClose(error: Throwable, closeStage: CompletionStage<Void>): CompletableFuture<T> {
        val result = CompletableFuture<T>()
        closeStage.whenComplete { _, closeError -> completeCreationFailure(result, error, closeError) }
        return result
    }

    private fun <T> completeCreationFailure(
        result: CompletableFuture<T>,
        creationError: Throwable,
        closeError: Throwable?
    ) {
        val failure = CompletionStages.unwrap(creationError)
        closeError?.let {
            val closeFailure = CompletionStages.unwrap(it)
            if (closeFailure !== failure) failure.addSuppressed(closeFailure)
        }
        result.completeExceptionally(failure)
    }
}
