package model.game.player;

import model.cards.DeckCard;
import model.cards.DeckCard.Rank;
import model.cards.DeckCard.Suit;
import model.cards.jokers.JokerCard;
import model.cards.jokers.Jokers;
import model.modifiers.Enhancement;
import model.game.scoring.HandEvaluation;
import model.game.scoring.HandEvaluator;
import model.game.scoring.ScoringEngine;

import java.util.List;

/** Run-as-main harness for jokers that exercise the slot read and the ON_ROUND_START dispatch. */
public final class JokerHooksTests {

    private static int failures = 0;
    private static final HandEvaluator EVAL = new HandEvaluator();
    private static final ScoringEngine ENGINE = new ScoringEngine();

    public static void main(String[] args) {
        // Joker Stencil: lone in 5 slots -> 4 empty + itself = X5. Pair of kings (30 chips x 2 mult) -> 30 x (2*5) = 300.
        Run a = new Run(0L); a.getJokers().add(Jokers.JOKER_STENCIL.make());
        checkScore("Stencil X5 lone", score(a, kings()), 300);
        // With the board full (5 used), only its own slot counts -> X1 -> 60.
        Run full = new Run(0L);
        full.getJokers().add(Jokers.JOKER_STENCIL.make());
        for (int i = 0; i < 4; i++) full.getJokers().add(Jokers.JOKER.make());
        // (Joker also adds +4 mult x4 = +16; isolate Stencil by checking it's not X-ing: 30 x (2+16) x1 = 540.)
        checkScore("Stencil X1 on full board", score(full, kings()), 540);

        // Mystic Summit: +15 mult only when discards == 0.
        Run zero = new Run(0L); zero.setBaseDiscards(0); zero.getJokers().add(Jokers.MYSTIC_SUMMIT.make());
        zero.beginRound(1_000_000);   // round present, 0 discards, target high enough not to auto-win
        checkScore("Mystic +15 at 0 discards", score(zero, kings()), 510);   // 30 x (2+15)
        Run some = new Run(0L); some.setBaseDiscards(3); some.getJokers().add(Jokers.MYSTIC_SUMMIT.make());
        some.beginRound(1_000_000);
        checkScore("Mystic inert with discards", score(some, kings()), 60);

        // Ceremonial Dagger: at round start, eats the joker to its right and banks 3x its sell value.
        Run d = new Run(0L);
        d.getJokers().add(Jokers.CEREMONIAL_DAGGER.make());   // cost 6 -> sell 3
        JokerCard victim = Jokers.JOKER.make();                // cost 2 -> sell 1
        d.getJokers().add(victim);
        d.beginRound(300);
        JokerCard dagger = d.getJokers().get(0);
        check("dagger ate its neighbour", d.getJokers().size() == 1);
        checkInt("dagger banked 3x sell (3*1)", dagger.getCounter(), 3);

        // Marble Joker: at round start, adds a Stone card to the deck.
        Run m = new Run(0L); m.getJokers().add(Jokers.MARBLE_JOKER.make());
        int before = m.getDeck().size();
        m.beginRound(300);
        int after = m.getDeck().size();
        checkInt("marble grew the deck by 1", after - before, 1);
        DeckCard added = m.getDeck().get(m.getDeck().size() - 1);
        check("added card is Stone", added.getEnhancement() == Enhancement.STONE);

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

    private static void checkScore(String label, long actual, long expected) { check(label + " (" + actual + ")", actual == expected); }
    private static void checkInt(String label, int actual, int expected)     { check(label + " (" + actual + ")", actual == expected); }
    private static void check(String label, boolean ok) {
        if (!ok) failures++;
        System.out.printf("%-38s %s%n", label, ok ? "PASS" : "FAIL");
    }
}
