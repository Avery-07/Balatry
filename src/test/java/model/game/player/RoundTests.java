package model.game.player;

import model.cards.DeckCard;
import model.cards.DeckCard.Rank;
import model.cards.DeckCard.Suit;

import java.util.ArrayList;
import java.util.List;

/** Run-as-main harness for {@link Round}: dealing, play/discard, win/loss, shared-seed mirroring, and guards. */
public final class RoundTests {

    private static int failures = 0;

    public static void main(String[] args) {
        // --- deal ---
        Run run = standardRun(42L);
        Round round = run.beginRound(1_000_000_000L);   // unreachable target -> stays in progress
        checkInt("hand dealt to 8", round.getHand().size(), 8);
        checkInt("draw pile is 44", round.getDrawPile().size(), 44);
        checkInt("4 hands", round.getHandsRemaining(), 4);
        checkInt("3 discards", round.getDiscardsRemaining(), 3);
        check("in progress", round.getOutcome() == RoundOutcome.IN_PROGRESS);
        check("score starts at 0", round.getScore().signum() == 0);

        // --- play consumes a hand, redraws back to 8, banks a positive score ---
        List<DeckCard> first = round.getHand().subList(0, 5);
        PlayResult played = round.play(new ArrayList<>(first));
        check("a hand scored > 0", played.handScore().signum() > 0);
        check("running total equals hand score", round.getScore().compareTo(played.handScore()) == 0);
        checkInt("hands now 3", round.getHandsRemaining(), 3);
        checkInt("redrew to 8", round.getHand().size(), 8);
        checkInt("draw pile now 39", round.getDrawPile().size(), 39);

        // --- discard consumes a discard, redraws, no outcome change ---
        round.discard(new ArrayList<>(round.getHand().subList(0, 3)));
        checkInt("discards now 2", round.getDiscardsRemaining(), 2);
        checkInt("hand still 8", round.getHand().size(), 8);

        // --- win: a reachable target flips to WON ---
        Run winRun = standardRun(42L);
        Round winnable = winRun.beginRound(1L);
        winnable.play(new ArrayList<>(winnable.getHand().subList(0, 5)));
        check("low target -> WON", winnable.getOutcome() == RoundOutcome.WON);

        // --- loss: exhaust all 4 hands under an unreachable target ---
        Run loseRun = standardRun(7L);
        Round losing = loseRun.beginRound(1_000_000_000L);
        for (int i = 0; i < 4; i++) losing.play(new ArrayList<>(losing.getHand().subList(0, 1)));
        check("hands exhausted -> LOST", losing.getOutcome() == RoundOutcome.LOST);

        // --- shared seed mirrors the shuffle for identical decks ---
        Run a = standardRun(99L);
        Run b = standardRun(99L);
        List<DeckCard> handA = a.beginRound(1_000_000_000L).getHand();
        List<DeckCard> handB = b.beginRound(1_000_000_000L).getHand();
        boolean mirrored = handA.size() == handB.size();
        for (int i = 0; mirrored && i < handA.size(); i++)
            mirrored = sameCard(handA.get(i), handB.get(i));
        check("same seed -> identical deal", mirrored);

        Run c = standardRun(100L);
        List<DeckCard> handC = c.beginRound(1_000_000_000L).getHand();
        boolean differs = false;
        for (int i = 0; i < handA.size(); i++) if (!sameCard(handA.get(i), handC.get(i))) { differs = true; break; }
        check("different seed -> different deal", differs);

        // --- guards ---
        Run g = standardRun(1L);
        Round guard = g.beginRound(1_000_000_000L);
        checkThrows("reject 6-card play", () -> guard.play(new ArrayList<>(guard.getHand().subList(0, 6))));
        checkThrows("reject empty play", () -> guard.play(new ArrayList<>()));
        checkThrows("reject card not in hand", () -> guard.play(List.of(new DeckCard(Rank.ACE, Suit.SPADES))));

        System.out.println(failures == 0 ? "\nALL PASS" : "\n" + failures + " FAILURE(S)");
        if (failures != 0) System.exit(1);
    }

    private static Run standardRun(long seed) {
        Run run = new Run(seed);
        for (Suit suit : Suit.values())
            for (Rank rank : Rank.values())
                run.getDeck().add(new DeckCard(rank, suit));
        return run;
    }

    private static boolean sameCard(DeckCard x, DeckCard y) {
        return x.getRank() == y.getRank() && x.getSuit() == y.getSuit();
    }

    private static void check(String label, boolean ok) {
        if (!ok) failures++;
        System.out.printf("%-38s %s%n", label, ok ? "PASS" : "FAIL");
    }

    private static void checkInt(String label, int actual, int expected) {
        check(label + " (" + actual + ")", actual == expected);
    }

    private static void checkThrows(String label, Runnable action) {
        boolean threw = false;
        try { action.run(); } catch (RuntimeException e) { threw = true; }
        check(label, threw);
    }
}