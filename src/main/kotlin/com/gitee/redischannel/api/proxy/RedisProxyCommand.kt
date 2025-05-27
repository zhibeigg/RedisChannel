package com.gitee.redischannel.api.proxy

import io.lettuce.core.api.sync.*
import io.lettuce.core.cluster.api.sync.RedisClusterCommands

class RedisProxyCommand<K, V>(command: RedisCommands<K, V>? = null, commandCluster: RedisClusterCommands<K, V>? = null): BaseRedisCommands<K, V> by command ?: commandCluster!!,
    RedisAclCommands<K, V> by command ?: commandCluster!!,
    RedisFunctionCommands<K, V> by command ?: commandCluster!!,
    RedisGeoCommands<K, V> by command ?: commandCluster!!,
    RedisHashCommands<K, V> by command ?: commandCluster!!,
    RedisHLLCommands<K, V> by command ?: commandCluster!!,
    RedisKeyCommands<K, V> by command ?: commandCluster!!,
    RedisListCommands<K, V> by command ?: commandCluster!!,
    RedisScriptingCommands<K, V> by command ?: commandCluster!!,
    RedisServerCommands<K, V> by command ?: commandCluster!!,
    RedisSetCommands<K, V> by command ?: commandCluster!!,
    RedisSortedSetCommands<K, V> by command ?: commandCluster!!,
    RedisStreamCommands<K, V> by command ?: commandCluster!!,
    RedisStringCommands<K, V> by command ?: commandCluster!!,
    RedisJsonCommands<K, V> by command ?: commandCluster!!
{
    constructor(command: RedisCommands<K, V>): this(command, null)
    constructor(command: RedisClusterCommands<K, V>): this(null, command)
}