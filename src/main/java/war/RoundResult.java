package war;

import java.util.List;

/** One full round, including every repeated tie. Winner seats are zero-based; null means no award. */
public record RoundResult(int round, List<Battle> battles, Integer winner,
                          List<Card> awardedCards, GameState.Result result) {
    public record Contribution(int seat, List<Card> faceDown, Card faceUp) {
        public Contribution { faceDown = List.copyOf(faceDown); }
    }
    public record Battle(int depth, List<Contribution> contributions, List<Integer> tiedSeats) {
        public Battle { contributions = List.copyOf(contributions); tiedSeats = List.copyOf(tiedSeats); }
    }
    public RoundResult { battles = List.copyOf(battles); awardedCards = List.copyOf(awardedCards); }
}
