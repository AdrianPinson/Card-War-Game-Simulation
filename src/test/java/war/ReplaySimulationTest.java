package war;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static war.GameState.*;

class ReplaySimulationTest {
    @TempDir Path directory;
    private final List<String> names = List.of("Adrian", "Jordan");
    private final Rules rules = new Rules(3,400,true);
    @Test void replayRegeneratesTheSameFullGameAndIsByteRepeatable() throws Exception {
        Path first=directory.resolve("game one.war"),second=directory.resolve("game two.war");
        Result result=Replay.write(first,names,42,rules);
        assertEquals(WarGame.newGame(names,42,rules).runToEnd(),result);
        assertEquals(result,Replay.verify(first));
        Replay.write(second,names,42,rules);
        assertArrayEquals(Files.readAllBytes(first),Files.readAllBytes(second));
        assertEquals(result.rounds(),Files.readAllLines(first).stream().filter(s->s.startsWith("round ")).count());
    }
    @Test void unicodeAndQuotedNamesRoundTripThroughReplay() throws Exception {
        Path trace=directory.resolve("unicode.war");
        Result result=Replay.write(trace,List.of("Zoë \"A\"","東京"),-42,rules);
        assertEquals(result,Replay.verify(trace));
        assertTrue(Reports.game(List.of("Zoë \"A\"","東京"),-42,rules,result).contains("Zo\\u00eb \\\"A\\\""));
        assertEquals("\"line\\u000abreak\\\\end\"",Reports.quote("line\nbreak\\end"));
    }
    @Test void changedSeedAndChangedRoundHashAreRejected() throws Exception {
        Path trace=directory.resolve("trace.war"); Replay.write(trace,names,42,rules);
        String original=Files.readString(trace);
        Files.writeString(trace,original.replace("seed 42\n","seed 43\n"));
        assertThrows(IOException.class,()->Replay.verify(trace));
        Files.writeString(trace,original.replaceFirst("round [0-9a-f]{64}","round "+"0".repeat(64)));
        assertThrows(IOException.class,()->Replay.verify(trace));
    }
    @Test void missingAndAdditionalEventsAreRejected() throws Exception {
        Path trace=directory.resolve("trace.war"); Replay.write(trace,names,42,rules);
        String original=Files.readString(trace);
        Files.writeString(trace,original.replaceFirst("round [0-9a-f]{64}\\n",""));
        assertThrows(IOException.class,()->Replay.verify(trace));
        Files.writeString(trace,original+"extra\n");
        assertThrows(IOException.class,()->Replay.verify(trace));
        Files.writeString(trace,original.substring(0,original.lastIndexOf("end ")));
        assertThrows(IOException.class,()->Replay.verify(trace));
    }
    @Test void unknownVersionsAndInvalidBoundedManifestFieldsAreRejected() throws Exception {
        Path trace=directory.resolve("trace.war"); Replay.write(trace,names,42,rules);
        String original=Files.readString(trace);
        for (String bad : List.of(original.replace(Replay.VERSION,"WAR-REPLAY/99"),
                original.replace("players 2","players 999"),original.replace("cycles true","cycles maybe"),
                original.replace("seed 42","seed abc"),"x".repeat(2050))) {
            Files.writeString(trace,bad); assertThrows(IOException.class,()->Replay.verify(trace));
        }
    }
    @Test void failedReplayGenerationDoesNotReplaceAnExistingFile() throws Exception {
        Path trace=directory.resolve("existing.war");Files.writeString(trace,"keep me");
        assertThrows(CancellationException.class,()->Replay.write(trace,names,42,rules,round->{throw new CancellationException();}));
        assertEquals("keep me",Files.readString(trace));
        try(var paths=Files.list(directory)){assertEquals(1,paths.count());}
    }
    @Test void batchesUseExplicitConsecutiveSeedsAndAccurateAggregateStatistics() {
        Simulation.Report report=Simulation.run(names,rules,-10,30);
        assertEquals(30,report.games());
        for(int i=0;i<30;i++) {
            Simulation.Sample sample=report.samples().get(i);
            assertEquals(i,sample.index());assertEquals(-10+i,sample.seed());
            assertEquals(WarGame.newGame(names,-10+i,rules).runToEnd(),sample.result());
        }
        assertEquals(30,report.wins().stream().mapToInt(Integer::intValue).sum()+report.draws().values().stream().mapToInt(Integer::intValue).sum());
        int[] values=report.samples().stream().mapToInt(s->s.result().rounds()).sorted().toArray();
        assertEquals(Arrays.stream(values).asLongStream().sum(),report.totalRounds());
        assertEquals(report.samples().stream().mapToLong(s->s.result().wars()).sum(),report.totalWars());
        assertEquals((values[14]+values[15])/2.0,report.medianRounds());
        assertEquals(values[28],report.p95Rounds());
        assertEquals(values[0],report.minRounds());assertEquals(values[29],report.maxRounds());
        assertEquals(report.totalRounds()/30.0,report.meanRounds());
        assertThrows(UnsupportedOperationException.class,()->report.samples().clear());
        assertThrows(UnsupportedOperationException.class,()->report.draws().clear());
    }
    @Test void batchRepeatabilityIncludesDrawClassificationsAndOneGamePercentiles() {
        Rules capped=new Rules(3,1,true);
        Simulation.Report report=Simulation.run(names,capped,42,10);
        assertEquals(report,Simulation.run(names,capped,42,10));
        assertEquals(10,report.draws().get(EndReason.ROUND_LIMIT));
        Simulation.Report single=Simulation.run(names,capped,42,1);
        assertEquals(1,single.medianRounds());assertEquals(1,single.p95Rounds());
    }
    @Test void cancellationIsCheckedBetweenGamesAndInsideARunningGame() {
        AtomicInteger completed=new AtomicInteger();
        assertThrows(CancellationException.class,()->Simulation.run(names,rules,42,20,completed::set,()->completed.get()==3));
        assertEquals(3,completed.get());
        AtomicInteger checks=new AtomicInteger();
        assertThrows(CancellationException.class,()->Simulation.run(names,rules,42,20,n->fail("No game should complete"),()->checks.incrementAndGet()>2));
    }
    @Test void rejectsInvalidBatchBoundsAndSeedOverflow() {
        assertThrows(IllegalArgumentException.class,()->Simulation.run(names,rules,42,0));
        assertThrows(IllegalArgumentException.class,()->Simulation.run(names,rules,42,10001));
        assertThrows(IllegalArgumentException.class,()->Simulation.run(names,rules,Long.MAX_VALUE,2));
    }
    @Test void csvRowsContainReproductionInputsAndKeepNumericSeatsSeparateFromNames() throws Exception {
        Simulation.Report report=Simulation.run(List.of("=formula,one","Other"),new Rules(3,1,true),42,4);
        Path csv=directory.resolve("exports/data.csv");Reports.csv(csv,report);
        List<String> lines=Files.readAllLines(csv);
        assertEquals(5,lines.size()); assertTrue(lines.get(0).contains("max_rounds,detect_cycles,status,winner_seat"));
        assertEquals("1,42,2,3,1,true,DRAW,,ROUND_LIMIT,1,0,2",lines.get(1));
        assertFalse(Files.readString(csv).contains("=formula"));
        String summary=Reports.batch(report);
        assertTrue(summary.contains("\"percentOfAllGames\":0.0"));assertTrue(summary.contains("\"ROUND_LIMIT\":4"));
    }
}
