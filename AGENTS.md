# Repository Guidelines

## Project Structure & Module Organization

- `src/main/kotlin/com/gitee/redischannel/`: Kotlin source code, including plugin entry, public API v2, lifecycle coordinator, runtimes and utilities.
- `src/main/resources/`: Runtime templates, including `config.yml`, language files and `clusters/cluster0.yml`.
- `docs/api-v2.md`: Public API v2 reference.
- `docs/migration-api-v2.md`: v1 to v2 migration guide.
- `build.gradle.kts`, `gradle.properties`: Build definition and coordinates (`2.14.12`).
- `build/`: Build output; the final artifact path is controlled by `-Pbuild`.

## Compatibility & Dependencies

- Minecraft/Bukkit compatibility target: `1.12.2`.
- Runtime compatibility: Java 8 or newer.
- Build toolchain: JDK 17, compiling Java and Kotlin to Java 8 bytecode.
- Lettuce: `6.8.0.RELEASE`.
- Maven repository: `https://maven.mcwar.cn/releases`.
- API dependency: `com.gitee.redischannel:RedisChannel:2.14.12:api`.

## Build, Test, and Development Commands

- `./gradlew build -Pbuild=build/libs`: Build the production JAR.
- `./gradlew taboolibBuildApi -PDeleteCode -Pbuild=build/libs`: Build the API JAR with implementation code stripped.
- `./gradlew test`: Run the test suite.
- `./gradlew clean`: Remove build outputs before a clean rebuild.

## API v2 Contract

API v2 removes all synchronous Redis APIs. Do not add blocking wrappers or restore v1 methods.

Public entry points:

- `RedisChannelPlugin.api`: `lifecycle()`, `startAsync()`, `stopAsync()`, `reconnectAsync()`.
- `RedisChannelPlugin.commandAPI()`: `executeAsync`, `executeReactive`.
- `RedisChannelPlugin.clusterCommandAPI()`: `executeClusterAsync`, `executeClusterReactive`.
- `RedisChannelPlugin.pubSubAPI()`: `executePubSubAsync`, `executePubSubReactive`.
- `RedisChannelPlugin.clusterPubSubAPI()`: `executeClusterPubSubAsync`, `executeClusterPubSubReactive`.

Contract details:

- Redis I/O and lifecycle changes must remain non-blocking.
- The action's `CompletionStage` or `Publisher` represents the full operation lifetime.
- Failures propagate through exceptional completion or the Reactive error channel.
- A `null` Redis `GET` value is a valid successful result for a missing key, not an error sentinel.

## Bukkit Threading Rules

- Never execute Bukkit main-thread-sensitive logic in arbitrary Redis callbacks.
- `CompletionStage` and Reactive callbacks are not guaranteed to run on the Bukkit main thread.
- Switch back to the Bukkit main thread before accessing players, worlds, entities, inventories or similar state.
- `ClientStartEvent` and `ClientStopEvent` are always fired on the Bukkit main thread.
- Event handlers must still use asynchronous Redis APIs and must not call `get()`, `join()`, sleep or otherwise block.

## Configuration Contract

Current keys include:

- root `language`;
- `bukkit.blockLoginUntilReady`;
- `redis.lifecycle`;
- unified async pool `redis.pool`.

Do not reintroduce `maintNotifications`, synchronous pool options, or `redis.asyncPool` in the default template. Cluster seed files such as `clusters/cluster0.yml` use root-level `host`, not `redis.host`.

## Coding Style & Naming Conventions

- Use standard Kotlin formatting with 4-space indentation and UTF-8.
- Packages use `com.gitee.redischannel`; classes use `PascalCase`; functions and properties use `camelCase`.
- Avoid `!!`; prefer Kotlin null safety, explicit validation and result objects.
- Keep Redis/database I/O asynchronous.
- When public behavior changes, update the default/example configuration, language files, README and external API documentation together.

## Testing Guidelines

- Place tests under `src/test/kotlin` and run `./gradlew test`.
- Cover lifecycle transitions, exceptional Stage propagation, legal null Redis values, async pool resource release and Bukkit thread boundaries.
- Do not add tests that depend on blocking waits in production paths.

## Commit & Pull Request Guidelines

- Follow the repository's Conventional Commit style, commonly with concise Chinese descriptions, for example `docs(api): 更新 v2 迁移指南`.
- PRs should describe API, threading, configuration and Redis compatibility impacts.
- Never commit real Redis credentials; resource files are templates only.
