package war;

import java.util.*;

/** A value object: cards never contain mutable list/stack pointers. */
public record Card(Suit suit, int rank) {
    public enum Suit { SPADES, CLUBS, HEARTS, DIAMONDS }
    public Card {
        Objects.requireNonNull(suit, "suit");
        if (rank < 2 || rank > 14) throw new IllegalArgumentException("rank must be 2..14");
    }
    public int id() { return suit.ordinal() * 13 + rank - 2; }
    public String code() { return face() + "SCHD".charAt(suit.ordinal()); }
    public String face() { return switch(rank) { case 11 -> "J"; case 12 -> "Q"; case 13 -> "K"; case 14 -> "A"; default -> Integer.toString(rank); }; }
    @Override public String toString() { return face() + "♠♣♥♦".charAt(suit.ordinal()); }
    public static List<Card> standardDeck() {
        List<Card> deck = new ArrayList<>(52);
        for (Suit suit : Suit.values()) for (int rank = 2; rank <= 14; rank++) deck.add(new Card(suit, rank));
        return List.copyOf(deck);
    }
    /** Explicit Fisher-Yates shuffle with java.util.Random's specified seeded sequence. */
    public static List<Card> shuffled(long seed) {
        List<Card> deck = new ArrayList<>(standardDeck());
        Random random = new Random(seed);
        for (int i = deck.size() - 1; i > 0; i--) Collections.swap(deck, i, random.nextInt(i + 1));
        return List.copyOf(deck);
    }
}
