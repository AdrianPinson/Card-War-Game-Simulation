package war.ui;

import war.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/** UI-owned round history, bounded to 10,000 rounds. No toolkit dependencies. */
public final class TableSession {
    public static final int MAX_ROUNDS = 10_000;
    public record Config(List<String> names, long seed, Rules rules) {
        public Config {
            if (rules.maxRounds() > MAX_ROUNDS) throw new IllegalArgumentException("The table supports up to 10,000 rounds; use the CLI for larger runs");
            names = WarGame.newGame(names, seed, rules).snapshot().players().stream().map(GameState.Player::name).toList();
        }
    }
    public record Frame(GameState state, RoundResult round) {}
    private final Config config;
    private final WarGame game;
    private final List<Frame> frames;
    private int cursor;
    public TableSession(Config config) {
        this.config = config;
        game = WarGame.newGame(config.names(), config.seed(), config.rules());
        frames = new ArrayList<>();
        frames.add(new Frame(game.snapshot(), null));
    }
    private TableSession(Config config, List<Frame> frames) {
        this.config = config; this.frames = frames; game = null;
    }
    public static TableSession open(Path file) throws IOException {
        List<Frame> staged = new ArrayList<>();
        Replay.VerifiedGame verified = Replay.read(file, MAX_ROUNDS, (state, round) -> staged.add(new Frame(state, round)));
        return new TableSession(new Config(verified.names(), verified.seed(), verified.rules()), staged);
    }
    public Config config() { return config; }
    public Frame frame() { return frames.get(cursor); }
    public int cursor() { return cursor; }
    public int recordedRounds() { return frames.size() - 1; }
    public boolean replay() { return game == null; }
    public boolean canNext() {
        return cursor + 1 < frames.size() || game != null && game.result().status() == GameState.Status.IN_PROGRESS;
    }
    public void previous() { if (cursor > 0) cursor--; }
    public void beginning() { cursor = 0; }
    public void next() {
        if (cursor + 1 < frames.size()) cursor++;
        else if (canNext()) {
            RoundResult round = game.playRound();
            frames.add(new Frame(game.snapshot(), round)); cursor++;
        }
    }
}
