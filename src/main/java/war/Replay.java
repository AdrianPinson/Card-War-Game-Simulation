package war;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import static war.GameState.*;

/** Versioned seed/rules manifest plus exact per-round SHA-256 fingerprints, streamed to disk. */
public final class Replay {
    public static final String VERSION = "WAR-REPLAY/1";
    private Replay() {}
    public static Result write(Path file, List<String> names, long seed, Rules rules) throws IOException {
        return write(file, names, seed, rules, round -> {});
    }
    public static Result write(Path file, List<String> names, long seed, Rules rules,
                               java.util.function.Consumer<RoundResult> observer) throws IOException {
        Objects.requireNonNull(observer);
        WarGame game = WarGame.newGame(names, seed, rules);
        List<String> cleanNames = game.snapshot().players().stream().map(Player::name).toList();
        Result[] result = new Result[1];
        FilesOut.write(file, writer -> {
            line(writer, VERSION); line(writer, "seed " + seed); line(writer, "players " + cleanNames.size());
            line(writer, "faceDown " + rules.faceDownCards()); line(writer, "maxRounds " + rules.maxRounds());
            line(writer, "cycles " + rules.detectCycles());
            for (String name : cleanNames) line(writer, "name " + Base64.getEncoder().encodeToString(name.getBytes(StandardCharsets.UTF_8)));
            line(writer, "initial " + initialFingerprint(game.snapshot(), seed, rules));
            while (game.result().status() == Status.IN_PROGRESS) {
                RoundResult round = game.playRound();
                line(writer, "round " + fingerprint(game.snapshot(), round));
                observer.accept(round);
            }
            line(writer, "end " + fingerprint(game.snapshot(), null));
            result[0] = game.result();
        });
        return result[0];
    }
    /** Regenerate every round; reject changed rules/seeds, missing/trailing events, or tampered hashes. */
    public static Result verify(Path file) throws IOException {
        if (Files.size(file) > 96L * 1024 * 1024) throw new IOException("Replay exceeds the 96 MiB input limit");
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            expect(reader, VERSION);
            long seed = Long.parseLong(value(reader, "seed"));
            int count = Integer.parseInt(value(reader, "players"));
            if (count < 2 || count > 8) throw new IOException("Invalid replay player count");
            int faceDown = Integer.parseInt(value(reader, "faceDown"));
            int maxRounds = Integer.parseInt(value(reader, "maxRounds"));
            String cycles = value(reader, "cycles");
            if (!cycles.equals("true") && !cycles.equals("false")) throw new IOException("Invalid cycle flag");
            Rules rules = new Rules(faceDown, maxRounds, Boolean.parseBoolean(cycles));
            List<String> names = new ArrayList<>();
            for (int i = 0; i < count; i++) names.add(new String(Base64.getDecoder().decode(value(reader, "name")), StandardCharsets.UTF_8));
            WarGame game = WarGame.newGame(names, seed, rules);
            expect(reader, "initial " + initialFingerprint(game.snapshot(), seed, rules));
            while (game.result().status() == Status.IN_PROGRESS) {
                RoundResult round = game.playRound();
                expect(reader, "round " + fingerprint(game.snapshot(), round));
            }
            expect(reader, "end " + fingerprint(game.snapshot(), null));
            if (readLine(reader) != null) throw new IOException("Unexpected trailing replay content");
            return game.result();
        } catch (IllegalArgumentException e) { throw new IOException("Invalid replay manifest: " + e.getMessage(), e); }
    }
    private static String value(BufferedReader reader, String key) throws IOException {
        String line = readLine(reader);
        if (line == null || !line.startsWith(key + " ")) throw new IOException("Expected replay field: " + key);
        return line.substring(key.length() + 1);
    }
    private static void expect(BufferedReader reader, String expected) throws IOException {
        if (!expected.equals(readLine(reader))) throw new IOException("Replay mismatch (wrong version, state, or event sequence)");
    }
    private static void line(BufferedWriter writer, String text) throws IOException { writer.write(text); writer.write('\n'); }
    private static String readLine(BufferedReader reader) throws IOException {
        StringBuilder text = new StringBuilder(); int next;
        while ((next = reader.read()) != -1 && next != '\n') {
            if (text.length() >= 2048) throw new IOException("Replay line exceeds 2048 characters");
            text.append((char)next);
        }
        if (next == -1 && text.isEmpty()) return null;
        if (!text.isEmpty() && text.charAt(text.length() - 1) == '\r') text.setLength(text.length() - 1);
        return text.toString();
    }
    private static String initialFingerprint(GameState state, long seed, Rules rules) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DataOutputStream out = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
                out.writeUTF(VERSION); out.writeLong(seed); out.writeInt(rules.faceDownCards());
                out.writeInt(rules.maxRounds()); out.writeBoolean(rules.detectCycles());
                out.writeUTF(fingerprint(state, null));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException | IOException e) { throw new IllegalStateException("Cannot fingerprint replay manifest", e); }
    }
    static String fingerprint(GameState state, RoundResult round) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DataOutputStream out = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
                out.writeInt(1); // Binary fingerprint schema, not Java object serialization.
                if (round == null) out.writeBoolean(false);
                else {
                    out.writeBoolean(true); out.writeInt(round.round()); out.writeInt(round.winner() == null ? -1 : round.winner());
                    out.writeInt(round.battles().size());
                    for (RoundResult.Battle battle : round.battles()) {
                        out.writeInt(battle.depth()); out.writeInt(battle.contributions().size());
                        for (RoundResult.Contribution c : battle.contributions()) {
                            out.writeInt(c.seat()); cards(out, c.faceDown()); out.writeInt(c.faceUp().id());
                        }
                        out.writeInt(battle.tiedSeats().size()); for (int seat : battle.tiedSeats()) out.writeInt(seat);
                    }
                    cards(out, round.awardedCards());
                }
                out.writeInt(state.players().size());
                for (Player player : state.players()) {
                    out.writeInt(player.seat()); out.writeUTF(player.name()); cards(out, player.playPile()); cards(out, player.wonPile()); out.writeInt(player.recycles());
                }
                cards(out, state.unclaimedPot());
                Result result = state.result();
                out.writeUTF(result.status().name()); out.writeInt(result.winner() == null ? -1 : result.winner()); out.writeUTF(result.reason().name());
                out.writeInt(result.rounds()); out.writeInt(result.wars()); out.writeInt(result.largestPot());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException | IOException e) { throw new IllegalStateException("Cannot fingerprint game", e); }
    }
    private static void cards(DataOutputStream out, List<Card> cards) throws IOException {
        out.writeInt(cards.size()); for (Card card : cards) out.writeInt(card.id());
    }
}
