# RedisChannel v1 → API v2 迁移指南

RedisChannel `2.14.12` 引入 API v2，并删除全部同步 Redis API。最终公开 API 只保留 `CompletionStage` 异步接口；原响应式公开接口也已删除，因为相关类型跨插件重定位时 ABI 不安全。迁移的核心目标是：让调用链完整返回 `CompletionStage`，通过异步错误通道处理失败，并明确 Bukkit 主线程边界。

## 迁移清单

1. 将依赖版本更新到 `2.15.13`，仓库改为当前发布仓库。
2. 删除所有同步 API 调用。
3. 将旧命令方法改为 API v2 的四个 `CompletionStage` 方法；旧响应式调用也必须改写为异步 Stage 链。
4. 不再用 `null` 表示 Redis 错误；异常通过 Stage 的 exceptional completion 传播。
5. 保留 Redis `GET` 的合法 `null` 结果语义。
6. 移除 `get()`、`join()`、阻塞等待、休眠和同步锁桥接。
7. 在异步回调访问 Bukkit API 前切回主线程。
8. 更新配置为 `language`、`bukkit.blockLoginUntilReady`、`redis.lifecycle` 和统一 `redis.pool`。
9. 将集群节点文件改为根级 `host`。
10. 升级到 `2.15.13` 时，将普通 YAML 语言文件目录从 `lang/` 迁移到 `messages/`。

## 依赖迁移

### v1

```kotlin
repositories {
    maven("https://jfrog.mcwar.cn/artifactory/maven-releases")
}

dependencies {
    compileOnly("com.gitee.redischannel:RedisChannel:1.14.10:api")
}
```

### v2

```kotlin
repositories {
    maven("https://maven.mcwar.cn/releases")
}

dependencies {
    compileOnly("com.gitee.redischannel:RedisChannel:2.15.13:api")
}
```

## API 方法映射

| v1 | v2 |
|---|---|
| `useCommands { ... }` | 已删除；改为 `executeAsync(Function { ... })` |
| `useAsyncCommands { ... }` | `executeAsync(Function { ... })` |
| 旧响应式命令方法 | 已删除；改为 `executeAsync(Function { ... })` 并组合 Stage |
| 集群 `useCommands` / `useAsyncCommands` | `executeClusterAsync(Function { ... })` |
| 旧集群响应式命令方法 | 已删除；改为 `executeClusterAsync(Function { ... })` |
| `usePubSubCommands` / `usePubSubAsyncCommands` | `executePubSubAsync(Function { ... })` |
| 旧响应式 Pub/Sub 方法 | 已删除；改为 `executePubSubAsync(Function { ... })` |
| 集群 Pub/Sub 旧方法 | `executeClusterPubSubAsync(Function { ... })` |
| 旧启动/停止/重连入口 | `RedisChannelPlugin.api.startAsync/stopAsync/reconnectAsync` |

## 同步 GET 迁移

### v1：同步阻塞

```kotlin
val value = RedisChannelPlugin.commandAPI().useCommands { commands ->
    commands.get("player:$uuid:name")
}

if (value == null) {
    // v1 代码常把 null 同时当作“不存在”和“操作失败”
    useFallback()
} else {
    useName(value)
}
```

### v2：分别处理异常与合法 null

```kotlin
import java.util.function.Function

RedisChannelPlugin.commandAPI().executeAsync(
    Function { commands -> commands.get("player:$uuid:name") }
).whenComplete { value, error ->
    when {
        error != null -> handleRedisFailure(error)
        value == null -> handleMissingKey()
        else -> useName(value)
    }
}
```

关键变化：

- `error != null` 才表示 Redis 操作失败。
- `error == null && value == null` 表示 GET 成功，但 key 不存在。
- 不要用 `join()` 把 v2 改回同步写法。

## 异步命令迁移

### v1

