package model.game;

import model.game.sins.SinChoiceProvider;
import model.game.sins.SinModifier;
import model.game.sins.Sins;

import java.util.function.Function;

/**
 * The injectable policies for a {@link Match}: which sin is active each ante ({@link SinSelector}), how a sin maps
 * to behaviour ({@code sinResolver}, default {@link Sins#modifierFor}), and how a sin's player choices are resolved
 * ({@link SinChoiceProvider}). Bundled into one value so policy injection has a single home rather than an
 * ever-growing list of {@code create} overloads; add a policy here and the call sites keep using {@code with...}.
 *
 * <p>Null components fall back to defaults, so {@link #defaults()} plus a {@code with...} call is the normal path.
 */
public record MatchConfig(SinSelector sinSelector,
                          Function<Sin, SinModifier> sinResolver,
                          SinChoiceProvider sinChoiceProvider) {

    public MatchConfig {
        if (sinSelector == null)       sinSelector = SinSelector.SEEDED_UNIFORM;
        if (sinResolver == null)       sinResolver = Sins::modifierFor;
        if (sinChoiceProvider == null) sinChoiceProvider = SinChoiceProvider.FIRST;
    }

    /** All policies at their defaults. */
    public static MatchConfig defaults() { return new MatchConfig(null, null, null); }

    public MatchConfig withSinSelector(SinSelector s)               { return new MatchConfig(s, sinResolver, sinChoiceProvider); }
    public MatchConfig withSinResolver(Function<Sin, SinModifier> r){ return new MatchConfig(sinSelector, r, sinChoiceProvider); }
    public MatchConfig withSinChoiceProvider(SinChoiceProvider p)   { return new MatchConfig(sinSelector, sinResolver, p); }
}
