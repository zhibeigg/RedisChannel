package com.gitee.redischannel.api

import io.lettuce.core.RedisFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.Future

class RedisFuture<V>(val redisFuture: RedisFuture<V>) : CompletionStage<V> by redisFuture, Future<V> by redisFuture {

    override fun exceptionNow(): Throwable {
        return redisFuture.exceptionNow()
    }

    override fun state(): Future.State {
        return redisFuture.state()
    }
}