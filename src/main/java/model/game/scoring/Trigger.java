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
    /** While a purchase is being priced, before it is validated or paid; grant-style effects (a free purchase) act here. */
    ON_PURCHASE_PRICING,
    /** After a purchase has completed (paid and final); counting/reacting effects act here. Never fires for a failed buy. */
    ON_BOUGHT,
    ON_SOLD,
    ON_SPEND,
    ON_EARN,
    ON_BOSS_DEFEATED
}