```kotlin
RedisChannelPlugin.commandAPI().useAsyncCommands { commands ->
    commands.set("key", "value")
}.thenAccept { result ->
    logger.info("SET: $result")
}
```

### v2

```kotlin
RedisChannelPlugin.commandAPI().executeAsync(
    Function { commands -> commands.set("key", "value") }
).whenComplete { result, error ->
    if (error != null) {
        logger.warning("SET 失败: ${error.message}")
    } else {
        logger.info("SET: $result")
    }
}
```

v2 不会以 `CompletableFuture<null>` 吞掉连接或命令异常。失败会使返回 Stage 异常完成。

## 多命令流程迁移

### v1：同步顺序执行

```kotlin
RedisChannelPlugin.commandAPI().useCommands { commands ->
    commands.hset("player:$uuid", "level", "10")
    commands.expire("player:$uuid", 3600)
}
```

### v2：返回完整 Stage 链

```kotlin
RedisChannelPlugin.commandAPI().executeAsync(
    Function { commands ->
        commands.hset("player:$uuid", "level", "10")
            .thenCompose { commands.expire("player:$uuid", 3600) }
    }
).whenComplete { expireApplied, error ->
    if (error != null) {
        logger.warning("保存玩家数据失败: ${error.message}")
    } else {
        logger.info("过期时间设置结果: $expireApplied")
    }
}
```

### 常见错误：过早结束 action

```kotlin
// 错误：返回值没有覆盖 SET 的生命周期。
RedisChannelPlugin.commandAPI().executeAsync(
    Function { commands ->
        commands.set("key", "value")
        CompletableFuture.completedFuture(Unit)
    }
)
```

API v2 会在 action 返回的 Stage 结束后释放连接，因此必须返回真实命令 Stage 或组合后的完整 Stage 链。

## 原响应式调用迁移

最终 API v2 不再公开响应式方法。调用方必须改用 `executeAsync`，并通过 `thenApply`、`thenCompose`、`whenComplete` 等 Stage 操作符组合流程：

```kotlin
RedisChannelPlugin.commandAPI().executeAsync(
    Function { commands -> commands.get("key") }
).whenComplete { value, error ->
    if (error != null) handleRedisFailure(error) else handleValue(value)
}
```

## 集群命令迁移

### v1

```kotlin
RedisChannelPlugin.clusterCommandAPI().useAsyncCommands { commands ->
    commands.hget("player:data", "level")
}
```

### v2

```kotlin
RedisChannelPlugin.clusterCommandAPI().executeClusterAsync(
    Function { commands -> commands.hget("player:data", "level") }
).whenComplete { level, error ->
    if (error != null) {
        handleRedisFailure(error)
    } else {
        handleLevel(level)
    }
}
```

## Pub/Sub 迁移

### v1

```kotlin
RedisChannelPlugin.pubSubAPI().usePubSubAsyncCommands { commands ->
    commands.subscribe("server-events")
}
```

### v2

```kotlin
RedisChannelPlugin.pubSubAPI().executePubSubAsync(
    Function { commands -> commands.subscribe("server-events") }
).whenComplete { _, error ->
    if (error != null) logger.warning("订阅失败: ${error.message}")
}
```

集群专用 Pub/Sub：

```kotlin
RedisChannelPlugin.clusterPubSubAPI().executeClusterPubSubAsync(
    Function { commands -> commands.subscribe("cluster-events") }
)
```

## 生命周期迁移

API v2 将生命周期控制集中到 `RedisChannelPlugin.api`：

```kotlin
val current = RedisChannelPlugin.api.lifecycle()

RedisChannelPlugin.api.startAsync()
RedisChannelPlugin.api.stopAsync()
RedisChannelPlugin.api.reconnectAsync()
```

推荐组合处理：

```kotlin
RedisChannelPlugin.api.reconnectAsync().whenComplete { snapshot, error ->
    if (error != null) {
        logger.warning("重连失败: ${error.message}")
    } else {
        logger.info("重连完成: ${snapshot.state}")
    }
}
```

