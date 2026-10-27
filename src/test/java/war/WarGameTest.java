package war;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static war.Card.Suit.*;
import static war.GameState.*;

class WarGameTest {
    private Card c(Card.Suit suit, int rank) { return new Card(suit, rank); }
    private WarGame game(Rules rules, List<Card>... hands) {
        return WarGame.withHands(java.util.stream.IntStream.range(0, hands.length).mapToObj(i -> "P" + i).toList(), List.of(hands), rules);
    }
    private void conserved(WarGame game, Set<Card> expected) {
        GameState state = game.snapshot();
        List<Card> all = new ArrayList<>(state.unclaimedPot());
        state.players().forEach(p -> { all.addAll(p.playPile()); all.addAll(p.wonPile()); });
        assertEquals(expected.size(), all.size());
        assertEquals(expected, new HashSet<>(all));
    }
    @Test void standardDeckHasAll52DistinctRanksAndSuits() {
        List<Card> cards = Card.standardDeck();
        assertEquals(52, new HashSet<>(cards).size());
        assertEquals(52, cards.stream().map(Card::id).distinct().count());
        for (var suit : Card.Suit.values()) assertEquals(13, cards.stream().filter(c -> c.suit() == suit).count());
        assertEquals("AS", c(SPADES,14).code()); assertEquals("10H", c(HEARTS,10).code());
        assertThrows(IllegalArgumentException.class, () -> c(SPADES,15));
    }
    @Test void shuffleIsRepeatableAndMatchesTheJdkSeededShuffleContract() {
        List<Card> expected = new ArrayList<>(Card.standardDeck());
        Collections.shuffle(expected, new Random(123456789L));
        assertEquals(expected, Card.shuffled(123456789L));
        assertEquals(Card.shuffled(-123), Card.shuffled(-123));
        assertNotEquals(Card.shuffled(42), Card.shuffled(43));
        assertEquals(new HashSet<>(Card.standardDeck()), new HashSet<>(Card.shuffled(42)));
    }
    @ParameterizedTest @ValueSource(ints={2,3,4,5,6,7,8})
    void roundRobinDealsEveryCardWithAtMostOneCardDifference(int count) {
        List<String> names = java.util.stream.IntStream.range(0,count).mapToObj(i -> "P"+i).toList();
        GameState state = WarGame.newGame(names,42,Rules.defaults()).snapshot();
        List<Card> deck = Card.shuffled(42);
        for (int seat = 0; seat < count; seat++) {
            List<Card> expected = new ArrayList<>();
            for (int i = seat; i < 52; i += count) expected.add(deck.get(i));
            assertEquals(expected, state.players().get(seat).playPile());
            assertTrue(state.players().get(seat).wonPile().isEmpty());
        }
        assertEquals(52,state.players().stream().mapToInt(Player::cardCount).sum());
    }
    @Test void winnerCanUseLastCardAndCollectThePotBeforeElimination() {
        WarGame game = game(new Rules(3,1,true), List.of(c(SPADES,14)), List.of(c(HEARTS,13)));
        RoundResult round = game.playRound();
        assertEquals(0,round.winner()); assertEquals(Status.WIN,game.result().status());
        assertEquals(EndReason.LAST_PLAYER,game.result().reason()); // Win takes precedence over round cap.
        assertEquals(List.of(c(SPADES,14),c(HEARTS,13)),round.awardedCards());
        assertEquals(2,game.snapshot().players().get(0).cardCount());
        assertThrows(IllegalStateException.class,game::playRound);
    }
    @Test void lowerRankedThirdPlayerDoesNotJoinInitialWar() {
        WarGame game = game(Rules.defaults(),
            List.of(c(SPADES,14),c(SPADES,2),c(SPADES,3),c(SPADES,4),c(SPADES,10)),
            List.of(c(HEARTS,14),c(HEARTS,2),c(HEARTS,3),c(HEARTS,4),c(HEARTS,9)),
            List.of(c(CLUBS,13),c(CLUBS,14),c(CLUBS,12),c(CLUBS,11),c(CLUBS,10)));
        RoundResult round = game.playRound();
        assertEquals(List.of(0,1),round.battles().get(0).tiedSeats());
        assertEquals(List.of(0,1),round.battles().get(1).contributions().stream().map(RoundResult.Contribution::seat).toList());
        assertEquals(0,round.winner()); assertEquals(11,round.awardedCards().size());
        assertEquals(c(CLUBS,14),game.snapshot().players().get(2).playPile().get(0));
        assertEquals(4,game.snapshot().players().get(2).cardCount());
        // All face-down cards go into the pot by layer, then the face-up cards.
        assertEquals(List.of(c(SPADES,2),c(HEARTS,2),c(SPADES,3),c(HEARTS,3),c(SPADES,4),c(HEARTS,4)),round.awardedCards().subList(3,9));
    }
    @Test void repeatedWarNarrowsToOnlyTheLatestHighestTies() {
        WarGame game = game(new Rules(0,100,true),
            List.of(c(SPADES,13),c(SPADES,12),c(SPADES,14)),
            List.of(c(HEARTS,13),c(HEARTS,12),c(HEARTS,11)),
            List.of(c(CLUBS,13),c(CLUBS,11),c(CLUBS,14)));
        RoundResult round = game.playRound();
        assertEquals(3,round.battles().size()); assertEquals(2,game.result().wars());
        assertEquals(List.of(0,1),round.battles().get(1).tiedSeats());
        assertEquals(List.of(0,1),round.battles().get(2).contributions().stream().map(RoundResult.Contribution::seat).toList());
        assertEquals(0,round.winner()); assertEquals(8,round.awardedCards().size());
        assertEquals(List.of(c(CLUBS,14)),game.snapshot().players().get(2).playPile());
    }
    @Test void shortHandKeepsItsLastCardFaceUpInsteadOfDiscardingThePlayer() {
        WarGame game = game(Rules.defaults(), List.of(c(SPADES,14),c(SPADES,13)),
                List.of(c(HEARTS,14),c(HEARTS,2),c(HEARTS,3),c(HEARTS,4),c(HEARTS,12)));
        RoundResult round = game.playRound();
        assertEquals(0,round.winner()); assertEquals(7,round.awardedCards().size());
        assertEquals(0,round.battles().get(1).contributions().get(0).faceDown().size());
        assertEquals(3,round.battles().get(1).contributions().get(1).faceDown().size());
        assertEquals(Status.WIN,game.result().status());
    }
    @Test void exhaustedContenderCannotContinueButOtherContenderCanWin() {
        WarGame game = game(Rules.defaults(),List.of(c(SPADES,14)),List.of(c(HEARTS,14),c(HEARTS,2)));
        RoundResult round = game.playRound();
        assertEquals(1,round.winner()); assertEquals(3,round.awardedCards().size());
        assertEquals(List.of(1),round.battles().get(1).contributions().stream().map(RoundResult.Contribution::seat).toList());
    }
    @Test void allExhaustedWarEndsWithAnExplicitUnclaimedPot() {
        Set<Card> cards = Set.of(c(SPADES,14),c(HEARTS,14));
        WarGame game = game(Rules.defaults(),List.of(c(SPADES,14)),List.of(c(HEARTS,14)));
        RoundResult round = game.playRound();
        assertEquals(Status.DRAW,game.result().status()); assertEquals(EndReason.WAR_EXHAUSTED,game.result().reason());
        assertNull(round.winner()); assertTrue(round.awardedCards().isEmpty());
        assertEquals(2,game.snapshot().unclaimedPot().size()); conserved(game,cards);
    }
    @Test void unresolvableWarDoesNotArbitrarilyAwardPotToANonContender() {
        WarGame game = game(Rules.defaults(),List.of(c(SPADES,14)),List.of(c(HEARTS,14)),List.of(c(CLUBS,13),c(CLUBS,2)));
        game.playRound();
        assertEquals(EndReason.WAR_EXHAUSTED,game.result().reason());
        assertEquals(3,game.snapshot().unclaimedPot().size());
        assertEquals(1,game.snapshot().players().get(2).cardCount());
    }
    private WarGame cycling(boolean detect, int limit) {
        return game(new Rules(0,limit,detect),List.of(c(SPADES,14),c(SPADES,2)),List.of(c(HEARTS,3),c(HEARTS,13)));
    }
    @Test void cycleDetectionUsesExactOrderedPilesNotCounters() {
        WarGame game = cycling(true,100);
        assertEquals(EndReason.REPEATED_STATE,game.runToEnd().reason());
        assertEquals(5,game.result().rounds());
        assertTrue(game.snapshot().players().get(0).recycles() > 0);
    }
    @Test void roundLimitIsAlwaysAvailableWhenCycleDetectionIsDisabled() {
        WarGame game = cycling(false,12);
        assertEquals(EndReason.ROUND_LIMIT,game.runToEnd().reason()); assertEquals(12,game.result().rounds());
        assertNull(game.result().winner());
    }
    @Test void exactCycleTakesPrecedenceWhenItCoincidesWithTheRoundLimit() {
        assertEquals(EndReason.REPEATED_STATE,cycling(true,5).runToEnd().reason());
    }
    @Test void wonCardsAreRecycledInCaptureOrderWithoutMutatingAnOldSnapshot() {
        WarGame game = cycling(false,20);
        GameState before = game.snapshot();
        game.playRound();game.playRound();RoundResult third=game.playRound();
        assertEquals(c(SPADES,14),third.battles().get(0).contributions().get(0).faceUp());
        assertEquals(c(SPADES,2),third.battles().get(0).contributions().get(1).faceUp());
        assertEquals(1,game.snapshot().players().get(0).recycles());
        assertEquals(List.of(c(SPADES,14),c(SPADES,2)),before.players().get(0).playPile());
        assertThrows(UnsupportedOperationException.class, () -> before.players().clear());
        assertThrows(UnsupportedOperationException.class, () -> before.players().get(0).playPile().clear());
        assertThrows(UnsupportedOperationException.class, () -> third.battles().clear());
        assertThrows(UnsupportedOperationException.class, () -> third.awardedCards().clear());
    }
    @Test void validatesNamesRulesAndPhysicalCardUniqueness() {
        assertThrows(IllegalArgumentException.class, () -> WarGame.newGame(List.of("only"),42,Rules.defaults()));
        assertThrows(IllegalArgumentException.class, () -> WarGame.newGame(List.of("Alex"," alex "),42,Rules.defaults()));
        assertThrows(IllegalArgumentException.class, () -> WarGame.newGame(List.of(" ","B"),42,Rules.defaults()));
        assertThrows(IllegalArgumentException.class, () -> new Rules(-1,100,true));
        assertThrows(IllegalArgumentException.class, () -> new Rules(11,100,true));
        assertThrows(IllegalArgumentException.class, () -> new Rules(3,0,true));
        assertThrows(IllegalArgumentException.class, () -> game(Rules.defaults(),List.of(c(SPADES,2)),List.of(c(SPADES,2))));
        assertEquals(List.of("Alex","B"),WarGame.newGame(List.of(" Alex ","B"),42,Rules.defaults()).snapshot().players().stream().map(Player::name).toList());
    }
    @Test void seededGamesConserveCardsAndProduceIdenticalFullRoundEventsAcrossPlayerCounts() {
        Set<Card> deck = Set.copyOf(Card.standardDeck());
        for (int count = 2; count <= 8; count++) for (long seed = 0; seed < 12; seed++) {
            List<String> names = java.util.stream.IntStream.range(0,count).mapToObj(i -> "P"+i).toList();
            WarGame first=WarGame.newGame(names,seed,new Rules(3,700,true));
            WarGame second=WarGame.newGame(names,seed,new Rules(3,700,true));
            while (first.result().status() == Status.IN_PROGRESS) {
                assertEquals(first.playRound(),second.playRound());
                assertEquals(first.snapshot(),second.snapshot()); conserved(first,deck);
            }
            assertTrue(first.result().rounds() <= 700);
            assertEquals(first.result(),second.result());
            if(first.result().status()==Status.WIN) assertEquals(52,first.snapshot().players().get(first.result().winner()).cardCount());
        }
    }
}
