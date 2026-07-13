package com.gitee.redischannel.api.exception

import com.gitee.redischannel.api.RedisDeploymentMode
import com.gitee.redischannel.api.RedisLifecycleState

open class RedisChannelException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class RedisUnavailableException(state: RedisLifecycleState) :
    RedisChannelException("RedisChannel 当前不可用，生命周期状态: $state")

class WrongDeploymentModeException(expected: RedisDeploymentMode, actual: RedisDeploymentMode?) :
    RedisChannelException("Redis 部署模式不匹配，期望 $expected，实际 ${actual ?: "未连接"}")

class RedisConfigurationException(message: String, cause: Throwable? = null) :
    RedisChannelException(message, cause)

class RedisOperationException(message: String, cause: Throwable? = null) :
    RedisChannelException(message, cause)
