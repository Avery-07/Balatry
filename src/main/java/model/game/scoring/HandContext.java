package model.game.scoring;

/** The played hand's traits, exposed to joker effects during scoring (e.g. "contains a Pair", card count). */
public record HandContext(
        HandType type,
        int playedCount,
        boolean hasPair,
        boolean hasTwoPair,
        boolean hasThreeOfAKind,
        boolean hasFourOfAKind,
        boolean hasStraight,
        boolean hasFlush) {
}