不要：

```kotlin
// 禁止：阻塞当前线程，主线程尤其危险。
RedisChannelPlugin.api.reconnectAsync().toCompletableFuture().join()
```

`lifecycle()` 返回当前不可变快照，可用于检查 `state`、`mode`、`generation`、`failureMessage` 和 `initialized`。`RedisDeploymentMode` 现在包含 `SINGLE`、`SENTINEL`、`MASTER_REPLICA`、`CLUSTER`。

## Bukkit 回调线程迁移

v1 代码可能默认异步完成回调位于主线程，这在 v2 中不成立。Stage 回调线程不保证是 Bukkit 主线程。

### 错误写法

```kotlin
RedisChannelPlugin.commandAPI().executeAsync(
    Function { commands -> commands.get("player:$uuid:title") }
).thenAccept { title ->
    // 错误：直接在未知线程访问 Bukkit Player。
    Bukkit.getPlayer(uuid)?.setDisplayName(title ?: "Player")
}
```

### 正确写法

```kotlin
RedisChannelPlugin.commandAPI().executeAsync(
    Function { commands -> commands.get("player:$uuid:title") }
).whenComplete { title, error ->
    Bukkit.getScheduler().runTask(this, Runnable {
        val player = Bukkit.getPlayer(uuid) ?: return@Runnable
        if (error != null) {
            player.sendMessage("读取称号失败")
        } else {
            player.setDisplayName(title ?: player.name)
        }
    })
}
```

示例中的 `this` 是依赖 RedisChannel 的 `JavaPlugin` 实例。

## 生命周期事件迁移

`ClientStartEvent` 与 `ClientStopEvent` 在 v2 中明确保证由 Bukkit 主线程触发。`startAsync()` 与 `reconnectAsync()` 返回的 Future 会在 `ClientStartEvent` 主线程触发尝试结束后才完成；启动事件监听器异常只记录日志，不会把已成功启动的 Future 改为失败。

```kotlin
@SubscribeEvent
fun onRedisStart(event: ClientStartEvent) {
    // 当前是 Bukkit 主线程。
    val players = Bukkit.getOnlinePlayers().map { it.uniqueId.toString() }

    // Redis I/O 仍必须异步，不能 join/get。
    RedisChannelPlugin.commandAPI().executeAsync(
        Function { commands -> commands.sadd("online", *players.toTypedArray()) }
    )
}
```

`ClientStopEvent` 中可以发起异步 API v2 操作；生命周期协调器会在关闭宽限时间内等待已登记的在途操作，但监听器自身不得阻塞。即使停止事件触发失败，runtime 关闭仍会继续；事件失败或关闭失败都会使停止/重连 Future exceptional completion，两者同时失败时会合并异常。

## 2.15.13 语言目录迁移

`2.15.13` 将普通 YAML 语言资源从 `lang/` 调整为 `messages/`，避免继续使用旧资源目录。新插件 JAR 只包含 `messages/*.yml`。

自动兼容规则：

- 启动或重载语言时扫描 `plugins/RedisChannel/lang/*.yml`；
- 仅在 `plugins/RedisChannel/messages/` 中不存在同名文件时原样复制；
- 不删除旧文件，不覆盖新目录中的文件，也不改写自定义文本；
- 如果自动复制失败，当前选择的旧语言文件仍会被直接读取。

建议在部署新 JAR 前停服迁移：

1. `messages/` 不存在时，直接将整个 `lang/` 目录重命名为 `messages/`。
2. `messages/` 已存在时，只复制其中不存在的文件；同名文件人工比较并合并，禁止覆盖。
3. 启动 `2.15.13` 并确认自定义文本正确后，再归档或删除旧 `lang/`。

该迁移不涉及公开 API 变更，也不引入任何阻塞等待。

## 配置迁移

### v1 示例

