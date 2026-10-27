package war.ui;

import war.*;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.chart.BarChart;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises actual JavaFX controls, worker callbacks, layout and snapshots under a display. */
class WarUiIT {
    private static final List<Throwable> errors = new CopyOnWriteArrayList<>();
    private WarView view;
    private Stage stage;
    @TempDir Path temp;
    @BeforeAll static void toolkit() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        Platform.startup(() -> { Thread.currentThread().setUncaughtExceptionHandler((t,e) -> errors.add(e)); Platform.setImplicitExit(false); ready.countDown(); });
        assertTrue(ready.await(15, TimeUnit.SECONDS));
    }
    @BeforeEach void open() throws Exception {
        fx(() -> { stage = new Stage(); view = new WarView(stage); Scene scene = new Scene(view.root(), 1320, 900);
            scene.getStylesheets().add(getClass().getResource("/war/ui/table.css").toExternalForm());
            stage.setScene(scene); stage.show(); view.root().applyCss(); view.root().layout(); return null; });
    }
    @AfterEach void close() throws Exception {
        fx(() -> { view.close(); stage.close(); return null; }); assertTrue(errors.isEmpty(), () -> errors.toString());
    }
    @AfterAll static void shutdown() { Platform.exit(); }
    @Test void steppingBackAndForwardShowsIdenticalEngineFrames() throws Exception {
        fx(() -> { button("next").fire(); var first = view.session().frame(); button("next").fire(); var second = view.session().frame();
            button("previous").fire(); assertSame(first, view.session().frame()); button("next").fire(); assertSame(second, view.session().frame());
            button("beginning").fire(); assertEquals(0, view.session().cursor()); assertTrue(button("previous").isDisabled()); return null; });
    }
    @Test void invalidDealPreservesExistingTableAndCanRecover() throws Exception {
        fx(() -> { var existing = view.session(); text("seed").setText("not a seed"); button("deal").fire(); assertSame(existing, view.session());
            assertTrue(label("message").getStyleClass().contains("error")); text("seed").setText("-9223372036854775808");
            button("deal").fire(); assertEquals(Long.MIN_VALUE, view.session().config().seed());
            ((TextArea)node("names")).setText("A\na"); button("deal").fire(); assertTrue(label("message").getText().contains("unique")); return null; });
    }
    @Test void endOfGameStopsAndExplainsTheDraw() throws Exception {
        fx(() -> { text("max-rounds").setText("1"); button("deal").fire(); button("next").fire();
            assertTrue(button("next").isDisabled()); assertTrue(button("play").isDisabled()); assertTrue(label("outcome").getText().contains("round limit"));
            button("previous").fire(); assertFalse(button("next").isDisabled()); return null; });
    }
    @Test void autoplayAdvancesAndPausesOnTabChange() throws Exception {
        fx(() -> { button("play").fire(); assertTrue(view.clockRunning()); return null; });
        await(() -> view.session().cursor() > 0);
        fx(() -> { ((TabPane)node("tabs")).getSelectionModel().select(1); assertFalse(view.clockRunning()); return null; });
    }
    @Test void batchChartsCountEveryGameAndRetainTheOriginalConfig() throws Exception {
        fx(() -> { tab(1); text("games").setText("1000"); text("max-rounds").setText("10000"); button("run-batch").fire(); return null; });
        await(() -> !view.busy());
        fx(() -> { var r = view.report(); assertNotNull(r); assertEquals(1000, r.games());
            assertEquals(Simulation.run(List.of("Adrian", "Jordan"), new Rules(3,10000,true),42,1000), r);
            for (String id : List.of("wins-chart", "length-chart")) {
                @SuppressWarnings("unchecked") var chart = (BarChart<String,Number>)node(id);
                assertEquals(1000, chart.getData().get(0).getData().stream().mapToInt(d -> d.getYValue().intValue()).sum());
            }
            text("seed").setText("99"); assertEquals(42, r.baseSeed()); assertFalse(button("batch-csv").isDisabled()); text("seed").setText("42"); return null;
        });
        snapshot("simulation-lab");
    }
    @Test void cancellationPreservesPriorResultsAndEnablesControls() throws Exception {
        fx(() -> { tab(1); text("games").setText("2"); text("max-rounds").setText("2"); button("run-batch").fire(); return null; }); await(() -> !view.busy());
        var prior = fx(() -> view.report());
        fx(() -> { text("games").setText("10000"); text("max-rounds").setText("10000"); button("run-batch").fire();
            assertTrue(view.busy()); assertTrue(node("deal").isDisabled()); button("cancel").fire(); return null; });
        await(() -> !view.busy());
        fx(() -> { assertSame(prior, view.report()); assertFalse(node("deal").isDisabled()); assertTrue(label("message").getText().contains("cancelled")); return null; });
    }
    @Test void replaySaveOpenAndTamperRejectionKeepTheTableConsistent() throws Exception {
        Path saved = temp.resolve("unicode replay.war");
        fx(() -> { text("max-rounds").setText("40"); button("deal").fire(); button("next").fire(); view.saveReplay(saved); return null; });
        await(() -> !view.busy()); assertTrue(Files.size(saved) > 0);
        fx(() -> { assertEquals(1, view.session().cursor()); view.openReplay(saved); return null; }); await(() -> !view.busy());
        var replay = fx(() -> view.session()); assertTrue(replay.replay()); assertEquals(0, replay.cursor());
        Files.writeString(saved, Files.readString(saved).replace("seed 42", "seed 43"));
        fx(() -> { view.openReplay(saved); return null; }); await(() -> !view.busy());
        fx(() -> { assertSame(replay, view.session()); assertTrue(label("message").getStyleClass().contains("error")); return null; });
    }
    @Test void tieBattleSelectorDisplaysSeparateRevealsAndStakes() throws Exception {
        fx(() -> {
            for (int i = 0; i < 100 && (view.session().frame().round() == null || view.session().frame().round().battles().size() < 2); i++) button("next").fire();
            var event = view.session().frame().round(); assertTrue(event.battles().size() >= 2);
            ComboBox<?> battles = (ComboBox<?>)node("battle"); assertEquals(event.battles().size(), battles.getItems().size());
            battles.getSelectionModel().select(1); assertTrue(view.root().lookupAll(".stake").stream().anyMatch(n -> ((Label)n).getText().equals("3 face down"))); return null;
        });
        snapshot("card-table");
    }
    @Test void eightSeatsRemainReachableInCompactScrollableLayout() throws Exception {
        fx(() -> { stage.setWidth(960); stage.setHeight(800); ((TextArea)node("names")).setText("A\nB\nC\nD\nE\nF\nG\nH");
            button("deal").fire(); button("next").fire(); view.root().applyCss(); view.root().layout();
            assertEquals(8, view.root().lookupAll(".seat-name").size());
            assertTrue(view.root().lookupAll(".seat-name").stream().allMatch(n -> n.getBoundsInParent().getWidth() > 0)); return null; });
        snapshot("eight-seats");
    }
    @Test void badBatchBoundsDoNotStartAWorker() throws Exception {
        fx(() -> { tab(1); text("games").setText("10001"); button("run-batch").fire(); assertFalse(view.busy()); assertNull(view.report());
            text("games").setText("2"); text("seed").setText(Long.toString(Long.MAX_VALUE)); button("run-batch").fire();
            assertFalse(view.busy()); assertTrue(label("message").getText().contains("overflow")); return null; });
    }
    private Node node(String id) { Node n = view.root().lookup("#" + id); assertNotNull(n, id); return n; }
    private Button button(String id) { return (Button)node(id); }
    private TextField text(String id) { return (TextField)node(id); }
    private Label label(String id) { return (Label)node(id); }
    private void tab(int n) { ((TabPane)node("tabs")).getSelectionModel().select(n); view.root().applyCss(); view.root().layout(); }
    private void await(Callable<Boolean> ready) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(25);
        while (System.nanoTime() < end) { if (fx(ready)) return; Thread.sleep(30); }
        fail("Timed out waiting for UI: " + fx(() -> label("message").getText()));
    }
    private void snapshot(String name) throws Exception {
        fx(() -> { view.root().applyCss(); view.root().layout(); WritableImage image = view.root().snapshot(null,null);
            BufferedImage buffered = new BufferedImage((int)image.getWidth(), (int)image.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int y=0;y<buffered.getHeight();y++) for (int x=0;x<buffered.getWidth();x++) buffered.setRGB(x,y,image.getPixelReader().getArgb(x,y));
            Path out = Path.of("target/ui-screenshots"); Files.createDirectories(out); ImageIO.write(buffered,"png",out.resolve(name + ".png").toFile()); return null; });
    }
    private static <T> T fx(Callable<T> action) throws Exception { FutureTask<T> task = new FutureTask<>(action); Platform.runLater(task); return task.get(30,TimeUnit.SECONDS); }
}
