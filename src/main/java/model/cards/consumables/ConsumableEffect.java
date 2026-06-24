package model.cards.consumableHelpers;

import model.cards.ConsumableCard;

@FunctionalInterface
public interface ConsumableEffect {
    void use(GameContext ctx, ConsumableCard self);
    ConsumableEffect NO_OP = (ctx, self) -> {};
}