package model.game.player;

import model.cards.DeckCard;
import model.cards.DeckCard.Rank;
import model.cards.DeckCard.Suit;
import model.cards.consumables.Planets;
import model.cards.jokers.Jokers;
import model.game.scoring.HandEvaluation;
import model.game.scoring.HandEvaluator;
import model.game.scoring.HandType;
import model.game.scoring.ScoringEngine;

import java.util.List;

/** Run-as-main harness validating the content slice: joker scoring deltas, planet leveling, and the catalog pool. */
public final class ContentTests {

    private static int failures = 0;
    private static final HandEvaluator EVAL = new HandEvaluator();
    private static final ScoringEngine ENGINE = new ScoringEngine();

    public static void main(String[] args) {
        // Baselines (no jokers): single Ace high card, and a pair of Kings.
        checkScore("baseline high card (5+11)x1", score(new Run(0L), ace()), 16);
        checkScore("baseline pair of kings (10+20)x2", score(new Run(0L), kings()), 60);

        // Joker: +4 Mult -> (5+11) x (1+4) = 80
        checkScore("Joker +4 mult", scoreWith(ace(), Jokers.JOKER), 80);

        // Jolly: +8 Mult if contains a Pair -> (10+20) x (2+8) = 300
        checkScore("Jolly +8 on pair", scoreWith(kings(), Jokers.JOLLY_JOKER), 300);
        // Jolly does nothing on a high card.
        checkScore("Jolly inert on high card", scoreWith(ace(), Jokers.JOLLY_JOKER), 16);

        // Sly: +50 Chips if contains a Pair -> (10+20+50) x 2 = 160
        checkScore("Sly +50 chips on pair", scoreWith(kings(), Jokers.SLY_JOKER), 160);

        // Half Joker: +20 Mult if <= 3 cards -> pair is 2 cards -> (10+20) x (2+20) = 660
        checkScore("Half +20 mult on 2 cards", scoreWith(kings(), Jokers.HALF_JOKER), 660);

        // Abstract: +3 Mult per joker (itself = 1) -> (10+20) x (2+3) = 150
        checkScore("Abstract +3 per joker", scoreWith(kings(), Jokers.ABSTRACT_JOKER), 150);

        // Scary Face: +30 Chips per scored face (two kings) -> (10+20+60) x 2 = 180
        checkScore("Scary Face +30/face", scoreWith(kings(), Jokers.SCARY_FACE), 180);

        // Even Steven: +4 Mult per scored even card (two 4s) -> (10+8) x (2+8) = 180
        checkScore("Even Steven +4/even", scoreWith(fours(), Jokers.EVEN_STEVEN), 180);

        // Greedy: +3 Mult per scored Diamond (one scoring Ace of Diamonds) -> (5+11) x (1+3) = 64
        checkScore("Greedy +3/diamond", scoreWith(List.of(new DeckCard(Rank.ACE, Suit.DIAMONDS)), Jokers.GREEDY_JOKER), 64);

        // --- planets level their hand type via useConsumable ---
        Run pr = new Run(1L);
        pr.getConsumables().add(Planets.MERCURY.make());
        checkInt("pair level before", pr.getHandLevels().levelOf(HandType.PAIR), 1);
        pr.useConsumable(0);
        checkInt("pair level after Mercury", pr.getHandLevels().levelOf(HandType.PAIR), 2);
        checkInt("consumable removed on use", pr.getConsumables().size(), 0);
        check("all 12 planets present", Planets.values().length == 12);

        // A leveled hand scores higher: pair of kings after Mercury -> (10+15+20) x (2+1) = 135
        checkScore("leveled pair scores higher", score(pr, kings()), 135);

        // --- catalog pool yields valid, priced cards ---
        boolean ok = true;
        for (int seed = 0; seed < 30 && ok; seed++) {
            var card = CatalogShopPool.INSTANCE.roll(new java.util.Random(seed));
            ok = card != null && card.getShopValue() >= 0;
        }
        check("catalog pool yields priced cards", ok);

        System.out.println(failures == 0 ? "\nALL PASS" : "\n" + failures + " FAILURE(S)");
        if (failures != 0) System.exit(1);
    }

    private static long score(Run run, List<DeckCard> cards) {
        HandEvaluation e = EVAL.evaluate(cards);
        long bc = run.getHandLevels().chipsFor(e.type());
        long bm = run.getHandLevels().multFor(e.type());
        return ENGINE.score(run, e.context(), bc, bm, e.scoringCards(), List.of()).score().longValueExact();
    }

    private static long scoreWith(List<DeckCard> cards, Jokers joker) {
        Run run = new Run(0L);
        run.getJokers().add(joker.make());
        return score(run, cards);
    }

    private static List<DeckCard> ace()   { return List.of(new DeckCard(Rank.ACE, Suit.SPADES)); }
    private static List<DeckCard> kings()  { return List.of(new DeckCard(Rank.KING, Suit.SPADES), new DeckCard(Rank.KING, Suit.HEARTS)); }
    private static List<DeckCard> fours()  { return List.of(new DeckCard(Rank.FOUR, Suit.SPADES), new DeckCard(Rank.FOUR, Suit.HEARTS)); }

    private static void checkScore(String label, long actual, long expected) {
        check(label + " (" + actual + ")", actual == expected);
    }

    private static void checkInt(String label, int actual, int expected) {
        check(label + " (" + actual + ")", actual == expected);
    }

    private static void check(String label, boolean ok) {
        if (!ok) failures++;
        System.out.printf("%-38s %s%n", label, ok ? "PASS" : "FAIL");
    }
}