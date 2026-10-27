package war;

import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.*;
import static war.GameState.*;

/** Deterministic batch runner; no threads or exports are owned by the rules engine. */
public final class Simulation {
    private Simulation() {}
    public record Sample(int index, long seed, Result result) {}
    public record Report(List<String> names, Rules rules, long baseSeed, List<Sample> samples,
                         List<Integer> wins, Map<EndReason,Integer> draws, long totalRounds,
                         long totalWars, int minRounds, int maxRounds, double meanRounds,
                         double medianRounds, int p95Rounds) {
        public Report { names = List.copyOf(names); samples = List.copyOf(samples); wins = List.copyOf(wins); EnumMap<EndReason,Integer> copy = new EnumMap<>(EndReason.class); copy.putAll(draws); draws = Collections.unmodifiableMap(copy); }
        public int games() { return samples.size(); }
    }
    public static Report run(List<String> names, Rules rules, long baseSeed, int games) {
        return run(names, rules, baseSeed, games, n -> {}, () -> false);
    }
    public static Report run(List<String> names, Rules rules, long baseSeed, int games,
                             IntConsumer progress, BooleanSupplier cancelled) {
        if (games < 1 || games > 10_000) throw new IllegalArgumentException("games must be 1..10000");
        try { Math.addExact(baseSeed, games - 1L); }
        catch (ArithmeticException e) { throw new IllegalArgumentException("Consecutive game seeds would overflow", e); }
        Objects.requireNonNull(progress); Objects.requireNonNull(cancelled);
        // Validate configuration before running or invoking progress callbacks.
        WarGame validated = WarGame.newGame(names, baseSeed, rules);
        names = validated.snapshot().players().stream().map(Player::name).toList();
        List<Sample> samples = new ArrayList<>(games);
        int[] wins = new int[names.size()], rounds = new int[games];
        Map<EndReason,Integer> draws = new EnumMap<>(EndReason.class);
        long totalRounds = 0, totalWars = 0;
        for (int i = 0; i < games; i++) {
            checkCancelled(cancelled);
            WarGame game = i == 0 ? validated : WarGame.newGame(names, baseSeed + i, rules);
            while (game.result().status() == Status.IN_PROGRESS) { checkCancelled(cancelled); game.playRound(); }
            Result result = game.result();
            samples.add(new Sample(i, baseSeed + i, result));
            if (result.status() == Status.WIN) wins[result.winner()]++;
            else draws.merge(result.reason(), 1, Integer::sum);
            rounds[i] = result.rounds(); totalRounds += result.rounds(); totalWars += result.wars();
            progress.accept(i + 1);
        }
        Arrays.sort(rounds);
        double median = (rounds[(games - 1) / 2] + (double)rounds[games / 2]) / 2;
        return new Report(names, rules, baseSeed, samples, Arrays.stream(wins).boxed().toList(), draws,
                totalRounds, totalWars, rounds[0], rounds[games - 1], totalRounds / (double)games,
                median, rounds[(int)Math.ceil(.95 * games) - 1]);
    }
    private static void checkCancelled(BooleanSupplier cancelled) {
        if (Thread.currentThread().isInterrupted() || cancelled.getAsBoolean()) throw new CancellationException("Simulation cancelled");
    }
}
