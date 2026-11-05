package war;

import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ParallelSimulationTest {
    @TempDir Path temp;
    @Test void workerCountsProduceTheSameOrderedReportsAndExports() throws Exception {
        var names=List.of("A","B","C");var rules=new Rules(3,300,true);
        var reference=Simulation.run(names,rules,42,60);
        Reports.csv(temp.resolve("reference.csv"),reference);
        for(int workers:List.of(1,2,4,16)) {
            List<Integer> progress=new ArrayList<>();Thread coordinator=Thread.currentThread();
            var result=Simulation.runParallel(names,rules,42,60,workers,n->{assertSame(coordinator,Thread.currentThread());progress.add(n);},()->false);
            assertEquals(reference,result);assertEquals(Reports.batch(reference),Reports.batch(result));
            assertEquals(java.util.stream.IntStream.rangeClosed(1,60).boxed().toList(),progress);
            Reports.csv(temp.resolve("parallel.csv"),result);
            assertArrayEquals(Files.readAllBytes(temp.resolve("reference.csv")),Files.readAllBytes(temp.resolve("parallel.csv")));
        }
    }
    @Test void cancellationAndCallbackFailuresStopOwnedWorkers() {
        AtomicBoolean cancelled=new AtomicBoolean();
        assertThrows(CancellationException.class,()->Simulation.runParallel(List.of("A","B"),Rules.defaults(),42,1000,4,
                n->{if(n==2)cancelled.set(true);},cancelled::get));
        assertThrows(IllegalStateException.class,()->Simulation.runParallel(List.of("A","B"),Rules.defaults(),42,1000,4,
                n->{throw new IllegalStateException("injected progress failure");},()->false));
        assertTrue(Thread.getAllStackTraces().keySet().stream().noneMatch(t->t.isAlive()&&t.getName().equals("war-simulation-worker")));
    }
    @Test void validatesBoundsBeforeWorkAndSupportsExtremeSeeds() {
        for(int workers:List.of(0,17))assertThrows(IllegalArgumentException.class,()->Simulation.runParallel(List.of("A","B"),Rules.defaults(),42,1,workers,n->fail(),()->false));
        assertThrows(IllegalArgumentException.class,()->Simulation.runParallel(List.of("A","B"),Rules.defaults(),Long.MAX_VALUE,2,2,n->fail(),()->false));
        for(long seed:List.of(Long.MIN_VALUE,Long.MAX_VALUE-4)) {
            var result=Simulation.runParallel(List.of("A","B"),new Rules(0,20,true),seed,5,3,n->{},()->false);
            assertEquals(Simulation.run(List.of("A","B"),new Rules(0,20,true),seed,5),result);
        }
    }
    @Test void parallelPairedExperimentsMatchSequentialExperiments() {
        var a=new Rules(0,100,true);var b=new Rules(3,100,true);
        assertEquals(RuleExperiments.compare(List.of("A","B"),a,b,42,20,n->{},()->false),
                RuleExperiments.compare(List.of("A","B"),a,b,42,20,4,n->{},()->false));
    }
}
