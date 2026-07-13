# RedisChannel API v2

RedisChannel API v2 自 `2.14.12` 起提供稳定的非阻塞外部接口。全部同步 Redis API 已删除，Redis I/O 只能通过 `CompletionStage` 异步接口执行。公开 API 不包含响应式类型，因为这类类型跨插件重定位时 ABI 不安全。

## 兼容性

- RedisChannel：`2.14.12`
- Minecraft/Bukkit：兼容 `1.12.2`
- 运行时：Java 8 或更高版本
- 构建：JDK 17，输出 Java 8 字节码
- Kotlin 调用方：建议使用 Kotlin 2.1.20 或更高版本；Java 调用方不受 Kotlin metadata 版本限制
- Lettuce：`6.8.0.RELEASE`

## 引入 API

```kotlin
repositories {
    maven("https://maven.mcwar.cn/releases")
}

dependencies {
    compileOnly("com.gitee.redischannel:RedisChannel:2.14.12:api")
}
```

插件运行时还需要将 RedisChannel 安装到服务端，并在依赖插件的元数据中声明 RedisChannel 依赖或软依赖。

## 入口概览

```kotlin
import com.gitee.redischannel.RedisChannelPlugin

val lifecycleAPI = RedisChannelPlugin.api
val commandAPI = RedisChannelPlugin.commandAPI()
val clusterCommandAPI = RedisChannelPlugin.clusterCommandAPI()
val pubSubAPI = RedisChannelPlugin.pubSubAPI()
val clusterPubSubAPI = RedisChannelPlugin.clusterPubSubAPI()
```

| 获取器 | 返回接口 | 使用场景 |
|---|---|---|
| `RedisChannelPlugin.api` | `RedisChannelAPI` | 生命周期状态、启动、停止、重连 |
| `commandAPI()` | `RedisCommandAPI` | 单机、哨兵、主从命令 |
| `clusterCommandAPI()` | `RedisClusterCommandAPI` | Redis Cluster 命令 |
| `pubSubAPI()` | `RedisPubSubAPI` | 当前部署模式的通用 Pub/Sub |
| `clusterPubSubAPI()` | `RedisClusterPubSubAPI` | Redis Cluster 专用 Pub/Sub |

调用方应根据实际部署模式选择普通命令接口或集群命令接口。可通过 `RedisChannelPlugin.api.lifecycle().mode` 检查当前模式。

## RedisChannelAPI

```kotlin
interface RedisChannelAPI {
    fun lifecycle(): RedisLifecycleSnapshot
    fun startAsync(): CompletionStage<RedisLifecycleSnapshot>
    fun stopAsync(): CompletionStage<RedisLifecycleSnapshot>
    fun reconnectAsync(): CompletionStage<RedisLifecycleSnapshot>
}
```

### lifecycle

`lifecycle()` 立即返回不可变快照，不执行 Redis I/O，也不等待状态变化。

```kotlin
val snapshot = RedisChannelPlugin.api.lifecycle()
logger.info(
    "state=${snapshot.state}, mode=${snapshot.mode}, " +
        "generation=${snapshot.generation}, initialized=${snapshot.initialized}"
)
```

`RedisLifecycleSnapshot` 字段：

| 字段 | 说明 |
|---|---|
| `state` | 当前生命周期状态 |
| `mode` | 当前部署模式；尚未建立 runtime 时可为 `null` |
| `generation` | runtime 代次，重连时变化 |
| `startedAt` | 当前 runtime 启动时间；未运行时可为 `null` |
| `failureMessage` | 最近一次生命周期失败信息；无失败时为 `null` |
| `initialized` | `state == RUNNING` 的便捷属性 |

生命周期状态：

- `STOPPED`
- `STARTING`
- `RUNNING`
- `RECONNECTING`
- `STOPPING`
- `FAILED`

部署模式 `RedisDeploymentMode`：

- `SINGLE`
- `SENTINEL`
- `MASTER_REPLICA`
- `CLUSTER`

### startAsync / stopAsync / reconnectAsync

三个方法均立即返回 `CompletionStage<RedisLifecycleSnapshot>`，不得阻塞等待。`startAsync()` 与 `reconnectAsync()` 返回的 Stage 会在 `ClientStartEvent` 的 Bukkit 主线程触发尝试结束后才完成；启动事件监听器抛出的异常会被记录，但不会使已成功启动的 Stage 异常完成。

