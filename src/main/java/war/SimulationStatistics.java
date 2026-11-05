package war;

import java.util.*;
import static war.GameState.EndReason;

/** Approximate binomial uncertainty; administrative round caps are kept separate from terminal draws. */
public final class SimulationStatistics {
    private SimulationStatistics() { }
    private static final double Z = 1.959963984540054;
    public record Interval(int successes, int trials, Double estimate, Double lower95, Double upper95) { }
    public record Seat(int seat, String name, Interval allAttempts, Interval completedOnly) { }
    public record Outcomes(int totalGames, int completedGames, int cappedGames, int terminalDraws, List<Seat> seats) {
        public Outcomes { seats = List.copyOf(seats); }
    }
    public static Interval wilson(int successes, int trials) {
        if(trials<0||successes<0||successes>trials)throw new IllegalArgumentException("Use 0 <= successes <= trials.");
        if(trials==0)return new Interval(0,0,null,null,null);
        double p=successes/(double)trials,z2=Z*Z,denominator=1+z2/trials;
        double center=(p+z2/(2*trials))/denominator;
        double radius=Z*Math.sqrt((p*(1-p)+z2/(4*trials))/trials)/denominator;
        return new Interval(successes,trials,p,Math.max(0,center-radius),Math.min(1,center+radius));
    }
    public static Outcomes summarize(Simulation.Report report) {
        int capped=report.draws().getOrDefault(EndReason.ROUND_LIMIT,0);
        int completed=report.games()-capped;
        int draws=report.draws().entrySet().stream().filter(e->e.getKey()!=EndReason.ROUND_LIMIT).mapToInt(Map.Entry::getValue).sum();
        List<Seat> seats=new ArrayList<>();
        for(int i=0;i<report.names().size();i++)seats.add(new Seat(i,report.names().get(i),wilson(report.wins().get(i),report.games()),wilson(report.wins().get(i),completed)));
        return new Outcomes(report.games(),completed,capped,draws,seats);
    }
}
