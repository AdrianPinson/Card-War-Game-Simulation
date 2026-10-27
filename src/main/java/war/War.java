package war;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;

/** Console adapter. The upcoming graphical app can call WarGame/Simulation without this class. */
public final class War {
    private War() {}
    public static void main(String[] args) {
        int code = run(args, new PrintStream(System.out, true, StandardCharsets.UTF_8),
                new PrintStream(System.err, true, StandardCharsets.UTF_8));
        if (code != 0) System.exit(code);
    }
    public static int run(String[] args, PrintStream out, PrintStream error) {
        try {
            if (args.length == 0 || args[0].equals("help") || Arrays.asList(args).contains("--help")) {
                out.println(HELP); return 0;
            }
            String command = args[0];
            Set<String> allowed = switch(command) {
                case "play" -> Set.of("seed", "players", "names", "face-down", "max-rounds", "no-cycles", "trace", "summary", "verbose");
                case "simulate" -> Set.of("seed", "players", "names", "face-down", "max-rounds", "no-cycles", "games", "csv", "summary");
                case "replay" -> Set.of("file", "summary");
                default -> throw new IllegalArgumentException("Unknown command: " + command);
            };
            Map<String,String> options = options(args, allowed);
            separatePaths(options);
            String summary;
            if (command.equals("replay")) {
                if (!options.containsKey("file")) throw new IllegalArgumentException("replay requires --file");
                summary = "{\"kind\":\"verifiedReplay\",\"result\":" + Reports.result(Replay.verify(Path.of(options.get("file")))) + "}";
            } else {
                List<String> names = names(options);
                long seed = Long.parseLong(options.getOrDefault("seed", "42"));
                Rules rules = new Rules(Integer.parseInt(options.getOrDefault("face-down", "3")),
                        Integer.parseInt(options.getOrDefault("max-rounds", "10000")), !options.containsKey("no-cycles"));
                // Normalize/validate names before any export file is touched.
                names = WarGame.newGame(names, seed, rules).snapshot().players().stream().map(GameState.Player::name).toList();
                if (command.equals("play")) {
                    Consumer<RoundResult> display = options.containsKey("verbose") ? round -> display(out, round) : round -> {};
                    GameState.Result result;
                    if (options.containsKey("trace")) result = Replay.write(Path.of(options.get("trace")), names, seed, rules, display);
                    else {
                        WarGame game = WarGame.newGame(names, seed, rules);
                        while (game.result().status() == GameState.Status.IN_PROGRESS) { RoundResult round = game.playRound(); display.accept(round); }
                        result = game.result();
                    }
                    summary = Reports.game(names, seed, rules, result);
                } else {
                    int games = Integer.parseInt(options.getOrDefault("games", "100"));
                    Simulation.Report report = Simulation.run(names, rules, seed, games);
                    if (options.containsKey("csv")) Reports.csv(Path.of(options.get("csv")), report);
                    summary = Reports.batch(report);
                }
            }
            if (options.containsKey("summary")) Reports.json(Path.of(options.get("summary")), summary);
            out.print(summary + "\n");
            return 0;
        } catch (java.util.concurrent.CancellationException e) { error.println("Cancelled: " + e.getMessage()); return 130; }
        catch (IllegalArgumentException e) { error.println("Invalid input: " + e.getMessage()); return 2; }
        catch (IOException e) { error.println("Replay/export error: " + e.getMessage()); return 1; }
    }
    private static Map<String,String> options(String[] args, Set<String> allowed) {
        Map<String,String> options = new HashMap<>();
        for (int i = 1; i < args.length; i++) {
            if (!args[i].startsWith("--")) throw new IllegalArgumentException("Expected an option, got " + args[i]);
            String key = args[i].substring(2);
            if (!allowed.contains(key)) throw new IllegalArgumentException("Unknown option: --" + key);
            if (options.containsKey(key)) throw new IllegalArgumentException("Duplicate option: --" + key);
            if (key.equals("verbose") || key.equals("no-cycles")) options.put(key, "true");
            else {
                if (++i >= args.length || args[i].startsWith("--")) throw new IllegalArgumentException("Missing value for --" + key);
                options.put(key, args[i]);
            }
        }
        return options;
    }
    private static List<String> names(Map<String,String> options) {
        if (options.containsKey("names")) {
            if (options.containsKey("players")) throw new IllegalArgumentException("Use --names or --players, not both");
            return Arrays.asList(options.get("names").split(",", -1));
        }
        int count = Integer.parseInt(options.getOrDefault("players", "2"));
        if (count < 2 || count > 8) throw new IllegalArgumentException("Use 2..8 players");
        List<String> names = new ArrayList<>();
        for (int i = 1; i <= count; i++) names.add("Player " + i);
        return names;
    }
    private static void separatePaths(Map<String,String> options) throws IOException {
        Set<Path> paths = new HashSet<>();
        for (String key : List.of("file", "trace", "csv", "summary")) if (options.containsKey(key)) {
            Path path = Path.of(options.get(key)).toAbsolutePath().normalize();
            if (Files.exists(path)) path = path.toRealPath();
            if (!paths.add(path)) throw new IllegalArgumentException("Replay/export file paths must differ");
        }
    }
    private static void display(PrintStream out, RoundResult round) {
        out.println("Round " + round.round());
        for (RoundResult.Battle battle : round.battles()) {
            out.print("  " + (battle.depth() == 0 ? "Flip" : "War " + battle.depth()) + ":");
            for (RoundResult.Contribution c : battle.contributions()) out.print(" seat " + c.seat() + " [" + c.faceDown().size() + " down] " + c.faceUp().code());
            out.println();
        }
        out.println(round.winner() == null ? "  No award; " + round.result().reason() : "  Seat " + round.winner() + " collects " + round.awardedCards().size() + " cards");
    }
    private static final String HELP = """
            War Card Game Simulation — core 0.1.0 (Java 17+)
            play     --seed 42 --names Adrian,Jordan --trace target/game.war --summary target/game.json
            simulate --games 100 --players 2 --seed 42 --csv target/games.csv --summary target/batch.json
            replay   --file target/game.war
            Options for play/simulate: --face-down 3 --max-rounds 10000 --no-cycles
            --verbose prints each play round. Without it, stdout is one JSON summary.
            Seats are zero-based. Defaults: 2 players, seed 42, 3 face-down cards, cycle detection on.
            Short hands use the last-card exhaustion variant; see docs/rules.md.
            Replay regenerates every round and checks its fingerprint; it is not a signed audit log.
            """;
}
