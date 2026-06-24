package model.game;

import model.game.rng.Rng;
import model.game.rng.RngSource;

/** Policy for choosing which {@link Sin} is active for a given ante, using the seeded match {@link Rng}. */
@FunctionalInterface
public interface SinSelector {

    Sin selectFor(int ante, Rng matchRng);

    /** Placeholder: deterministic uniform pick keyed by ante. */
    SinSelector SEEDED_UNIFORM = (ante, rng) -> {
        Sin[] all = Sin.values();
        return all[rng.nextInt(RngSource.ANTE_MODIFIER, ante, all.length)];
    };
}