```yaml
redis:
  language: zh_CN
  maintNotifications: true

  pool:
    maxTotal: 8
    maxWaitDuration: PT15S
    blockWhenExhausted: true
    testOnBorrow: false

  asyncPool:
    maxTotal: 8
    maxIdle: 8
    minIdle: 0
```

### v2 示例

```yaml
language: zh_CN

bukkit:
  blockLoginUntilReady: true

redis:
  host: localhost
  port: 6379
  password: ''
  ssl: false
  timeout: PT15S
  database: 0

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

变更说明：

| v1 | v2 |
|---|---|
| `redis.language` 或旧语言位置 | 根级 `language` |
| 无登录就绪保护或旧位置 | `bukkit.blockLoginUntilReady` |
| 无统一生命周期配置 | `redis.lifecycle` |
| 同步池与旧异步池节点 | 统一异步 `redis.pool`；旧 `redis.asyncPool` 会导致配置失败 |
| `maintNotifications` | 删除 |
| `maxWaitDuration`、`blockWhenExhausted`、检测/回收等同步池选项 | 删除 |

不要把同步池参数复制到 v2 的 `redis.pool`。`redis.asyncPool` 不再提供兼容读取；发现该节点会直接配置失败，必须先迁移为 `redis.pool`。`redis.lifecycle.healthCheckPeriod` 不得小于 1 秒，实际健康检查调度粒度为 1 秒。

## cluster0.yml 迁移

### 错误的旧嵌套结构

```yaml
redis:
  host: localhost
  port: 6379
```

### v2 根级结构

```yaml
host: localhost
port: 6379
password: ''
ssl: false
timeout: PT15S
database: 0
```

每个 `plugins/RedisChannel/clusters/*.yml` 文件表示一个 Redis Cluster seed 节点。

## 完整迁移示例

### v1：主线程同步加载玩家数据

```kotlin
fun loadPlayer(player: Player) {
    val data = RedisChannelPlugin.commandAPI().useCommands { commands ->
        commands.hgetall("player:${player.uniqueId}")
    }

    player.level = data?.get("level")?.toIntOrNull() ?: 0
}
```

问题：

- Redis I/O 阻塞 Bukkit 主线程。
- `null` 同时承担错误和业务空值语义。
- 无法正确传播连接或命令异常。

### v2：异步读取，主线程应用结果

```kotlin
fun loadPlayer(playerId: UUID) {
    RedisChannelPlugin.commandAPI().executeAsync(
        Function { commands -> commands.hgetall("player:$playerId") }
    ).whenComplete { data, error ->
        Bukkit.getScheduler().runTask(this, Runnable {
            val player = Bukkit.getPlayer(playerId) ?: return@Runnable

            if (error != null) {
                logger.warning("加载玩家数据失败: ${error.message}")
                player.sendMessage("数据服务暂时不可用")
                return@Runnable
            }

            player.level = data["level"]?.toIntOrNull() ?: 0
        })
    }
}
```

迁移后的代码具备以下性质：

- Redis I/O 不阻塞 Bukkit 主线程。
- Redis 异常通过 Stage 明确传播。
- Bukkit 状态只在主线程修改。
- action 返回完整命令 Stage，连接生命周期正确。

## 迁移后检查

在代码库中搜索并清理：

- `useCommands`
- `useAsyncCommands`
- `usePubSubCommands`
- `usePubSubAsyncCommands`
- `join()`
- `.get()` 用于等待 Future/Stage 的调用
- `maintNotifications`
- `asyncPool:`
- 同步池的等待、检测和回收参数

最后确认：

- 所有 Redis I/O 都返回或组合 Stage；
- 所有异常路径均有处理；
- GET 的 `null` 仅表示 key 不存在；
- Bukkit API 访问位于主线程；
- `ClientStartEvent`/`ClientStopEvent` 监听器中没有阻塞等待；
- 集群节点文件的 `host` 位于根级。
