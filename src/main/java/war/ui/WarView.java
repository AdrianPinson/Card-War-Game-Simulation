package war.ui;

import javafx.animation.*;
import javafx.concurrent.Task;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import javafx.util.Duration;
import war.*;
import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Toolkit adapter: games run one round per tick; file/batch work belongs to a cancellable worker. */
public final class WarView implements AutoCloseable {
    private final Window owner;
    private final BorderPane root = new BorderPane();
    private final VBox setup = new VBox(14), felt = new VBox(18), labResults = new VBox(18);
    private final Label message = label("Ready. Deal a seeded game or open a verified replay.", "message");
    private final Label round = label("00", "round-number");
    private final Label mode = label("LIVE TABLE", "eyebrow");
    private final Label totals = label("52 cards · 2 seats · seed 42", "muted");
    private final Label progressText = new Label();
    private final TextArea names = new TextArea("Adrian\nJordan");
    private final TextField seed = new TextField("42"), maxRounds = new TextField("10000"), games = new TextField("1000");
    private final Spinner<Integer> faceDown = new Spinner<>(0, 10, 3);
    private final CheckBox cycles = new CheckBox("Detect repeated states");
    private final ComboBox<String> battle = new ComboBox<>();
    private final ComboBox<String> speed = new ComboBox<>();
    private final Button previous, next, play, beginning, saveReplay, summary, cancel;
    private final ProgressBar progress = new ProgressBar();
    private final HBox controls, workBar;
    private final VBox lab;
    private final Timeline clock;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "war-desktop-worker"); t.setDaemon(true); return t;
    });
    private Task<?> active;
    private TableSession session;
    private Simulation.Report report;
    private boolean rendering, closed;

    public WarView(Window owner) {
        this.owner = owner;
        root.getStyleClass().add("app");
        root.setId("war-root");
        Label brand = label("WAR", "brand");
        VBox wordmark = new VBox(1, label("THE CARD TABLE", "eyebrow"), label("A game of chance. A study in outcomes.", "muted"));
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(20, brand, wordmark, spacer, label("♠  ♥  ♣  ♦", "suits"));
        header.getStyleClass().add("header"); header.setAlignment(Pos.CENTER_LEFT); root.setTop(header);

        names.setId("names"); names.setPrefRowCount(3); names.setWrapText(true);
        seed.setId("seed"); maxRounds.setId("max-rounds"); games.setId("games");
        faceDown.setEditable(false); faceDown.setMaxWidth(Double.MAX_VALUE);
        cycles.setSelected(true); cycles.setWrapText(true);
        setup.getStyleClass().add("setup"); setup.setPrefWidth(245); setup.setMinWidth(225);
        Button deal = button("Deal new game", "deal", this::deal); deal.getStyleClass().add("primary");
        Button open = button("Open replay…", "open-replay", this::chooseReplay);
        setup.getChildren().addAll(label("01 / THE DEAL", "eyebrow"), label("Set the table", "section-title"),
                field("Player names · one per line (2–8)", names), field("Shuffle seed · signed 64-bit integer", seed),
                field("Face-down cards per war", faceDown), field("Round limit · 1–10,000", maxRounds), cycles,
                deal, open, label("A seed reproduces a deal. Rules determine how it unfolds.", "hint"));
        setup.getChildren().stream().filter(n -> n instanceof Button).forEach(n -> ((Button)n).setMaxWidth(Double.MAX_VALUE));
        ScrollPane setupScroll = scroll(setup); setupScroll.setFitToWidth(true); setupScroll.setPrefWidth(265);

        previous = button("← Back", "previous", () -> { pause(); session.previous(); render(); });
        next = button("Next round →", "next", () -> { pause(); session.next(); render(); }); next.getStyleClass().add("primary");
        beginning = button("↶ Start", "beginning", () -> { pause(); session.beginning(); render(); });
        play = button("Auto play", "play", this::togglePlay);
        saveReplay = button("Save full replay…", "save-replay", this::chooseSaveReplay);
        summary = button("Export this round…", "game-summary", this::chooseGameSummary);
        speed.getItems().addAll("Leisurely · 1×", "Quick · 3×", "Fast · 6×"); speed.getSelectionModel().select(1);
        speed.setAccessibleText("Autoplay speed");
        speed.setOnAction(e -> { if (clockRunning()) { pause(); togglePlay(); } });
        clock = new Timeline(new KeyFrame(Duration.millis(160), e -> tick())); clock.setCycleCount(Animation.INDEFINITE);
        controls = new HBox(8, beginning, previous, play, next, speed); controls.setAlignment(Pos.CENTER);
        HBox exports = new HBox(8, saveReplay, summary); exports.setAlignment(Pos.CENTER_RIGHT);
        battle.setId("battle"); battle.setAccessibleText("Battle within current round"); battle.setMaxWidth(Double.MAX_VALUE);
        battle.setOnAction(e -> { if (!rendering) renderFelt(); });
        HBox tableHeading = new HBox(14, new VBox(2, mode, label("Every card tells the story.", "table-heading")), grow(),
                new VBox(0, label("ROUND", "eyebrow"), round));
        tableHeading.setAlignment(Pos.CENTER_LEFT);
        VBox table = new VBox(14, tableHeading, totals, battle, felt, controls, exports);
        table.setPadding(new Insets(22));
        felt.getStyleClass().add("felt"); felt.setAlignment(Pos.CENTER); felt.setMinHeight(360);
        ScrollPane tableScroll = scroll(table);
        lab = buildLab();
        TabPane tabs = new TabPane(); tabs.setId("tabs"); tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(new Tab("The table", tableScroll), new Tab("Simulation lab", scroll(lab)), new Tab("House rules", scroll(rulesView())));
        tabs.getSelectionModel().selectedIndexProperty().addListener((o,a,b) -> pause());
        BorderPane center = new BorderPane(tabs); center.setLeft(setupScroll); root.setCenter(center);

        cancel = button("Cancel", "cancel", () -> { if (active != null) active.cancel(true); });
        progress.setPrefWidth(150); progress.setAccessibleText("Background task progress");
        workBar = new HBox(12, progressText, progress, cancel); workBar.setAlignment(Pos.CENTER_LEFT);
        workBar.setVisible(false); workBar.setManaged(false);
        message.setId("message"); message.setWrapText(true); message.setAccessibleRole(AccessibleRole.TEXT);
        VBox footer = new VBox(8, workBar, message); footer.getStyleClass().add("footer"); root.setBottom(footer);
        deal();
    }
    public Parent root() { return root; }
    TableSession session() { return session; }
    Simulation.Report report() { return report; }
    boolean busy() { return active != null; }
    boolean clockRunning() { return clock != null && clock.getStatus() == Animation.Status.RUNNING; }

    private VBox buildLab() {
        Button run = button("Run simulation", "run-batch", this::runBatch); run.getStyleClass().add("primary");
        Button csv = button("Export games CSV…", "batch-csv", () -> chooseBatchExport(false));
        Button json = button("Export summary JSON…", "batch-json", () -> chooseBatchExport(true));
        csv.disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(() -> report == null, labResults.getChildren()));
        json.disableProperty().bind(csv.disableProperty());
        HBox toolbar = new HBox(12, field("Games · 1–10,000", games), run); toolbar.setAlignment(Pos.BOTTOM_LEFT);
        games.setPrefWidth(140);
        VBox box = new VBox(18, label("02 / THE LONG GAME", "eyebrow"), label("Chance, measured.", "lab-heading"),
                label("Uses the setup fields at left. Consecutive seeds reveal wins, draws, and game length across a repeatable batch.", "hint"),
                toolbar, new HBox(8, csv, json), labResults);
        box.setPadding(new Insets(26));
        labResults.getChildren().add(label("Run your first batch to compare seat outcomes and round distributions.", "empty-state"));
        return box;
    }
    private VBox rulesView() {
        VBox box = new VBox(18, label("03 / HOUSE RULES", "eyebrow"), label("Know the stakes.", "lab-heading"),
                rule("Highest card wins", "Aces are high. Players reveal in seat order; only the highest tied seats continue a war."),
                rule("Short on cards?", "Keep the last card face up. A player with cards places up to the configured face-down count before revealing. A player with none drops out of that war."),
                rule("The pot stays in order", "Cards are collected in table order, with face-down layers before each reveal. Captures wait in the won pile; when the play pile empties, they return in the same order."),
                rule("Draws are explicit", "If every tied contender is exhausted, the pot remains unclaimed and the game is a draw, even if a non-contender has cards. Exact repeated pile states and the round limit also produce draws."),
                rule("Playback and replay", "Back and Start review recorded rounds. Next continues from the latest round. Save full replay regenerates the complete seeded game, including rounds not yet displayed. Open replay verifies every fingerprint before showing the deal."),
                rule("Scope", "The table shows up to 10,000 rounds per game, and the lab supports up to 10,000 games. Use the CLI for higher round limits. Replays check regeneration integrity; they are not signed records."));
        box.setPadding(new Insets(30)); return box;
    }
    private VBox rule(String title, String text) { return new VBox(6, label(title, "section-title"), label(text, "hint")); }
    private TableSession.Config config() {
        return new TableSession.Config(names.getText().lines().toList(), parseSeed(),
                new Rules(faceDown.getValue(), Integer.parseInt(maxRounds.getText().trim()), cycles.isSelected()));
    }
    private long parseSeed() { return Long.parseLong(seed.getText().trim()); }
    private void deal() {
        pause();
        try { session = new TableSession(config()); render(); say("New deal ready. Next round reveals the cards.", false); }
        catch (IllegalArgumentException e) { error(e); }
    }
    private void togglePlay() {
        if (clockRunning()) { pause(); return; }
        if (session == null || !session.canNext() || busy()) return;
        clock.setRate(switch (speed.getSelectionModel().getSelectedIndex()) { case 0 -> .333333; case 2 -> 2; default -> 1; });
        clock.play(); play.setText("Pause");
    }
    private void tick() {
        if (session == null || !session.canNext() || busy()) { pause(); return; }
        session.next(); render(); if (!session.canNext()) pause();
    }
    private void pause() { if (clock != null) clock.stop(); if (play != null) play.setText("Auto play"); }
    private void render() {
        if (session == null) return;
        rendering = true;
        var state = session.frame().state(); var result = state.result();
        round.setText(String.format(Locale.ROOT, "%02d", result.rounds()));
        mode.setText(session.replay() ? "VERIFIED REPLAY" : "LIVE TABLE");
        totals.setText("52 cards · " + state.players().size() + " seats · seed " + session.config().seed()
                + " · " + result.wars() + " wars · largest pot " + result.largestPot()
                + "\n" + session.config().rules().faceDownCards() + " face down · cap " + session.config().rules().maxRounds()
                + " · cycle detection " + (session.config().rules().detectCycles() ? "on" : "off"));
        battle.getItems().clear();
        RoundResult event = session.frame().round();
        if (event != null) {
            for (var b : event.battles()) battle.getItems().add(b.depth() == 0 ? "Opening reveal" : "War " + b.depth() + " · face-down stakes + reveal");
            battle.getSelectionModel().select(0);
        }
        battle.setVisible(event != null); battle.setManaged(event != null);
        rendering = false; renderFelt(); updateControls();
    }
    private void updateControls() {
        boolean absent = session == null;
        previous.setDisable(absent || busy() || session.cursor() == 0);
        beginning.setDisable(previous.isDisabled());
        next.setDisable(absent || busy() || !session.canNext()); play.setDisable(next.isDisabled());
        saveReplay.setDisable(absent || busy()); summary.setDisable(absent || busy());
    }
    private void renderFelt() {
        if (session == null) return;
        var state = session.frame().state(); var result = state.result(); var event = session.frame().round();
        RoundResult.Battle chosen = event == null || battle.getSelectionModel().getSelectedIndex() < 0 ? null
                : event.battles().get(battle.getSelectionModel().getSelectedIndex());
        Map<Integer,RoundResult.Contribution> shown = new HashMap<>();
        if (chosen != null) chosen.contributions().forEach(c -> shown.put(c.seat(), c));
        FlowPane seats = new FlowPane(16, 16); seats.setAlignment(Pos.CENTER);
        for (var p : state.players()) {
            var contribution = shown.get(p.seat());
            Label name = label(p.name(), "seat-name"); name.setMaxWidth(154); name.setEllipsisString("…"); name.setTooltip(new Tooltip(p.name()));
            boolean winner = event != null && Objects.equals(event.winner(), p.seat());
            Label status = label(winner ? "ROUND WINNER" : p.cardCount() == 0 ? "OUT" : "SEAT " + (p.seat() + 1), "seat-status");
            Node card = contribution == null ? cardBack(p.cardCount() == 0 ? "OUT" : "WAR") : face(contribution.faceUp());
            Label down = label(contribution == null ? (event == null ? "Waiting for reveal" : "Not in this battle")
                    : contribution.faceDown().size() + " face down", "stake");
            Label count = label(p.cardCount() + " cards", "card-count");
            Label piles = label(p.playPile().size() + " play · " + p.wonPile().size() + " won", "pile-count");
            VBox seat = new VBox(8, status, name, card, down, count, piles); seat.setAlignment(Pos.CENTER);
            seat.setPrefWidth(165); seat.getStyleClass().add(winner ? "seat-winner" : "seat"); seats.getChildren().add(seat);
        }
        String title = event == null ? "THE DECK IS DEALT" : event.winner() == null ? "UNCLAIMED POT"
                : state.players().get(event.winner()).name() + " takes " + event.awardedCards().size() + " cards";
        String detail = event == null ? "Step into the first round to reveal each hand."
                : "Round " + event.round() + " resolved · " + Math.max(0, event.battles().size() - 1) + " war reveals · piles shown after the full round";
        if (result.status() != GameState.Status.IN_PROGRESS) {
            title = result.status() == GameState.Status.WIN ? state.players().get(result.winner()).name() + " wins the game" : "DRAW · " + readable(result.reason());
            detail += " · " + state.unclaimedPot().size() + " unclaimed cards";
        }
        Label outcome = label(title, "outcome"); outcome.setId("outcome"); outcome.setAlignment(Pos.CENTER);
        felt.getChildren().setAll(label("♠", "table-mark"), seats, outcome, label(detail, "felt-note"));
    }
    private Node face(Card card) {
        String suit = "♠♣♥♦".substring(card.suit().ordinal(), card.suit().ordinal() + 1);
        Label corner = new Label(card.face() + suit); corner.getStyleClass().add("card-corner");
        Label center = label(suit, "card-suit"); center.setMaxWidth(Double.MAX_VALUE); center.setAlignment(Pos.CENTER);
        Label bottom = new Label(card.face() + suit); bottom.getStyleClass().add("card-corner"); bottom.setRotate(180); bottom.setAlignment(Pos.CENTER_RIGHT);
        VBox cardBox = new VBox(2, corner, center, bottom); cardBox.getStyleClass().add("playing-card");
        if (card.suit() == Card.Suit.HEARTS || card.suit() == Card.Suit.DIAMONDS) cardBox.getStyleClass().add("red-card");
        cardBox.setAccessibleText(card.face() + " of " + card.suit().toString().toLowerCase(Locale.ROOT)); return cardBox;
    }
    private Node cardBack(String text) {
        StackPane back = new StackPane(label(text, "back-mark")); back.getStyleClass().add("card-back");
        back.setMinSize(82, 114); back.setMaxSize(82, 114); back.setAccessibleText(text.equals("OUT") ? "Eliminated player" : "Face-down card"); return back;
    }
    private void runBatch() {
        pause();
        try {
            var config = config(); int count = Integer.parseInt(games.getText().trim());
            if (count < 1 || count > 10_000) throw new IllegalArgumentException("Games must be 1–10,000");
            Math.addExact(config.seed(), count - 1L);
            submit(new Task<Simulation.Report>() {
                @Override protected Simulation.Report call() {
                    return Simulation.run(config.names(), config.rules(), config.seed(), count,
                            n -> { updateProgress(n, count); updateMessage(n + " / " + count + " games"); }, this::isCancelled);
                }
            }, completed -> { report = completed; renderReport(); say("Batch complete. Charts and exports use this completed batch's settings.", false); });
        } catch (ArithmeticException e) { say("Consecutive seeds would overflow. Choose a lower starting seed.", true); }
        catch (IllegalArgumentException e) { error(e); }
    }
    private void renderReport() {
        var r = report;
        Label context = label(r.games() + " games · seeds " + r.baseSeed() + " through " + (r.baseSeed() + r.games() - 1)
                + " · " + r.names().size() + " seats · " + r.rules().faceDownCards() + " face down · cap " + r.rules().maxRounds()
                + " · cycles " + (r.rules().detectCycles() ? "on" : "off"), "hint");
        HBox stats = new HBox(20, metric("MEAN ROUNDS", String.format(Locale.ROOT, "%.1f", r.meanRounds())),
                metric("MEDIAN", String.format(Locale.ROOT, "%.1f", r.medianRounds())), metric("95TH PERCENTILE", "" + r.p95Rounds()),
                metric("TOTAL WARS", "" + r.totalWars()));
        CategoryAxis seats = new CategoryAxis(); NumberAxis counts = new NumberAxis(); counts.setLabel("Games"); counts.setTickLabelFormatter(new NumberAxis.DefaultFormatter(counts));
        BarChart<String,Number> wins = new BarChart<>(seats, counts); wins.setTitle("Seat wins and draws"); wins.setId("wins-chart");
        wins.setLegendVisible(false); wins.setAnimated(false); wins.setPrefHeight(300);
        XYChart.Series<String,Number> outcomes = new XYChart.Series<>();
        for (int i = 0; i < r.names().size(); i++) outcomes.getData().add(new XYChart.Data<>((i + 1) + ": " + r.names().get(i), r.wins().get(i)));
        outcomes.getData().add(new XYChart.Data<>("Draw", r.draws().values().stream().mapToInt(Integer::intValue).sum())); wins.getData().add(outcomes);
        CategoryAxis bins = new CategoryAxis(); bins.setLabel("Rounds per game"); NumberAxis frequency = new NumberAxis(); frequency.setLabel("Games");
        BarChart<String,Number> distribution = new BarChart<>(bins, frequency); distribution.setTitle("Game length distribution");
        distribution.setLegendVisible(false); distribution.setAnimated(false); distribution.setPrefHeight(280); distribution.setId("length-chart");
        int width = Math.max(1, (int)Math.ceil((r.maxRounds() - r.minRounds() + 1) / 10.0));
        int binCount = (r.maxRounds() - r.minRounds()) / width + 1; int[] histogram = new int[binCount];
        for (var sample : r.samples()) histogram[(sample.result().rounds() - r.minRounds()) / width]++;
        XYChart.Series<String,Number> lengths = new XYChart.Series<>();
        for (int i = 0; i < binCount; i++) { int low = r.minRounds() + i * width, high = Math.min(r.maxRounds(), low + width - 1);
            lengths.getData().add(new XYChart.Data<>(low == high ? "" + low : low + "–" + high, histogram[i])); }
        distribution.getData().add(lengths);
        String draws = r.draws().isEmpty() ? "Draws: none" : "Draws: " + String.join(" · ", r.draws().entrySet().stream().map(e -> readable(e.getKey()) + " " + e.getValue()).toList());
        labResults.getChildren().setAll(context, stats, wins, label(draws, "hint"), distribution);
    }
    private VBox metric(String title, String value) { return new VBox(5, label(title, "eyebrow"), label(value, "metric")); }
    private void chooseReplay() {
        pause(); File file = chooser(false, "Open and verify replay", "War replay", "*.war");
        if (file != null) openReplay(file.toPath());
    }
    void openReplay(Path path) {
        submit(new Task<TableSession>() { @Override protected TableSession call() throws Exception { return TableSession.open(path); } }, loaded -> {
            session = loaded; render(); say("Replay verified. Next round plays the recorded deal; setup fields are for new games.", false);
        });
    }
    private void chooseSaveReplay() {
        pause(); File file = chooser(true, "Save complete seeded game", "War replay", "*.war");
        if (file != null) saveReplay(file.toPath());
    }
    void saveReplay(Path path) {
        var c = session.config();
        submit(new Task<GameState.Result>() { @Override protected GameState.Result call() throws Exception {
            return Replay.write(path, c.names(), c.seed(), c.rules(), r -> {
                if (isCancelled() || Thread.currentThread().isInterrupted()) throw new CancellationException();
                updateMessage("Recording round " + r.round());
            });
        } }, result -> say("Saved full replay through round " + result.rounds() + ". The displayed table is unchanged.", false));
    }
    private void chooseGameSummary() {
        pause(); File file = chooser(true, "Export displayed round summary", "JSON summary", "*.json");
        if (file == null) return;
        var c = session.config(); String json = Reports.game(c.names(), c.seed(), c.rules(), session.frame().state().result());
        writeJson(file.toPath(), json);
    }
    private void chooseBatchExport(boolean json) {
        if (report == null) return;
        File file = chooser(true, json ? "Export batch summary" : "Export per-game results", json ? "JSON summary" : "CSV results", json ? "*.json" : "*.csv");
        if (file == null) return;
        var completed = report;
        if (json) writeJson(file.toPath(), Reports.batch(completed));
        else submit(new Task<Void>() { @Override protected Void call() throws Exception { Reports.csv(file.toPath(), completed); return null; } }, ignored -> say("Saved completed batch CSV.", false));
    }
    private void writeJson(Path path, String json) {
        submit(new Task<Void>() { @Override protected Void call() throws Exception { Reports.json(path, json); return null; } }, ignored -> say("Saved JSON summary.", false));
    }
    private File chooser(boolean save, String title, String label, String pattern) {
        FileChooser chooser = new FileChooser(); chooser.setTitle(title); chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(label, pattern));
        return save ? chooser.showSaveDialog(owner) : chooser.showOpenDialog(owner);
    }
    private <T> void submit(Task<T> task, Consumer<T> done) {
        if (busy() || closed) return;
        pause(); active = task; setup.setDisable(true); controls.setDisable(true); lab.setDisable(true); battle.setDisable(true);
        updateControls(); workBar.setManaged(true); workBar.setVisible(true);
        progress.progressProperty().bind(task.progressProperty()); progressText.textProperty().bind(task.messageProperty());
        say("Working… You can cancel this task.", false);
        task.setOnSucceeded(e -> { finish(task); if (!closed) done.accept(task.getValue()); });
        task.setOnFailed(e -> { finish(task); if (!closed) error(task.getException()); });
        task.setOnCancelled(e -> { finish(task); if (!closed) say("Task cancelled. The previous table and completed batch remain available.", false); });
        worker.execute(task);
    }
    private void finish(Task<?> task) {
        if (active != task) return;
        active = null; setup.setDisable(false); controls.setDisable(false); lab.setDisable(false); battle.setDisable(false);
        progress.progressProperty().unbind(); progressText.textProperty().unbind(); workBar.setManaged(false); workBar.setVisible(false); updateControls();
    }
    private void error(Throwable e) {
        String text = e instanceof NumberFormatException ? "Enter whole numbers in the seed, round limit, and game count fields."
                : e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        say(text, true);
    }
    private void say(String text, boolean error) { message.setText(text); message.getStyleClass().remove("error"); if (error) message.getStyleClass().add("error"); }
    private static String readable(GameState.EndReason reason) { return reason.toString().toLowerCase(Locale.ROOT).replace('_', ' '); }
    private static Label label(String text, String css) { Label l = new Label(text); l.getStyleClass().add(css); l.setWrapText(true); return l; }
    private static VBox field(String caption, Control control) { Label l = label(caption, "field-label"); l.setLabelFor(control); return new VBox(6, l, control); }
    private static Region grow() { Region r = new Region(); HBox.setHgrow(r, Priority.ALWAYS); return r; }
    private static Button button(String text, String id, Runnable action) { Button b = new Button(text); b.setId(id); b.setOnAction(e -> action.run()); return b; }
    private static ScrollPane scroll(Node node) { ScrollPane s = new ScrollPane(node); s.setFitToWidth(true); s.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); return s; }
    @Override public void close() { closed = true; pause(); if (active != null) active.cancel(true); worker.shutdownNow(); }
}
