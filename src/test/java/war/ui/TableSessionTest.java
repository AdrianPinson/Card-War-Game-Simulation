package war.ui;

import war.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TableSessionTest {
    @TempDir Path temp;
    private TableSession.Config config() { return new TableSession.Config(List.of(" A ", "B"), Long.MAX_VALUE, new Rules(3, 100, true)); }
    @Test void historyNavigationNeverReplaysAMutation() {
        var session = new TableSession(config());
        session.next(); var first = session.frame(); session.next(); var second = session.frame();
        session.previous(); assertSame(first, session.frame()); session.beginning(); assertEquals(0, session.cursor());
        session.next(); session.next(); assertSame(second, session.frame()); assertEquals(2, session.recordedRounds());
        assertEquals(List.of("A", "B"), session.config().names());
    }
    @Test void completedHistoryCanBeReviewedWithoutAdvancingTheEngine() {
        var session = new TableSession(new TableSession.Config(List.of("A", "B"), 42, new Rules(3, 2, true)));
        while (session.canNext()) session.next(); var last = session.frame();
        session.next(); assertSame(last, session.frame()); session.beginning();
        while (session.canNext()) session.next(); assertSame(last, session.frame());
        assertEquals(GameState.EndReason.ROUND_LIMIT, last.state().result().reason());
    }
    @Test void replayHistoryContainsEveryVerifiedStateAndExactSettings() throws Exception {
        var c = config(); Path file = temp.resolve("saved.war"); Replay.write(file, c.names(), c.seed(), c.rules());
        var loaded = TableSession.open(file); var live = new TableSession(c);
        assertTrue(loaded.replay()); assertEquals(c, loaded.config()); assertEquals(0, loaded.cursor());
        do { assertEquals(live.frame(), loaded.frame()); if (!loaded.canNext()) break; loaded.next(); live.next(); } while (true);
        assertFalse(live.canNext()); assertEquals(loaded.cursor(), loaded.recordedRounds());
    }
    @Test void corruptAndTruncatedReplaysAreRejected() throws Exception {
        Path file = temp.resolve("bad.war"); Replay.write(file, config().names(), config().seed(), config().rules());
        String text = Files.readString(file); Files.writeString(file, text.replace("seed " + Long.MAX_VALUE, "seed 1"));
        assertThrows(java.io.IOException.class, () -> TableSession.open(file));
        Files.writeString(file, text.substring(0, text.lastIndexOf("end ")));
        assertThrows(java.io.IOException.class, () -> TableSession.open(file));
    }
    @Test void viewerRejectsHugeRoundCapsBeforeAccumulatingHistory() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new TableSession.Config(List.of("A", "B"), 42, new Rules(3, 10001, true)));
        Path file = temp.resolve("too-large.war"); Replay.write(file, List.of("A", "B"), 42, new Rules(3, 10001, true));
        assertTrue(assertThrows(java.io.IOException.class, () -> TableSession.open(file)).getMessage().contains("viewer"));
    }
    @Test void sessionMatchesIndependentEngineAcrossEightSeats() {
        var c = new TableSession.Config(List.of("A", "B", "C", "D", "E", "F", "G", "H"), -42, new Rules(0, 100, true));
        var session = new TableSession(c); var engine = WarGame.newGame(c.names(), c.seed(), c.rules());
        while (session.canNext()) { session.next(); assertEquals(engine.playRound(), session.frame().round()); assertEquals(engine.snapshot(), session.frame().state()); }
    }
}
