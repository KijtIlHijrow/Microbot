# Microbot API Guide (for Claude)

Short notes for writing automation scripts with the Microbot plugin inside RuneLite.

## Paths & Builds
- Plugin sources live in `runelite-client/src/main/java/net/runelite/client/plugins/microbot`.
- The queryable API lives in `.../microbot/api`; full guide: `.../microbot/api/QUERYABLE_API.md`.
- Build system is **Gradle** (not Maven). The client module is `:client`.
- Quick compile: `gradlew :client:compileJava`; full build: `gradlew :client:build -x test`; run: `gradlew :client:run`.
- Old source files and decompiled classes live in `temp-flipper/` for reference only.
- **Bolt launcher**: The user runs the client via `C:\Users\keith\Downloads\Bolt\bolt.exe`. Bolt's config at `C:\Users\keith\AppData\Roaming\bolt-launcher\config\launcher.json` has a `runelite_custom_jar` field pointing to a specific jar in `runelite-client/build/libs/`. After every build that bumps the version, update the jar filename in that JSON so Bolt picks up the new version (e.g. `microbot-2.1.18.jar` → `microbot-2.1.19.jar`).

## Plugin Versioning
- **Every time a plugin is modified, bump its patch version** (e.g. `v1.1.0` → `v1.1.1` → `v1.1.2`).
- The version string is typically in the plugin's overlay or panel class (e.g. `"Flipper v1.1.0"`). Update it with each change so the user can confirm they're running the latest.
- Minor feature additions bump the minor version; bug fixes and tweaks bump the patch version.

## Queryable API Quick Reference
Prefer the queryable API over legacy util calls.

## Interaction & Timing Tips
- Never sleep on the RuneLite client thread; use the script thread with `sleep(...)` / `sleepUntil(...)`.
- After interactions, wait for state changes (e.g., `Rs2Bank.isOpen()`, `Rs2Player.isAnimating()`).
- Limit search radius with `.within(...)` to reduce overhead, and cache query results when reusing in a loop.

## Helpful References
- Example templates: `runelite-client/src/main/java/net/runelite/client/plugins/microbot/example/`.
- API examples: `api/*/` directories contain `*ApiExample.java` files for NPCs, tile items, players, and objects.
- Core utilities (legacy but still useful): `microbot/util` (e.g., `Rs2Inventory`, `Rs2Bank`, `Rs2Walker`).

## QuestScript Loop (Quest Helper)
- `QuestScript.run(config, plugin)` sets a 400–1000ms fixed-delay loop; exits early if quest helper is toggled off, not logged in, paused (`super.run()`), or no quest is selected, and waits out player animations.
- Captures the active `QuestStep`, marks when dialogue starts, auto-chooses matching dialogue options, and clicks highlighted widgets (special shop buy for Pirate's Treasure).
- Runs quest-specific logic via `QuestRegistry.getQuest(...).executeCustomLogic()` (Pirate's Treasure gets the plugin injected).
- While incomplete: handles dialogue quirks (Cook's Assistant/Pirate's Treasure), exits cutscenes, clears walk targets when talking, and manages reachability flags.
- Requirement phase: equips required items, warns on missing items (rate-limited), and attempts to acquire them by looting nearby or walking toward the defined point; prioritizes item-on-item detailed steps before other step types.
- Dispatch order: `ConditionalStep` → `NpcStep` → `ObjectStep` → `DigStep` → `PuzzleStep`; per-type handlers choose the correct menu action, manage line-of-sight and walkable tiles, and call `sleepUntil` to wait for movement/animation/interactions before looping.
