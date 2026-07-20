package model.cards;

/**
 * The starting deck, chosen once for the whole table — every seat plays the same deck type, so it is part of the
 * match's shared configuration rather than a per-seat choice (contrast {@code Sleeve} and {@code Stake}, which are
 * per-seat). Most types only change how {@link Decks#of} builds the 52 cards; the rest change shop or scoring
 * behaviour and are marked below with the hook they still need.
 */
public enum DeckType {

    STANDARD    ("Standard Deck",  "No effect."),
    ABANDONED   ("Abandoned Deck", "Start the run with no face cards."),
    CROWDED     ("Crowded Deck",   "Start the run with twice as many face cards."),
    CHECKERED   ("Checkered Deck", "Start the run with 26 Spades and 26 Hearts."),
    ERRATIC     ("Erratic Deck",   "Every card's rank and suit is randomized."),

    // Behavioural decks: the deck itself is standard, the effect lives elsewhere in the engine.
    BAZAAR      ("Bazaar Deck",    "Shop rerolls also refresh the packs; packs cost $1 more."),
    GHOST       ("Ghost Deck",     "Spectral cards may appear in the shop."),
    ANAGLYPH    ("Anaglyph Deck",  "Defeating a boss blind grants a Double Tag and a Fool."),
    PLASMA      ("Plasma Deck",    "Chips and Mult are balanced before scoring; blinds are twice as large."),
    ECLIPSE     ("Eclipse Deck",   "Start with the Showman voucher and an Invisible Joker.");

    private final String displayName;
    private final String description;

    DeckType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String displayName() { return displayName; }
    public String description() { return description; }

    /** Whether {@link Decks#of} needs randomness to build this deck (only Erratic does). */
    public boolean isRandomized() { return this == ERRATIC; }

    @Override public String toString() { return displayName; }
}
