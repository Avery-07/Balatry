package model.game;

import model.cards.DeckCard;
import model.cards.DeckCard.Rank;
import model.cards.DeckCard.Suit;
import model.game.player.BlindResult;
import model.game.player.PlayerId;
import model.game.player.Run;
import model.game.player.RoundOutcome;

import java.util.ArrayList;
import java.util.List;

/** Run-as-main harness for the {@link Match} loop: target table, deal/barrier/settle, and blind/ante/sin progression. */
public final class MatchTests {

    private static int failures = 0;

    public static void main(String[] args) {
        // --- target table spot checks ---
        checkLong("ante1 small", BlindTargets.target(1, Blind.SMALL), 300);
        checkLong("ante1 big", BlindTargets.target(1, Blind.BIG), 450);
        checkLong("ante1 boss", BlindTargets.target(1, Blind.BOSS), 600);
        checkLong("ante2 small", BlindTargets.target(2, Blind.SMALL), 800);
        checkLong("ante8 boss", BlindTargets.target(8, Blind.BOSS), 100_000);

        Match match = Match.create(123L, List.of("Winner", "Loser"));
        PlayerId winner = match.getSeats().get(0);
        PlayerId loser = match.getSeats().get(1);

        // Stack the winner's deck so a single Flush Five clears ante-1 small (300).
        Run winRun = match.getRun(winner);
        winRun.getDeck().clear();
        for (int i = 0; i < 8; i++) winRun.getDeck().add(new DeckCard(Rank.ACE, Suit.SPADES));

        match.start();
        check("phase BLIND after start", match.getPhase() == MatchPhase.BLIND);
        checkInt("ante 1", match.getAnte(), 1);
        check("blind SMALL", match.getBlind() == Blind.SMALL);
        checkLong("current target 300", match.getCurrentTarget(), 300);
        check("sin selected", match.getActiveSin() != null);
        checkInt("winner dealt 8", match.getRun(winner).getRound().getHand().size(), 8);
        checkInt("loser dealt 8", match.getRun(loser).getRound().getHand().size(), 8);

        // Barrier rejects an early toShop while rounds are unfinished.
        checkThrows("toShop blocked mid-blind", match::toShop);

        // Winner clears with a Flush Five; loser exhausts hands and fails.
        match.getRun(winner).getRound().play(handOf(match.getRun(winner), 5));
        check("winner WON", match.getRun(winner).getRound().getOutcome() == RoundOutcome.WON);
        exhaust(match.getRun(loser));

        match.toShop();
        check("phase SHOP", match.getPhase() == MatchPhase.SHOP);
        BlindResult wr = match.getResult(winner);
        BlindResult lr = match.getResult(loser);
        check("winner cleared", wr.cleared());
        check("winner earned money", wr.moneyEarned() > 0);
        check("loser not cleared", !lr.cleared());
        checkInt("loser earned nothing", lr.moneyEarned(), 0);

        // Progress SMALL -> BIG -> BOSS -> ante 2 SMALL, checking targets and rollover.
        match.nextBlind();
        check("now BIG", match.getBlind() == Blind.BIG);
        checkLong("big target 450", match.getCurrentTarget(), 450);

        advance(match);   // exhaust BIG
        check("now BOSS", match.getBlind() == Blind.BOSS);
        checkLong("boss target 600", match.getCurrentTarget(), 600);

        Sin sinAnte1 = match.getActiveSin();
        advance(match);   // exhaust BOSS -> ante 2
        checkInt("ante rolled to 2", match.getAnte(), 2);
        check("back to SMALL", match.getBlind() == Blind.SMALL);
        checkLong("ante2 small target 800", match.getCurrentTarget(), 800);
        check("sin reselected at ante rollover", match.getActiveSin() != null);
        check("sin field repopulated", sinAnte1 != null);

        match.finish();
        check("finished", match.getPhase() == MatchPhase.FINISHED);

        System.out.println(failures == 0 ? "\nALL PASS" : "\n" + failures + " FAILURE(S)");
        if (failures != 0) System.exit(1);
    }

    /** Exhausts a run's hands with one-card plays so its round becomes terminal. */
    private static void exhaust(Run run) {
        while (run.getRound().getOutcome() == RoundOutcome.IN_PROGRESS)
            run.getRound().play(handOf(run, 1));
    }

    /** From SHOP/BLIND: deal (if needed), exhaust both seats, and advance to the next shop. */
    private static void advance(Match match) {
        for (PlayerId id : match.getSeats()) exhaust(match.getRun(id));
        match.toShop();
        match.nextBlind();
    }

    private static List<DeckCard> handOf(Run run, int n) {
        return new ArrayList<>(run.getRound().getHand().subList(0, n));
    }

    private static void check(String label, boolean ok) {
        if (!ok) failures++;
        System.out.printf("%-34s %s%n", label, ok ? "PASS" : "FAIL");
    }

    private static void checkInt(String label, int actual, int expected) {
        check(label + " (" + actual + ")", actual == expected);
    }

    private static void checkLong(String label, long actual, long expected) {
        check(label + " (" + actual + ")", actual == expected);
    }

    private static void checkThrows(String label, Runnable action) {
        boolean threw = false;
        try { action.run(); } catch (RuntimeException e) { threw = true; }
        check(label, threw);
    }
}