package com.gitee.redischannel.api

import io.lettuce.core.api.sync.*

interface RedisChannelAPI {

    fun baseCommand(): BaseRedisCommands<String, String>

    fun aclCommand(): RedisAclCommands<String, String>

    fun functionCommand(): RedisFunctionCommands<String, String>

    fun geoCommand(): RedisGeoCommands<String, String>

    fun hashCommand(): RedisHashCommands<String, String>

    fun hllCommand(): RedisHLLCommands<String, String>

    fun keyCommand(): RedisKeyCommands<String, String>

    fun listCommand(): RedisListCommands<String, String>

    fun scriptingCommand(): RedisScriptingCommands<String, String>

    fun serverCommand(): RedisServerCommands<String, String>

    fun setCommand(): RedisSetCommands<String, String>

    fun sortedSetCommand(): RedisSortedSetCommands<String, String>

    fun streamCommand(): RedisStreamCommands<String, String>

    fun stringCommand(): RedisStringCommands<String, String>

    fun jsonCommand(): RedisJsonCommands<String, String>
}