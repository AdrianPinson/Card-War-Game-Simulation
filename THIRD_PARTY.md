# Third-party components in desktop distributions

## OpenJFX 21.0.8

Desktop packages include the Maven `org.openjfx` modules `javafx-base`, `javafx-graphics`, and `javafx-controls`, including native binaries for the package's OS/CPU. Their Maven parent identifies the license as **GNU General Public License version 2 with the Classpath Exception**. The application uses these libraries without modifying them.

Unmodified upstream license/notice files for these modules are included under `licenses/OpenJFX-21.0.8/`, copied from the OpenJFX 21 update source tag `21.0.8+1`. `SOURCES.json` records each file's exact upstream URL and SHA-256. The files include the GPL text, linking/assembly information, and graphics third-party notices. These dependency notices do not apply an application license to the original coursework.

- Source: https://github.com/openjdk/jfx21u/tree/21.0.8%2B1
- Maven artifacts/source JARs: https://repo.maven.apache.org/maven2/org/openjfx/
- Project: https://openjfx.io/

## Java runtime in bundled packages

CI uses Eclipse Temurin OpenJDK 17 from `actions/setup-java` and creates a reduced runtime with `jlink`. The runtime's `legal/` tree and notices are retained inside the application image. `PROVENANCE.json` records the build Java's vendor/version output. A local build uses the JDK selected by JAVA_HOME and must retain that runtime's notices too.

- Eclipse Temurin: https://adoptium.net/temurin/
- Source information: https://github.com/adoptium/temurin17-binaries

## Build/test tools

Maven plugins, JUnit, Python, and CI actions are used to build or test and are not shipped as application runtime dependencies. Operating-system GTK/XTest libraries are external prerequisites. No remote fonts, stock images, or third-party raster artwork are used; cards and the table are rendered by application code/CSS.

The repository's original application code remains under its owner's existing terms. This milestone does not choose a new application license.
