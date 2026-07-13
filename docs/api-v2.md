# RedisChannel API v2

RedisChannel API v2 自 `2.14.12` 起提供稳定的非阻塞外部接口。全部同步 Redis API 已删除，Redis I/O 只能通过 `CompletionStage` 或 Reactive Streams `Publisher` 执行。

## 兼容性

- RedisChannel：`2.14.12`
- Minecraft/Bukkit：兼容 `1.12.2`
- 运行时：Java 8 或更高版本
- 构建：JDK 17，输出 Java 8 字节码
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

### startAsync / stopAsync / reconnectAsync

三个方法均立即返回 `CompletionStage<RedisLifecycleSnapshot>`，不得阻塞等待。

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

    fun <T> executeReactive(
        action: Function<RedisReactiveCommands<String, String>, out Publisher<T>>
    ): Publisher<T>
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

### executeReactive

```kotlin
val publisher = RedisChannelPlugin.commandAPI().executeReactive(
    Function { commands -> commands.hgetall("profile:uuid") }
)
```

返回值是标准 Reactive Streams `Publisher<T>`。订阅、调度与背压处理由调用方选择的 Reactive Streams 实现负责。异常通过 error signal 传播。

## RedisClusterCommandAPI

适用于 Redis Cluster。

```kotlin
interface RedisClusterCommandAPI {
    fun <T> executeClusterAsync(
        action: Function<RedisClusterAsyncCommands<String, String>, out CompletionStage<T>>
    ): CompletionStage<T>

    fun <T> executeClusterReactive(
        action: Function<RedisClusterReactiveCommands<String, String>, out Publisher<T>>
    ): Publisher<T>
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

Reactive 版本使用 `executeClusterReactive`，其 action 接收 `RedisClusterReactiveCommands<String, String>`。

## Pub/Sub API

### 通用 Pub/Sub

```kotlin
interface RedisPubSubAPI {
    fun <T> executePubSubAsync(
        action: Function<RedisPubSubAsyncCommands<String, String>, out CompletionStage<T>>
    ): CompletionStage<T>

    fun <T> executePubSubReactive(
        action: Function<RedisPubSubReactiveCommands<String, String>, out Publisher<T>>
    ): Publisher<T>
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

    fun <T> executeClusterPubSubReactive(
        action: Function<RedisClusterPubSubReactiveCommands<String, String>, out Publisher<T>>
    ): Publisher<T>
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

### Reactive

Reactive API 通过 Publisher 的 error signal 传播错误。调用方必须注册错误处理逻辑，避免未处理错误被丢弃或只写入全局日志。

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

- `ClientStartEvent`：runtime 完整建立并进入 `RUNNING` 后触发；首次启动及成功重连均可能触发。
- `ClientStopEvent`：runtime 停止接受新操作前触发；禁用、热重载及重连均可能触发。
- 停止期间会在 `shutdownGracePeriod` 内等待已登记在途操作，但 JVM 关闭时资源释放属于尽力完成。

## 配置关联

API v2 依赖以下配置结构：

```yaml
language: zh_CN

bukkit:
  blockLoginUntilReady: true

redis:
  lifecycle:
    shutdownGracePeriod: PT10S
    healthCheckPeriod: PT5S
    statusTimeout: PT5S

  pool:
    maxTotal: 8
    maxIdle: 8
    minIdle: 0
```

API v2 只有统一异步连接池。不要在新配置中加入同步池参数、`maintNotifications` 或独立的 `asyncPool` 节点。
