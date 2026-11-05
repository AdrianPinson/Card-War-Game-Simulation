package war;

import java.util.*;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RuleExperimentsTest {
    @Test void identicalRulesHaveIdenticalPerSeedOutcomes() {
        Rules rules = new Rules(3, 300, true); var progress = new ArrayList<Integer>();
        var report = RuleExperiments.compare(List.of("A", "B"), rules, rules, 42, 12, progress::add, () -> false);
        assertEquals(report.baseline(), report.alternative()); assertEquals(0, report.changedOutcomes());
        assertEquals(0, report.meanRoundsDelta()); assertEquals(24, progress.get(progress.size()-1));
    }
    @Test void eachVariantMatchesAnIndependentRunAndPreservesFullRangeSeeds() {
        Rules a = new Rules(0, 300, true), b = new Rules(3, 300, true);
        var report = RuleExperiments.compare(List.of("A", "B", "C"), a, b, Long.MAX_VALUE - 9, 10, n -> {}, () -> false);
        assertEquals(Simulation.run(List.of("A", "B", "C"), a, Long.MAX_VALUE-9, 10), report.baseline());
        assertEquals(Simulation.run(List.of("A", "B", "C"), b, Long.MAX_VALUE-9, 10), report.alternative());
        assertEquals(Long.MAX_VALUE, report.alternative().samples().get(9).seed());
    }
    @Test void cancellationAndInvalidSeedRangesDoNotProduceAReport() {
        assertThrows(CancellationException.class, () -> RuleExperiments.compare(List.of("A", "B"), Rules.defaults(), Rules.defaults(), 42, 10, n -> {}, () -> true));
        assertThrows(IllegalArgumentException.class, () -> RuleExperiments.compare(List.of("A", "B"), Rules.defaults(), Rules.defaults(), Long.MAX_VALUE, 2, n -> fail(), () -> false));
    }
    @Test void cliExportsBothLabeledVariants() throws Exception {
        var out = new java.io.ByteArrayOutputStream();
        assertEquals(0, War.run(new String[]{"compare", "--games", "3", "--max-rounds", "100", "--face-down", "0", "--against-face-down", "3"}, new java.io.PrintStream(out), System.err));
        String json = out.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(json.contains("\"kind\":\"ruleComparison\"")); assertTrue(json.contains("\"baseline\":"));
        assertTrue(json.contains("\"alternative\":")); assertTrue(json.contains("\"changedOutcomes\":"));
    }
}
