package model.modifiers;

/**
 * A modifier stuck to a card, usually a drawback traded for getting the card at all. Most arrive from the higher
 * {@link model.game.Stake}s, which is largely what makes those stakes harder. {@link #DEBUFFED} is the odd one
 * out: it is the engine's own "this card does nothing right now" marker, applied by bosses, relics and sins as
 * well as by {@link #PERISHABLE} and {@link #FRAGILE} turning sour.
 */
public enum Sticker {
    ETERNAL,    // cannot be sold or destroyed
    PERISHABLE, // becomes debuffed and negative after 5 rounds
    RENTAL,     // costs $3 at the end of each round
    FLOATING,   // moves to a random board position at the start of each hand
    DELAYED,    // has no effect on the round's first hand
    FRAGILE,    // becomes debuffed if a hand scores under a tenth of the blind's target
    STICKY,     // costs money to sell, and that cost grows every round
    DEBUFFED,   // effects are nullified
}
