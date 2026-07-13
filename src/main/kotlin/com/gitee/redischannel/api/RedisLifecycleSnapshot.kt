package com.gitee.redischannel.api

import java.time.Instant

/**
 * RedisChannel 生命周期的不可变快照。
 *
 * @since 2.14.12
 */
data class RedisLifecycleSnapshot(
    val state: RedisLifecycleState,
    val mode: RedisDeploymentMode?,
    val generation: Long,
    val startedAt: Instant?,
    val failureMessage: String?
) {
    val initialized: Boolean
        get() = state == RedisLifecycleState.RUNNING
}
