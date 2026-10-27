package war.ui;

import war.*;
import java.nio.file.*;
import java.util.*;

/** Headless health check for extracted distributions; graphical tests run separately. */
final class PackageProbe {
    private PackageProbe() {}
    static void verify() throws Exception {
        Class.forName("javafx.scene.control.Control", false, PackageProbe.class.getClassLoader());
        try (var css = Objects.requireNonNull(PackageProbe.class.getResourceAsStream("table.css"), "Missing table stylesheet")) {
            if (!new String(css.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).contains(".playing-card"))
                throw new IllegalStateException("Invalid stylesheet");
        }
        var config = new TableSession.Config(List.of("Adrian", "Jordán"), Long.MAX_VALUE, new Rules(3, 100, true));
        var session = new TableSession(config);
        Path temp = Files.createTempDirectory("war-package-check-");
        try {
            Path replay = temp.resolve("replay.war");
            var result = Replay.write(replay, config.names(), config.seed(), config.rules());
            if (!result.equals(Replay.verify(replay))) throw new IllegalStateException("Replay mismatch");
            var opened = TableSession.open(replay);
            while (session.canNext()) {
                session.next(); opened.next();
                if (!session.frame().equals(opened.frame())) throw new IllegalStateException("History mismatch");
            }
            var batch = Simulation.run(config.names(), config.rules(), -20, 40);
            if (batch.wins().stream().mapToInt(Integer::intValue).sum() + batch.draws().values().stream().mapToInt(Integer::intValue).sum() != 40)
                throw new IllegalStateException("Batch totals mismatch");
            Reports.csv(temp.resolve("games.csv"), batch);
            Reports.json(temp.resolve("summary.json"), Reports.batch(batch));
            if (Files.readAllLines(temp.resolve("games.csv")).size() != 41) throw new IllegalStateException("CSV mismatch");
        } finally {
            try (var paths = Files.walk(temp)) {
                for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
            }
        }
    }
}
