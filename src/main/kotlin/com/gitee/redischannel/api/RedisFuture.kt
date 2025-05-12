package com.gitee.redischannel.api

import io.lettuce.core.RedisFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.Future

class RedisFuture<V>(redisFuture: RedisFuture<V>) : CompletionStage<V?> by redisFuture, Future<V?> by redisFuture