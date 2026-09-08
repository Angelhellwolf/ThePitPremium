# Repository Guidelines

## Supporter Commerce and Currency

- The owner confirmed **1 yuan = 100 points**. Derive displayed point prices, debits, refunds, and upgrade differences from the same yuan prices.
- Current approved products: 天坑·会员 50 yuan / 5,000 points permanently; 神话契约 38 yuan / 3,800 points per 30 days; 神话圣契 88 yuan / 8,800 points per 30 days.
- The two absolute base probabilities are 0.006 for ordinary/permanent-only accounts, 0.0075 for 神话契约, and 0.0125 for 神话圣契. Preserve gameplay modifiers and the separate lucky-gem/guaranteed paths. Legacy `rate.*` and `mythicDropChance.*` permission entries no longer determine commercial-tier rates.
- Paid entitlements and purchase receipts live in `pit_supporter_accounts` / `pit_supporter_orders` in the RemiaftValue database, with the points debit in the same transaction. Mongo profile fields are mirrors; do not grant or revoke a paid entitlement by changing only the mirror.
- VIP through MVP++ are the main network's rank names. Keep ThePit's product names and entitlements independent.
- Player-facing sponsor menus and purchase confirmations use qualitative copy: 神话契约 says 略微提高; 神话圣契 says 大幅提高. Do not display numeric chances or calculation details in those menus. Keep exact values available in configuration and administrator diagnostics.
- The permanent product is displayed as 天坑·会员. Owning its stored permanent entitlement is mandatory before buying or renewing either contract. Do not bundle permanent membership into a contract checkout or use a network rank / wildcard permission as a substitute for that entitlement.

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
- `./gradlew test` - runs the existing JUnit 4/Mockito tests. Use `./gradlew test --tests '<TestClass>'` for a relevant class.

## Coding Style & Naming Conventions
- Use 4-space indentation; avoid tabs.
- Keep packages lowercase; classes/interfaces in `PascalCase`; methods/fields in `camelCase`; constants in `UPPER_SNAKE_CASE`.
- Preserve existing Java/Kotlin interop style and avoid unnecessary rewrites between languages.
- Keep Bukkit main-thread operations explicit (schedule server-thread work when required).
- Encoding is UTF-8 (`build.gradle.kts`); ensure files are saved as UTF-8.
- No dedicated lint config is committed; use IntelliJ formatter and optimize imports before commit.

## Testing Guidelines
Existing tests under `src/test/java` cover gameplay rules, persistence and utility behavior. Extend the relevant coverage:
- Place tests under `src/test/java` or `src/test/kotlin`.
- Name files `*Test` and methods with behavior-focused names.
- Prioritize logic-heavy units (data serializers, utility classes, command parsing).
- Run affected tests after behavior changes; use the full suite when the change crosses gameplay or persistence boundaries.

## Commit & Pull Request Guidelines
Recent history uses short messages (e.g., `fix`, `clean`, `Fixing events`) in mixed Chinese/English. Keep commits concise and action-oriented, but include scope when possible, for example:
- `event: fix hamburger trigger condition`
- `data: guard null quest state`

PRs should include:
- What changed and why.
- Risk/rollback notes for gameplay-impacting changes.
- Any config/resource updates (`config.yml`, `plugin.yml`, `items.csv`).
- Screenshots or server log snippets for command/UI behavior changes.
