package model.game;

import model.game.sins.SinChoiceProvider;
import model.game.sins.SinModifier;
import model.game.sins.Sins;

import java.util.function.Function;

/**
 * The injectable policies for a {@link Match}: which sin is active each ante ({@link SinSelector}), how a sin maps
 * to behaviour ({@code sinResolver}, default {@link Sins#modifierFor}), how a sin's player choices are resolved
 * ({@link SinChoiceProvider}), and how many antes the match runs ({@code anteCount}, default 7). Bundled into one
 * value so policy injection has a single home rather than an ever-growing list of {@code create} overloads; add a
 * policy here and the call sites keep using {@code with...}.
 *
 * <p>Null (or, for {@code anteCount}, non-positive) components fall back to defaults, so {@link #defaults()} plus
 * a {@code with...} call is the normal path.
 */
public record MatchConfig(SinSelector sinSelector,
                          Function<Sin, SinModifier> sinResolver,
                          SinChoiceProvider sinChoiceProvider,
                          int anteCount) {

    /** The standard match length in antes. */
    public static final int DEFAULT_ANTE_COUNT = 7;

    public MatchConfig {
        if (sinSelector == null)       sinSelector = SinSelector.SEEDED_UNIFORM;
        if (sinResolver == null)       sinResolver = Sins::modifierFor;
        if (sinChoiceProvider == null) sinChoiceProvider = SinChoiceProvider.FIRST;
        if (anteCount <= 0)            anteCount = DEFAULT_ANTE_COUNT;
    }

    /** All policies at their defaults. */
    public static MatchConfig defaults() { return new MatchConfig(null, null, null, 0); }

    public MatchConfig withSinSelector(SinSelector s)               { return new MatchConfig(s, sinResolver, sinChoiceProvider, anteCount); }
    public MatchConfig withSinResolver(Function<Sin, SinModifier> r){ return new MatchConfig(sinSelector, r, sinChoiceProvider, anteCount); }
    public MatchConfig withSinChoiceProvider(SinChoiceProvider p)   { return new MatchConfig(sinSelector, sinResolver, p, anteCount); }
    public MatchConfig withAnteCount(int n)                         { return new MatchConfig(sinSelector, sinResolver, sinChoiceProvider, n); }
}
