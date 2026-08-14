# Repository Guidelines

## Project Structure & Module Organization
This repository is a Gradle-based Minecraft plugin (`Spigot 1.8.8`) with mixed Java/Kotlin code.
- Main code: `src/main/java`
- Core gameplay: `src/main/java/cn/charlotte/pit`
- NPC/network utilities: `src/main/java/net/jitse/npclib`, `src/main/java/com/comphenix`
- Kotlin constructors/registries: `src/main/java/real/nanoneko`
- Runtime resources: `src/main/resources` (`plugin.yml`, `config.yml`, `items.csv`, `js/`, `.nbs`, `.schematic`)
- Local dependency jars: `libs/`
- Performance analysis scripts/data: `perf_case/`

## Build, Test, and Development Commands
Use the Gradle wrapper from repository root.
- `./gradlew clean` - removes previous build outputs.
- `./gradlew build` - compiles and packages the plugin (wired to `shadowJar`).
- `./gradlew shadowJar` - creates shaded plugin jar in `build/libs/` (default name: `ThePitPremium-3.93.jar`).
- `./gradlew test` - runs unit tests (currently minimal/none; see testing section).

## Coding Style & Naming Conventions
- Use 4-space indentation; avoid tabs.
- Keep packages lowercase; classes/interfaces in `PascalCase`; methods/fields in `camelCase`; constants in `UPPER_SNAKE_CASE`.
- Preserve existing Java/Kotlin interop style and avoid unnecessary rewrites between languages.
- Keep Bukkit main-thread operations explicit (schedule server-thread work when required).
- Encoding is UTF-8 (`build.gradle.kts`); ensure files are saved as UTF-8.
- No dedicated lint config is committed; use IntelliJ formatter and optimize imports before commit.

## Testing Guidelines
There is no committed `src/test` suite yet. For new tests:
- Place tests under `src/test/java` or `src/test/kotlin`.
- Name files `*Test` and methods with behavior-focused names.
- Prioritize logic-heavy units (data serializers, utility classes, command parsing).
- Run `./gradlew test` locally before opening a PR.

## Commit & Pull Request Guidelines
Recent history uses short messages (e.g., `fix`, `clean`, `Fixing events`) in mixed Chinese/English. Keep commits concise and action-oriented, but include scope when possible, for example:
- `event: fix hamburger trigger condition`
- `data: guard null quest state`

PRs should include:
- What changed and why.
- Risk/rollback notes for gameplay-impacting changes.
- Any config/resource updates (`config.yml`, `plugin.yml`, `items.csv`).
- Screenshots or server log snippets for command/UI behavior changes.
