# Reproduction, replay, and exports

## What must match

Use the same seed, player ordering, rule settings, and engine/replay version. Player names are trimmed and validated before a run. This milestone uses `war-core-v1` / `WAR-REPLAY/1`; a future rule or fingerprint change must introduce a version change or a compatibility implementation rather than quietly accepting old traces.

A seed is a signed Java `long`. `java.util.Random` has a specified deterministic sequence but is not a cryptographic generator; seeds are reproducibility inputs, not secrets. Different seeds are not a promise of different outcomes. Batch seeds are consecutive, not a claim of statistically independent experimental samples.

## `.war` replay format

UTF-8 text, written with LF line endings:

```text
WAR-REPLAY/1
seed 42
players 2
faceDown 3
maxRounds 10000
cycles true
name QWRyaWFu
name Sm9yZGFu
initial <64 hexadecimal SHA-256 characters>
round <64 hexadecimal SHA-256 characters>
...
end <64 hexadecimal SHA-256 characters>
```

Names are Base64-encoded UTF-8 so spaces, quotes, and Unicode do not require a custom string parser. This is a seed manifest plus a per-round verification log, not a Java serialized object graph or a standalone animation recording. To show playback, the UI can recreate the engine from the manifest and step through its immutable events.

Verification rebuilds the initial deal and every subsequent round. Each binary fingerprint encodes its explicit schema version, player identities and ordered piles, recycle counters, unclaimed pot, terminal result/statistics, and (for round lines) all battle stages, face-down/up contributions, latest tie sets, and captured card order. It rejects missing/extra lines, changed hashes, incompatible versions, and invalid manifests. Input is bounded to 96 MiB and 2048 characters per line. CRLF input is accepted for convenience; generated files are byte-repeatable LF.

Fingerprints detect disagreement with regeneration. A person who changes the manifest and recomputes every valid event can create another valid replay; this is not authentication or a digital signature. It does not prove who played or generated a game.

## JSON summary

`play` writes `kind: game`, exact seed string, players, rules, and result. `simulate` writes `kind: batch`, base-seed string, game count, per-seat wins, draw reasons, and round/war statistics. `replay` prints a verified terminal result. All normal summaries use UTF-8/ASCII-compatible JSON with LF termination; `--verbose` adds human-readable round text before the game summary.

Winner seats are zero-based and are `null` for a draw. Percentages use **all games**, including draws, as the denominator. Median uses the middle value or arithmetic mean of the middle pair. P95 is the nearest-rank 95th percentile. Capped games remain in the statistics and are separately labeled; there is no claim that their round count is the unknown duration an uncapped game would take.

Long seeds are JSON decimal **strings**, preserving exactness in JavaScript. Other counts fit their documented numeric bounds.

## CSV rows

One row per game, in run order:

```text
game,seed,players,face_down_cards,max_rounds,detect_cycles,status,winner_seat,reason,rounds,wars,largest_pot
```

`game` is one-based; `winner_seat` is zero-based and blank for a draw. Names are kept in the JSON summary instead of CSV cells; CSV fields are controlled numeric/enum values. When opening in a spreadsheet, import `seed` as text to preserve the full 64-bit value rather than allowing spreadsheet numeric rounding.

Exports are written to a temporary file beside the destination and moved into place only after successful completion. Existing destinations are replaced. The move is atomic when the filesystem supports it; the fallback is a normal replacement move. Export paths and replay input paths must differ. Exporting multiple files is not one filesystem transaction: an I/O failure for the second file does not roll back a completed first export.

CLI exit codes: 0 success/help, 1 replay/export I/O or verification failure, 2 invalid command/options/values, 130 cooperative cancellation. Replay mismatch is a failure, not a successful game draw. Draws are valid simulation outcomes and return 0.
