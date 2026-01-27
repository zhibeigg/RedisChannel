# Repository Guidelines

## Project Structure & Module Organization
- `src/main/kotlin/com/gitee/redischannel/`: Kotlin source code (plugin entry, APIs, core managers, utilities).
- `src/main/resources/`: Runtime resources and configuration templates, including `config.yml` and `clusters/cluster0.yml`.
- `build.gradle.kts`, `gradle.properties`: Gradle build definition and project coordinates.
- `build/`: Build output directory; artifacts are written to the path supplied by the `-Pbuild` property.

## Build, Test, and Development Commands
- `./gradlew build -Pbuild=build/libs`: Build the production JAR (uses TabooLib). Output path is controlled by `-Pbuild`.
- `./gradlew taboolibBuildApi -PDeleteCode -Pbuild=build/libs`: Build a development API JAR with logic stripped for size.
- `./gradlew clean`: Remove build outputs before a clean rebuild.

## Coding Style & Naming Conventions
- Kotlin code follows standard Kotlin formatting with 4-space indentation and UTF-8 source encoding.
- Package naming uses `com.gitee.redischannel` and subpackages like `api`, `core`, and `util`.
- Classes use `PascalCase`, functions and properties use `camelCase`.
- YAML configuration files are lower-case and live under `src/main/resources/`.

## Testing Guidelines
- There are currently no automated tests in this repository.
- If you add tests, place them under `src/test/kotlin` and wire dependencies in Gradle, then run `./gradlew test`.

## Commit & Pull Request Guidelines
- Commit messages follow a Conventional Commit style (e.g., `feat(core): ...`, `refactor(plugin): ...`, `chore(build): ...`) and often use Chinese descriptions; keep the same tone and scope formatting.
- PRs should include a clear description, link related issues, and call out any configuration changes or Redis compatibility impacts.

## Configuration & Security Tips
- Do not commit real credentials. Treat `src/main/resources/config.yml` and `clusters/cluster0.yml` as templates only.
- Runtime configs live under `plugins/RedisChannel/` on the server; keep defaults conservative and document any new keys.
