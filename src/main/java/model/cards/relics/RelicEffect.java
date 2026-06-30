package model.cards.relics;

/**
 * A relic's one-shot effect. Unlike {@link model.cards.consumables.ConsumableEffect}, which sees a single
 * {@link model.game.player.Run}, a relic resolves against a {@link RelicContext}: the caster, the (optional)
 * target seat, the owning match, and the caster's selection. Named {@code resolve} to read as a cross-player
 * action rather than a self-application.
 */
@FunctionalInterface
public interface RelicEffect {
    void resolve(RelicContext ctx);

    RelicEffect NO_OP = ctx -> { };
}
