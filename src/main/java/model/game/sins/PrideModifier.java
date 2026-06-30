package model.game.sins;

import model.game.Sin;
import model.game.player.BlindResult;
import model.game.player.Run;

import java.math.BigDecimal;
import java.util.List;

/**
 * Pride. At round start each player picks a score multiplier (x1 / x1.5 / x2 / x3); if their round score reaches
 * {@code target x multiplier}, the multiplier applies to the points they win that round. A pure risk/reward
 * gamble — it embodies the sin through incentive (overreach for a bigger payoff), changes no hand/discard sizes,
 * favours no build, and is symmetric (every seat faces the same offer).
 *
 * <p>Per-player and round-scoped: the choice and the met/not-met result live on {@link SinState}. The final
 * multiply into the player's competition points is intentionally left to the points-award system (not yet built);
 * it reads {@link SinState#pridePointMultiplier()}, which is fully resolved here.
 */
public final class PrideModifier implements SinModifier {

    /** Option index -> multiplier; mirrors {@link #CHOICE} option order. x1 is the no-gamble default. */
    private static final List<BigDecimal> MULTIPLIERS =
            List.of(BigDecimal.ONE, new BigDecimal("1.5"), new BigDecimal("2"), new BigDecimal("3"));

    static final SinChoice CHOICE = new SinChoice(Sin.PRIDE,
            "Pride: choose a score multiplier (higher = harder target, bigger payoff)",
            List.of("x1", "x1.5", "x2", "x3"));

    @Override
    public void onRoundBegin(Run run) {
        if (run.getMatch() == null) return;   // sins act only within a match
        int i = run.getMatch().getSinChoiceProvider().choose(run, CHOICE);
        if (i < 0 || i >= MULTIPLIERS.size()) i = 0;   // defensive clamp: an invalid answer falls back to x1
        run.getSinState().setPrideMultiplier(MULTIPLIERS.get(i));
    }

    @Override
    public void onRoundSettled(Run run, BlindResult result) {
        BigDecimal threshold = BigDecimal.valueOf(result.target()).multiply(run.getSinState().getPrideMultiplier());
        run.getSinState().setPrideThresholdMet(result.score().compareTo(threshold) >= 0);
    }
}
