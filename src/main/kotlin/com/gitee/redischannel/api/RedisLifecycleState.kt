package com.gitee.redischannel.api

/**
 * RedisChannel 生命周期状态。
 *
 * @since 2.14.12
 */
enum class RedisLifecycleState {
    STOPPED,
    STARTING,
    RUNNING,
    RECONNECTING,
    STOPPING,
    FAILED
}
