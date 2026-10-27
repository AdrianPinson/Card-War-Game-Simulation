# Core boundaries and tradeoffs

- `Card` is an immutable rank/suit value. It has no linked-list pointer shared between decks.
- `WarGame` owns independent mutable play/won deques for each player and returns immutable `GameState` / `RoundResult` values. A round resolves every repeated war; its ordered battle/contribution events let a future UI animate those stages without reimplementing rules.
- `Rules` validates configurable face-down stakes, a mandatory round bound, and optional exact cycle detection.
- `Simulation` drives the same engine for consecutive seeds, computes aggregate statistics, and accepts progress/cancellation callbacks. It creates no threads; a UI can own background execution and marshal updates to its UI thread.
- `Replay` streams a versioned manifest and per-round fingerprints, then regenerates and verifies them with the same core. This version covers full seeded decks; custom fixture replay is not implemented.
- `Reports` encodes known JSON fields and controlled CSV values; it is not a general-purpose JSON parser. `FilesOut` completes an individual export before replacing the destination.
- `War` validates CLI options and adapts console/files; it is the only executable entry point. The engine never scans stdin, prints output, or writes a file.

## Fixes relative to the original

The original console loop passed all active players into an initial battle, including players whose cards were below the highest tie. Repeated wars could also continue indefinitely after no player could draw. Shared mutable card-node links made ownership harder to reason about; the shuffle was unseeded and swapped each card with a full-deck random index.

The new engine narrows every tie to its current highest ranks, defines short-hand and all-exhausted outcomes, uses explicit seeded Fisher–Yates, and records exact round boundaries. Card conservation is always checked: the same physical set must remain across player piles and a terminal unclaimed pot. A win is decided after the pot is awarded, so a final face-up card can still win.

## Limits

Exact cycle detection retains prior full ordered pile states for one game. It favors transparent correctness over compactness. Large round caps may use considerable memory; the default is 10,000 and the validated maximum is 1,000,000. Batch samples are retained for up to 10,000 games, while detailed replay events are streamed. These are bounded tools for a card simulation, not a measured large-scale distributed simulator.

The game has no player strategy inputs once a deal and rules are fixed. The upcoming graphical UI should distinguish a single-game playback from aggregate batch analysis, report capped/exhaustion/cycle draws, and perform long runs off the UI thread. The public state lists expose the full deck for a simulator; hidden-information competitive multiplayer is outside this scope.

## Validation

Handcrafted cases cover unique wins, highest-only multiplayer ties, narrowing repeated ties, last-card wars, exhausted contenders and unclaimed pots, FIFO recycling, an exact five-round repeated-state fixture, and terminal precedence. Property tests compare immutable full events and conserve all 52 physical cards over 84 seeded deals across 2–8 players. Additional tests cover replay tampering/truncation, seeded batch aggregation and quantiles, progress/cancellation, input errors, and atomic export preservation on failure.

CI also starts the standalone JAR from another working directory on Linux, Windows, and macOS. The smoke script uses Python's independent JSON/CSV readers to check exports, seed precision, row totals, replay reproducibility, corruption rejection, and real process exit codes.

## JavaFX adapter

`war.ui.TableSession` owns a validated configuration and bounded immutable round history. Live forward steps mutate the engine only at the history frontier; verified replays are read-only frame sequences. `Replay.read` stages callbacks during full regeneration and returns immutable metadata only after every fingerprint validates. `TableSession.open` publishes the staged history only on success. The CLI's existing verification API delegates to the same parser.

`WarView` owns controls, a timeline for paced round progression, and one cancellable background executor for batches and files. The engine remains free of toolkit dependencies. Charts consume the same completed `Simulation.Report` as CLI exports. JavaFX dependencies are on the build classpath; the console JAR still starts via `war.War` and loads no desktop classes. The desktop-package profile stages platform-specific JavaFX dependencies; the Python packager adds portable launchers or jlink/jpackage application images. Extracted package checks use the same engine and replay boundaries; Linux also exercises the packaged GUI.
