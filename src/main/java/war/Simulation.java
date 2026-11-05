package war;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import static war.GameState.*;

/** Deterministic batches; each worker owns an independent game and the coordinator owns progress. */
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
        names = validate(names, rules, baseSeed, games, progress, cancelled);
        List<Sample> samples = new ArrayList<>(games);
        for (int i=0;i<games;i++) { samples.add(play(names,rules,baseSeed,i,cancelled)); progress.accept(i+1); }
        checkCancelled(cancelled);
        return summarize(names,rules,baseSeed,samples);
    }
    public static Report runParallel(List<String> names, Rules rules, long baseSeed, int games, int parallelism,
                                     IntConsumer progress, BooleanSupplier cancelled) {
        if (parallelism < 1 || parallelism > 16) throw new IllegalArgumentException("workers must be 1..16");
        if (parallelism == 1) return run(names,rules,baseSeed,games,progress,cancelled);
        final List<String> normalized = validate(names,rules,baseSeed,games,progress,cancelled);
        checkCancelled(cancelled);
        int workers = Math.min(parallelism,games);
        ExecutorService pool = Executors.newFixedThreadPool(workers, task -> {
            Thread thread = new Thread(task,"war-simulation-worker"); thread.setDaemon(true); return thread;
        });
        CompletionService<Sample> completed = new ExecutorCompletionService<>(pool);
        List<Future<Sample>> tasks = new ArrayList<>();
        Sample[] samples = new Sample[games];
        try {
            int next=0, count=0;
            // Only a bounded window is queued; large batches do not allocate all games at once.
            while(next<Math.min(games,workers*2)) { int index=next++; tasks.add(completed.submit(() -> play(normalized,rules,baseSeed,index,cancelled))); }
            while(count<games) {
                checkCancelled(cancelled);
                Future<Sample> future = completed.poll(100,TimeUnit.MILLISECONDS);
                if(future==null) continue;
                Sample sample=future.get(); samples[sample.index()]=sample; progress.accept(++count);
                if(next<games) { int index=next++; tasks.add(completed.submit(() -> play(normalized,rules,baseSeed,index,cancelled))); }
            }
            checkCancelled(cancelled);
            return summarize(normalized,rules,baseSeed,Arrays.asList(samples));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); throw new CancellationException("Simulation interrupted");
        } catch (ExecutionException failed) {
            if(failed.getCause() instanceof RuntimeException exception) throw exception;
            if(failed.getCause() instanceof Error error) throw error;
            throw new IllegalStateException("Simulation worker failed",failed.getCause());
        } finally {
            tasks.forEach(task -> task.cancel(true)); pool.shutdownNow();
            try { if(!pool.awaitTermination(5,TimeUnit.SECONDS)) throw new IllegalStateException("Simulation workers did not stop"); }
            catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        }
    }
    private static List<String> validate(List<String> names,Rules rules,long seed,int games,IntConsumer progress,BooleanSupplier cancelled) {
        if(games<1||games>10000) throw new IllegalArgumentException("games must be 1..10000");
        try { Math.addExact(seed,games-1L); } catch(ArithmeticException overflow) { throw new IllegalArgumentException("Consecutive game seeds would overflow",overflow); }
        Objects.requireNonNull(progress); Objects.requireNonNull(cancelled);
        return WarGame.newGame(names,seed,rules).snapshot().players().stream().map(Player::name).toList();
    }
    private static Sample play(List<String> names,Rules rules,long seed,int index,BooleanSupplier cancelled) {
        checkCancelled(cancelled);
        WarGame game=WarGame.newGame(names,seed+index,rules);
        while(game.result().status()==Status.IN_PROGRESS) { checkCancelled(cancelled); game.playRound(); }
        return new Sample(index,seed+index,game.result());
    }
    private static Report summarize(List<String> names,Rules rules,long seed,List<Sample> samples) {
        int games=samples.size(); int[] wins=new int[names.size()],rounds=new int[games];
        Map<EndReason,Integer> draws=new EnumMap<>(EndReason.class); long totalRounds=0,totalWars=0;
        for(Sample sample:samples) {
            var result=sample.result();
            if(result.status()==Status.WIN)wins[result.winner()]++;else draws.merge(result.reason(),1,Integer::sum);
            rounds[sample.index()]=result.rounds();totalRounds+=result.rounds();totalWars+=result.wars();
        }
        Arrays.sort(rounds);
        double median=(rounds[(games-1)/2]+(double)rounds[games/2])/2;
        return new Report(names,rules,seed,samples,Arrays.stream(wins).boxed().toList(),draws,totalRounds,totalWars,
                rounds[0],rounds[games-1],totalRounds/(double)games,median,rounds[(int)Math.ceil(.95*games)-1]);
    }
    private static void checkCancelled(BooleanSupplier cancelled) {
        if(Thread.currentThread().isInterrupted()||cancelled.getAsBoolean())throw new CancellationException("Simulation cancelled");
    }
}
