# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

RedisChannel 是一个基于 TabooLib 框架的 Bukkit/Spigot 插件，为 Minecraft 服务器提供 Redis 集成能力。底层使用 Lettuce 客户端，支持单机/集群/哨兵/主从四种部署模式。

## 构建命令

```bash
# 构建生产版本
./gradlew build

# 构建 API 包（开发参考用，-PDeleteCode 移除逻辑代码以减少体积）
./gradlew taboolibBuildApi -PDeleteCode

# 发布到 Maven 仓库（需要凭证）
./gradlew publish -PpublishUsername=xxx -PpublishPassword=xxx
```

构建产物输出到 `build/` 目录。

## 架构设计

### 双模式架构

插件在启动时根据配置自动选择运行模式：

- **SINGLE 模式**: `RedisManager` 处理，支持单机和主从部署
- **CLUSTER 模式**: `ClusterRedisManager` 处理，支持 Redis Cluster

两个 Manager 都实现 `RedisChannelAPI` 接口，通过 `RedisChannelPlugin.api` 获取当前激活的实例。

### API 层次结构

```
RedisChannelAPI (统一入口)
├── RedisCommandAPI        (单机命令)
├── RedisClusterCommandAPI (集群命令)
├── RedisPubSubAPI         (单机 Pub/Sub)
└── RedisClusterPubSubAPI  (集群 Pub/Sub)
```

### 连接池策略

- 同步操作: `GenericObjectPool` (commons-pool2)
- 异步操作: `BoundedAsyncPool` (Lettuce 内置)

主从模式使用 `StatefulRedisMasterReplicaConnection` 独立连接池。

### 依赖重定位

通过 TabooLib 的 relocate 机制将依赖包重定位到 `com.gitee.redischannel.*` 命名空间，避免与其他插件冲突：

- `io.netty` → `com.gitee.redischannel.netty`
- `reactor` → `com.gitee.redischannel.reactor`
- `org.reactivestreams` → `com.gitee.redischannel.reactivestreams`
- `org.apache.commons.pool2` → `com.gitee.redischannel.commons.pool2`

## 关键文件

| 文件 | 职责 |
|------|------|
| `RedisChannelPlugin.kt` | 插件主类，提供静态 API 入口 |
| `RedisManager.kt` | 单机/主从模式的连接管理与命令执行 |
| `ClusterRedisManager.kt` | 集群模式的连接管理与命令执行 |
| `RedisConfig.kt` | 配置文件解析与 Redis URI 构建 |
| `src/main/resources/config.yml` | 主配置模板 |

## TabooLib 注解

- `@Parallel(runOn = LifeCycle.ENABLE)`: 异步并行初始化
- `@Awake(LifeCycle.DISABLE)`: 插件关闭时触发资源释放
- `@Config(migrate = true)`: 自动加载并迁移配置文件
- `@RuntimeDependency`: 运行时下载依赖，配合 relocate 使用
