# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 语言设置

- 始终以简体中文回复。

## 代码与线程规则

- 禁止把 Bukkit 主线程敏感逻辑放到异步线程中执行。
- 数据库与 Redis I/O 必须异步处理，不得使用 `get()`、`join()`、休眠或锁等方式阻塞等待。
- `CompletionStage` 回调不保证位于 Bukkit 主线程；访问玩家、世界、实体、背包等 Bukkit API 前必须切回主线程。
- `ClientStartEvent` 与 `ClientStopEvent` 始终在 Bukkit 主线程触发，但事件中的 Redis I/O 仍必须使用非阻塞 API。
- 禁止滥用 `!!`，优先使用 Kotlin 空安全、显式判空和结果对象。
- 新增功能时必须同步更新默认配置、示例配置、语言文件和外部 API 文档。

## 项目概述

RedisChannel `2.15.13` 是基于 TabooLib 的 Bukkit/Spigot Redis 插件，支持单机、Redis Cluster、哨兵和主从模式。

兼容性与构建基线：

- Minecraft/Bukkit：兼容 `1.12.2`。
- 运行时：Java 8 或更高版本。
- 构建：JDK 17 工具链，输出 Java 8 字节码。
- Lettuce：`6.8.0.RELEASE`。

## 构建命令

```bash
./gradlew build -Pbuild=build/libs
./gradlew verifyApiConsumer -Pbuild=build/libs
./gradlew test
./gradlew publish -PpublishUsername=xxx -PpublishPassword=xxx -Pbuild=build/libs
```

构建产物目录由 `-Pbuild` 属性指定。

自动发版由 `.github/workflows/build.yml` 处理。仅推送与 `gradle.properties` 版本一致的 `v*` 标签才会发布 Maven 产物并创建 GitHub Release；Release 会自动生成说明并上传生产 JAR 与 API JAR。`workflow_dispatch` 只执行验证，不直接发布正式版本。

## API v2

API v2 自 `2.14.12` 起删除所有同步 Redis API，只公开基于 `CompletionStage` 的异步接口。

### 稳定入口

- `RedisChannelPlugin.api`
  - `lifecycle()`
  - `startAsync()`
  - `stopAsync()`
  - `reconnectAsync()`
- `RedisChannelPlugin.commandAPI()`
- `RedisChannelPlugin.clusterCommandAPI()`
- `RedisChannelPlugin.pubSubAPI()`
- `RedisChannelPlugin.clusterPubSubAPI()`

### 命令接口

```text
RedisCommandAPI
└── executeAsync(Function<RedisAsyncCommands<String, String>, CompletionStage<T>>)

RedisClusterCommandAPI
└── executeClusterAsync(...)

RedisPubSubAPI
└── executePubSubAsync(...)

RedisClusterPubSubAPI
└── executeClusterPubSubAsync(...)
```

实现约束：

- action 返回的 Stage 必须代表完整 Redis 操作，资源在其终止后释放。
- 异常通过 Stage 的 exceptional completion 传播，不用 `null` 表示错误。
- 不公开 Reactive/Publisher 接口，避免重定位类型泄漏造成跨插件 ABI 不兼容。
- Redis `GET` 对不存在 key 返回 `null` 是合法成功结果，不能与异常混淆。
- 生命周期操作和 Redis I/O 均保持非阻塞。

详细公开文档：`docs/api-v2.md`；v1 迁移文档：`docs/migration-api-v2.md`。

## 生命周期架构

`RedisChannelPlugin.api` 委托给统一 facade，生命周期状态为：

- `STOPPED`
- `STARTING`
- `RUNNING`
- `RECONNECTING`
- `STOPPING`
- `FAILED`

运行时根据配置选择：

- `SINGLE`：单机、哨兵、主从。
- `CLUSTER`：Redis Cluster。

API v2 只使用 Lettuce `BoundedAsyncPool` 异步连接池，不再维护同步连接池。

## 配置结构

关键配置：

- 根级 `language`；普通 YAML 语言文件位于 `messages/`，旧 `lang/*.yml` 仅做非覆盖兼容迁移。
- `bukkit.blockLoginUntilReady`。
- `redis.lifecycle.shutdownGracePeriod`。
- `redis.lifecycle.healthCheckPeriod`。
- `redis.lifecycle.statusTimeout`。
- 统一异步连接池 `redis.pool`。
- 集群 seed 文件 `clusters/cluster0.yml` 使用根级 `host`、`port` 等字段。

已删除或不应重新引入：

- `maintNotifications`。
- 同步连接池配置。
- 新模板中的 `redis.asyncPool`。
- `cluster0.yml` 外层 `redis` 节点。

## Maven 坐标

```kotlin
repositories {
    maven("https://maven.mcwar.cn/releases")
}

dependencies {
    compileOnly("com.gitee.redischannel:RedisChannel:2.15.13:api")
}
```

## 关键文件

| 文件 | 职责 |
|---|---|
| `RedisChannelPlugin.kt` | 插件入口与公开 API 获取器 |
| `api/RedisChannelAPI.kt` | 生命周期 facade 接口 |
| `api/RedisCommandAPI.kt` | 单机/哨兵/主从命令 API |
| `api/RedisPubSubAPI.kt` | 通用 Pub/Sub API |
| `api/cluster/RedisClusterCommandAPI.kt` | 集群命令 API |
| `api/cluster/RedisClusterPubSubAPI.kt` | 集群 Pub/Sub API |
| `core/lifecycle/RedisLifecycleCoordinator.kt` | 非阻塞生命周期协调 |
| `core/runtime/` | 单机与集群 runtime |
| `core/RedisConfig.kt` | v2 配置解析 |
| `src/main/resources/config.yml` | 默认配置模板 |
| `src/main/resources/messages/` | 普通 YAML 语言资源 |
| `src/main/resources/clusters/cluster0.yml` | 集群 seed 示例 |
| `docs/api-v2.md` | API v2 外部文档 |
| `docs/migration-api-v2.md` | v1 → v2 迁移指南 |
