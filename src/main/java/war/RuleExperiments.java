package war;

import java.util.*;
import java.util.function.*;

/** Paired experiments use identical deals/seeds; changing rules never changes the initial shuffle. */
public final class RuleExperiments {
    private RuleExperiments() { }
    public record Report(Simulation.Report baseline, Simulation.Report alternative,
                         int changedOutcomes, double meanRoundsDelta) { }
    public static Report compare(List<String> names, Rules baseline, Rules alternative, long seed, int games,
                                 IntConsumer progress, BooleanSupplier cancelled) {
        return compare(names, baseline, alternative, seed, games, 1, progress, cancelled);
    }
    public static Report compare(List<String> names, Rules baseline, Rules alternative, long seed, int games,
                                 int workers, IntConsumer progress, BooleanSupplier cancelled) {
        Objects.requireNonNull(baseline); Objects.requireNonNull(alternative);
        Objects.requireNonNull(progress); Objects.requireNonNull(cancelled);
        // Validate both complete configurations before any work/progress callback.
        WarGame.newGame(names, seed, baseline); WarGame.newGame(names, seed, alternative);
        Simulation.Report a = Simulation.runParallel(names, baseline, seed, games, workers, progress, cancelled);
        Simulation.Report b = Simulation.runParallel(names, alternative, seed, games, workers, n -> progress.accept(games + n), cancelled);
        int changed = 0;
        for (int i=0; i<games; i++) {
            var x = a.samples().get(i).result(); var y = b.samples().get(i).result();
            if (x.status() != y.status() || x.reason() != y.reason() || !Objects.equals(x.winner(), y.winner())) changed++;
        }
        return new Report(a, b, changed, b.meanRounds() - a.meanRounds());
    }
}