```kotlin
RedisChannelPlugin.api.reconnectAsync().whenComplete { snapshot, error ->
    if (error != null) {
        logger.warning("Redis 重连失败: ${error.message}")
    } else {
        logger.info("Redis 已进入 ${snapshot.state}")
    }
}
```

`reconnectAsync()` 会异步重载配置并替换 runtime。关闭旧 runtime 时，会依据 `redis.lifecycle.shutdownGracePeriod` 等待已登记的在途异步操作完成。

## RedisCommandAPI

适用于单机、哨兵和主从模式。

```kotlin
interface RedisCommandAPI {
    fun <T> executeAsync(
        action: Function<RedisAsyncCommands<String, String>, out CompletionStage<T>>
    ): CompletionStage<T>
}
```

### executeAsync

读取值：

```kotlin
import java.util.function.Function

val stage = RedisChannelPlugin.commandAPI().executeAsync(
    Function { commands -> commands.get("profile:uuid:name") }
)

stage.whenComplete { value, error ->
    when {
        error != null -> logger.warning("Redis GET 失败: ${error.message}")
        value == null -> logger.info("key 不存在")
        else -> logger.info("value=$value")
    }
}
```

写入并设置过期时间：

```kotlin
RedisChannelPlugin.commandAPI().executeAsync(
    Function { commands ->
        commands.set("session:uuid", "online")
            .thenCompose { commands.expire("session:uuid", 300) }
    }
).whenComplete { applied, error ->
    if (error != null) {
        logger.warning("保存会话失败: ${error.message}")
    } else {
        logger.info("过期时间设置结果: $applied")
    }
}
```

action 返回的 Stage 定义资源占用周期。连接只会在这个 Stage 完成后归还连接池。因此以下写法是错误的：

```kotlin
// 错误：Redis 命令还没结束，action 却返回了无关的已完成 Stage。
RedisChannelPlugin.commandAPI().executeAsync(
    Function { commands ->
        commands.set("key", "value")
        java.util.concurrent.CompletableFuture.completedFuture(Unit)
    }
)
```

应直接返回 Redis 命令 Stage，或用 `thenCompose`/`thenApply` 组合完整流程。

## RedisClusterCommandAPI

适用于 Redis Cluster。

```kotlin
interface RedisClusterCommandAPI {
    fun <T> executeClusterAsync(
        action: Function<RedisClusterAsyncCommands<String, String>, out CompletionStage<T>>
    ): CompletionStage<T>
}
```

```kotlin
RedisChannelPlugin.clusterCommandAPI().executeClusterAsync(
    Function { commands -> commands.get("global:motd") }
).whenComplete { value, error ->
    if (error != null) {
        logger.warning("集群读取失败: ${error.message}")
    } else {
        logger.info("motd=${value ?: "<未设置>"}")
    }
}
```

## Pub/Sub API

### 通用 Pub/Sub

```kotlin
interface RedisPubSubAPI {
    fun <T> executePubSubAsync(
        action: Function<RedisPubSubAsyncCommands<String, String>, out CompletionStage<T>>
    ): CompletionStage<T>
}
```

```kotlin
RedisChannelPlugin.pubSubAPI().executePubSubAsync(
    Function { commands -> commands.subscribe("server-events") }
).whenComplete { _, error ->
    if (error != null) logger.warning("订阅失败: ${error.message}")
}
```

`pubSubAPI()` 会按照当前部署模式返回通用 Pub/Sub 实现。

### 集群 Pub/Sub

```kotlin
interface RedisClusterPubSubAPI {
    fun <T> executeClusterPubSubAsync(
        action: Function<RedisClusterPubSubAsyncCommands<String, String>, out CompletionStage<T>>
    ): CompletionStage<T>
}
```

```kotlin
RedisChannelPlugin.clusterPubSubAPI().executeClusterPubSubAsync(
    Function { commands -> commands.subscribe("cluster-events") }
)
```

## 异常与 null

API v2 明确区分“命令成功但值为空”和“操作失败”。

### CompletionStage

下列情况通过 exceptional completion 传播：

