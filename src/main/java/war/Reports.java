package war;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import static war.GameState.*;

/** JSON encoding for known report fields only; replay parsing uses its separately versioned format. */
public final class Reports {
    private Reports() {}
    public static String game(List<String> names, long seed, Rules rules, Result result) {
        return "{\"kind\":\"game\",\"engineVersion\":\"war-core-v1\",\"seed\":" + quote(Long.toString(seed))
                + ",\"players\":" + names(names) + ",\"rules\":" + rules(rules) + ",\"result\":" + result(result) + "}";
    }
    public static String batch(Simulation.Report report) {
        StringBuilder json = new StringBuilder("{\"kind\":\"batch\",\"engineVersion\":\"war-core-v1\",\"baseSeed\":")
                .append(quote(Long.toString(report.baseSeed()))).append(",\"games\":").append(report.games())
                .append(",\"rules\":").append(rules(report.rules())).append(",\"wins\":[");
        for (int i = 0; i < report.names().size(); i++) {
            if (i > 0) json.append(',');
            json.append("{\"seat\":").append(i).append(",\"name\":").append(quote(report.names().get(i)))
                .append(",\"count\":").append(report.wins().get(i)).append(",\"percentOfAllGames\":")
                .append(report.wins().get(i) * 100.0 / report.games()).append('}');
        }
        json.append("],\"drawsByReason\":{");
        boolean first = true;
        for (var draw : report.draws().entrySet()) {
            if (!first) json.append(','); first = false;
            json.append(quote(draw.getKey().name())).append(':').append(draw.getValue());
        }
        return json.append("},\"totalRounds\":").append(report.totalRounds())
                .append(",\"totalWars\":").append(report.totalWars())
                .append(",\"minRounds\":").append(report.minRounds()).append(",\"maxRounds\":").append(report.maxRounds())
                .append(",\"meanRounds\":").append(report.meanRounds()).append(",\"medianRounds\":").append(report.medianRounds())
                .append(",\"p95Rounds\":").append(report.p95Rounds()).append('}').toString();
    }
    public static String result(Result result) {
        return "{\"status\":" + quote(result.status().name()) + ",\"winnerSeat\":" + result.winner()
                + ",\"reason\":" + quote(result.reason().name()) + ",\"rounds\":" + result.rounds()
                + ",\"wars\":" + result.wars() + ",\"largestPot\":" + result.largestPot() + "}";
    }
    static String quote(String text) {
        StringBuilder encoded = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            if (c == '"' || c == '\\') encoded.append('\\').append(c);
            else if (c < 32 || c > 126) encoded.append(String.format(Locale.ROOT, "\\u%04x", (int)c));
            else encoded.append(c);
        }
        return encoded.append('"').toString();
    }
    private static String names(List<String> names) { return "[" + String.join(",", names.stream().map(Reports::quote).toList()) + "]"; }
    private static String rules(Rules rules) {
        return "{\"faceDownCards\":" + rules.faceDownCards() + ",\"maxRounds\":" + rules.maxRounds() + ",\"detectCycles\":" + rules.detectCycles() + "}";
    }
    public static void json(Path file, String json) throws IOException { FilesOut.write(file, writer -> { writer.write(json); writer.write('\n'); }); }
    public static void csv(Path file, Simulation.Report report) throws IOException {
        FilesOut.write(file, writer -> {
            writer.write("game,seed,players,face_down_cards,max_rounds,detect_cycles,status,winner_seat,reason,rounds,wars,largest_pot\n");
            for (Simulation.Sample sample : report.samples()) {
                Result r = sample.result();
                writer.write((sample.index() + 1) + "," + sample.seed() + "," + report.names().size() + ","
                        + report.rules().faceDownCards() + "," + report.rules().maxRounds() + "," + report.rules().detectCycles()
                        + "," + r.status() + "," + (r.winner() == null ? "" : r.winner()) + "," + r.reason()
                        + "," + r.rounds() + "," + r.wars() + "," + r.largestPot() + "\n");
            }
        });
    }
}
