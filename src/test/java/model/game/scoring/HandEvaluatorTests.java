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

        // Shortcut: straights may skip a single rank per step. 2-4-6-8-10 runs only with a gap of 1.
        List<DeckCard> gapped = hand(card(Rank.TWO, Suit.SPADES), card(Rank.FOUR, Suit.HEARTS),
                card(Rank.SIX, Suit.DIAMONDS), card(Rank.EIGHT, Suit.CLUBS), card(Rank.TEN, Suit.SPADES));
        check("gapped run is not a vanilla straight", vanilla, gapped, HandType.HIGH_CARD, 1);
        check("Shortcut makes a gapped run a straight", new HandEvaluator(5, 5, 1, false, false), gapped, HandType.STRAIGHT, 5);

        // Smeared: hearts and diamonds count as one suit. A mix of five reds is a flush only when smeared.
        List<DeckCard> reds = hand(card(Rank.TWO, Suit.HEARTS), card(Rank.FIVE, Suit.DIAMONDS),
                card(Rank.SEVEN, Suit.HEARTS), card(Rank.NINE, Suit.DIAMONDS), card(Rank.JACK, Suit.HEARTS));
        check("mixed red is not a vanilla flush", vanilla, reds, HandType.HIGH_CARD, 1);
        check("Smeared merges red suits into a flush", new HandEvaluator(5, 5, 0, true, false), reds, HandType.FLUSH, 5);

        // Splash: the hand type is unchanged, but every played card scores (a pair normally scores 2).
        List<DeckCard> pair = hand(card(Rank.KING, Suit.SPADES), card(Rank.KING, Suit.HEARTS),
                card(Rank.THREE, Suit.DIAMONDS), card(Rank.SEVEN, Suit.CLUBS), card(Rank.NINE, Suit.SPADES));
        check("a pair normally scores 2 of 5", vanilla, pair, HandType.PAIR, 2);
        check("Splash scores every played card", new HandEvaluator(5, 5, 0, false, true), pair, HandType.PAIR, 5);

        // Dyscalculie: every card also counts as the rank above (Ace as 2). It slides a broken run into a straight
        // and pairs adjacent ranks — one card being two ranks, resolved by trying every assignment.
        HandEvaluator dys = new HandEvaluator(5, 5, 0, false, false, true);
        List<DeckCard> brokenRun = hand(card(Rank.TWO, Suit.SPADES), card(Rank.THREE, Suit.HEARTS),
                card(Rank.FOUR, Suit.DIAMONDS), card(Rank.FIVE, Suit.CLUBS), card(Rank.SEVEN, Suit.SPADES));
        check("2-3-4-5-7 is not a vanilla straight", vanilla, brokenRun, HandType.HIGH_CARD, 1);
        check("Dyscalculie slides a broken run into a straight", dys, brokenRun, HandType.STRAIGHT, 5);

        List<DeckCard> adjacent = hand(card(Rank.FIVE, Suit.SPADES), card(Rank.SIX, Suit.HEARTS),
                card(Rank.EIGHT, Suit.DIAMONDS), card(Rank.TEN, Suit.CLUBS), card(Rank.QUEEN, Suit.SPADES));
        check("5-6-8-10-Q is a vanilla high card", vanilla, adjacent, HandType.HIGH_CARD, 1);
        check("Dyscalculie pairs the adjacent 5 and 6", dys, adjacent, HandType.PAIR, 2);

        List<DeckCard> aceTwo = hand(card(Rank.ACE, Suit.SPADES), card(Rank.TWO, Suit.HEARTS),
                card(Rank.EIGHT, Suit.DIAMONDS), card(Rank.TEN, Suit.CLUBS), card(Rank.QUEEN, Suit.SPADES));
        check("Dyscalculie pairs an Ace with a Two", dys, aceTwo, HandType.PAIR, 2);

        // The canonical example: an Ace read as a 2 completes 2-3-4-5-6 (a gap the wheel/broadway cannot fill).
        List<DeckCard> aceGap = hand(card(Rank.ACE, Suit.SPADES), card(Rank.THREE, Suit.HEARTS),
                card(Rank.FOUR, Suit.DIAMONDS), card(Rank.FIVE, Suit.CLUBS), card(Rank.SIX, Suit.SPADES));
        check("A-3-4-5-6 is not a vanilla straight", vanilla, aceGap, HandType.HIGH_CARD, 1);
        check("Dyscalculie: an Ace-as-2 completes 2-3-4-5-6", dys, aceGap, HandType.STRAIGHT, 5);

        // Never worse than vanilla: a hand that is already a flush stays (at least) a flush under Dyscalculie.
        List<DeckCard> flushHand = hand(card(Rank.TWO, Suit.SPADES), card(Rank.FIVE, Suit.SPADES),
                card(Rank.SEVEN, Suit.SPADES), card(Rank.NINE, Suit.SPADES), card(Rank.JACK, Suit.SPADES));
        check("Dyscalculie keeps a flush at least a flush", dys, flushHand, HandType.FLUSH, 5);

        // Numbered-only: a Nine counts as a Ten, but face cards are out of the loop (a Queen never becomes a King).
        List<DeckCard> nineTen = hand(card(Rank.NINE, Suit.SPADES), card(Rank.TEN, Suit.HEARTS),
                card(Rank.TWO, Suit.DIAMONDS), card(Rank.FOUR, Suit.CLUBS), card(Rank.SIX, Suit.SPADES));
        check("Dyscalculie pairs a Nine and a Ten", dys, nineTen, HandType.PAIR, 2);
        List<DeckCard> faceAdjacent = hand(card(Rank.QUEEN, Suit.SPADES), card(Rank.KING, Suit.HEARTS),
                card(Rank.TWO, Suit.DIAMONDS), card(Rank.FOUR, Suit.CLUBS), card(Rank.SIX, Suit.SPADES));
        check("Dyscalculie leaves face cards unshifted (Q-K stay a high card)", dys, faceAdjacent, HandType.HIGH_CARD, 1);

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