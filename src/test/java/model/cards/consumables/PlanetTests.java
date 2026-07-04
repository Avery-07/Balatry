package model.cards.consumables;

import model.cards.DeckCard;
import model.cards.DeckCard.Rank;
import model.cards.DeckCard.Suit;
import model.game.player.Run;
import model.game.scoring.HandEvaluation;
import model.game.scoring.HandEvaluator;
import model.game.scoring.HandType;
import model.game.scoring.ScoringEngine;

import java.util.List;

/** Run-as-main harness for Planet cards: leveling a hand type via {@code useConsumable} and its scoring impact. */
public final class PlanetTests {

    private static int failures = 0;
    private static final HandEvaluator EVAL = new HandEvaluator();
    private static final ScoringEngine ENGINE = new ScoringEngine();

    public static void main(String[] args) {
        check("all 12 planets present", Planets.values().length == 12);

        // --- planets level their hand type via useConsumable ---
        Run pr = new Run(1L);
        pr.addConsumable(Planets.MERCURY.make());
        checkInt("pair level before", pr.getHandLevels().levelOf(HandType.PAIR), 1);
        pr.useConsumable(0);
        checkInt("pair level after Mercury", pr.getHandLevels().levelOf(HandType.PAIR), 2);
        checkInt("consumable removed on use", pr.getConsumables().size(), 0);

        // A leveled hand scores higher: pair of kings after Mercury -> (10+15+20) x (2+1) = 135
        checkScore("leveled pair scores higher", score(pr, kings()), 135);

        System.out.println(failures == 0 ? "\nALL PASS" : "\n" + failures + " FAILURE(S)");
        if (failures != 0) System.exit(1);
    }

    private static long score(Run run, List<DeckCard> cards) {
        HandEvaluation e = EVAL.evaluate(cards);
        long bc = run.getHandLevels().chipsFor(e.type());
        long bm = run.getHandLevels().multFor(e.type());
        return ENGINE.score(run, e.context(), bc, bm, e.scoringCards(), List.of()).score().longValueExact();
    }

    private static List<DeckCard> kings() {
        return List.of(new DeckCard(Rank.KING, Suit.SPADES), new DeckCard(Rank.KING, Suit.HEARTS));
    }

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
