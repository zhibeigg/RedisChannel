package com.gitee.redischannel.api

/**
 * 当前 Redis 部署模式。
 *
 * @since 2.14.12
 */
enum class RedisDeploymentMode {
    SINGLE,
    SENTINEL,
    MASTER_REPLICA,
    CLUSTER
}