- Redis runtime 未处于可接受操作的状态；
- 获取连接失败；
- Lettuce 命令失败；
- action 同步抛出异常；
- action 返回的 Stage 异常完成。

```kotlin
RedisChannelPlugin.commandAPI().executeAsync(
    Function { commands -> commands.get("optional-key") }
).whenComplete { value, error ->
    if (error != null) {
        handleRedisFailure(error)
        return@whenComplete
    }

    // error == null 时，value == null 表示 key 不存在。
    handleSuccessfulValue(value)
}
```

不要使用 `value == null` 判断连接失败，也不要通过返回 `null` 吞掉异常。

## Bukkit 线程边界

### 事件线程

`ClientStartEvent` 和 `ClientStopEvent` 始终在 Bukkit 主线程调用。

```kotlin
@SubscribeEvent
fun onRedisStart(event: ClientStartEvent) {
    // Bukkit 主线程：可以读取 Bukkit 状态。
    val onlineIds = Bukkit.getOnlinePlayers().map { it.uniqueId.toString() }

    // Redis I/O 仍然异步。
    RedisChannelPlugin.commandAPI().executeAsync(
        Function { commands -> commands.sadd("online", *onlineIds.toTypedArray()) }
    )
}
```

### CompletionStage 回调

Redis 命令回调不保证位于 Bukkit 主线程。若回调需要访问 Bukkit API，应使用依赖插件自己的调度器切回主线程：

```kotlin
RedisChannelPlugin.commandAPI().executeAsync(
    Function { commands -> commands.get("player:$uuid:display-name") }
).whenComplete { displayName, error ->
    Bukkit.getScheduler().runTask(this, Runnable {
        val player = Bukkit.getPlayer(uuid) ?: return@Runnable
        if (error != null) {
            player.sendMessage("Redis 读取失败")
        } else {
            player.setDisplayName(displayName ?: player.name)
        }
    })
}
```

示例中的 `this` 是依赖 RedisChannel 的 `JavaPlugin` 实例。

禁止：

- 在 Bukkit 主线程调用 `stage.get()` 或 `stage.toCompletableFuture().join()`；
- 在异步回调直接修改玩家、世界、实体或背包；
- 为等待 Redis 结果而休眠或自旋；
- 在事件监听器中阻塞生命周期停止或重连。

## 生命周期事件

```kotlin
@SubscribeEvent
fun onRedisStart(event: ClientStartEvent) {
    logger.info("Redis 已启动，cluster=${event.cluster}")
}

@SubscribeEvent
fun onRedisStop(event: ClientStopEvent) {
    logger.info("Redis 即将停止，cluster=${event.cluster}")
    // 可以发起 API v2 异步操作，但不要阻塞等待。
}
```

- `ClientStartEvent`：runtime 完整建立并进入 `RUNNING` 后触发；首次启动及成功重连均可能触发。`startAsync()`/`reconnectAsync()` 会等到主线程触发尝试结束后才完成；监听器异常会记录日志，但启动仍按成功完成。
- `ClientStopEvent`：runtime 停止接受新操作前触发；禁用、热重载及重连均可能触发。即使事件触发失败，runtime 关闭仍会继续执行。
- 停止期间会在 `shutdownGracePeriod` 内等待已登记在途操作。事件失败或关闭失败都会使停止/重连 Stage exceptional completion；若两者都失败，关闭异常会作为 suppressed exception 合并。JVM 关闭时资源释放属于尽力完成。

## 配置关联

API v2 依赖以下配置结构：

```yaml
language: zh_CN

bukkit:
  blockLoginUntilReady: true

redis:
  lifecycle:
    shutdownGracePeriod: PT10S
    # 最小 1 秒；健康检查调度粒度为 1 秒
    healthCheckPeriod: PT5S
    statusTimeout: PT5S

  pool:
    maxTotal: 8
    maxIdle: 8
    minIdle: 0
```

API v2 只有统一异步连接池。不要加入同步池参数或 `maintNotifications`。`redis.asyncPool` 不再兼容，检测到该节点会配置失败，必须迁移为 `redis.pool`。Cluster 与 Sentinel 互斥，不能同时启用。所有时间配置必须是有限、可安全换算的正数；`redis.lifecycle.healthCheckPeriod` 最小为 1 秒，健康检查任务以 1 秒为调度粒度。
