# Engine v1 house rules

War has configurable conventions. This project defines the following contract so simulations, replay, and the future UI agree.

## Setup and ordinary rounds

- Use 2–8 named players and one standard 52-card deck, without jokers. Ranks are 2 through 14 (Ace high); suits are physical identity, not a tiebreaker.
- Build the canonical deck in suit order spades, clubs, hearts, diamonds, each in ascending rank order. Explicit Fisher–Yates with Java's specified `java.util.Random` sequence shuffles it from the signed 64-bit seed.
- Deal round-robin in seat order. Each player's play pile is next-card-first; the won pile initially is empty. Uneven deals differ by at most one card.
- Every player who owns cards turns one card face up. A unique highest rank captures the whole table pot.
- A capture appends the pot to the winner's won pile in table order. If the play pile is empty when drawing, move the entire won pile into the play pile in capture order, then draw. Recycling is not shuffled and may happen mid-war.

The original coursework already maintained separate play/won piles. This version uses independent deques rather than pointers stored inside shared card objects, and deals in explicitly documented first-dealt-first order.

## War stages

Only players tied for the latest highest face-up rank continue. A lower-ranked player contributes the card already on the table but no additional cards in that war. Losing a later war comparison also removes the player from later stages.

For each continuing player with `n > 0` cards, contribute `min(faceDownCards, n - 1)` face down and then one face up. This reserves the final available card for comparison rather than eliminating a short hand merely because it cannot provide all three face-down cards. A player with zero available cards contributes nothing further. Normal `faceDownCards` is 3; permitted experiments range from 0 to 10.

Pot order is deterministic: initial face-up cards in seat order; for each war, one face-down layer across eligible seats at a time, followed by face-up cards in seat order. The event model groups each player's face-down cards for presentation, but the captured list preserves actual table order.

A unique highest face-up rank wins the accumulated pot. If multiple ranks remain tied, repeat. If only one tied contender still has cards, that player makes its configured short-hand contribution and wins the pot. A player's final face-up card can win; elimination is evaluated after the pot is awarded.

If **no** tied contender can draw another face-up card, the game ends in a `WAR_EXHAUSTED` draw. Cards on the table remain in `unclaimedPot`; they are never dropped, reassigned to an arbitrary player, or accidentally counted twice. This terminal rule also applies if a non-contender still owns cards. It intentionally resolves a previously unbounded tie rather than silently choosing a winner.

## End conditions and counts

After a pot award:

1. If one player owns all cards, finish with `WIN / LAST_PLAYER`.
2. Otherwise, if exact cycle detection is enabled and the ordered play/won piles match a prior round boundary, finish with `DRAW / REPEATED_STATE`.
3. Otherwise, if the configured number of top-level rounds is reached, finish with `DRAW / ROUND_LIMIT`.

Wins take priority over a cap on the same round. A repeated state takes priority over a coincident cap. Cycle keys include every physical card and each pile boundary, but omit counters such as rounds/recycles: counters do not change future card behavior. Cycles are exact state equality, not a comparison of card counts or a probabilistic hash-only shortcut.

`rounds` counts top-level rounds, each including any chain of wars. `wars` counts tie escalation attempts, including the final attempted escalation when all contenders are exhausted. `largestPot` is the maximum cards on the table at a stage boundary. `recycles` counts moves of a nonempty won pile into an empty play pile. No state in a terminal game can be advanced again.

A nonterminal round always makes progress by drawing cards, and an exhausted stage terminates. The round cap provides a finite upper bound even with cycle detection disabled. Every boundary snapshot owns exactly the starting card set across player piles plus any terminal unclaimed pot.

## Custom deals

`WarGame.withHands` supports deterministic fixtures and future custom scenarios: 2–8 players, 2–52 total unique physical cards, supplied next-card-first. It does not add missing standard-deck cards. Seed replay and the public CLI use full standard-deck games; custom-deal replay is outside this milestone.
