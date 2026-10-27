package war;

import java.util.List;

/** Immutable UI/replay boundary. Pile lists are in next-draw/next-recycle order. */
public record GameState(List<Player> players, List<Card> unclaimedPot, Result result) {
    public enum Status { IN_PROGRESS, WIN, DRAW }
    public enum EndReason { NONE, LAST_PLAYER, REPEATED_STATE, ROUND_LIMIT, WAR_EXHAUSTED }
    public record Result(Status status, Integer winner, EndReason reason, int rounds, int wars, int largestPot) {}
    public record Player(int seat, String name, List<Card> playPile, List<Card> wonPile, int recycles) {
        public Player { playPile = List.copyOf(playPile); wonPile = List.copyOf(wonPile); }
        public int cardCount() { return playPile.size() + wonPile.size(); }
    }
    public GameState { players = List.copyOf(players); unclaimedPot = List.copyOf(unclaimedPot); }
}
