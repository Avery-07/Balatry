package model.game.scoring;

public enum Trigger {
    ON_ROUND_START,
    ON_ROUND_END,
    ON_HAND_PLAYED,
    ON_HAND_DISCARDED,
    ON_SCORED_CARD,
    ON_HELD_CARD,
    ON_SHOP_START,
    ON_SHOP_REROLL,
    ON_SHOP_END,
    ON_PURCHASE_PRICING,
    ON_BOUGHT,
    ON_SOLD,
    ON_SPEND,
    ON_EARN,
    ON_BOSS_DEFEATED
    // No ON_HOVERED: hover is a local, per-seat, unlogged FX-thread event, so a firing trigger there could
    // mutate one seat's model and desync the lockstep replay. A joker describes its current effect purely
    // instead — see JokerSpec.state and model.game.player.JokerInfo.
}