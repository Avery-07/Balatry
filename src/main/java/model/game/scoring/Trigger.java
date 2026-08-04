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
    ON_SHOP_EMPTIED,     // the shop's card row was just bought out (Scalper)
    ON_SHOP_END,
    ON_PURCHASE_PRICING,
    ON_BOUGHT,
    ON_SOLD,
    ON_SPEND,
    ON_EARN,
    ON_BOSS_DEFEATED,
    ON_CARD_DESTROYED,   // a deck card was destroyed — the card is on Run#getDestroyedCard (Canio)
    ON_PACK_OPENED,      // a booster pack opening began (Hallucination)
    ON_PACK_SKIPPED,     // a booster pack opening was abandoned via Skip (Red Joker)
    ON_LUCKY_TRIGGERED,  // a played Lucky card's chance succeeded this scoring (Lucky Cat)
    ON_JOKER_DESTROYED   // a joker was destroyed — the card is on Run#getDestroyedJoker (Chef Joker)
    // No ON_HOVERED: hover is a local, per-seat, unlogged FX-thread event, so a firing trigger there could
    // mutate one seat's model and desync the lockstep replay. A joker describes its current effect purely
    // instead — see JokerSpec.state and model.game.player.JokerInfo.
}