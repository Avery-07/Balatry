package model.cards.jokerHelpers;

import model.cards.JokerCard;

@FunctionalInterface
public interface JokerEffect {
    void apply(GameContext ctx, JokerCard self);
    JokerEffect NO_OP = (ctx, self) -> {};
}