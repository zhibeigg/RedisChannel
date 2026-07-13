package com.gitee.redischannel.core

import com.gitee.redischannel.api.RedisCommandAPI
import com.gitee.redischannel.api.RedisPubSubAPI
import com.gitee.redischannel.core.lifecycle.RedisLifecycleCoordinator
import io.lettuce.core.api.async.RedisAsyncCommands
import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands
import taboolib.common.env.RuntimeDependencies
import taboolib.common.env.RuntimeDependency
import java.util.concurrent.CompletionStage
import java.util.function.Function

@RuntimeDependencies(
    RuntimeDependency(
        "!io.lettuce:lettuce-core:6.8.0.RELEASE",
        test = "!io.lettuce.core.RedisURI",
        relocate = ["!io.netty", "!com.gitee.redischannel.netty",
            "!reactor", "!com.gitee.redischannel.reactor",
            "!org.reactivestreams", "!com.gitee.redischannel.reactivestreams",
            "!org.slf4j", "!com.gitee.redischannel.slf4j",
            "!redis.clients.authentication", "!com.gitee.redischannel.redis.clients.authentication"],
        transitive = false
    ),
    RuntimeDependency("!io.netty:netty-common:4.1.118.Final", test = "!com.gitee.redischannel.netty.util.AbstractConstant", relocate = ["!io.netty", "!com.gitee.redischannel.netty"], transitive = false),
    RuntimeDependency("!io.netty:netty-handler:4.1.118.Final", test = "!com.gitee.redischannel.netty.handler.ssl.SslHandler", relocate = ["!io.netty", "!com.gitee.redischannel.netty"], transitive = false),
    RuntimeDependency("!io.netty:netty-resolver-dns:4.1.118.Final", test = "!com.gitee.redischannel.netty.resolver.dns.DnsNameResolver", relocate = ["!io.netty", "!com.gitee.redischannel.netty"], transitive = false),
    RuntimeDependency("!io.netty:netty-transport:4.1.118.Final", test = "!com.gitee.redischannel.netty.channel.Channel", relocate = ["!io.netty", "!com.gitee.redischannel.netty"], transitive = false),
    RuntimeDependency("!io.netty:netty-buffer:4.1.118.Final", test = "!com.gitee.redischannel.netty.buffer.ByteBuf", relocate = ["!io.netty", "!com.gitee.redischannel.netty"], transitive = false),
    RuntimeDependency("!io.netty:netty-codec:4.1.118.Final", test = "!com.gitee.redischannel.netty.handler.codec.ByteToMessageDecoder", relocate = ["!io.netty", "!com.gitee.redischannel.netty"], transitive = false),
    RuntimeDependency("!io.netty:netty-resolver:4.1.118.Final", test = "!com.gitee.redischannel.netty.resolver.AddressResolver", relocate = ["!io.netty", "!com.gitee.redischannel.netty"], transitive = false),
    RuntimeDependency("!io.netty:netty-transport-native-unix-common:4.1.118.Final", test = "!com.gitee.redischannel.netty.channel.unix.UnixChannel", relocate = ["!io.netty", "!com.gitee.redischannel.netty"], transitive = false),
    RuntimeDependency("!io.netty:netty-codec-dns:4.1.118.Final", test = "!com.gitee.redischannel.netty.handler.codec.dns.DnsRecord", relocate = ["!io.netty", "!com.gitee.redischannel.netty"], transitive = false),
    RuntimeDependency("!org.reactivestreams:reactive-streams:1.0.4", test = "!com.gitee.redischannel.reactivestreams.Publisher", relocate = ["!org.reactivestreams", "!com.gitee.redischannel.reactivestreams"], transitive = false),
    RuntimeDependency("!org.slf4j:slf4j-api:1.7.36", test = "!com.gitee.redischannel.slf4j.Logger", relocate = ["!org.slf4j", "!com.gitee.redischannel.slf4j"], transitive = false),
    RuntimeDependency("!io.projectreactor:reactor-core:3.6.6", test = "!com.gitee.redischannel.reactor.core.publisher.Flux", relocate = ["!reactor", "!com.gitee.redischannel.reactor", "!org.reactivestreams", "!com.gitee.redischannel.reactivestreams"], transitive = false),
    RuntimeDependency("!redis.clients.authentication:redis-authx-core:0.1.1-beta2", test = "!com.gitee.redischannel.redis.clients.authentication.core.TokenManager", relocate = ["!redis.clients.authentication", "!com.gitee.redischannel.redis.clients.authentication"], transitive = false)
)
internal object RedisManager : RedisCommandAPI, RedisPubSubAPI {

    override fun <T> executeAsync(
        action: Function<RedisAsyncCommands<String, String>, out CompletionStage<T>>
    ): CompletionStage<T> = RedisLifecycleCoordinator.runtime()?.executeAsync(action)
        ?: com.gitee.redischannel.util.CompletionStages.failed(
            com.gitee.redischannel.api.exception.RedisUnavailableException(RedisLifecycleCoordinator.lifecycle().state)
        )

    override fun <T> executePubSubAsync(
        action: Function<RedisPubSubAsyncCommands<String, String>, out CompletionStage<T>>
    ): CompletionStage<T> = RedisLifecycleCoordinator.runtime()?.executePubSubAsync(action)
        ?: com.gitee.redischannel.util.CompletionStages.failed(
            com.gitee.redischannel.api.exception.RedisUnavailableException(RedisLifecycleCoordinator.lifecycle().state)
        )

}
