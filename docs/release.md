# Build, validate, and package War 1.0.0

## Source builds

Java 17+ and Maven 3.9+:

```sh
mvn clean verify
java -jar target/war-simulation.jar help
mvn javafx:run
```

The console JAR contains the application classes and CSS but starts at `war.War`; its CLI needs no external dependencies. The desktop launcher is `war.ui.DesktopLauncher` and needs the matching JavaFX libraries. `mvn -Pui-tests verify` adds real JavaFX integration tests and requires a display; Linux CI uses Xvfb.

## Distributions

Build on the target OS and CPU with a full **JDK 17+** (including `jlink` and `jpackage`), Maven, and Python 3.12+:

```sh
python scripts/package_desktop.py
```

The script builds with `-Pdesktop-package`, stages only runtime dependencies, and produces two ZIPs under `target/releases/`, plus `SHA256SUMS-<os>-<cpu>.txt`:

| Suffix | Launch | Java requirement |
| --- | --- | --- |
| `-portable.zip` | `start-war.cmd` or `start-war.sh` | Installed Java 17+ |
| `-bundled.zip` | WarCardTable native application launcher | Included reduced Java runtime |

Names include version, OS, and CPU, such as `war-1.0.0-linux-x64-bundled.zip`. CI builds Linux x64, Windows x64, and the macOS runner's architecture, recorded explicitly in filenames/provenance. Do not use a package for a different OS or CPU. These are application folders, not installers. Archives preserve executable modes and macOS runtime links; use an extractor that supports them.

For an already-built `-Pdesktop-package` tree, add `--skip-build`. Add `--portable-only` to skip `jlink`/`jpackage`, or `--verify-gui` to exercise the extracted bundled GUI under a real display or Xvfb. The default checks are headless package-health checks; they do not open the graphical toolkit. Linux CI additionally uses `--verify-gui`.

`JAVA_HOME` selects build/runtime tools. Native launchers use their bundled Java even if JAVA_HOME is invalid; this is checked after extraction. Portable launchers quote absolute application paths and leave the caller's working directory unchanged. Exports use native destination dialogs, not a default mutable data directory.

## Validation performed by the packager

1. Check application class/resource entries and the three JavaFX platform-specific JARs.
2. Stage docs, third-party license texts, provenance, and a SHA-256 manifest of files. Use completed temporary ZIP replacement and verify ZIP CRCs.
3. Extract each ZIP into a directory containing spaces and verify all file hashes and metadata.
4. Launch from a separate working directory using the real portable script/native executable, with an absolute report path. The health check loads JavaFX class/CSS resources without starting the toolkit, verifies a replay, compares live/imported histories, and runs batch/CSV/JSON operations.
5. Run the extracted console JAR and parse its JSON result. Confirm native Java lives inside the extracted application, with JAVA_HOME deliberately pointing elsewhere.
6. With `--verify-gui`, start the extracted native GUI, apply its CSS, reveal two cards, and enforce a one-round draw using the real controls. Exit only after the GUI writes its successful check report.

Health-check flags are intended for release validation: `--smoke-test --report <path>`, and the display-requiring `--smoke-ui --report <path>`. `--version` prints the JAR's manifest version. Normal launch needs no flags.

## Provenance and notices

Each distribution records version, OS/CPU, source revision, dirty-tree status, build Java version, JavaFX version, and whether Java is included in `PROVENANCE.json`. On PR CI, GITHUB_SHA identifies GitHub's tested candidate merge commit. A local dirty build is explicitly marked. Checksums establish file integrity, not code signing or publisher identity; archives are not guaranteed byte-for-byte reproducible across build times/toolchains.

Bundled runtimes retain their `legal/` notices. `licenses/` and `THIRD_PARTY.md` include OpenJFX notices/source locations. No application license is invented for the original coursework. Windows/macOS packages are unsigned and not notarized. Linux still needs operating-system GTK 3 and XTest libraries; these are not supplied by a Java runtime image.

## CI and draft release

Core tests plus the actual console JAR run on Linux, Windows, and macOS. Real JavaFX integration checks run on Linux. After those pass, PR and main workflows build and extract-test both distributions on all three OSes; Linux also checks the packaged GUI. Artifacts retain the ZIPs, per-platform checksums, and smoke reports for seven days.

Only a successful **main push** may prepare a `v1.0.0` **draft** release with the six platform ZIPs and checksum files. An existing versioned release is left unchanged. PR CI never creates a release, and this workflow never publishes one or changes repository visibility. Review and publication remain explicit owner decisions.
