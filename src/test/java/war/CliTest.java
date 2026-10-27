package war;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CliTest {
    @TempDir Path directory;
    private record Run(int code,String output,String error) {}
    private Run run(String...args) {
        ByteArrayOutputStream output=new ByteArrayOutputStream(),error=new ByteArrayOutputStream();
        int code=War.run(args,new PrintStream(output,true,StandardCharsets.UTF_8),new PrintStream(error,true,StandardCharsets.UTF_8));
        return new Run(code,output.toString(StandardCharsets.UTF_8),error.toString(StandardCharsets.UTF_8));
    }
    @Test void noArgumentsAndHelpDescribeSupportedCommands() {
        assertEquals(0,run().code());assertTrue(run("help").output().contains("simulate"));
    }
    @Test void playWritesAReplayAndSummaryThenVerifiesIt() throws Exception {
        Path trace=directory.resolve("seed42.war"),summary=directory.resolve("game.json");
        Run play=run("play","--seed","42","--names","Adrian,Jordan","--max-rounds","300","--trace",trace.toString(),"--summary",summary.toString());
        assertEquals(0,play.code(),play.error());assertEquals(play.output(),Files.readString(summary));
        assertTrue(play.output().startsWith("{\"kind\":\"game\""));
        Run replay=run("replay","--file",trace.toString());
        assertEquals(0,replay.code(),replay.error());assertTrue(replay.output().contains("verifiedReplay"));
    }
    @Test void verboseOutputIncludesStagesAndAwards() {
        Run result=run("play","--seed","42","--max-rounds","2","--verbose");
        assertEquals(0,result.code());assertTrue(result.output().contains("Round 1"));assertTrue(result.output().contains("collects"));
    }
    @Test void simulationWritesOneRowPerGameAndAJsonSummary() throws Exception {
        Path csv=directory.resolve("batch.csv"),summary=directory.resolve("batch.json");
        Run result=run("simulate","--games","10","--max-rounds","50","--csv",csv.toString(),"--summary",summary.toString());
        assertEquals(0,result.code(),result.error());assertEquals(11,Files.readAllLines(csv).size());
        assertEquals(result.output(),Files.readString(summary));assertTrue(result.output().contains("\"games\":10"));
    }
    @Test void invalidOptionsAndValuesReturnUsageErrors() {
        for(String[] args:new String[][]{{"unknown"},{"play","--players","0"},{"play","--seed","abc"},
                {"play","--seed"},{"play","--seed","1","--seed","2"},{"play","--games","3"},
                {"simulate","--verbose"},{"play","--players","2","--names","A,B"},{"play","--names","A,"},
                {"replay"},{"play","--face-down","11"}}) {
            Run result=run(args);assertEquals(2,result.code(),String.join(" ",args));assertFalse(result.error().isBlank());
        }
    }
    @Test void malformedReplayAndInvalidOutputPathsReturnIoErrorsWithoutStackTraces() throws Exception {
        Path bad=directory.resolve("bad.war");Files.writeString(bad,"corrupt");
        Run replay=run("replay","--file",bad.toString());assertEquals(1,replay.code());assertFalse(replay.error().contains("Exception"));
        Run output=run("play","--max-rounds","1","--summary",directory.toString());assertEquals(1,output.code());
    }
    @Test void overlappingInputAndOutputPathsAreRejectedBeforeOverwritingReplay() throws Exception {
        Path trace=directory.resolve("same.war");Files.writeString(trace,"keep me");
        Run result=run("replay","--file",trace.toString(),"--summary",trace.toString());
        assertEquals(2,result.code());assertEquals("keep me",Files.readString(trace));
    }
}
