package model.modifiers;

public enum Sticker {
    ETERNAL, // Can not be sold
    PERISHABLE, // Becomes debuffed and negative after 5 rounds
    RENTAL, // At end of round, removes 3$
    DEBUFFED, // Effects are nullified
}
