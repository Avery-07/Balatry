package model.game;

import client.MatchSnapshot;
import model.items.DeckCard;
import model.items.DeckCard.Rank;
import model.items.DeckCard.Suit;
import model.items.consumables.Tarots;
import model.items.relics.Relics;
import model.game.player.PlayerId;
import model.game.player.Round;
import model.game.player.Run;

import java.util.ArrayList;
import java.util.List;

/**
 * Run-as-main harness for face-down cards (the four hiding bosses, previously inert) and the drag-reorder moves.
 * Face-down is a visibility state, not a debuff: the card plays normally, its owner just cannot see it — so the
 * checks here are about who gets hidden, that hiding is deterministic, and that the snapshot masks identity.
 */
public final class FaceDownTests {

    private static int failures = 0;

    public static void main(String[] args) {
        theHouse();
        theMark();
        theWheelDeterminism();
        theFish();
        snapshotMasking();
        reorderMoves();

        System.out.println(failures == 0 ? "\nALL PASS" : "\n" + failures + " FAILURE(S)");
        if (failures != 0) System.exit(1);
    }

    /** The House: the whole opening deal is hidden; cards drawn later are not. */
    private static void theHouse() {
        Run run = stockedRun(1L);
        Round r = run.beginRound(1_000_000, BossBlind.THE_HOUSE);
        boolean allHidden = true;
        for (DeckCard c : r.getHand()) if (!r.isFaceDown(c)) allHidden = false;
        check("the first deal is entirely face down", allHidden);

        List<DeckCard> before = new ArrayList<>(r.getHand());
        playFive(r);
        boolean newDrawsVisible = true;   // the unplayed originals rightly stay hidden; only new draws are open
        for (DeckCard c : r.getHand()) if (!before.contains(c) && r.isFaceDown(c)) newDrawsVisible = false;
        check("cards drawn after the first hand are visible", newDrawsVisible);
    }

    /** The Mark: exactly the face cards are hidden, whenever they are drawn. */
    private static void theMark() {
        Run run = stockedRun(2L);
        Round r = run.beginRound(1_000_000, BossBlind.THE_MARK);
        boolean correct = true;
        for (DeckCard c : r.getHand()) if (r.isFaceDown(c) != c.isFace()) correct = false;
        check("face cards and only face cards are hidden", correct);
    }

    /** The Wheel: the 1-in-5 roll is keyed by card id, so the same seed hides the same cards. */
    private static void theWheelDeterminism() {
        check("the same seed hides the same cards", wheelPattern(3L).equals(wheelPattern(3L)));
    }

    /** The Fish: the opening deal is visible; every card drawn after a play is hidden. */
    private static void theFish() {
        Run run = stockedRun(4L);
        Round r = run.beginRound(1_000_000, BossBlind.THE_FISH);
        boolean openVisible = true;
        for (DeckCard c : r.getHand()) if (r.isFaceDown(c)) openVisible = false;
        check("the opening deal is visible", openVisible);

        List<DeckCard> before = new ArrayList<>(r.getHand());
        playFive(r);
        boolean drawnHidden = true;
        for (DeckCard c : r.getHand()) if (!before.contains(c) && !r.isFaceDown(c)) drawnHidden = false;
        check("cards drawn after the play are hidden", drawnHidden);
        boolean survivorsVisible = true;
        for (DeckCard c : r.getHand()) if (before.contains(c) && r.isFaceDown(c)) survivorsVisible = false;
        check("cards already held stay visible", survivorsVisible);
    }

    /** The snapshot masks a hidden card completely: no rank, no suit, no label — nothing for a tooltip to leak. */
    private static void snapshotMasking() {
        Match m = Match.create(9L, List.of("A", "B"));
        PlayerId a = m.getSeats().get(0);
        m.start();
        Run run = m.getRun(a);
        run.getRound().finish();
        run.endRound(Blind.SMALL);
        run.beginRound(1_000_000, BossBlind.THE_HOUSE);   // a fresh, fully-hidden deal

        MatchSnapshot s = MatchSnapshot.of(m, a);
        boolean masked = true;
        for (MatchSnapshot.HandCardView c : s.hand())
            if (!c.faceDown() || c.rank() != -1 || c.suit() != -1 || !c.label().isEmpty()) masked = false;
        check("every hidden card is fully masked at the boundary", masked && !s.hand().isEmpty());
    }

    /** The drag-reorder moves: pure arrangement, bounds-checked, one list never leaks into the other. */
    private static void reorderMoves() {
        Run run = new Run(5L);
        run.setConsumableSlots(5);   // consumables and relics share this pool; the default 2 would reject cards
        run.addConsumable(Tarots.THE_FOOL.make());
        run.addConsumable(Tarots.STRENGTH.make());
        run.addConsumable(Tarots.DEATH.make());
        run.moveConsumable(0, 2);
        check("a consumable reorders", run.getConsumables().get(2).getSpec().getName().equals("The Fool"));
        check("the others shift up", run.getConsumables().get(0).getSpec().getName().equals("Strength"));
        checkInt("nothing was lost", run.getConsumables().size(), 3);

        run.addRelic(Relics.values()[0].make());
        run.addRelic(Relics.values()[1].make());
        String first = run.getRelics().get(0).getSpec().getName();
        run.moveRelic(0, 1);
        check("a relic reorders", run.getRelics().get(1).getSpec().getName().equals(first));
        check("relic moves never touch consumables", run.getConsumables().size() == 3);

        checkThrows("an out-of-range consumable move is refused", () -> run.moveConsumable(0, 9));
        checkThrows("an out-of-range relic move is refused", () -> run.moveRelic(5, 0));
    }

    // --- helpers ---

    /**
     * The Wheel's hidden cards for a seed, as hand <em>positions</em> — deliberately not card ids, which come
     * from a JVM-global counter and differ between two runs in one process. The draw-counter salt makes the
     * positions the stable thing.
     */
    private static List<Integer> wheelPattern(long seed) {
        Run run = stockedRun(seed);
        Round r = run.beginRound(1_000_000, BossBlind.THE_WHEEL);
        List<Integer> hidden = new ArrayList<>();
        for (int i = 0; i < r.getHand().size(); i++) if (r.isFaceDown(r.getHand().get(i))) hidden.add(i);
        return hidden;
    }

    /** A run with a mixed deck: half face cards, half number cards, enough to redraw from. */
    private static Run stockedRun(long seed) {
        Run run = new Run(seed);
        for (int i = 0; i < 20; i++) {
            run.addCardToDeck(new DeckCard(Rank.KING, Suit.values()[i % 4]));
            run.addCardToDeck(new DeckCard(Rank.values()[i % 9], Suit.values()[i % 4]));   // TWO..TEN
        }
        return run;
    }

    private static void playFive(Round r) {
        r.play(new ArrayList<>(r.getHand().subList(0, Math.min(5, r.getHand().size()))));
    }

    private static void check(String label, boolean ok) {
        if (!ok) failures++;
        System.out.printf("%-52s %s%n", label, ok ? "PASS" : "FAIL");
    }

    private static void checkInt(String label, int actual, int expected) {
        check(label + " (" + actual + ")", actual == expected);
    }

    private static void checkThrows(String label, Runnable r) {
        try { r.run(); check(label, false); }
        catch (RuntimeException e) { check(label, true); }
    }
}
