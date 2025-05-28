# RedisChannel
[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/zhibeigg/RedisChannel)
## 构建发行版本

发行版本用于正常使用, 不含 TabooLib 本体。

```
./gradlew build
```

## 构建开发版本

开发版本包含 TabooLib 本体, 用于开发者使用, 但不可运行。

```
./gradlew taboolibBuildApi -PDeleteCode
```

> 参数 -PDeleteCode 表示移除所有逻辑代码以减少体积。

## 使用API

```
repositories {
    maven("https://www.mcwar.cn/nexus/repository/maven-public/")
}

dependencies {
    implementation("com.gitee.redischannel:RedisChannel:{VERSION}:api")
}
```
### 直接调用自动判断 集群/单机

```kotlin
val api = RedisChannelPlugin.api
val id = "player"

// 读取数据
val data = api.stringCommand()[id]

// 更新过期时间
api.keyCommand().expire(id, 3600)

// 保存
api.stringCommand().setex(id, 3600, data)

// 异步使用
api.proxyAsyncCommand().thenAccept { command ->
    
    // 读取数据
    val data = command.stringCommand()[id]

    // 更新过期时间
    command.expire(id, 3600)

    // 保存
    command.setex(id, 3600, data)
}

// 反应式使用
api.getProxyReactiveCommand().thenApply { command ->
    
    val result = command.set(id, "")
        .then(command.get(id))
        .doOnNext { value ->
            println("获取的值: $value")
        }

    result.subscribe(
        {
            println("value $it")
        },
        {
            println("error ${it.message}")
        },
        {
            println("操作完成")
        }
    )
}
```

### 获取集群/单机的 Command

```kotlin
val api = RedisChannelPlugin.api.commandAPI()
val clusterApi = RedisChannelPlugin.api.clusterCommandAPI()
val id = "player"

// 普通模式
val data = api.useCommands { command ->
    command.get(id)
}
val asyncData = api.useAsyncCommands { command ->
    command.get(id)
}
val asyncData = api.useReactiveCommands { command ->
    //......
}

// 集群模式
val clusterData = api.useCommands { command ->
    command.get(id)
}
val clusterAsyncData = api.useAsyncCommands { command ->
    command.get(id)
}
val asyncData = api.useReactiveCommands { command ->
    //......
}
```

> {VERSION} 处填写版本号 如 1.0.0