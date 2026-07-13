package com.gitee.redischannel.util

import io.netty.util.concurrent.Future
import java.util.concurrent.CompletableFuture

internal fun <T> Future<T>.asCompletableFuture(): CompletableFuture<T> {
    val source = this
    val result = CompletableFuture<T>()
    result.whenComplete { _, _ ->
        if (result.isCancelled) source.cancel(false)
    }
    try {
        addListener { future ->
            if (future.isSuccess) {
                @Suppress("UNCHECKED_CAST")
                result.complete((future as Future<T>).getNow())
            } else {
                result.completeExceptionally(future.cause() ?: IllegalStateException("Netty operation failed"))
            }
        }
    } catch (error: Throwable) {
        result.completeExceptionally(error)
    }
    return result
}
