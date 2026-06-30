package model.game.sins;

import model.game.Match;
import model.game.MatchConfig;
import model.game.Sin;
import model.game.SinSelector;
import model.game.player.BlindResult;
import model.game.player.PlayerId;
import model.game.player.Run;
import model.game.player.RoundOutcome;

import java.math.BigDecimal;
import java.util.List;

/**
 * Run-as-main harness for the sin seam and the Pride modifier: registry wiring, the round-begin choice resolved
 * through the injected {@link SinChoiceProvider}, the round-settled threshold check (met / not met / no-gamble),
 * and {@link SinState} reset.
 */
public final class SinTests {

    private static int failures = 0;
    private static final BigDecimal TWO = new BigDecimal("2");

    public static void main(String[] args) {
        registration();
        roundBeginChoice();
        roundSettledThreshold();
        stateReset();

        System.out.println(failures == 0 ? "\nALL PASS" : "\n" + failures + " FAILURE(S)");
        if (failures != 0) System.exit(1);
    }

    private static void registration() {
        check("Pride registered as PrideModifier", Sins.modifierFor(Sin.PRIDE) instanceof PrideModifier);
        check("unbuilt sin resolves to NONE", Sins.modifierFor(Sin.WRATH) == SinModifier.NONE);
    }

    /** onRoundBegin consults the injected provider and stores the chosen multiplier on each seat's SinState. */
    private static void roundBeginChoice() {
        SinSelector alwaysPride = (ante, rng) -> Sin.PRIDE;

        // Provider picks option index 2 -> x2, for every seat.
        Match m2 = Match.create(7L, List.of("A", "B"),
                MatchConfig.defaults().withSinSelector(alwaysPride).withSinChoiceProvider((run, req) -> 2));
        m2.start();
        for (PlayerId id : m2.getSeats())
            check("Pride x2 stored for " + id, m2.getRun(id).getSinState().getPrideMultiplier().compareTo(TWO) == 0);

        // Default provider (FIRST -> index 0 -> x1, the no-gamble default).
        Match m1 = Match.create(7L, List.of("A", "B"), MatchConfig.defaults().withSinSelector(alwaysPride));
        m1.start();
        check("default provider -> x1",
                m1.getRun(m1.getSeats().get(0)).getSinState().getPrideMultiplier().compareTo(BigDecimal.ONE) == 0);
    }

    /** onRoundSettled marks the threshold met iff score >= target x multiplier; the point multiplier follows. */
    private static void roundSettledThreshold() {
        PrideModifier pride = new PrideModifier();
        long target = 600;

        // x2 chosen, score exactly target x2 -> met, point multiplier x2.
        Run met = headlessWithMultiplier(TWO);
        pride.onRoundSettled(met, result(target, target * 2));
        check("met threshold (score == target x2)", met.getSinState().isPrideThresholdMet());
        check("met -> point multiplier x2", met.getSinState().pridePointMultiplier().compareTo(TWO) == 0);

        // x2 chosen, cleared the blind but below target x2 -> not met, point multiplier x1.
        Run missed = headlessWithMultiplier(TWO);
        pride.onRoundSettled(missed, result(target, target * 2 - 1));
        check("not met (cleared but below target x2)", !missed.getSinState().isPrideThresholdMet());
        check("not met -> point multiplier x1", missed.getSinState().pridePointMultiplier().compareTo(BigDecimal.ONE) == 0);

        // x1 chosen -> threshold == target, so any clear meets it (no-gamble baseline, multiplier has no effect).
        Run safe = headlessWithMultiplier(BigDecimal.ONE);
        pride.onRoundSettled(safe, result(target, target));
        check("x1 met on clear", safe.getSinState().isPrideThresholdMet());
        check("x1 -> point multiplier x1", safe.getSinState().pridePointMultiplier().compareTo(BigDecimal.ONE) == 0);
    }

    private static void stateReset() {
        Run run = headlessWithMultiplier(TWO);
        run.getSinState().setPrideThresholdMet(true);
        run.getSinState().beginRound();
        check("beginRound resets multiplier to x1", run.getSinState().getPrideMultiplier().compareTo(BigDecimal.ONE) == 0);
        check("beginRound clears threshold-met", !run.getSinState().isPrideThresholdMet());
    }

    private static Run headlessWithMultiplier(BigDecimal m) {
        Run run = new Run(0L);
        run.getSinState().setPrideMultiplier(m);
        return run;
    }

    private static BlindResult result(long target, long score) {
        return new BlindResult(RoundOutcome.WON, BigDecimal.valueOf(score), target, 0, 0);
    }

    private static void check(String label, boolean ok) {
        if (!ok) failures++;
        System.out.printf("%-46s %s%n", label, ok ? "PASS" : "FAIL");
    }
}
