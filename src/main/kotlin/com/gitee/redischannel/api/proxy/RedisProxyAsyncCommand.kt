package com.gitee.redischannel.api.proxy

import io.lettuce.core.api.async.*
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands

class RedisProxyAsyncCommand<K, V>(command: RedisAsyncCommands<K, V>? = null, commandCluster: RedisClusterAsyncCommands<K, V>? = null): BaseRedisAsyncCommands<K, V> by command ?: commandCluster!!,
    RedisAclAsyncCommands<K, V> by command ?: commandCluster!!,
    RedisFunctionAsyncCommands<K, V> by command ?: commandCluster!!,
    RedisGeoAsyncCommands<K, V> by command ?: commandCluster!!,
    RedisHashAsyncCommands<K, V> by command ?: commandCluster!!,
    RedisHLLAsyncCommands<K, V> by command ?: commandCluster!!,
    RedisKeyAsyncCommands<K, V> by command ?: commandCluster!!,
    RedisListAsyncCommands<K, V> by command ?: commandCluster!!,
    RedisScriptingAsyncCommands<K, V> by command ?: commandCluster!!,
    RedisServerAsyncCommands<K, V> by command ?: commandCluster!!,
    RedisSetAsyncCommands<K, V> by command ?: commandCluster!!,
    RedisSortedSetAsyncCommands<K, V> by command ?: commandCluster!!,
    RedisStreamAsyncCommands<K, V> by command ?: commandCluster!!,
    RedisStringAsyncCommands<K, V> by command ?: commandCluster!!,
    RedisJsonAsyncCommands<K, V> by command ?: commandCluster!!
{
    constructor(command: RedisAsyncCommands<K, V>): this(command, null)
    constructor(command: RedisClusterAsyncCommands<K, V>): this(null, command)
}