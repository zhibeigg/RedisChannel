<div align="center">

# RedisChannel

<img src="https://img.shields.io/badge/Minecraft-1.12.2+-green?style=flat-square" alt="Minecraft">
<img src="https://img.shields.io/badge/Java_Runtime-8+-orange?style=flat-square&logo=openjdk" alt="Java Runtime">
<img src="https://img.shields.io/badge/Lettuce-6.8.0.RELEASE-red?style=flat-square" alt="Lettuce">
<img src="https://img.shields.io/badge/Version-2.14.12-blue?style=flat-square" alt="Version">

**面向 Bukkit/Spigot 的非阻塞 Redis 集成插件**

支持单机、Redis Cluster、哨兵和主从部署；API v2 仅提供异步与 Reactive 接口。

[快速开始](#快速开始) · [配置](#配置) · [API-v2](#api-v2) · [生命周期与事件](#生命周期与事件) · [迁移指南](docs/migration-api-v2.md)

</div>

## 版本与兼容性

| 项目 | 当前值 |
|---|---|
| RedisChannel | `2.14.12` |
| Minecraft/Bukkit | 兼容 `1.12.2` |
| 运行时 Java | Java 8 或更高版本 |
| 构建 JDK | JDK 17 |
| 产物字节码 | Java 8 |
| Lettuce | `6.8.0.RELEASE` |

项目使用 JDK 17 工具链构建，并通过 Java/Kotlin 编译选项生成 Java 8 字节码，因此可以在 Minecraft 1.12.2 常见的 Java 8 环境中运行。

## 功能

- 单机、哨兵、主从与 Redis Cluster。
- 基于 Lettuce `CompletionStage` 的异步命令 API。
- 基于 Reactive Streams `Publisher` 的响应式 API。
- 单机与集群 Pub/Sub。
- 统一异步连接池、生命周期管理、健康检查与自动重连。
- SSL/TLS、集群拓扑刷新和登录就绪保护。
- `ClientStartEvent`、`ClientStopEvent` 始终在 Bukkit 主线程触发。

> API v2 已删除全部同步 Redis API。不要通过 `join()`、`get()`、`await`、锁或休眠把异步调用重新变成阻塞调用。

## 快速开始

1. 将 RedisChannel JAR 放入服务端 `plugins` 目录。
2. 启动服务端生成默认配置。
3. 编辑 `plugins/RedisChannel/config.yml`。
4. 使用 `/redis reconnect` 异步重建连接，或重启服务端。

最小单机配置：

```yaml
language: zh_CN

bukkit:
  blockLoginUntilReady: true

redis:
  host: localhost
  port: 6379
  password: ''
  database: 0

  lifecycle:
    shutdownGracePeriod: PT10S
    healthCheckPeriod: PT5S
    statusTimeout: PT5S

  pool:
    maxTotal: 8
    maxIdle: 8
    minIdle: 0
```

## 配置

当前配置结构如下：

```yaml
# zh_CN / en_US
language: zh_CN

bukkit:
  # Redis 未进入 RUNNING 时是否阻止玩家登录
  blockLoginUntilReady: true

redis:
  host: localhost
  port: 6379
  password: ''
  ssl: false
  truststorePassword: ''
  timeout: PT15S
  database: 0

  # 0 表示由 Lettuce 自动决定
  ioThreadPoolSize: 0
  computationThreadPoolSize: 0
  autoReconnect: true
  pingBeforeActivateConnection: true

  lifecycle:
    # 停止或重连时等待已登记在途操作完成的最长时间
    shutdownGracePeriod: PT10S
    # 健康检查周期
    healthCheckPeriod: PT5S
    # 状态检查超时
    statusTimeout: PT5S

  sentinel:
    enable: false
    masterId: master
    nodes:
      - '127.0.0.1:26379'
      - '127.0.0.2:26379'

  slaves:
    enable: false
    readFrom: nearest

  cluster:
    enable: false
    enablePeriodicRefresh: false
    refreshPeriod: PT60S
    enableAdaptiveRefreshTrigger: []
    adaptiveRefreshTriggersTimeout: PT30S
    refreshTriggersReconnectAttempts: 5
    dynamicRefreshSources: true
    closeStaleConnections: true
    maxRedirects: 5
    validateClusterNodeMembership: true

  # API v2 的统一异步连接池
  pool:
    maxTotal: 8
    maxIdle: 8
    minIdle: 0
```

配置变更要点：

- 语言键为根级 `language`。
- Bukkit 登录保护位于 `bukkit.blockLoginUntilReady`。
- 生命周期参数统一位于 `redis.lifecycle`。
- 连接池统一为 `redis.pool`，不再区分同步池与异步池。
- 已删除 `maintNotifications` 和所有同步连接池选项。
- 旧 `redis.asyncPool` 不应继续写入新配置。

### 集群 seed 节点

启用 `redis.cluster.enable` 后，在 `plugins/RedisChannel/clusters/` 中放置节点文件。`cluster0.yml` 的 `host` 等字段位于文件根级，不要再包一层 `redis`：

```yaml
# plugins/RedisChannel/clusters/cluster0.yml
host: localhost
port: 6379
password: ''
ssl: false
timeout: PT15S
database: 0
```

Redis Cluster 仅支持 database `0`。每个 YAML 文件表示一个 seed 节点。

## API v2

完整签名、错误语义和更多示例见 [docs/api-v2.md](docs/api-v2.md)。从 v1 升级请阅读 [docs/migration-api-v2.md](docs/migration-api-v2.md)。

### Maven 依赖

仓库地址和版本与当前 `build.gradle.kts`、`gradle.properties` 保持一致：

```kotlin
repositories {
    maven("https://maven.mcwar.cn/releases")
}

dependencies {
    compileOnly("com.gitee.redischannel:RedisChannel:2.14.12:api")
}
```

### 稳定入口

```kotlin
import com.gitee.redischannel.RedisChannelPlugin

val lifecycleAPI = RedisChannelPlugin.api

val commandAPI = RedisChannelPlugin.commandAPI()
val clusterCommandAPI = RedisChannelPlugin.clusterCommandAPI()
val pubSubAPI = RedisChannelPlugin.pubSubAPI()
val clusterPubSubAPI = RedisChannelPlugin.clusterPubSubAPI()
```

`RedisChannelPlugin.api` 提供：

- `lifecycle()`：立即读取不可变生命周期快照，不执行 Redis I/O。
- `startAsync()`：异步启动 Redis runtime。
- `stopAsync()`：异步停止 Redis runtime。
- `reconnectAsync()`：异步重载配置并重建 Redis runtime。

### 普通命令

```kotlin
import com.gitee.redischannel.RedisChannelPlugin
import java.util.function.Function

val stage = RedisChannelPlugin.commandAPI().executeAsync(
    Function { commands -> commands.get("player:uuid:name") }
)

stage.whenComplete { value, error ->
    if (error != null) {
        logger.warning("读取 Redis 失败: ${error.message}")
    } else if (value == null) {
        // GET 不存在的 key：这是成功结果，不是连接错误
        logger.info("玩家名称尚未缓存")
    } else {
        logger.info("玩家名称: $value")
    }
}
```

`executeAsync` 的 action 必须返回代表完整操作的 `CompletionStage<T>`。该 Stage 完成后连接才会归还连接池：

```kotlin
RedisChannelPlugin.commandAPI().executeAsync(
    Function { commands ->
        commands.hset("player:uuid", "level", "10")
            .thenCompose { commands.expire("player:uuid", 3600) }
    }
)
```

不要在 action 中启动异步命令后返回一个无关的、已经完成的 Stage。

### Reactive 命令

```kotlin
import java.util.function.Function

val publisher = RedisChannelPlugin.commandAPI().executeReactive(
    Function { commands -> commands.get("player:uuid:name") }
)

// 使用你选择的 Reactive Streams 实现订阅 publisher。
```

### 集群与 Pub/Sub 方法名

| 接口 | 异步方法 | Reactive 方法 |
|---|---|---|
| `RedisCommandAPI` | `executeAsync` | `executeReactive` |
| `RedisClusterCommandAPI` | `executeClusterAsync` | `executeClusterReactive` |
| `RedisPubSubAPI` | `executePubSubAsync` | `executePubSubReactive` |
| `RedisClusterPubSubAPI` | `executeClusterPubSubAsync` | `executeClusterPubSubReactive` |

Pub/Sub 示例：

```kotlin
RedisChannelPlugin.pubSubAPI().executePubSubAsync(
    Function { commands -> commands.subscribe("server-events") }
).whenComplete { _, error ->
    if (error != null) logger.warning("订阅失败: ${error.message}")
}
```

### 错误与 null 语义

- 连接不可用、连接池获取失败、命令异常和 action 抛出的异常通过返回的 `CompletionStage` 异常完成。
- Reactive 调用通过 Publisher 的 error signal 传播异常。
- Redis `GET` 在 key 不存在时返回 `null` 是合法的成功结果。
- 不要再把 `null` 当作“Redis 操作失败”；应分别处理 `error` 与成功值 `null`。
- 不要阻塞等待 Stage。使用 `thenApply`、`thenCompose`、`whenComplete` 等方式组合操作。

## 生命周期与事件

生命周期状态包括：`STOPPED`、`STARTING`、`RUNNING`、`RECONNECTING`、`STOPPING`、`FAILED`。

```kotlin
val snapshot = RedisChannelPlugin.api.lifecycle()
if (snapshot.initialized) {
    // 当前状态为 RUNNING
}

RedisChannelPlugin.api.reconnectAsync().whenComplete { next, error ->
    if (error != null) {
        logger.warning("Redis 重连失败: ${error.message}")
    } else {
        logger.info("Redis 状态: ${next.state}, generation=${next.generation}")
    }
}
```

`ClientStartEvent` 与 `ClientStopEvent` **始终在 Bukkit 主线程触发**：

```kotlin
import com.gitee.redischannel.api.events.ClientStartEvent
import taboolib.common.platform.event.SubscribeEvent

@SubscribeEvent
fun onRedisStart(event: ClientStartEvent) {
    // 当前位于 Bukkit 主线程，可以安全读取 Bukkit 主线程数据。
    // Redis I/O 仍必须使用 API v2 异步接口。
    RedisChannelPlugin.commandAPI().executeAsync(
        Function { commands -> commands.get("motd") }
    ).whenComplete { motd, error ->
        // 此回调不保证位于 Bukkit 主线程。
        // 如需访问玩家、世界、实体等 Bukkit API，必须切回主线程。
    }
}
```

线程规则：

1. Redis I/O 始终使用异步或 Reactive API。
2. Stage/Publisher 的回调线程不保证是 Bukkit 主线程。
3. 回调中访问玩家、世界、实体、背包等 Bukkit API 时，使用你的插件调度器切回主线程。
4. 即使在 `ClientStartEvent`/`ClientStopEvent` 中，也不要调用 `join()` 或 `get()` 阻塞主线程。

## 游戏内命令

| 命令 | 权限 | 描述 |
|---|---|---|
| `/redis` | `RedisChannel.Command.Main` | 查看帮助或状态 |
| `/redis reconnect` | `RedisChannel.Command.Main` | 异步重载配置并重建连接 |

## 构建

```bash
# JDK 17 工具链，生成 Java 8 字节码
./gradlew build -Pbuild=build/libs

# 构建 API 包
./gradlew taboolibBuildApi -PDeleteCode -Pbuild=build/libs
```

构建产物输出目录由 `-Pbuild` 指定。

## 技术栈

| 组件 | 版本/说明 |
|---|---|
| Kotlin | `2.1.20` |
| TabooLib Gradle 插件 | `2.0.37` |
| TabooLib | `6.3.0-932e79c` |
| Lettuce | `6.8.0.RELEASE` |
| Reactor | `3.6.6`（运行时依赖） |
| Java Toolchain | JDK 17，目标 Java 8 |

## 许可证

本项目采用 [CC0 1.0 Universal](LICENSE) 许可证。
