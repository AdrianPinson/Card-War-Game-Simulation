package war;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SimulationStatisticsTest {
    @Test void matchesKnownWilsonIntervalsAndBounds() {
        var middle=SimulationStatistics.wilson(50,100);
        assertEquals(0.4038315304,middle.lower95(),1e-9);assertEquals(0.5961684696,middle.upper95(),1e-9);
        var none=SimulationStatistics.wilson(0,10);var all=SimulationStatistics.wilson(10,10);
        assertEquals(0,none.lower95(),1e-12);assertEquals(0.2775327999,none.upper95(),1e-9);
        assertEquals(0.7224672001,all.lower95(),1e-9);assertEquals(1,all.upper95(),1e-12);
        assertNull(SimulationStatistics.wilson(0,0).estimate());assertNull(SimulationStatistics.wilson(0,0).lower95());
        assertThrows(IllegalArgumentException.class,()->SimulationStatistics.wilson(2,1));
    }
    @Test void fullyCappedRunsHaveNoCompletedOnlyRate() {
        var report=Simulation.run(List.of("A","B"),new Rules(3,1,true),42,20);
        var stats=SimulationStatistics.summarize(report);
        assertEquals(20,stats.cappedGames());assertEquals(0,stats.completedGames());assertEquals(0,stats.terminalDraws());
        for(var seat:stats.seats()) {assertNull(seat.completedOnly().estimate());assertEquals(20,seat.allAttempts().trials());}
        String json=Reports.batch(report);
        assertTrue(json.contains("\"completedGames\":0"));assertTrue(json.contains("\"estimate\":null"));
    }
    @Test void classificationsAndDenominatorsCoverEveryAttempt() {
        var report=Simulation.run(List.of("A","B","C"),new Rules(3,300,true),42,100);
        var stats=SimulationStatistics.summarize(report);
        int wins=report.wins().stream().mapToInt(Integer::intValue).sum();
        assertEquals(100,wins+stats.terminalDraws()+stats.cappedGames());
        assertEquals(wins+stats.terminalDraws(),stats.completedGames());
        for(var seat:stats.seats()) {assertEquals(100,seat.allAttempts().trials());assertEquals(stats.completedGames(),seat.completedOnly().trials());}
    }
    @Test void moreTrialsNarrowTheIntervalForTheSameObservedRate() {
        var small=SimulationStatistics.wilson(5,10);var large=SimulationStatistics.wilson(500,1000);
        assertTrue(large.upper95()-large.lower95()<small.upper95()-small.lower95());
    }
}
