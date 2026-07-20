package model.game.player;

/**
 * A seat's sleeve: a per-player run modifier, chosen independently of everyone else's (contrast {@code DeckType},
 * which the whole table shares). Most sleeves are one-shot adjustments applied when the run is built — see
 * {@link Sleeves#apply} — and are live. Two need ongoing engine hooks and are described-but-inert for now, marked
 * below with what they still need.
 */
public enum Sleeve {

    STANDARD ("Standard Sleeve",  "No effect."),
    RED_BLUE ("Red & Blue Sleeve","+1 hand and +1 discard each round."),
    LEGACY   ("Legacy Sleeve",    "Start with an additional $10."),
    BLACK    ("Black Sleeve",     "+1 Joker slot; -1 hand each round."),
    COLORFUL ("Colorful Sleeve",  "+2 hand size; -1 hand each round."),
    SILK     ("Silk Sleeve",      "Start with the Planet Merchant, Tarot Merchant, Relic Merchant and Overstock vouchers."),
    FRACTURE ("Fracture Sleeve",  "10 random cards are removed from the deck."),

    // Behavioural sleeves: described, not yet wired.
    FRUGAL   ("Frugal Sleeve",    "Each round, earn $2 per hand left and $1 per discard left, but no interest."),   // needs RoundSettlement hook
    CELESTIAL("Celestial Sleeve", "Upgrade every hand type by 2 levels each ante; no Planets or Celestial Packs in the shop.");   // needs ante hook + shop pool filter

    private final String displayName;
    private final String description;

    Sleeve(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String displayName() { return displayName; }
    public String description() { return description; }

    @Override public String toString() { return displayName; }
}
