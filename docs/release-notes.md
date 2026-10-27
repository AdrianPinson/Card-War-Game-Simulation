# War 1.0.0 — The Card Table

War evolves the original Java console coursework into a desktop game observer and reproducible simulation tool.

- JavaFX felt table with 2–8 seats, seeded deals, round review, autoplay, and separate views of repeated war reveals/stakes.
- Deterministic engine with immutable snapshots/events, FIFO play/won piles, precise multiplayer tie and short-hand rules, card conservation, exact cycle detection, and explicit draw reasons.
- Full-seed/rule-bound replay manifests with regenerated state/event fingerprint checks, CLI compatibility, and staged import validation.
- Cancellable batch simulation lab with outcome/round distributions, median/p95 statistics, and CSV/JSON export of completed results.
- Java 17+ console JAR, portable desktop ZIPs, and bundled-runtime application ZIPs for Linux, Windows, and macOS, with OS/CPU names, provenance, third-party notices, and checksums.
- 49 unit/CLI/history/package-probe cases and ten real JavaFX integration cases (59 total), plus cross-platform console and extracted distribution checks. Linux additionally opens the extracted packaged GUI.

House rules are documented. The GUI is bounded to 10,000 rounds/history frames per game (plus the initial deal) and batches of 10,000 games. Replay fingerprints check agreement with regeneration; they are not signatures. JavaFX display tests are Linux-based. Packages are unsigned app folders, not installers; macOS builds are not notarized. Linux needs GTK 3/XTest system libraries. Use the ZIP matching your OS and CPU.

This release is prepared as a draft. Publication is separate.
