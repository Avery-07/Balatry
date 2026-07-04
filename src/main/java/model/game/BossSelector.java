package model.game;

import java.util.random.RandomGenerator;

/**
 * Policy for choosing an ante's boss blind, injected via {@link MatchConfig} like {@link SinSelector}: the
 * default draws from the table-level stream, while tests and scripted matches can pin a specific boss.
 * {@code exclude} carries Metabole's reroll constraint (never re-pick the rerolled boss); it is {@code null}
 * for a normal selection, and implementations that ignore it accept returning the excluded boss.
 */
@FunctionalInterface
public interface BossSelector {

    /** The boss for {@code ante}, never {@code exclude} unless the policy deliberately ignores it. */
    BossBlind select(int ante, RandomGenerator rng, BossBlind exclude);

    /** Seeded default: uniform over the ante's pool (finishers on every 8th ante, regulars otherwise). */
    BossSelector SEEDED = (ante, rng, exclude) -> BossBlind.select(rng, ante, exclude);
}
