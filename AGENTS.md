# Repository Guidelines

## Supporter Commerce and Currency

- The owner confirmed **1 yuan = 100 points**. Derive displayed point prices, debits, refunds, and upgrade differences from the same yuan prices.
- Current approved products: 天坑·会员 50 yuan / 5,000 points permanently; 神话契约 38 yuan / 3,800 points per 30 days; 神话圣契 88 yuan / 8,800 points per 30 days.
- The two absolute base probabilities are 0.006 for ordinary/permanent-only accounts, 0.0075 for 神话契约, and 0.0125 for 神话圣契. Preserve gameplay modifiers and the separate lucky-gem/guaranteed paths. Legacy `rate.*` and `mythicDropChance.*` permission entries no longer determine commercial-tier rates.
- Owner-approved 2026 National Day exception: from October 1 00:00 through October 7 (UTC+8; exclusive end October 8 00:00), white/fresh drop probabilities are 0.04 / 0.06 / 0.06 and rare probabilities are 0.02 / 0.03 / 0.04 for ordinary, 神话契约, and 神话圣契 tiers. Apply through the timed `supporter.national-day-2026` override and automatically restore ordinary values (0.006 / 0.0075 / 0.0125) outside the window. Supply control is silent probability adjustment, with no quota messages or blocked normal enchanting, fresh drops, reward claims, crafting or auctions. Rolling server/player/room budgets reduce random weights smoothly, retaining a positive floor. Preserve guaranteed book effects and material consumption; guaranteed sources count toward pressure without quota rejection. `pit.admin`, staff, OP, `pit.rare.bypass`, SC_shengcheng, and 77hz bypass production pressure without an extra multiplier. As authorized on October 2, SC_shengcheng has a 6% rare-enchantment base and 77hz 4%; 77hz's UUID takes precedence over the SC permission. Only those two UUIDs' RARE grants are excluded from global and room pressure, including existing records; retain personal records and FRESH accounting. Low stock (0 or 1 rare item across inventory, armor, chest, well, mail and trade escrow) grants one next eligible enchant roll with a minimum 20% base per rolling 24 hours; retain production pressure. Outbound rare transfers or pending own rare ground drops prevent this compensation. Completed trades and confirmed ground handoffs persist sender/item records. At 3 distinct items/hour or 6/day use 25% of the rare probability; at 6/hour or 12/day use 10%. This transfer penalty applies to all accounts, including staff, SC and 77hz; never let pressure bypass disable it. Keep cumulative-expectation drought protection, RSL1 v1-to-v2 migration and administrator-only diagnostics.
- Latest owner correction on October 2: low-stock compensation is enabled ONLY during the configured 2026 National Day window, at most once per rolling 24 hours. At October 8 00:00 UTC+8 it is disabled completely, with no weekly compensation afterwards, while ordinary seasonal probabilities restore automatically. Evaluate this window using monotonic ledger time and preserve existing use timestamps; do not grant a final or queued boost on activity closure. Count actual held item quantities even when copied items share a UUID. SC/77 rates, RARE isolation and transfer penalties remain effective outside the activity window. Transfer penalties cover ALL players and ALL pressure-exempt accounts.
- The subsequent October 2 correction disables server-wide production pressure for both pools, including legacy global cap/reservation checks. Keep global observed counts only for diagnostics; personal and room pressure remain. Combat target factors apply to all recipients, including SC, 77hz and staff: actual bot targets retain 5% of their previous unpressured probability, connected human targets use 125% of the unscaled base. Real network connections override persisted bot flags, NPC metadata and economy/training classification; classify a disconnected synthetic entity by actual manager entity ownership or NPC metadata, never a UUID-only match. Apply to combat white drops, cherry drops and random rare jewel awakenings, preserving guaranteed results and normal well/fishing probabilities. Managed synthetic recipients remain excluded from controlled supply; falsely flagged real recipients remain eligible. The 5% bot factor prevents removing the former global 5% pressure floor from increasing ordinary bot drop probabilities. Seasonal bases still restore automatically on October 8; combat target factors continue afterwards.
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
- The owner requested removal of the automated test tree on 2026-09-14. This checkout currently has no test suite; do not report an empty Gradle test task as executed coverage.

## Coding Style & Naming Conventions
- Use 4-space indentation; avoid tabs.
- Keep packages lowercase; classes/interfaces in `PascalCase`; methods/fields in `camelCase`; constants in `UPPER_SNAKE_CASE`.
- Preserve existing Java/Kotlin interop style and avoid unnecessary rewrites between languages.
- Keep Bukkit main-thread operations explicit (schedule server-thread work when required).
- Encoding is UTF-8 (`build.gradle.kts`); ensure files are saved as UTF-8.
- No dedicated lint config is committed; use IntelliJ formatter and optimize imports before commit.

## Testing Guidelines
The automated test sources and test-only dependencies were removed at the owner's request on 2026-09-14. Verify changes proportionately using compilation, artifact inspection, and any necessary narrowly scoped checks. Distinguish those checks from a test-suite run and from live gameplay validation.

## Commit & Pull Request Guidelines
Recent history uses short messages (e.g., `fix`, `clean`, `Fixing events`) in mixed Chinese/English. Keep commits concise and action-oriented, but include scope when possible, for example:
- `event: fix hamburger trigger condition`
- `data: guard null quest state`

PRs should include:
- What changed and why.
- Risk/rollback notes for gameplay-impacting changes.
- Any config/resource updates (`config.yml`, `plugin.yml`, `items.csv`).
- Screenshots or server log snippets for command/UI behavior changes.
