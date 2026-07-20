package model.game.scoring;

import model.items.DeckCard;
import model.items.DeckCard.Rank;
import model.items.DeckCard.Suit;
import model.modifiers.Enhancement;

import java.util.List;

/** Run-as-main harness verifying {@link HandEvaluator} classification and scoring-card selection. */
public final class HandEvaluatorTests {

    private static int failures = 0;

    public static void main(String[] args) {
        HandEvaluator vanilla = new HandEvaluator();

        check("high card", vanilla, hand(card(Rank.ACE, Suit.SPADES), card(Rank.KING, Suit.HEARTS),
                        card(Rank.THREE, Suit.DIAMONDS), card(Rank.SEVEN, Suit.CLUBS), card(Rank.NINE, Suit.SPADES)),
                HandType.HIGH_CARD, 1);

        check("pair scores 2 of 5", vanilla, hand(card(Rank.KING, Suit.SPADES), card(Rank.KING, Suit.HEARTS),
                        card(Rank.THREE, Suit.DIAMONDS), card(Rank.SEVEN, Suit.CLUBS), card(Rank.NINE, Suit.SPADES)),
                HandType.PAIR, 2);

        check("two pair scores 4 of 5", vanilla, hand(card(Rank.KING, Suit.SPADES), card(Rank.KING, Suit.HEARTS),
                        card(Rank.THREE, Suit.DIAMONDS), card(Rank.THREE, Suit.CLUBS), card(Rank.NINE, Suit.SPADES)),
                HandType.TWO_PAIR, 4);

        check("three of a kind scores 3 of 5", vanilla, hand(card(Rank.KING, Suit.SPADES), card(Rank.KING, Suit.HEARTS),
                        card(Rank.KING, Suit.DIAMONDS), card(Rank.SEVEN, Suit.CLUBS), card(Rank.NINE, Suit.SPADES)),
                HandType.THREE_OF_A_KIND, 3);

        check("straight", vanilla, hand(card(Rank.FIVE, Suit.SPADES), card(Rank.SIX, Suit.HEARTS),
                        card(Rank.SEVEN, Suit.DIAMONDS), card(Rank.EIGHT, Suit.CLUBS), card(Rank.NINE, Suit.SPADES)),
                HandType.STRAIGHT, 5);

        check("wheel (ace-low straight)", vanilla, hand(card(Rank.ACE, Suit.SPADES), card(Rank.TWO, Suit.HEARTS),
                        card(Rank.THREE, Suit.DIAMONDS), card(Rank.FOUR, Suit.CLUBS), card(Rank.FIVE, Suit.SPADES)),
                HandType.STRAIGHT, 5);

        check("broadway (ace-high straight)", vanilla, hand(card(Rank.TEN, Suit.SPADES), card(Rank.JACK, Suit.HEARTS),
                        card(Rank.QUEEN, Suit.DIAMONDS), card(Rank.KING, Suit.CLUBS), card(Rank.ACE, Suit.SPADES)),
                HandType.STRAIGHT, 5);

        check("flush", vanilla, hand(card(Rank.TWO, Suit.SPADES), card(Rank.FIVE, Suit.SPADES),
                        card(Rank.SEVEN, Suit.SPADES), card(Rank.NINE, Suit.SPADES), card(Rank.JACK, Suit.SPADES)),
                HandType.FLUSH, 5);

        check("full house", vanilla, hand(card(Rank.KING, Suit.SPADES), card(Rank.KING, Suit.HEARTS),
                        card(Rank.KING, Suit.DIAMONDS), card(Rank.SEVEN, Suit.CLUBS), card(Rank.SEVEN, Suit.SPADES)),
                HandType.FULL_HOUSE, 5);

        check("four of a kind scores 4 not kicker", vanilla, hand(card(Rank.KING, Suit.SPADES), card(Rank.KING, Suit.HEARTS),
                        card(Rank.KING, Suit.DIAMONDS), card(Rank.KING, Suit.CLUBS), card(Rank.NINE, Suit.SPADES)),
                HandType.FOUR_OF_A_KIND, 4);

        check("straight flush", vanilla, hand(card(Rank.FIVE, Suit.SPADES), card(Rank.SIX, Suit.SPADES),
                        card(Rank.SEVEN, Suit.SPADES), card(Rank.EIGHT, Suit.SPADES), card(Rank.NINE, Suit.SPADES)),
                HandType.STRAIGHT_FLUSH, 5);

        check("five of a kind", vanilla, hand(card(Rank.KING, Suit.SPADES), card(Rank.KING, Suit.HEARTS),
                        card(Rank.KING, Suit.DIAMONDS), card(Rank.KING, Suit.CLUBS), card(Rank.KING, Suit.HEARTS)),
                HandType.FIVE_OF_A_KIND, 5);

        check("flush house", vanilla, hand(card(Rank.KING, Suit.SPADES), card(Rank.KING, Suit.SPADES),
                        card(Rank.KING, Suit.SPADES), card(Rank.SEVEN, Suit.SPADES), card(Rank.SEVEN, Suit.SPADES)),
                HandType.FLUSH_HOUSE, 5);

        check("flush five", vanilla, hand(card(Rank.KING, Suit.SPADES), card(Rank.KING, Suit.SPADES),
                        card(Rank.KING, Suit.SPADES), card(Rank.KING, Suit.SPADES), card(Rank.KING, Suit.SPADES)),
                HandType.FLUSH_FIVE, 5);

        check("wild completes a flush", vanilla, hand(card(Rank.TWO, Suit.SPADES), card(Rank.FIVE, Suit.SPADES),
                        card(Rank.SEVEN, Suit.SPADES), wild(Rank.NINE, Suit.HEARTS), card(Rank.JACK, Suit.SPADES)),
                HandType.FLUSH, 5);

        check("stone always scores on top of a pair", vanilla, hand(card(Rank.KING, Suit.SPADES),
                        card(Rank.KING, Suit.HEARTS), card(Rank.THREE, Suit.DIAMONDS), card(Rank.SEVEN, Suit.CLUBS),
                        stone(Rank.NINE, Suit.SPADES)),
                HandType.PAIR, 3);

        check("stone-only hand", vanilla, hand(stone(Rank.NINE, Suit.SPADES), stone(Rank.TWO, Suit.HEARTS),
                        stone(Rank.FIVE, Suit.CLUBS)),
                HandType.HIGH_CARD, 3);

        // Parameterized threshold: a 4-card flush only classifies when the size drops to 4.
        List<DeckCard> fourSpades = hand(card(Rank.TWO, Suit.SPADES), card(Rank.FIVE, Suit.SPADES),
                card(Rank.SEVEN, Suit.SPADES), card(Rank.NINE, Suit.SPADES), card(Rank.KING, Suit.HEARTS));
        check("4-card flush is NOT a flush at threshold 5", vanilla, fourSpades, HandType.HIGH_CARD, 1);
        check("4-card flush IS a flush at threshold 4", new HandEvaluator(4, 4), fourSpades, HandType.FLUSH, 5);

        System.out.println(failures == 0 ? "\nALL PASS" : "\n" + failures + " FAILURE(S)");
        if (failures != 0) System.exit(1);
    }

    private static void check(String label, HandEvaluator ev, List<DeckCard> played, HandType expectedType, int expectedScoring) {
        HandEvaluation result = ev.evaluate(played);
        boolean ok = result.type() == expectedType && result.scoringCards().size() == expectedScoring;
        if (!ok) failures++;
        System.out.printf("%-45s %s  (got %s, %d scoring)%n",
                label, ok ? "PASS" : "FAIL", result.type(), result.scoringCards().size());
    }

    private static List<DeckCard> hand(DeckCard... cards) { return List.of(cards); }

    private static DeckCard card(Rank rank, Suit suit) { return new DeckCard(rank, suit); }

    private static DeckCard wild(Rank rank, Suit suit) {
        DeckCard c = new DeckCard(rank, suit); c.apply(Enhancement.WILD); return c;
    }

    private static DeckCard stone(Rank rank, Suit suit) {
        DeckCard c = new DeckCard(rank, suit); c.apply(Enhancement.STONE); return c;
    }
}