package model.game;

import model.game.sins.SinChoiceProvider;
import model.game.sins.SinModifier;
import model.game.sins.Sins;

import java.util.function.Function;

/** The injectable policies for a {@link Match}: which sin is active each ante ({@link SinSelector}), which boss closes it ({@link BossSelector}), how a sin maps to behaviour ({@code sinResolver}, default {@link Sins#modifierFor}), how a sin's player choices are resolved ({@link SinChoiceProvider}), how settled results become competition points ({@link PointsPolicy}, default {@link ProportionalPointsPolicy}), and how many antes the match runs ({@code anteCount}, default 7). */
public record MatchConfig(SinSelector sinSelector,
                          BossSelector bossSelector,
                          Function<Sin, SinModifier> sinResolver,
                          SinChoiceProvider sinChoiceProvider,
                          PointsPolicy pointsPolicy,
                          int anteCount,
                          boolean blindSelection) {

    /** The standard match length in antes. */
    public static final int DEFAULT_ANTE_COUNT = 7;

    public MatchConfig {
        if (sinSelector == null)       sinSelector = SinSelector.SEEDED_UNIFORM;
        if (bossSelector == null)      bossSelector = BossSelector.SEEDED;
        if (sinResolver == null)       sinResolver = Sins::modifierFor;
        if (sinChoiceProvider == null) sinChoiceProvider = SinChoiceProvider.FIRST;
        if (pointsPolicy == null)      pointsPolicy = PointsPolicy.PROPORTIONAL;
        if (anteCount <= 0)            anteCount = DEFAULT_ANTE_COUNT;
    }

    /** All policies at their defaults. */
    public static MatchConfig defaults() { return new MatchConfig(null, null, null, null, null, 0, false); }

    public MatchConfig withSinSelector(SinSelector s)               { return new MatchConfig(s, bossSelector, sinResolver, sinChoiceProvider, pointsPolicy, anteCount, blindSelection); }
    public MatchConfig withBossSelector(BossSelector b)             { return new MatchConfig(sinSelector, b, sinResolver, sinChoiceProvider, pointsPolicy, anteCount, blindSelection); }
    public MatchConfig withSinResolver(Function<Sin, SinModifier> r){ return new MatchConfig(sinSelector, bossSelector, r, sinChoiceProvider, pointsPolicy, anteCount, blindSelection); }
    public MatchConfig withSinChoiceProvider(SinChoiceProvider p)   { return new MatchConfig(sinSelector, bossSelector, sinResolver, p, pointsPolicy, anteCount, blindSelection); }
    public MatchConfig withPointsPolicy(PointsPolicy p)             { return new MatchConfig(sinSelector, bossSelector, sinResolver, sinChoiceProvider, p, anteCount, blindSelection); }
    public MatchConfig withAnteCount(int n)                         { return new MatchConfig(sinSelector, bossSelector, sinResolver, sinChoiceProvider, pointsPolicy, n, blindSelection); }
    public MatchConfig withBlindSelection(boolean b)               { return new MatchConfig(sinSelector, bossSelector, sinResolver, sinChoiceProvider, pointsPolicy, anteCount, b); }
}
