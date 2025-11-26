<div align="center">

# RedisChannel

<img src="https://img.shields.io/badge/Minecraft-1.12+-green?style=flat-square" alt="Minecraft">
<img src="https://img.shields.io/badge/Kotlin-1.8+-purple?style=flat-square&logo=kotlin" alt="Kotlin">
<img src="https://img.shields.io/badge/Redis-6.0+-red?style=flat-square&logo=redis" alt="Redis">
<img src="https://img.shields.io/badge/License-CC0%201.0-blue?style=flat-square" alt="License">
<img src="https://img.shields.io/badge/Version-1.11.7-orange?style=flat-square" alt="Version">

[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/zhibeigg/RedisChannel)

**为 Minecraft 服务器打造的企业级 Redis 集成解决方案**

*基于 Lettuce 构建，支持单机/集群/哨兵/主从模式，提供同步、异步、响应式三种 API 风格*

[快速开始](#-快速开始) •
[功能特性](#-功能特性) •
[配置指南](#-配置指南) •
[API 文档](#-api-文档) •
[开发指南](#-开发指南)

</div>

---

## 📖 项目简介

**RedisChannel** 是一个基于 [TabooLib](https://github.com/TabooLib/TabooLib) 框架开发的 Bukkit/Spigot 插件，为 Minecraft 服务器提供完整的 Redis 集成能力。它封装了 [Lettuce](https://lettuce.io/) Redis 客户端，提供简洁易用的 API，让开发者能够轻松地在插件中使用 Redis 的强大功能。

### 为什么选择 RedisChannel？

| 特性            | 描述                      |
|---------------|-------------------------|
| **多模式支持**     | 单机、集群、哨兵、主从模式全覆盖        |
| **三种 API 风格** | 同步、异步、响应式，满足不同场景需求      |
| **连接池管理**     | 内置高效的连接池，支持精细化配置        |
| **生产就绪**      | 自动重连、SSL/TLS 加密、完善的错误处理 |
| **开箱即用**      | 简洁的配置文件，分钟级快速接入         |

---

## ✨ 功能特性

### 核心功能

```
┌─────────────────────────────────────────────────────────────┐
│                      RedisChannel                           │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │  单机模式   │  │  集群模式   │  │  哨兵模式   │         │
│  │  Single     │  │  Cluster    │  │  Sentinel   │         │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘         │
│         │                │                │                 │
│         └────────────────┼────────────────┘                 │
│                          ▼                                  │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              统一 API 层 (Unified API)               │   │
│  ├─────────────┬─────────────┬─────────────────────────┤   │
│  │ 同步 Sync   │ 异步 Async  │ 响应式 Reactive         │   │
│  └─────────────┴─────────────┴─────────────────────────┘   │
│                          ▼                                  │
│  ┌─────────────────────────────────────────────────────┐   │
│  │           连接池管理 (Connection Pool)               │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### 功能清单

- **连接模式**
  - ✅ 单机模式 - 直连单个 Redis 实例
  - ✅ 集群模式 - 支持 Redis Cluster 分布式部署
  - ✅ 哨兵模式 - 高可用自动故障转移
  - ✅ 主从模式 - 读写分离，负载均衡

- **API 能力**
  - ✅ 同步命令 - 阻塞式操作，简单直接
  - ✅ 异步命令 - 非阻塞，返回 `Future`
  - ✅ 响应式命令 - 基于 Project Reactor 的响应式流

- **高级特性**
  - ✅ 发布/订阅 (Pub/Sub)
  - ✅ 连接池管理
  - ✅ SSL/TLS 加密连接
  - ✅ 自动重连机制
  - ✅ 集群拓扑自动刷新
  - ✅ 游戏内命令管理

---

## 🚀 快速开始

### 环境要求

- **Minecraft 服务端**: Bukkit/Spigot 1.12+
- **Java**: 8 或更高版本
- **Redis**: 6.0 或更高版本（推荐）

### 安装步骤

1. **下载插件**

   从 [Releases](https://github.com/zhibeigg/RedisChannel/releases) 页面下载最新版本的 JAR 文件

2. **放置插件**

   将 JAR 文件放入服务器的 `plugins` 目录

3. **启动服务器**

   首次启动会生成默认配置文件

4. **配置连接**

   编辑 `plugins/RedisChannel/config.yml` 配置 Redis 连接信息

5. **重载插件**

   使用 `/redis reconnect` 命令应用配置

### 快速配置示例

```yaml
redis:
  host: localhost
  port: 6379
  password: your_password
  database: 0
```

---

## 📋 配置指南

### 基础配置

配置文件位于 `plugins/RedisChannel/config.yml`

```yaml
redis:
  # ==================== 基础连接 ====================
  host: localhost           # Redis 服务器地址
  port: 6379               # Redis 端口
  password: password       # 连接密码（无密码留空）
  database: 0              # 数据库编号 (0-15)
  timeout: PT15S           # 连接超时时间

  # ==================== SSL/TLS ====================
  ssl: false               # 是否启用 SSL
  truststorePassword: ""   # JKS 证书密码
  # 启用 SSL 时，将证书文件命名为 default.jks 放入插件目录

  # ==================== 线程池 ====================
  ioThreadPoolSize: 0              # I/O 线程数 (0=自动)
  computationThreadPoolSize: 0     # 计算线程数 (0=自动)

  # ==================== 连接管理 ====================
  autoReconnect: false                  # 自动重连
  pingBeforeActivateConnection: true    # 连接前 PING 检测
```

### 哨兵模式配置

```yaml
redis:
  sentinel:
    enable: true
    masterId: master              # 主节点名称
    nodes:                        # 哨兵节点列表
      - "127.0.0.1:26379"
      - "127.0.0.2:26379"
      - "127.0.0.3:26379"
```

### 主从模式配置

```yaml
redis:
  slaves:
    enable: true
    readFrom: nearest    # 读取策略
    # 可选值:
    # - master            仅从主节点读取
    # - masterPreferred   优先主节点
    # - replica           仅从从节点读取
    # - replicaPreferred  优先从节点
    # - nearest           选择延迟最低的节点
    # - any               任意节点
```

### 集群模式配置

启用集群模式后，需要在 `plugins/RedisChannel/clusters/` 目录下创建节点配置文件。

```yaml
redis:
  cluster:
    enable: true
    enablePeriodicRefresh: true       # 定期刷新拓扑
    refreshPeriod: PT60S              # 刷新周期
    maxRedirects: 5                   # 最大重定向次数
    closeStaleConnections: true       # 关闭过期连接
    dynamicRefreshSources: true       # 动态刷新源
    validateClusterNodeMembership: true

    # 自适应刷新触发器
    enableAdaptiveRefreshTrigger:
      - MOVED_REDIRECT
      - ASK_REDIRECT
    adaptiveRefreshTriggersTimeout: PT30S
    refreshTriggersReconnectAttempts: 5
```

集群节点配置示例 (`clusters/cluster0.yml`):

```yaml
host: 127.0.0.1
port: 7000
```

### 连接池配置

```yaml
redis:
  # 同步连接池
  pool:
    maxTotal: 8              # 最大连接数
    maxIdle: 8               # 最大空闲连接
    minIdle: 0               # 最小空闲连接
    maxWaitDuration: PT15S   # 最大等待时间

    lifo: true               # LIFO 模式
    fairness: false          # 公平锁
    blockWhenExhausted: true # 资源耗尽时阻塞

    # 连接检测
    testOnCreate: false
    testOnBorrow: false
    testOnReturn: false
    testWhileIdle: false

    # 回收策略
    timeBetweenEvictionRuns: PT30M
    minEvictableIdleDuration: PT30M
    softMinEvictableIdleDuration: PT30M
    numTestsPerEvictionRun: 3

  # 异步连接池
  asyncPool:
    maxTotal: 8
    maxIdle: 8
    minIdle: 0
```

---

## 📚 API 文档

### Maven 依赖

```kotlin
repositories {
    maven("https://www.mcwar.cn/nexus/repository/maven-public/")
}

dependencies {
    compileOnly("com.gitee.redischannel:RedisChannel:1.11.7:api")
}
```

### 获取 API 实例

```kotlin
import com.gitee.redischannel.RedisChannelPlugin

// 获取通用 API（自动识别单机/集群模式）
val api = RedisChannelPlugin.api

// 获取特定模式的 API
val commandAPI = RedisChannelPlugin.commandAPI()           // 单机命令 API
val clusterCommandAPI = RedisChannelPlugin.clusterCommandAPI()  // 集群命令 API
val pubSubAPI = RedisChannelPlugin.pubSubAPI()             // 发布订阅 API
val clusterPubSubAPI = RedisChannelPlugin.clusterPubSubAPI()    // 集群发布订阅 API
```

### 命令操作

#### 同步操作

```kotlin
// 单机模式
val result = api.useCommands { commands ->
    commands.set("player:uuid:name", "Steve")
    commands.get("player:uuid:name")
}

// 集群模式
val clusterResult = clusterApi.useCommands { commands ->
    commands.hset("player:data", "level", "10")
    commands.hget("player:data", "level")
}
```

#### 异步操作

```kotlin
// 异步设置值
api.useAsyncCommands { commands ->
    commands.set("key", "value").thenAccept { result ->
        println("设置结果: $result")
    }
}

// 异步获取值
val future = api.useAsyncCommands { commands ->
    commands.get("key")
}
future.thenAccept { value ->
    println("获取到: $value")
}
```

#### 响应式操作

```kotlin
api.useReactiveCommands { commands ->
    commands.get("key")
        .subscribe { value ->
            println("响应式获取: $value")
        }
}
```

### 发布/订阅

```kotlin
// 订阅频道
pubSubAPI.usePubSubCommands { commands ->
    commands.subscribe("my-channel")
}

// 发布消息
pubSubAPI.usePubSubAsyncCommands { commands ->
    commands.publish("my-channel", "Hello, Redis!")
}

// 集群发布订阅
clusterPubSubAPI.useClusterPubSubCommands { commands ->
    commands.subscribe("cluster-channel")
}
```

### 完整示例

```kotlin
class MyPlugin : JavaPlugin() {

    override fun onEnable() {
        // 保存玩家数据
        savePlayerData("player-uuid", PlayerData("Steve", 100, 50))

        // 读取玩家数据
        val data = loadPlayerData("player-uuid")
        logger.info("玩家数据: $data")
    }

    private fun savePlayerData(uuid: String, data: PlayerData) {
        RedisChannelPlugin.commandAPI().useCommands { cmd ->
            cmd.hset("players:$uuid", mapOf(
                "name" to data.name,
                "level" to data.level.toString(),
                "coins" to data.coins.toString()
            ))
            cmd.expire("players:$uuid", 3600) // 1小时过期
        }
    }

    private fun loadPlayerData(uuid: String): PlayerData? {
        return RedisChannelPlugin.commandAPI().useCommands { cmd ->
            val map = cmd.hgetall("players:$uuid")
            if (map.isNotEmpty()) {
                PlayerData(
                    name = map["name"] ?: "",
                    level = map["level"]?.toIntOrNull() ?: 0,
                    coins = map["coins"]?.toIntOrNull() ?: 0
                )
            } else null
        }
    }

    data class PlayerData(val name: String, val level: Int, val coins: Int)
}
```

---

## 🎮 游戏内命令

| 命令 | 权限 | 描述 |
|------|------|------|
| `/redis` | `RedisChannel.Command.Main` | 显示帮助信息 |
| `/redis reconnect` | `RedisChannel.Command.Main` | 重新连接 Redis |

---

## 🔧 开发指南

### 构建项目

**构建发行版本**（用于生产环境）:

```bash
./gradlew build
```

构建产物位于 `build/libs/` 目录。

**构建开发版本**（包含 TabooLib，仅供开发参考）:

```bash
./gradlew taboolibBuildApi -PDeleteCode
```

> `-PDeleteCode` 参数会移除逻辑代码以减少体积

### 项目结构

```
RedisChannel/
├── src/main/kotlin/com/gitee/redischannel/
│   ├── RedisChannelPlugin.kt      # 插件主类
│   ├── api/                        # 公共 API
│   │   ├── RedisChannelAPI.kt
│   │   ├── RedisCommandAPI.kt
│   │   ├── RedisPubSubAPI.kt
│   │   └── cluster/
│   │       ├── RedisClusterCommandAPI.kt
│   │       └── RedisClusterPubSubAPI.kt
│   ├── core/                       # 核心实现
│   │   ├── RedisManager.kt
│   │   ├── ClusterRedisManager.kt
│   │   ├── RedisChannelCommand.kt
│   │   └── RedisConfig.kt
│   └── util/
│       └── File.kt
├── src/main/resources/
│   ├── config.yml                  # 主配置文件
│   └── clusters/                   # 集群节点配置
│       └── cluster0.yml
├── build.gradle.kts
└── gradle.properties
```

### 技术栈

| 组件 | 版本 | 用途 |
|------|------|------|
| Kotlin | 1.8+ | 开发语言 |
| TabooLib | 6.2 | 插件框架 |
| Lettuce | 6.6.0 | Redis 客户端 |
| Project Reactor | 3.6.6 | 响应式支持 |
| Netty | 4.1.118 | 网络通信 |
| Commons Pool2 | 2.12.1 | 连接池 |

---

## 📄 许可证

本项目采用 [CC0 1.0 Universal](LICENSE) 许可证 - 公共领域贡献

---

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 打开 Pull Request

---

## 📮 联系方式

- **作者**: zhibei
- **仓库**: [GitHub](https://github.com/zhibeigg/RedisChannel)
- **问题反馈**: [Issues](https://github.com/zhibeigg/RedisChannel/issues)

---

<div align="center">

**如果这个项目对你有帮助，请给一个 ⭐ Star 支持一下！**

Made with ❤️ for Minecraft Community

</div>
