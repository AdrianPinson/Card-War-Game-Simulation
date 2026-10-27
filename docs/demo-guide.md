# War 1.0.0 — recruiter demo

## What this project demonstrates

A Java card-game simulator evolved from the original console coursework into a deterministic rules engine, JavaFX desktop, replay verifier, and batch analysis tool. The GUI has a felt card-table design with ivory cards and brass accents. Its simulation lab studies game outcomes; it does not change the rules to create a strategy game.

## Two-minute walkthrough

1. Open the package for your OS and CPU. The bundled edition includes Java; the portable edition needs Java 17+. Development launch: `mvn javafx:run`.
2. Keep Adrian/Jordan, seed **42**, three face-down cards, cycle detection on, and the **10,000**-round cap. Click **Next round**, then **Auto play**. Pause and use **Back** to show that history can be reviewed without reapplying an engine mutation.
3. Round **18** of the default deal contains the first war. Use the reveal selector to compare the opening tie with the next reveal, its three face-down stakes per contender, and the ten-card award. Piles show the state after the complete round.
4. Save a full `.war` replay. Open it again: the application regenerates and checks the complete event sequence before accepting it. The imported table starts at the deal; a malformed file leaves the previous table intact. Recording does not require watching every round first.
5. Open **Simulation lab** and run **1,000** games with the same settings. Inspect seat outcomes, draw reasons, median/p95 game length, and the round-count histogram. Export per-game CSV and the JSON summary. A larger run can be cancelled while keeping the previous completed report.

For the default batch, seeds 42–1041, results are **404** first-seat wins, **465** second-seat wins, and **131** exact-state draws. Mean rounds: **651.402**, median: **493**, nearest-rank p95: **1,751**. These are reproducible simulator outputs under the documented house rules, not real-world adoption or performance metrics.

## Explain the engineering

- Immutable cards, snapshots, and round events form the boundary between rules and UI. Each player owns separate play and won deques.
- Seeded Fisher–Yates and deterministic capture/recycle order make CLI, desktop, and replay regeneration agree. Manifests bind the full seed and every rule setting, even when different settings happen to produce the same observed outcome.
- Only the current highest tied seats continue. Short hands reserve their final card face up. All-tied exhaustion, exact repeated states, and round limits are explicit draw reasons. Physical card conservation is checked after every round.
- A JavaFX timeline advances whole rounds. A cancellable background worker owns batch and file work; completed results are published on the UI thread. History navigation reuses immutable recorded frames.
- Distribution checks test extracted ZIPs, individual file hashes, launcher quoting, source provenance, included Java selection, and real packaged GUI controls on Linux. OS-specific JavaFX libraries stay with their matching platform.

## Resume wording

**War Card Game Simulator | Java, JavaFX, Maven, JUnit, GitHub Actions**

Choose two bullets that fit your available space:

- Built a JavaFX multiplayer War simulator with seeded deals, round-history playback, verified replays, and cancellable batch analytics with CSV/JSON exports.
- Implemented a deterministic rules engine with immutable state snapshots, multiplayer tie/exhaustion handling, exact cycle detection, and per-round card-conservation checks.
- Validated the application with **59 automated Java cases**, cross-platform executable checks, and extracted application-package smoke tests; automated Linux, Windows, and macOS release builds with bundled Java runtimes.

The 59 cases comprise 49 unit/CLI/history/package-probe cases and ten real JavaFX integration cases. Generated deals within a property-test loop and package smoke invocations are not extra named JUnit cases. Use the third bullet after the final release PR's CI passes and the change is merged. No resume file is edited by this repository documentation.

## Screenshots and links

- [Card table](images/card-table.png)
- [Simulation lab](images/simulation-lab.png)
- [House rules](rules.md)
- [Desktop behavior and limits](desktop.md)
- [Release/build instructions](release.md)

The GitHub repository is public. Releases are prepared as drafts after the final PR is merged; publishing is a separate decision. Until a release is published, reviewers can build from source and the owner can inspect workflow artifacts.
