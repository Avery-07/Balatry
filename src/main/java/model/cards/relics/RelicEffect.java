package model.cards.relics;

/** A relic's one-shot effect. */
@FunctionalInterface
public interface RelicEffect {
    void resolve(RelicContext ctx);

    RelicEffect NO_OP = ctx -> { };
}
