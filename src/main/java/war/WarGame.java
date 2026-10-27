package war;

import java.util.*;
import static war.GameState.*;
import static war.RoundResult.*;

/** Stateful engine with immutable outward snapshots/events. No console, files, or UI dependencies. */
public final class WarGame {
    private static final class Piles {
        final String name;
        final Deque<Card> play = new ArrayDeque<>(), won = new ArrayDeque<>();
        int recycles;
        Piles(String name, List<Card> cards) { this.name = name; play.addAll(cards); }
        int count() { return play.size() + won.size(); }
        Card draw() {
            if (play.isEmpty()) {
                play.addAll(won); won.clear();
                if (!play.isEmpty()) recycles++;
            }
            return play.removeFirst();
        }
    }
    private final List<Piles> players = new ArrayList<>();
    private final Rules rules;
    private final Set<String> seen = new HashSet<>();
    private final List<Card> unclaimed = new ArrayList<>();
    private final Set<Card> original;
    private Status status = Status.IN_PROGRESS;
    private EndReason reason = EndReason.NONE;
    private Integer winner;
    private int rounds, wars, largestPot;

    public static WarGame newGame(List<String> names, long seed, Rules rules) {
        List<String> checked = names(names);
        List<List<Card>> hands = new ArrayList<>();
        checked.forEach(n -> hands.add(new ArrayList<>()));
        List<Card> deck = Card.shuffled(seed);
        for (int i = 0; i < deck.size(); i++) hands.get(i % checked.size()).add(deck.get(i));
        return new WarGame(checked, hands, rules);
    }
    /** Fixture/custom-deal entry point. Each hand is next-card-first; all cards must be unique. */
    public static WarGame withHands(List<String> names, List<List<Card>> hands, Rules rules) {
        return new WarGame(names(names), hands, rules);
    }
    private static List<String> names(List<String> names) {
        if (names == null || names.size() < 2 || names.size() > 8) throw new IllegalArgumentException("Use 2..8 players");
        Set<String> unique = new HashSet<>();
        List<String> result = new ArrayList<>();
        for (String name : names) {
            if (name == null || name.isBlank() || name.trim().length() > 40) throw new IllegalArgumentException("Names must contain 1..40 characters");
            name = name.trim();
            if (!unique.add(name.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("Player names must be unique (ignoring case)");
            result.add(name);
        }
        return List.copyOf(result);
    }
    private WarGame(List<String> names, List<List<Card>> hands, Rules rules) {
        this.rules = Objects.requireNonNull(rules, "rules");
        if (hands == null || hands.size() != names.size()) throw new IllegalArgumentException("One hand is required per player");
        Set<Card> cards = new HashSet<>();
        for (int i = 0; i < names.size(); i++) {
            List<Card> copy = List.copyOf(Objects.requireNonNull(hands.get(i), "hand"));
            for (Card card : copy) if (!cards.add(card)) throw new IllegalArgumentException("Duplicate card: " + card.code());
            players.add(new Piles(names.get(i), copy));
        }
        if (cards.size() < 2 || cards.size() > 52) throw new IllegalArgumentException("A deal must contain 2..52 unique cards");
        original = Set.copyOf(cards);
        finishIfOnePlayer();
        if (status == Status.IN_PROGRESS && rules.detectCycles()) seen.add(stateKey());
        verifyConservation();
    }
    public Rules rules() { return rules; }
    public Result result() { return new Result(status, winner, reason, rounds, wars, largestPot); }
    public GameState snapshot() {
        List<Player> views = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            Piles p = players.get(i);
            views.add(new Player(i, p.name, List.copyOf(p.play), List.copyOf(p.won), p.recycles));
        }
        return new GameState(views, unclaimed, result());
    }
    public Result runToEnd() {
        while (status == Status.IN_PROGRESS) playRound();
        return result();
    }
    public RoundResult playRound() {
        if (status != Status.IN_PROGRESS) throw new IllegalStateException("The game has already ended");
        rounds++;
        List<Integer> contenders = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) if (players.get(i).count() > 0) contenders.add(i);
        List<Card> pot = new ArrayList<>();
        List<Battle> battles = new ArrayList<>();
        Integer roundWinner = null;
        List<Card> award = List.of();
        int depth = 0;
        while (true) {
            if (depth > 0) wars++;
            List<Integer> eligible = contenders.stream().filter(i -> players.get(i).count() > 0).toList();
            Map<Integer, List<Card>> down = new LinkedHashMap<>();
            Map<Integer, Integer> stakes = new LinkedHashMap<>();
            for (int seat : eligible) {
                down.put(seat, new ArrayList<>());
                stakes.put(seat, depth == 0 ? 0 : Math.min(rules.faceDownCards(), players.get(seat).count() - 1));
            }
            // Pot order matches the table: interleaved face-down layers, then face-up seat order.
            for (int layer = 0; layer < (depth == 0 ? 0 : rules.faceDownCards()); layer++) {
                for (int seat : eligible) if (layer < stakes.get(seat)) {
                    Card card = players.get(seat).draw(); down.get(seat).add(card); pot.add(card);
                }
            }
            List<Contribution> contributions = new ArrayList<>();
            int maximum = -1;
            for (int seat : eligible) {
                Card card = players.get(seat).draw(); pot.add(card);
                contributions.add(new Contribution(seat, down.get(seat), card));
                maximum = Math.max(maximum, card.rank());
            }
            int high = maximum;
            List<Integer> tied = contributions.stream().filter(c -> c.faceUp().rank() == high).map(Contribution::seat).toList();
            battles.add(new Battle(depth, contributions, tied.size() > 1 ? tied : List.of()));
            largestPot = Math.max(largestPot, pot.size());
            if (tied.isEmpty()) {
                unclaimed.addAll(pot);
                end(Status.DRAW, null, EndReason.WAR_EXHAUSTED);
                break;
            }
            if (tied.size() == 1) {
                roundWinner = tied.get(0);
                award = List.copyOf(pot);
                players.get(roundWinner).won.addAll(pot);
                finishIfOnePlayer();
                if (status == Status.IN_PROGRESS && rules.detectCycles() && !seen.add(stateKey())) {
                    end(Status.DRAW, null, EndReason.REPEATED_STATE);
                }
                if (status == Status.IN_PROGRESS && rounds >= rules.maxRounds()) end(Status.DRAW, null, EndReason.ROUND_LIMIT);
                break;
            }
            // Only this stage's highest tied ranks continue. Lower ranks never re-enter the war.
            contenders = tied;
            depth++;
        }
        verifyConservation();
        return new RoundResult(rounds, battles, roundWinner, award, result());
    }
    private void finishIfOnePlayer() {
        List<Integer> alive = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) if (players.get(i).count() > 0) alive.add(i);
        if (alive.size() == 1) end(Status.WIN, alive.get(0), EndReason.LAST_PLAYER);
    }
    private void end(Status status, Integer winner, EndReason reason) { this.status = status; this.winner = winner; this.reason = reason; }
    private String stateKey() {
        StringBuilder key = new StringBuilder();
        for (Piles p : players) for (Deque<Card> pile : List.of(p.play, p.won)) {
            key.append((char)(100 + pile.size()));
            for (Card card : pile) key.append((char)(card.id() + 1));
        }
        return key.toString();
    }
    /** Always on: every boundary owns exactly the original physical cards, without duplicates. */
    private void verifyConservation() {
        List<Card> owned = new ArrayList<>(unclaimed);
        for (Piles p : players) { owned.addAll(p.play); owned.addAll(p.won); }
        if (owned.size() != original.size() || !new HashSet<>(owned).equals(original)) {
            throw new IllegalStateException("Card conservation violated");
        }
    }
}
