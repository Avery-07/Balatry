package model.game.scoring;

public enum Trigger {
    ON_ROUND_START,
    ON_ROUND_END,
    ON_HAND_PLAYED,
    ON_HAND_DISCARDED,
    ON_SCORED_CARD, // Triggers for cards scored when hand is played
    ON_HELD_CARD, // Triggers for cards held in hand when hand is played
    ON_SHOP_START,
    ON_SHOP_END,
    ON_BOUGHT,
    ON_SOLD
}