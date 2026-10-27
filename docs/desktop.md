# The card table

## A five-minute demonstration

1. Run `mvn javafx:run` with Java 17+ and Maven 3.9+ on a graphical desktop. Default deal: Adrian/Jordan, seed 42, three face-down cards, exact cycle detection, 10,000-round cap.
2. Choose **Next round** to reveal the opening cards. The brass border marks the round winner. The card totals and play/won counts are **after the full round**, even while viewing an earlier reveal within that round.
3. Continue to a tie. Use the reveal selector to inspect each war; only the highest tied seats continue. Stake counts show the number of face-down cards, without exposing their faces. The final round award remains displayed while inspecting each reveal.
4. Use **Back**, **Start**, and **Next round** to review history. Forward navigation reuses recorded frames until the latest round; it never applies the same round twice. **Auto play** advances whole rounds, with three speeds. It stops at game end, on tab changes, before a deal/import/export, or when you use manual navigation.
5. In **Simulation lab**, run 1,000 games. This captures the setup fields at the time you click Run. Charts show each seat's wins, all draws, and game lengths in up to ten equal-width integer bins. Median and nearest-rank p95 are computed from the raw samples. Draw reasons remain visible. Export per-game CSV or a JSON summary.

The table is an observer of an automatic War game; there are no strategic card choices. A round includes all repeated ties. It is not a per-card animation or a real-money game.

## Setup and limits

Names are one per line, 2–8 seats, nonblank and distinct ignoring case, at most 40 characters after trimming. Seeds preserve the entire signed 64-bit range. The table accepts 0–10 face-down cards, 1–10,000 maximum rounds, and optional exact cycle detection. The round limit always applies. The lab accepts 1–10,000 games with consecutive nonoverflowing seeds.

The window supports 960×720 or larger. Setup and content scroll independently; eight-seat tables wrap into rows. Controls have text labels, input labels are associated with fields, cards have accessible descriptions, and focus is visible. Keyboard navigation uses JavaFX controls. This is not a complete assistive-technology conformance audit.

## Replays and exports

- **Save full replay** regenerates the entire game using the active table's captured configuration, including rounds not yet shown. It does not alter the displayed round or use edited setup fields. The resulting `.war` file is compatible with the CLI verifier.
- **Open replay** validates the full manifest and every regenerated state/event before replacing the table. Playback begins at the initial deal. Imported names/rules/seed are shown through the active table; setup fields remain a form for a new deal. Failed or cancelled imports keep the existing table.
- The desktop rejects replay manifests with a configured cap over 10,000 before accumulating history, even if that game might finish sooner. Use the CLI for these files. The existing 96 MiB and line-length input limits still apply.
- **Export this round** writes the displayed round's JSON summary, which may describe an in-progress game. A full replay always describes a complete game. The summary's round count makes its scope explicit.
- Batch charts and exports refer to the last successful report. Later setup edits and cancelled/failed new batches leave that report intact. Seeds in JSON are decimal strings.
- Export paths use the engine's completed-file replacement (atomic move where supported). Native save dialogs select destinations. Cancellation is cooperative: a file operation that already completed its replacement can leave a completed file even if cancellation arrives at that instant. No group transaction spans multiple exports.

Verification proves agreement with regeneration, not authorship or authenticity. House rules and replay fingerprints are the same as the CLI.

## Threading and memory

The JavaFX application thread owns the live engine and bounded immutable history. A timeline advances exactly one complete round per tick. File verification/recording and batch simulations use one daemon worker; progress updates use JavaFX Task properties. Cancel checks occur at game/round boundaries, and closing the window interrupts the worker. Controls prevent concurrent table mutations while work is active. UI callbacks publish only successful, complete replay histories/reports.

History retains up to 10,001 snapshots and their round events. Exact cycle detection also retains ordered pile states, so large caps use more memory. The desktop cap avoids exposing the CLI's one-million-round setting to this history viewer. Batch samples retain per-game summaries, not per-round histories. Background export operations capture immutable configuration/results before submission.

## Validation

`mvn clean verify` runs 47 unit/CLI/history cases. `mvn -Pui-tests verify` adds ten JavaFX integration cases with a real display, covering control-driven stepping, history identity, seeded input validation, terminal draws, autoplay, cancellation, chart totals, replay save/load/tamper rejection, tie stages, and the eight-seat compact layout. Linux CI uses Xvfb and captures screenshots as artifacts. Existing standalone-JAR checks continue on all three OSes. The core suite's 84 generated deals are part of one named property test.

Resume wording, after this milestone is merged:

- Built a JavaFX War card-game simulator with seeded multiplayer deals, round history, verified replay playback, and cancellable batch analytics with CSV/JSON exports.
- Separated a deterministic Java rules engine from desktop/CLI adapters; validated tie and exhaustion rules, card conservation, and replay integrity with 57 automated cases, including real JavaFX interactions.

These describe implemented capabilities and automated validation, without invented usage or performance claims. Release packaging remains a separate milestone.
